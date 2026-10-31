"""Launch real dev servers and clients and drive the knockdown modes and notice end to end.

Run: python tools/check_knockdown_runtime.py [--singleplayer-only] [module ...]

Per module:
  dedicated  runServer (offline, RCON) + runClient joining it: first-join notice, console change while online,
             NEVER death, console change while the player is offline, notice on the next join.
  first      runClient into a copy of that world in singleplayer: every mode, coefficient 0 and 5, no notice for
             coefficient changes outside CHANCE, a downed player surviving a switch to NEVER, superseded notices.
  seen       rejoin: no notice for unchanged settings.
  config     edit the server config file while offline (knockdownMode = never): notice on the next join.
Requires a JDK with java/javac/jar on PATH. Logs, results and screenshots stay in each module's build/ and run/.
Touched run files (server.properties, eula.txt, the First Aid server config) are restored afterwards.
"""
from pathlib import Path
import json
import os
import re
import shutil
import socket
import struct
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
MODULES = ("forge1.20.1", "fabric1.21.1", "neoforge1.21.1", "fabric26.2",
           "neoforge26.2", "fabric26.3", "neoforge26.3")
WORLD = "KnockdownQA"
SERVER_WORLD = "KnockdownQA-server"
RCON_PORT, RCON_PASSWORD, SERVER_PORT = 25585, "firstaid-knockdown", 25595
# --singleplayer-only reuses the world a previous dedicated stage generated
singleplayer_only = "--singleplayer-only" in sys.argv
selected = [arg for arg in sys.argv[1:] if not arg.startswith("--")] or MODULES
if any(module not in MODULES for module in selected):
    raise SystemExit("Choose maintained modules: " + ", ".join(MODULES))

probe = ROOT / "neoforge26.3/build/knockdown-probe"
probe.mkdir(parents=True, exist_ok=True)
subprocess.run(["javac", "--release", "17", "-d", str(probe), str(ROOT / "tools/KnockdownRuntimeProbe.java")], check=True)
(probe / "MANIFEST.MF").write_text("Manifest-Version: 1.0\nAgent-Class: KnockdownRuntimeProbe\n\n", encoding="utf-8")
probe_jar = probe / "probe.jar"
subprocess.run(["jar", "cfm", str(probe_jar), str(probe / "MANIFEST.MF"), "-C", str(probe), "."], check=True)


def java_pids(module, pattern):
    env = dict(os.environ, FIRSTAID_PROBE_MODULE=module, FIRSTAID_PROBE_PATTERN=pattern)
    command = r"""
    @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
      Where-Object { $_.CommandLine -and $_.CommandLine.Contains('\' + $env:FIRSTAID_PROBE_MODULE + '\') -and
                     ($_.CommandLine -match $env:FIRSTAID_PROBE_PATTERN) } |
      ForEach-Object { $_.ProcessId }) | ConvertTo-Json -Compress
    """
    output = subprocess.check_output(["powershell", "-NoProfile", "-Command", command], env=env, text=True).strip()
    value = json.loads(output) if output else []
    return value if isinstance(value, list) else [value]


# any dev game JVM of the module; new clients are told apart from the running server by their PID
CLIENT = r"devlaunchinjector.Main|net.neoforged.DevLaunch|forgeclientuserdev|forgeserveruserdev"


def rcon(command):
    with socket.create_connection(("127.0.0.1", RCON_PORT), timeout=20) as sock:
        def send(request_id, kind, payload):
            data = struct.pack("<ii", request_id, kind) + payload.encode("utf-8") + b"\0\0"
            sock.sendall(struct.pack("<i", len(data)) + data)
            length = struct.unpack("<i", recv(4))[0]
            body = recv(length)
            return struct.unpack("<ii", body[:8])[0], body[8:-2].decode("utf-8", "replace")

        def recv(count):
            data = b""
            while len(data) < count:
                chunk = sock.recv(count - len(data))
                if not chunk:
                    raise ConnectionError("RCON closed")
                data += chunk
            return data

        if send(1, 3, RCON_PASSWORD)[0] == -1:
            raise SystemExit("RCON login failed")
        return send(2, 2, command)[1]


class Module:
    def __init__(self, name):
        self.name = name
        self.root = ROOT / name
        self.build = self.root / "build"
        self.build.mkdir(exist_ok=True)
        forge = name.startswith("forge")
        self.client_dir = self.root / ("run/client" if forge else "run")
        self.server_dir = self.root / ("run/server" if forge else "run")
        self.fabric = name.startswith("fabric")
        self.neoforge = name.startswith("neoforge")
        self.backups = {}
        # originals are kept on disk too, so an interrupted run is undone by the next one
        self.backup_dir = self.build / "knockdown-backup"
        self.restore()

    def backup(self, path):
        if path in self.backups:
            return
        self.backups[path] = path.read_bytes() if path.exists() else None
        self.backup_dir.mkdir(exist_ok=True)
        index = len(list(self.backup_dir.glob("*.json")))
        entry = {"path": str(path), "present": self.backups[path] is not None}
        if entry["present"]:
            (self.backup_dir / f"{index}.data").write_bytes(self.backups[path])
        (self.backup_dir / f"{index}.json").write_text(json.dumps(entry), encoding="utf-8")

    def restore(self):
        for path, data in self.backups.items():
            if data is None:
                path.unlink(missing_ok=True)
            else:
                path.write_bytes(data)
        if self.backup_dir.exists():
            for entry_file in self.backup_dir.glob("*.json"):
                entry = json.loads(entry_file.read_text(encoding="utf-8"))
                path = Path(entry["path"])
                if path in self.backups:
                    continue
                if entry["present"]:
                    path.write_bytes(entry_file.with_suffix(".data").read_bytes())
                else:
                    path.unlink(missing_ok=True)
            shutil.rmtree(self.backup_dir)
        self.backups = {}

    def config_file(self, run_dir):
        # NeoForge and Forge keep the SERVER config in the run's global config/, not per world
        if self.fabric:
            return run_dir / "config/firstaid/firstaid-server.json"
        return run_dir / "config/firstaid-server.toml"

    def gradle(self, task, log_name, args=None):
        command = ["cmd", "/c", "gradlew.bat", task, "--console=plain"]
        if args:
            command.append("--args=" + args)
        log = (self.build / log_name).open("w", encoding="utf-8")
        # cmd must find gradlew.bat in the module directory
        env = {key: value for key, value in os.environ.items() if key.lower() != "nodefaultcurrentdirectoryinexepath"}
        process = subprocess.Popen(command, cwd=self.root, stdout=log, stderr=subprocess.STDOUT, env=env,
                                   stdin=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW)
        return process, log

    def client(self, phase, quick_play, store, expected="", status=None):
        result = self.build / f"knockdown-{phase}-result.txt"
        result.unlink(missing_ok=True)
        status = status or (self.build / "knockdown-status.txt")
        status.write_text("", encoding="utf-8")
        # Fabric Loom keeps its launch arguments when --args is given, so it uses quick play and a fixed name;
        # NeoForge and Forge replace them, so their probe opens the world itself (their dev name is always "Dev").
        args = quick_play + " --username " + WORLD if self.fabric else None
        join = "" if self.fabric else ("sp:" + WORLD if "Singleplayer" in quick_play else "mp:localhost:" + str(SERVER_PORT))
        before = set(java_pids(self.name, CLIENT))
        process, log = self.gradle("runClient", f"knockdown-{phase}-client.log", args)
        try:
            deadline = time.monotonic() + 240
            pid = None
            while time.monotonic() < deadline and pid is None:
                if process.poll() is not None:
                    raise SystemExit(f"{self.name} {phase}: client exited during startup; see {log.name}")
                text = Path(log.name).read_text(encoding="utf-8", errors="replace")
                if "Sound engine started" in text or "Sound engine initialized" in text:
                    fresh = [p for p in java_pids(self.name, CLIENT) if p not in before]
                    if len(fresh) == 1:
                        pid = fresh[0]
                time.sleep(1)
            if pid is None:
                raise SystemExit(f"{self.name} {phase}: client startup timed out; see {log.name}")
            options = "|".join((phase, str(result), str(status), str(store), expected, join))
            subprocess.run(["java", "-cp", str(probe), "KnockdownRuntimeProbe", str(pid), str(probe_jar), options], check=True)
            deadline = time.monotonic() + 600
            while not result.exists() and time.monotonic() < deadline:
                if process.poll() is not None and not result.exists():
                    raise SystemExit(f"{self.name} {phase}: client exited before the probe finished; see {log.name}")
                request = status.read_text(encoding="utf-8").strip()
                if request.startswith("REQ "):
                    name = request[4:]
                    reply = rcon({"never": "firstaid knockdown never",
                                  "damage": "execute as @a run damagePart HEAD 100"}[name])
                    print(f"  rcon {name}: {reply[:120]!r}", flush=True)
                    status.write_text("DONE " + name, encoding="utf-8")
                time.sleep(0.25)
            if not result.exists():
                raise SystemExit(f"{self.name} {phase}: probe timed out")
            output = result.read_text(encoding="utf-8")
            try:
                process.wait(timeout=120)
            except subprocess.TimeoutExpired:
                dump = self.build / f"knockdown-{phase}-hang.txt"
                with dump.open("w", encoding="utf-8") as out:
                    subprocess.run(["jstack", str(pid)], stdout=out, stderr=subprocess.STDOUT)
                raise SystemExit(f"{self.name} {phase}: client did not exit after the probe finished; thread dump in {dump.name}")
        finally:
            if process.poll() is None:
                subprocess.run(["taskkill", "/F", "/T", "/PID", str(process.pid)], capture_output=True)
            log.close()
        if not output.startswith("PASS"):
            raise SystemExit(f"{self.name} {phase}:\n{output}")
        print(f"PASS {self.name} {phase}: {output.count(chr(10) + 'PASS')} checks", flush=True)
        return output

    def dedicated(self):
        properties = self.server_dir / "server.properties"
        eula = self.server_dir / "eula.txt"
        self.backup(properties)
        self.backup(eula)
        eula.write_text("eula=true\n", encoding="utf-8")
        text = properties.read_text(encoding="utf-8") if properties.exists() else ""
        values = {"level-name": SERVER_WORLD, "online-mode": "false", "enable-rcon": "true", "rcon.port": str(RCON_PORT),
                  "rcon.password": RCON_PASSWORD, "server-port": str(SERVER_PORT), "broadcast-rcon-to-ops": "false",
                  "white-list": "false", "enforce-whitelist": "false"}
        for key, value in values.items():
            if re.search(rf"^{re.escape(key)}=.*$", text, flags=re.M):
                text = re.sub(rf"^{re.escape(key)}=.*$", f"{key}={value}", text, flags=re.M)
            else:
                text += f"\n{key}={value}"
        properties.write_text(text + "\n", encoding="utf-8")
        world = self.server_dir / SERVER_WORLD
        shutil.rmtree(world, ignore_errors=True)
        server_config = self.config_file(self.server_dir)
        self.backup(server_config)
        server, log = self.gradle("runServer", "knockdown-dedicated-server.log")
        try:
            deadline = time.monotonic() + 420
            while time.monotonic() < deadline:
                if server.poll() is not None:
                    raise SystemExit(f"{self.name}: dedicated server exited during startup; see {log.name}")
                if "RCON running" in Path(log.name).read_text(encoding="utf-8", errors="replace"):
                    break
                time.sleep(1)
            else:
                raise SystemExit(f"{self.name}: dedicated server startup timed out; see {log.name}")
            reply = rcon("firstaid knockdown")
            assert "knockdown" in reply.lower() or "倒地" in reply, reply
            print(f"  rcon query: {reply[:120]!r}", flush=True)
            reply = rcon("firstaid knockdown chance")
            store = world / "firstaid/knockdown_notice.json"
            self.client("remote1", f"--quickPlayMultiplayer localhost:{SERVER_PORT}", store)
            reply = rcon("firstaid knockdown always")
            print(f"  rcon always (player offline): {reply[:120]!r}", flush=True)
            self.client("remote2", f"--quickPlayMultiplayer localhost:{SERVER_PORT}", store, "ALWAYS")
            rcon("firstaid knockdown chance")
            persisted = server_config.read_text(encoding="utf-8")
            assert re.search(r'knockdownMode"?\s*[=:]\s*"(?:CHANCE|chance)"', persisted), "knockdownMode not persisted in " + str(server_config)
            assert "knockdownOverflowCoefficient" in persisted, "coefficient not persisted"
            try:
                rcon("stop")
            except (ConnectionError, OSError):
                pass
            server.wait(timeout=180)
        finally:
            if server.poll() is None:
                subprocess.run(["taskkill", "/F", "/T", "/PID", str(server.pid)], capture_output=True)
            log.close()
        server_log = Path(log.name).read_text(encoding="utf-8", errors="replace")
        problems = [line for line in server_log.splitlines() if re.search(r"(Exception|ERROR).*(firstaid|Knockdown)", line, re.I)]
        assert not problems, "server log problems:\n" + "\n".join(problems[:20])
        print(f"PASS {self.name} dedicated server: boot, RCON commands, persisted config, clean shutdown", flush=True)
        return world

    def singleplayer(self, source_world):
        saves = self.client_dir / "saves"
        world = saves / WORLD
        shutil.rmtree(world, ignore_errors=True)
        shutil.copytree(source_world, world, ignore=shutil.ignore_patterns("session.lock", "firstaid", "serverconfig"))
        config = self.config_file(self.client_dir)
        self.backup(config)
        store = world / "firstaid/knockdown_notice.json"
        quick = "--quickPlaySingleplayer " + WORLD
        self.client("first", quick, store)
        self.client("seen", quick, store)
        text = config.read_text(encoding="utf-8")
        edited = re.sub(r'(knockdownMode"?\s*[=:]\s*")(?:CHANCE|chance)(")', lambda m: m.group(1) + ("never" if self.fabric else "NEVER") + m.group(2), text)
        assert edited != text, "could not find knockdownMode in " + str(config)
        config.write_text(edited, encoding="utf-8")
        self.client("config", quick, store, "NEVER")
        config.write_text(text, encoding="utf-8")


for name in selected:
    module = Module(name)
    if java_pids(name, CLIENT):
        raise SystemExit(f"{name}: a dev client is already running; close it before this automated launch.")
    try:
        server_world = module.server_dir / SERVER_WORLD if singleplayer_only else module.dedicated()
        module.singleplayer(server_world)
    finally:
        module.restore()
    print(f"PASS {name}: knockdown runtime scenarios", flush=True)

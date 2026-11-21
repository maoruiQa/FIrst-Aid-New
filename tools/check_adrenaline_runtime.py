"""Launch real dev clients and drive the combat adrenaline limits in a singleplayer world.

Run: python tools/check_adrenaline_runtime.py [module ...]

Per module, runClient opens a copy of the KnockdownQA world (made by tools/check_knockdown_runtime.py) as
AdrenalineQA and tools/AdrenalineRuntimeProbe.java drives the integrated server: /firstaid adrenaline maxlevel,
attackgain, attackcooldown and attackhold, /damage hits on real husks through the loader's damage events, the
injector above the cap, config persistence (settings issued in one tick and settings saved while the loader's config
watcher reloads an older copy), /firstaid reset and two 25 second horde fights with screenshots.
Requires a JDK with java/javac/jar on PATH. Logs and results go to each module's build/, screenshots to its run
screenshots/. The First Aid server config of the run directory is restored afterwards.
"""
from pathlib import Path
import json
import os
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
MODULES = ("forge1.20.1", "fabric1.21.1", "neoforge1.21.1", "fabric26.2",
           "neoforge26.2", "fabric26.3", "neoforge26.3")
SOURCE_WORLD, WORLD = "KnockdownQA", "AdrenalineQA"
CLIENT = r"devlaunchinjector.Main|net.neoforged.DevLaunch|forgeclientuserdev"
selected = [arg for arg in sys.argv[1:] if not arg.startswith("--")] or MODULES
if any(module not in MODULES for module in selected):
    raise SystemExit("Choose maintained modules: " + ", ".join(MODULES))

probe = ROOT / "neoforge26.3/build/adrenaline-probe"
probe.mkdir(parents=True, exist_ok=True)
subprocess.run(["javac", "--release", "17", "-d", str(probe), str(ROOT / "tools/AdrenalineRuntimeProbe.java")], check=True)
(probe / "MANIFEST.MF").write_text("Manifest-Version: 1.0\nAgent-Class: AdrenalineRuntimeProbe\n\n", encoding="utf-8")
probe_jar = probe / "probe.jar"
subprocess.run(["jar", "cfm", str(probe_jar), str(probe / "MANIFEST.MF"), "-C", str(probe), "."], check=True)


def java_pids(module):
    env = dict(os.environ, FIRSTAID_PROBE_MODULE=module, FIRSTAID_PROBE_PATTERN=CLIENT)
    command = r"""
    @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
      Where-Object { $_.CommandLine -and $_.CommandLine.Contains('\' + $env:FIRSTAID_PROBE_MODULE + '\') -and
                     ($_.CommandLine -match $env:FIRSTAID_PROBE_PATTERN) } |
      ForEach-Object { $_.ProcessId }) | ConvertTo-Json -Compress
    """
    output = subprocess.check_output(["powershell", "-NoProfile", "-Command", command], env=env, text=True).strip()
    value = json.loads(output) if output else []
    return value if isinstance(value, list) else [value]


def run_module(name):
    root = ROOT / name
    build = root / "build"
    build.mkdir(exist_ok=True)
    fabric = name.startswith("fabric")
    run_dir = root / ("run/client" if name.startswith("forge") else "run")
    config = run_dir / ("config/firstaid/firstaid-server.json" if fabric else "config/firstaid-server.toml")
    source = run_dir / "saves" / SOURCE_WORLD
    if not source.exists():
        raise SystemExit(f"{name}: {source} is missing; run tools/check_knockdown_runtime.py {name} first")
    world = run_dir / "saves" / WORLD
    shutil.rmtree(world, ignore_errors=True)
    shutil.copytree(source, world, ignore=shutil.ignore_patterns("session.lock", "firstaid", "serverconfig"))
    saved_config = config.read_bytes() if config.exists() else None
    result = build / "adrenaline-runtime-result.txt"
    result.unlink(missing_ok=True)
    log_path = build / "adrenaline-runtime-client.log"
    # Fabric Loom keeps its launch arguments with --args, so quick play opens the world; NeoForge and Forge replace
    # them, so the probe opens the world itself.
    command = ["cmd", "/c", "gradlew.bat", "runClient", "--console=plain"]
    if fabric:
        command.append(f"--args=--quickPlaySingleplayer {WORLD} --username {WORLD}")
    join = "" if fabric else "sp:" + WORLD
    env = {key: value for key, value in os.environ.items() if key.lower() != "nodefaultcurrentdirectoryinexepath"}
    if java_pids(name):
        raise SystemExit(f"{name}: a dev client is already running; close it first")
    before = set(java_pids(name))
    log = log_path.open("w", encoding="utf-8")
    process = subprocess.Popen(command, cwd=root, stdout=log, stderr=subprocess.STDOUT, env=env,
                               stdin=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        deadline = time.monotonic() + 300
        pid = None
        while time.monotonic() < deadline and pid is None:
            if process.poll() is not None:
                raise SystemExit(f"{name}: client exited during startup; see {log_path}")
            text = log_path.read_text(encoding="utf-8", errors="replace")
            if "Sound engine started" in text or "Sound engine initialized" in text:
                fresh = [p for p in java_pids(name) if p not in before]
                if len(fresh) == 1:
                    pid = fresh[0]
            time.sleep(1)
        if pid is None:
            raise SystemExit(f"{name}: client startup timed out; see {log_path}")
        options = "|".join((str(result), str(config), join))
        subprocess.run(["java", "-cp", str(probe), "AdrenalineRuntimeProbe", str(pid), str(probe_jar), options], check=True)
        deadline = time.monotonic() + 900
        while not result.exists() and time.monotonic() < deadline:
            if process.poll() is not None and not result.exists():
                raise SystemExit(f"{name}: client exited before the probe finished; see {log_path}")
            time.sleep(0.5)
        if not result.exists():
            raise SystemExit(f"{name}: probe timed out")
        output = result.read_text(encoding="utf-8")
        try:
            process.wait(timeout=120)
        except subprocess.TimeoutExpired:
            pass
    finally:
        if process.poll() is None:
            subprocess.run(["taskkill", "/F", "/T", "/PID", str(process.pid)], capture_output=True)
        log.close()
        if saved_config is None:
            config.unlink(missing_ok=True)
        else:
            config.write_bytes(saved_config)
    client_log = log_path.read_text(encoding="utf-8", errors="replace")
    problems = [line for line in client_log.splitlines()
                if ("Exception" in line or "ERROR" in line) and ("firstaid" in line.lower() or "adrenaline" in line.lower())]
    for line in output.splitlines():
        if line.startswith(("DATA", "FAIL")) or line.startswith("  at") and "FAIL" in output[:10]:
            print("  " + line, flush=True)
    if not output.startswith("PASS"):
        raise SystemExit(f"{name}:\n{output}")
    if problems:
        raise SystemExit(f"{name}: First Aid errors in the client log:\n" + "\n".join(problems[:20]))
    print(f"PASS {name}: {output.count(chr(10) + 'PASS')} adrenaline runtime checks", flush=True)


for module in selected:
    run_module(module)

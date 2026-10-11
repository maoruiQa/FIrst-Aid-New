"""Launch dev clients and probe real sound/command APIs, without desktop input.

Run: python tools/check_tinnitus_runtime.py [--lifetime] [module ...]
--lifetime checks native fade, expiry and cancellation without unrelated camera/config checks.
Requires a JDK with java/javac/jar on PATH. Logs/results stay in module build directories.
"""
from pathlib import Path
import json
import os
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
MODULES = ("forge1.20.1", "fabric1.21.1", "neoforge1.21.1", "fabric26.2",
           "neoforge26.2", "fabric26.3", "neoforge26.3")
lifetime_only = "--lifetime" in sys.argv[1:]
selected = [arg for arg in sys.argv[1:] if arg != "--lifetime"] or MODULES
if any(module not in MODULES for module in selected):
    raise SystemExit("Choose maintained modules: " + ", ".join(MODULES))
probe = ROOT / "fabric26.2/build/tinnitus-probe"
probe.mkdir(parents=True, exist_ok=True)
subprocess.run(["javac", "--release", "17", "-d", str(probe), str(ROOT / "tools/TinnitusRuntimeProbe.java")], check=True)
(probe / "MANIFEST.MF").write_text("Manifest-Version: 1.0\nAgent-Class: TinnitusRuntimeProbe\n\n", encoding="utf-8")
jar = probe / "probe.jar"
subprocess.run(["jar", "cfm", str(jar), str(probe / "MANIFEST.MF"), "-C", str(probe), "TinnitusRuntimeProbe.class"], check=True)


def clients(module):
    env = dict(os.environ, FIRSTAID_PROBE_MODULE=module)
    command = r"""
    @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
      Where-Object { $_.CommandLine -and $_.CommandLine.Contains('\' + $env:FIRSTAID_PROBE_MODULE + '\') -and
                     ($_.CommandLine -match 'devlaunchinjector.Main|net.neoforged.DevLaunch|forgeclientuserdev') } |
      ForEach-Object { $_.ProcessId }) | ConvertTo-Json -Compress
    """
    output = subprocess.check_output(["powershell", "-NoProfile", "-Command", command], env=env, text=True).strip()
    value = json.loads(output) if output else []
    return value if isinstance(value, list) else [value]


for module in selected:
    if clients(module):
        raise SystemExit(f"{module}: a dev client is already running; close it before this automated launch.")
    build = ROOT / module / "build"
    build.mkdir(exist_ok=True)
    result = build / ("tinnitus-lifetime-result.txt" if lifetime_only else "tinnitus-runtime-result.txt")
    result.unlink(missing_ok=True)
    with (build / "tinnitus-runtime-client.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(["cmd", "/c", "gradlew.bat", "runClient", "--console=plain"],
                                   cwd=ROOT / module, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise SystemExit(f"{module}: client exited during startup; see {log.name}")
            text = Path(log.name).read_text(encoding="utf-8", errors="replace")
            if "Sound engine started" in text or "Sound engine initialized" in text:
                targets = clients(module)
                if len(targets) == 1:
                    break
            time.sleep(1)
        else:
            raise SystemExit(f"{module}: startup timed out; see {log.name}")
        output_arg = ("lifetime:" if lifetime_only else "") + str(result)
        subprocess.run(["java", "-cp", str(probe), "TinnitusRuntimeProbe", str(targets[0]), str(jar), output_arg], check=True)
        deadline = time.monotonic() + 40
        while not result.exists() and time.monotonic() < deadline:
            time.sleep(0.25)
        if not result.exists():
            raise SystemExit(f"{module}: runtime probe timed out")
        output = result.read_text(encoding="utf-8")
        if output.startswith("FAIL"):
            raise SystemExit(f"{module}: {output}")
        process.wait(timeout=30)
        if process.returncode != 0:
            raise SystemExit(f"{module}: client shutdown failed; see {log.name}")
    print(f"PASS {module}: {output.count('PASS')} native checks; {result}", flush=True)

"""Check real blur transition methods and post-effect wiring in all maintained modules.

Run: python tools/check_pain_blur.py (requires javac/java on PATH).
"""
from pathlib import Path
import json
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MODULES = ("forge1.20.1", "fabric1.21.1", "neoforge1.21.1", "fabric26.2",
           "neoforge26.2", "fabric26.3", "neoforge26.3")


def method(source, signature):
    start = source.index(signature)
    end = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


STUBS = """
static class FirstAid { static boolean enablePainBlur = true; }
static class PlayerDamageModel { static final float PAIN_HARD_CAP = 1.85F; }
static class Mth {
    static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    static float lerp(float t, float a, float b) { return a + t * (b - a); }
}
static void check(boolean ok) { if (!ok) throw new AssertionError(); }
static void same(float a, float b) { check(Math.abs(a - b) < 0.00001F); }
"""

TESTS = """
void step() {
    lastBlurStrength = blurStrength;
    updateBlurStrength();
    check(blurStrength >= 0 && blurStrength <= 0.262F);
    check(blurStrength - lastBlurStrength <= 0.01801F);
    check(lastBlurStrength - blurStrength <= 0.01001F);
    same(getBlurStrength(-1), lastBlurStrength);
    same(getBlurStrength(2), blurStrength);
    same(getBlurStrength(0.5F), (lastBlurStrength + blurStrength) / 2);
    for (int i = 1; i <= 20; i++) {
        float before = getBlurStrength((i - 1) / 20F);
        float after = getBlurStrength(i / 20F);
        check(Math.abs(after - before) <= 0.00091F);
    }
}
void settle() { for (int i = 0; i < 50; i++) step(); }
void run() {
    FirstAid.enablePainBlur = true;
    for (float pain : new float[]{0.1F, 0.5F, 1.85F}) {
        painStrength = pain;
        step();
        check(blurStrength > 0 && blurStrength < expected(pain));
        settle();
        same(blurStrength, expected(pain));
        painStrength = 0;
        float previous = blurStrength;
        step();
        check(blurStrength < previous && blurStrength > 0);
        settle();
        same(blurStrength, 0);
    }
    // Damage/healing/relief changes can reverse during a transition without snapping.
    for (float pain : new float[]{0.03F, 1.65F, 0.5F, 0, 1.1F, 0.04F, 0.41F, 0.71F, 0}) {
        painStrength = pain;
        step();
        step();
    }
    settle();
    for (float strength : new float[]{0.1F, 0.3F, 0.8F, 0}) {
        suppressionStrength = strength;
        step();
        settle();
        if (strength > 0) check(blurStrength > 0);
    }
    painStrength = 1;
    settle();
    FirstAid.enablePainBlur = false;
    step();
    check(blurStrength > 0);
    settle();
    same(blurStrength, 0);
    FirstAid.enablePainBlur = true;
}
"""

classes = []
for index, module in enumerate(MODULES):
    source_set = "client" if module.startswith("fabric") else "main"
    client = ROOT / module / f"src/{source_set}/java/ichttt/mods/firstaid/client"
    source = (client / "PainVisualEffectsController.java").read_text(encoding="utf-8")
    classic = "26." not in module
    tick = method(source, "public void tick(")
    assert tick.count("lastBlurStrength = blurStrength;") == 1
    assert tick.count("updateBlurStrength();") == 2  # Normal tick and invalid/dead player.
    clear = method(source, "public void clear(")
    assert "blurStrength = 0.0F;" in clear and "lastBlurStrength = 0.0F;" in clear
    signatures = ["private void updateBlurStrength(", "public float getBlurStrength(", "private static float approach("]
    constants = "\n".join(re.findall(r"private static final float (?:PAIN_SHADER_PEAK|SUPPRESSION_BLUR_PEAK|MORPHINE_SAT_PEAK|SUPPRESSION_DESAT_PEAK) = [^;]+;", source))
    extra = ""
    if classic:
        expected = "return 0.18F * Math.min(1.45F, 0.22F + pain * 0.70F);"
        assert '"Strength", getBlurStrength(partialTick)' in source
        assert "blurStrength > 0.0F || lastBlurStrength > 0.0F" in source
    else:
        expected = "return pain > 0.7F ? 0.14F : pain > 0.4F ? 0.10F : 0.07F;"
        signatures += ["private ActiveEffect resolveTargetEffect(", "private ActiveEffect resolveDesiredEffect(", "public float getNetColorAmount("]
        extra = method(source, "private enum ActiveEffect") + """
        void colorChecks() {
            painStrength = 1;
            settle();
            check(resolveDesiredEffect() == ActiveEffect.PAIN_HIGH);
            morphineStrength = 1;
            step();
            check(blurStrength > 0); // Morphine's graph carries the fading blur.
            settle();
            same(blurStrength, 0);
            morphineStrength = 0;
            painStrength = 0;
            lastBlurStrength = 0.005F;
            check(resolveDesiredEffect() == ActiveEffect.PAIN_HIGH);
            lastBlurStrength = 0;
            check(resolveDesiredEffect() == ActiveEffect.NONE);
        }
        """
        resources = ROOT / module / "src/main/resources"
        mixin = json.loads((resources / "firstaid.mixins.json").read_text(encoding="utf-8"))
        assert "client.PostPassMixin" in mixin["client"]
        for name in ["pain"] + [f"{prefix}_t{i}" for prefix in ("morphine_saturation", "suppression_desat") for i in range(1, 6)]:
            graph = json.loads((resources / f"assets/firstaid/post_effect/{name}.json").read_text(encoding="utf-8"))
            blur = [p for p in graph["passes"] if p["fragment_shader"] == "firstaid:post/pain_pulse_blur"]
            assert len(blur) == 1
            assert [(u["name"], u["type"]) for u in blur[0]["uniforms"]["BlurSettings"]] == [("Center", "vec2"), ("Samples", "int"), ("Strength", "float")]
            assert blur[0]["output"] in graph["targets"]
    shader_root = ROOT / module / "src/main/resources/assets"
    for shader in shader_root.rglob("*pain*.fsh"):
        data = shader.read_bytes()
        assert not re.search(rb"clamp\(effectStrength \* [0-9.]+, 0\.(?:15|20),", data), shader
    bodies = "\n".join(method(source, signature) for signature in signatures)
    classes.append(f"static class Blur{index} {{ float painStrength, morphineStrength, suppressionStrength, blurStrength, lastBlurStrength; {constants} {extra} {bodies} float expected(float pain) {{ {expected} }} {TESTS} }}")

main = "\n".join(f"new Blur{i}().run(); " + (f"new Blur{i}().colorChecks(); " if "26." in module else "") + f'System.out.println("PASS {module}: transitions, interpolation, fade, reset and shader wiring");' for i, module in enumerate(MODULES))
java = "public class PainBlurRegression {" + STUBS + "\n".join(classes) + "public static void main(String[] args) {" + main + "}}"
with tempfile.TemporaryDirectory(prefix="firstaid-pain-blur-") as temp:
    path = Path(temp) / "PainBlurRegression.java"
    path.write_text(java, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", temp, str(path)], check=True)
    subprocess.run(["java", "-cp", temp, "PainBlurRegression"], check=True)

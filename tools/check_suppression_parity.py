"""Compare production Fabric and NeoForge suppression draw calls: python tools/check_suppression_parity.py."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def method(source, signature):
    start = source.index(signature)
    end = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


classes, tests = [], []
for index, version in enumerate(("1.21.1", "26.2", "26.3")):
    for loader in ("fabric", "neoforge"):
        module = ROOT / f"{loader}{version}"
        client = module / ("src/client/java" if loader == "fabric" else "src/main/java") / "ichttt/mods/firstaid/client"
        visual = (client / "PainVisualEffectsController.java").read_text(encoding="utf-8")
        overlay = (client / "StatusEffectLayer.java").read_text(encoding="utf-8")
        cool_edge = method(overlay, "if (smoothSuppression > 0.0F)")
        methods = "\n".join(method(visual, signature) for signature in (
            "private static void renderSuppressionGrayRim", "private static void fillEdge", "private static int color"))
        methods += method(overlay, "private static void renderVignette")
        methods = methods.replace("GuiGraphicsExtractor", "GuiGraphics")
        classes.append(f"static class {loader}{index} {{ {methods} "
                       f"static void cool(GuiGraphics guiGraphics,int width,int height,float smoothSuppression,float pulseTime) {{ {cool_edge} }} }}")
        if loader == "neoforge":
            handler = (client / "ClientEventHandler.java").read_text(encoding="utf-8")
            assert "StatusEffectLayer.INSTANCE.render(" not in handler, f"{loader}{version}: duplicate HUD draw"
            hooks = (client / "ClientHooks.java").read_text(encoding="utf-8")
            assert '"status_overlay"), StatusEffectLayer.INSTANCE' in hooks
    assets = Path("src/main/resources/assets/firstaid")
    fabric = ROOT / f"fabric{version}" / assets
    neo = ROOT / f"neoforge{version}" / assets
    for source in fabric.rglob("*"):
        if source.is_file() and ("suppression" in source.name or source.name in ("suppression.fsh", "desaturate.fsh")):
            target = neo / source.relative_to(fabric)
            assert target.is_file() and target.read_bytes() == source.read_bytes(), target
    tests.append(f"""
        for (int width : new int[]{{320,854,1920}}) for (int height : new int[]{{240,480,1080}})
        for (float mode : new float[]{{0.5F,0.75F}}) for (float pressure : new float[]{{0,0.2F,0.5F,0.8F,1}})
        for (float time : new float[]{{0,1,40,100,100.5F}}) {{
            GuiGraphics a=new GuiGraphics(),b=new GuiGraphics();
            fabric{index}.renderSuppressionGrayRim(a,width,height,pressure*mode,time);
            neoforge{index}.renderSuppressionGrayRim(b,width,height,pressure*mode,time);
            if(!a.draws.equals(b.draws)) throw new AssertionError("gray rim {version}");
            a.draws.clear();b.draws.clear();
            fabric{index}.cool(a,width,height,pressure*mode,time);
            neoforge{index}.cool(b,width,height,pressure*mode,time);
            if(!a.draws.equals(b.draws)) throw new AssertionError("cool edge {version}");
        }}
        System.out.println("PASS {version}: 450 gray-rim and 450 cool-edge draw comparisons; one NeoForge HUD registration; matching suppression resources");
    """)

java = """import java.util.*; public class SuppressionParity {
    static class Mth {
        static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
        static float sin(float v){return (float)Math.sin(v);}
    }
    static class GuiGraphics {
        List<List<Integer>> draws=new ArrayList<>();
        void fill(int x1,int y1,int x2,int y2,int color){draws.add(List.of(x1,y1,x2,y2,color));}
    }
""" + "\n".join(classes) + "public static void main(String[] args){" + "\n".join(tests) + "}}"
with tempfile.TemporaryDirectory(prefix="firstaid-suppression-parity-") as tmp:
    source = Path(tmp) / "SuppressionParity.java"
    source.write_text(java, encoding="utf-8")
    subprocess.run(["javac", "-d", tmp, str(source)], check=True)
    subprocess.run(["java", "-cp", tmp, "SuppressionParity"], check=True)

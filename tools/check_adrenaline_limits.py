"""Run the production combat adrenaline limits against minimal stubs: python tools/check_adrenaline_limits.py.

Covers adrenalineMaxLevel, adrenalineAttackGainMultiplier, adrenalineAttackCooldownTicks and
adrenalineAttackHoldTicks in every maintained module: the tier cap for hits, near misses and encounters,
same-tick hit merging, the cooldown, the zero multiplier, config keys, commands and translations.
"""
from pathlib import Path
import json
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MODULES = ("forge1.20.1", "fabric1.21.1", "neoforge1.21.1", "fabric26.2",
           "neoforge26.2", "fabric26.3", "neoforge26.3")

NEW_KEYS = {
    "adrenalineMaxLevel": ("5", "0", "5"),
    "adrenalineAttackGainMultiplier": ("1.0", "0.0", "2.0"),
    "adrenalineAttackCooldownTicks": ("0", "0", "200"),
    "adrenalineAttackHoldTicks": ("40", "0", "200"),
}
COMMANDS = {
    "maxlevel": 'IntegerArgumentType.integer(0, 5)',
    "attackgain": 'DoubleArgumentType.doubleArg(0.0D, 2.0D)',
    "attackcooldown": 'IntegerArgumentType.integer(0, 200)',
    "attackhold": 'IntegerArgumentType.integer(0, 200)',
}
LANG_KEYS = ("firstaid.command.adrenaline.maxlevel", "firstaid.command.adrenaline.attackgain",
             "firstaid.command.adrenaline.attackcooldown", "firstaid.command.adrenaline.attackhold")


def method(source, signature):
    start = source.index(signature)
    end = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def number(value):
    return float(value.rstrip("DdFf"))


def check_sources(module):
    base = ROOT / module / "src/main/java/ichttt/mods/firstaid"
    config = (base / "FirstAidConfig.java").read_text(encoding="utf-8")
    for key, expected in NEW_KEYS.items():
        match = re.search(r'(?:defineInRange|intValue|doubleValue)\("' + key + r'",\s*([\d.DdFf]+),\s*([\d.DdFf]+),\s*([\d.DdFf]+)\)', config)
        assert match, (module, "missing config key", key)
        assert [number(v) for v in match.groups()] == [number(v) for v in expected], (module, key, match.groups())
        assert re.search(r"public final \S+ " + key + ";", config), (module, "missing config field", key)

    commands = (base / "common/FirstAidCommand.java").read_text(encoding="utf-8")
    branch = method(commands, "LiteralArgumentBuilder<CommandSourceStack> buildAdrenalineBranch(")
    for literal, argument in COMMANDS.items():
        position = branch.find('Commands.literal("' + literal + '")')
        assert position >= 0, (module, "missing command", literal)
        assert argument in branch[position:position + 200], (module, "command range", literal)
    for setter, key in (("setAdrenalineMaxLevel(", "adrenalineMaxLevel"), ("setAdrenalineAttackGain(", "adrenalineAttackGainMultiplier"),
                        ("setAdrenalineAttackCooldown(", "adrenalineAttackCooldownTicks"), ("setAdrenalineAttackHold(", "adrenalineAttackHoldTicks")):
        body = method(commands, "private static int " + setter)
        writes = ("FirstAidConfig.SERVER." + key + ".set(", "FirstAidConfig.setServerValue(FirstAidConfig.SERVER." + key + ", ")
        assert any(write in body for write in writes) and "FirstAidConfig.persistCommandSettings();" in body, (module, setter)

    events = (base / "common/EventHandler.java").read_text(encoding="utf-8")
    assert events.count("model.registerAdrenalineAttack(") == 1, module
    assert "FirstAidConfig.SERVER.adrenalineAttackHoldTicks.get()" in events, (module, "hold ticks are not wired")

    for locale in ("en_us.json", "zh_cn.json"):
        values = json.loads((ROOT / module / "src/main/resources/assets/firstaid/lang" / locale).read_text(encoding="utf-8-sig"))
        for key in LANG_KEYS:
            assert "%s" in values.get(key, ""), (module, locale, key)


STUBS = r"""
    static final class Mth {
        static float clamp(float v, float min, float max) { return v < min ? min : Math.min(v, max); }
        static int clamp(int v, int min, int max) { return v < min ? min : Math.min(v, max); }
        static double clamp(double v, double min, double max) { return v < min ? min : Math.min(v, max); }
    }
    static final class Level { long gameTime; long getGameTime() { return gameTime; } }
    static final class Player { final Level world = new Level(); Level level() { return world; } }
    static final class FirstAid { static float suppressionGainMultiplier = 0.15F; }
    static final class Value<T> { T value; Value(T value) { this.value = value; } T get() { return value; } }
    static final class FirstAidConfig {
        static final Server SERVER = new Server();
        static final class Server {
            final Value<Integer> adrenalineMaxLevel = new Value<>(5);
            final Value<Double> adrenalineAttackGainMultiplier = new Value<>(1.0D);
            final Value<Integer> adrenalineAttackCooldownTicks = new Value<>(0);
            final Value<Integer> adrenalineAttackHoldTicks = new Value<>(40);
            final Value<Double> encounterBaseIntensity = new Value<>(0.20D);
        }
        static void reset() {
            SERVER.adrenalineMaxLevel.value = 5; SERVER.adrenalineAttackGainMultiplier.value = 1.0D;
            SERVER.adrenalineAttackCooldownTicks.value = 0; SERVER.adrenalineAttackHoldTicks.value = 40;
            SERVER.encounterBaseIntensity.value = 0.20D; FirstAid.suppressionGainMultiplier = 0.15F;
        }
    }
    interface Model {
        void attack(Player p, float strength, int hold); void nearMiss(Player p, float strength); void nearMiss(Player p, float strength, int hold);
        void encounter(Player p, float floor); void recover(Player p, int threats); float cap(int level); int level(float intensity);
        float intensity(); void intensity(float value); int hold(); int level(); int resyncs();
    }
    static void check(boolean condition, String module, String message) {
        if (!condition) throw new AssertionError(module + ": " + message);
    }
    static boolean near(float a, float b) { return Math.abs(a - b) < 0.0001F; }

    static void run(String module, java.util.function.Supplier<Model> factory) {
        FirstAidConfig.reset();
        Model m = factory.get();
        int[] expected = {0, 1, 2, 3, 4, 5};
        for (int level = 0; level <= 5; ++level) {
            float cap = m.cap(level);
            check(m.level(cap) == expected[level], module, "cap for tier " + level + " lands on tier " + m.level(cap));
            if (level < 5) check(m.level(cap + 0.0101F) == level + 1, module, "cap for tier " + level + " is not just below the next tier");
        }
        check(m.cap(-3) == m.cap(0) && m.cap(99) == 1.0F, module, "out-of-range tiers must clamp");

        Player p = new Player();
        for (int i = 0; i < 5; ++i) m.attack(p, 0.50F, 40);
        check(near(m.intensity(), 0.06F), module, "same-tick hits must count once, got " + m.intensity());
        check(m.hold() == 40 && m.resyncs() == 1, module, "counted hit holds and resyncs once");
        p.world.gameTime = 1;
        m.attack(p, 0.50F, 40);
        check(near(m.intensity(), 0.06F + 0.06F * 0.94F), module, "the next tick counts again with cooldown 0");

        FirstAidConfig.SERVER.adrenalineAttackCooldownTicks.value = 10;
        m = factory.get(); p = new Player(); p.world.gameTime = 100;
        m.attack(p, 0.50F, 40);
        p.world.gameTime = 105; m.attack(p, 0.50F, 40);
        check(near(m.intensity(), 0.06F), module, "hits inside the cooldown are ignored");
        p.world.gameTime = 110; m.attack(p, 0.50F, 40);
        check(near(m.intensity(), 0.06F + 0.06F * 0.94F), module, "the cooldown boundary counts");
        p.world.gameTime = 50; m.attack(p, 0.50F, 40);
        check(m.intensity() > 0.06F + 0.06F * 0.94F, module, "a game time that moved backwards must not lock gains");
        FirstAidConfig.reset();

        FirstAidConfig.SERVER.adrenalineAttackGainMultiplier.value = 0.0D;
        m = factory.get(); p = new Player();
        m.attack(p, 0.50F, 40);
        check(m.intensity() == 0.0F && m.hold() == 0 && m.resyncs() == 0, module, "zero attack gain must be a full no-op");
        m.nearMiss(p, 0.35F, 0);
        check(m.intensity() > 0.0F, module, "zero attack gain leaves near misses alone");
        FirstAidConfig.SERVER.adrenalineAttackGainMultiplier.value = 2.0D;
        m = factory.get(); p = new Player();
        m.attack(p, 0.50F, 40);
        check(near(m.intensity(), 0.12F), module, "attack gain multiplier 2 doubles hit growth");
        FirstAidConfig.reset();

        m = factory.get(); p = new Player();
        m.nearMiss(p, 0.35F);
        check(m.intensity() > 0.0F && m.hold() == 80, module, "the default near-miss overload must apply growth and an 80 tick hold");

        for (int level = 0; level <= 5; ++level) {
            FirstAidConfig.SERVER.adrenalineMaxLevel.value = level;
            Model hits = factory.get(); Player hp = new Player();
            for (int i = 0; i < 2000; ++i) { hp.world.gameTime = i; hits.attack(hp, 0.50F, 40); }
            check(hits.level() == level, module, "hits must stop at tier " + level + ", reached " + hits.level());
            Model misses = factory.get();
            for (int i = 0; i < 2000; ++i) misses.nearMiss(hp, 1.45F, 0);
            check(misses.level() == level, module, "near misses must stop at tier " + level);
            Model encounter = factory.get();
            encounter.encounter(hp, 1.0F);
            check(encounter.level() == level, module, "encounters must stop at tier " + level);
            for (int i = 0; i < 600; ++i) encounter.recover(hp, 20);
            check(encounter.level() <= level, module, "encounter recovery must respect tier " + level);
        }
        FirstAidConfig.SERVER.adrenalineMaxLevel.value = 2;
        m = factory.get(); p = new Player();
        m.intensity(1.0F);
        m.attack(p, 0.50F, 40); m.nearMiss(p, 0.35F, 80);
        check(m.intensity() == 1.0F, module, "the cap must never lower stronger sources such as injectors");
        check(m.hold() == 0 && m.resyncs() == 0, module, "combat above the cap must not extend the hold of a stronger source");
        m = factory.get(); p = new Player();
        for (int i = 0; i < 200; ++i) { p.world.gameTime = i; m.attack(p, 0.50F, 40); }
        check(m.intensity() == m.cap(2) && m.hold() == 40, module, "pressure at the cap keeps its hold while fighting");
        FirstAidConfig.reset();

        // Report: hits needed for each tier with one counted hit per swing at the default gain.
        m = factory.get(); p = new Player();
        StringBuilder tiers = new StringBuilder();
        int reached = 0;
        for (int hit = 1; hit <= 200 && reached < 5; ++hit) {
            p.world.gameTime = hit * 12L;
            for (int target = 0; target < 4; ++target) m.attack(p, 0.50F, 40);
            while (reached < m.level()) { ++reached; tiers.append(" T").append(reached).append('=').append(hit); }
        }
        System.out.println(module + ": sweeping 4 targets per swing ->" + tiers);
    }
"""


def model_class(module, index):
    model = (ROOT / module / "src/main/java/ichttt/mods/firstaid/common/damagesystem/PlayerDamageModel.java").read_text(encoding="utf-8")
    constants = "\n".join(re.findall(r"private static final int MAX_ADRENALINE_LEVEL = [^;]+;", model))
    methods = "\n".join(method(model, signature) for signature in (
        "public void registerAdrenalineAttack(", "public static float getCombatAdrenalineCap()",
        "public static float getCombatAdrenalineCap(int maxLevel)", "private void registerAdrenalineGain(",
        "public void registerAdrenalineNearMiss(Player player, float strength)",
        "public void registerAdrenalineNearMiss(Player player, float strength, int holdTicks)",
        "public void ensureEncounterAdrenaline(", "public void recoverEncounterAdrenaline(",
        "private int calculateAdrenalineLevel(", "private void refreshSuppressionSnapshot()"))
    assert "private long lastAdrenalineAttackTick = Long.MIN_VALUE;" in model, module
    return f"""static final class Model{index} implements Model {{
        {constants}
        float suppressionIntensity; int suppressionHoldTicks, suppressionDecayTicker, encounterRecoveryTicks, adrenalineTicks, adrenalineLevel, resyncs;
        long lastAdrenalineAttackTick = Long.MIN_VALUE;
        void scheduleResync() {{ resyncs++; }} void refreshAdrenalineCombatAttributes(Player player) {{}}
        {methods}
        public void attack(Player p, float s, int h) {{ registerAdrenalineAttack(p, s, h); }}
        public void nearMiss(Player p, float s) {{ registerAdrenalineNearMiss(p, s); }}
        public void nearMiss(Player p, float s, int h) {{ registerAdrenalineNearMiss(p, s, h); }}
        public void encounter(Player p, float f) {{ ensureEncounterAdrenaline(p, f); }}
        public void recover(Player p, int t) {{ recoverEncounterAdrenaline(p, t); }}
        public float cap(int l) {{ return getCombatAdrenalineCap(l); }}
        public int level(float i) {{ return calculateAdrenalineLevel(i); }}
        public float intensity() {{ return suppressionIntensity; }}
        public void intensity(float v) {{ suppressionIntensity = v; refreshSuppressionSnapshot(); }}
        public int hold() {{ return suppressionHoldTicks; }}
        public int level() {{ return adrenalineLevel; }}
        public int resyncs() {{ return resyncs; }}
    }}"""


def main():
    for module in MODULES:
        check_sources(module)
    classes = [model_class(module, index) for index, module in enumerate(MODULES)]
    runs = "\n".join(f'        run("{module}", Model{index}::new);' for index, module in enumerate(MODULES))
    source = f"public class AdrenalineLimitsCheck {{\n{STUBS}\n" + "\n".join(classes) + f"\n    public static void main(String[] args) {{\n{runs}\n    }}\n}}\n"
    with tempfile.TemporaryDirectory() as temp:
        path = Path(temp) / "AdrenalineLimitsCheck.java"
        path.write_text(source, encoding="utf-8")
        subprocess.run(["javac", "-d", temp, str(path)], check=True)
        subprocess.run(["java", "-cp", temp, "AdrenalineLimitsCheck"], check=True)
    print("combat adrenaline limits ok for", len(MODULES), "modules")


if __name__ == "__main__":
    main()

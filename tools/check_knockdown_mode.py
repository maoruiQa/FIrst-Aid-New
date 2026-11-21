"""Check the knockdown mode (always/chance/never) and its notice across every maintained module.

Production KnockdownRules, KnockdownCommands, KnockdownNotices, DamageablePart.damage and
PlayerDamageModel.handlePostDamage are compiled against small Minecraft stubs, real Brigadier and real Gson.
"""
from pathlib import Path
import json
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MODULES = ("forge1.20.1", "fabric1.21.1", "neoforge1.21.1", "fabric26.2",
           "neoforge26.2", "fabric26.3", "neoforge26.3")
LANG_KEYS = (
    "firstaid.knockdown.mode.always", "firstaid.knockdown.mode.chance", "firstaid.knockdown.mode.never",
    "firstaid.command.knockdown.query", "firstaid.command.knockdown.samples", "firstaid.command.knockdown.mode.set",
    "firstaid.command.knockdown.coefficient.set", "firstaid.command.knockdown.coefficient.inactive",
    "firstaid.tip.commands.knockdown.label", "firstaid.tip.commands.knockdown.detail",
    "firstaid.knockdown.notice.title", "firstaid.knockdown.notice.current", "firstaid.knockdown.notice.always.detail",
    "firstaid.knockdown.notice.chance.detail", "firstaid.knockdown.notice.never.detail", "firstaid.knockdown.notice.shared",
    "firstaid.knockdown.notice.footer", "firstaid.knockdown.notice.table.overflow", "firstaid.knockdown.notice.table.chance",
    "firstaid.knockdown.notice.confirm",
)


def method(source, signature):
    start = source.index(signature)
    while start > 0 and source[start - 1] in " \t":
        start -= 1
    end = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def nested(source, name):
    """Turns a production top-level class into a static nested class without package and imports."""
    body = source[source.index("public final class " + name):]
    return body.replace("public final class " + name, "static final class " + name, 1)


def jar(group_path, pattern):
    jars = sorted((Path.home() / ".gradle/caches/modules-2/files-2.1" / group_path).rglob(pattern))
    if not jars:
        raise SystemExit("missing dependency jar under " + group_path)
    return jars[-1]


STUBS = r"""
@interface Nullable {}
@interface Nonnull {}
static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
static class Log { void info(Object... a){} void warn(Object... a){warnings++;} void error(Object... a){errors++;System.err.println(Arrays.toString(a));} static int warnings, errors; }
static class FirstAid {
    static final String MODID="firstaid"; static final Log LOGGER=new Log();
    static final double DEFAULT_KNOCKDOWN_OVERFLOW_COEFFICIENT=0.35D, MAX_KNOCKDOWN_OVERFLOW_COEFFICIENT=5.0D;
    static KnockdownMode knockdownMode=KnockdownMode.CHANCE; static double knockdownOverflowCoefficient=DEFAULT_KNOCKDOWN_OVERFLOW_COEFFICIENT;
    enum KnockdownMode { ALWAYS, CHANCE, NEVER }
    enum InjuryDebuffMode { NORMAL, LOW, OFF }
    static InjuryDebuffMode injuryDebuffMode=InjuryDebuffMode.NORMAL; static float lowInjuryDebuffDamageScale=0.4F;
    static final Networking NETWORKING=new Networking();
}
static class Networking {
    void sendKnockdownNotice(ServerPlayer p, MessageKnockdownNotice m){p.notices++;}
    void send(Target t, MessageKnockdownNotice m){t.player.notices++;}
}
static class Target { ServerPlayer player; }
static class PacketDistributor { static final PacketDistributor PLAYER=new PacketDistributor(); Target with(Supplier<ServerPlayer> s){Target t=new Target();t.player=s.get();return t;} }
static class FirstAidNetworking { static void sendKnockdownNotice(ServerPlayer p){p.notices++;} }
static class MessageKnockdownNotice { static MessageKnockdownNotice current(){return new MessageKnockdownNotice();} }
static class Value<T> { T v; Value(T v){this.v=v;} T get(){return v;} }
static class FirstAidConfig {
    static int persisted; static void persistCommandSettings(){persisted++;}
    static final General GENERAL=new General(); static class General { Value<Boolean> debug=new Value<>(false); }
}
static class Component {
    String key; Object[] args;
    static Component translatable(String key,Object... args){Component c=new Component();c.key=key;c.args=args;return c;}
    String getString(){return key;}
}
static class Permissions { static int COMMANDS_GAMEMASTER=2; }
static class CommandSourceStack {
    int permission; List<Component> messages=new ArrayList<>(); CommandSourceStack(int p){permission=p;}
    boolean hasPermission(int p){return permission>=p;} CommandSourceStack permissions(){return this;}
    MinecraftServer getServer(){return server;}
    void sendSuccess(Supplier<Component> s,boolean broadcast){messages.add(s.get());}
    void sendFailure(Component c){messages.add(c);}
}
static class Commands {
    static LiteralArgumentBuilder<CommandSourceStack> literal(String name){return LiteralArgumentBuilder.literal(name);}
    static <T> RequiredArgumentBuilder<CommandSourceStack,T> argument(String name,ArgumentType<T> type){return RequiredArgumentBuilder.argument(name,type);}
}
enum LevelResource { ROOT }
static class MinecraftServer {
    Path world; List<ServerPlayer> players=new ArrayList<>();
    Path getWorldPath(LevelResource r){return world;}
    MinecraftServer getPlayerList(){return this;} List<ServerPlayer> getPlayers(){return players;}
}
static MinecraftServer server=new MinecraftServer();
static class RandomSource { float next; float nextFloat(){return next;} }
static class Player {
    UUID uuid=UUID.randomUUID(); RandomSource random=new RandomSource(); float health=20;
    UUID getUUID(){return uuid;} RandomSource getRandom(){return random;} Component getName(){return Component.translatable("player");}
    float getHealth(){return health;} void setHealth(float h){health=h;}
}
static class ServerPlayer extends Player { int notices; }
static class DamageSource {}
interface IDebuff { void handleDamageTaken(float damage, float healthFraction, ServerPlayer player); }
static float softenDebuffHealthFraction(float f){return f;}
static class AbstractDamageablePart {
    final boolean canCauseDeath; float currentHealth; AbstractDamageablePart(boolean c){canCauseDeath=c;}
}
static class DamageablePart extends AbstractDamageablePart {
    int maxHealth; @Nullable IDebuff[] debuffs;
    DamageablePart(int max, boolean critical){super(critical);maxHealth=max;currentHealth=max;}
    int getMaxHealth(){return maxHealth;}
    KNOCKDOWN_FIELD
    DAMAGE_METHOD
    GET_OVERFLOW
    RESET_OVERFLOW
}
static class CommonUtils {
    static boolean totem; static int totemsUsed;
    static boolean tryUseTotem(PlayerDamageModel m, Player p, DamageSource s){if(totem){totemsUsed++;return true;}return false;}
    static void runWithoutSetHealthInterception(Runnable r){r.run();}
}
static class PlayerDamageModel implements Iterable<AbstractDamageablePart> {
    static final float PAIN_HARD_CAP=1.85f; static final String UNCONSCIOUS_REASON_CRITICAL="critical";
    final DamageablePart HEAD=new DamageablePart(4,true), BODY=new DamageablePart(6,true), ARM=new DamageablePart(4,false);
    List<AbstractDamageablePart> parts=List.of(HEAD,BODY,ARM);
    boolean criticalConditionActive; float acutePainIntensity; int painLevel, unconsciousTicks, resyncs;
    public Iterator<AbstractDamageablePart> iterator(){return parts.iterator();}
    void clearUnconsciousState(){unconsciousTicks=0;} void clearUnconsciousPenalties(Player p){} void resetRecoveredPlayerState(Player p){}
    void scheduleResync(){resyncs++;}
    void setUnconsciousState(int ticks, boolean giveUp, boolean death, String reason){unconsciousTicks=ticks;}
    HANDLE_POST_DAMAGE
    RESET_MODEL_OVERFLOW
    CRITICAL_HELPERS
}
"""

TESTS = r"""
double k = FirstAid.DEFAULT_KNOCKDOWN_OVERFLOW_COEFFICIENT;
check(FirstAid.knockdownMode == FirstAid.KnockdownMode.CHANCE, "MODULE default mode must be CHANCE");
check(KnockdownRules.chanceForOverflowRatio(0, k) == 1.0D, "MODULE no overflow must always down");
check(Math.abs(KnockdownRules.chanceForOverflowRatio(1, k) - Math.exp(-0.35D)) < 1e-12, "MODULE ratio 1 must be exp(-k)");
check(KnockdownRules.chanceForOverflowRatio(3, 0) == 1.0D, "MODULE coefficient 0 must always down");
check(KnockdownRules.chanceForOverflowRatio(Double.NaN, k) == 1.0D && KnockdownRules.chanceForOverflowRatio(-2, k) == 1.0D, "MODULE invalid overflow must not lower the chance");
check(KnockdownRules.chanceForOverflowRatio(1000, 5) >= 0.0D, "MODULE chance must stay within [0, 1]");
check(KnockdownRules.sampleSummary(k).equals("0.0x 100% | 0.5x 84% | 1.0x 70% | 2.0x 50% | 3.0x 35% | 5.0x 17%"), "MODULE default samples: " + KnockdownRules.sampleSummary(k));
check(KnockdownRules.fingerprint(FirstAid.KnockdownMode.ALWAYS, 0.1).equals(KnockdownRules.fingerprint(FirstAid.KnockdownMode.ALWAYS, 4)), "MODULE coefficient is irrelevant outside CHANCE");
check(!KnockdownRules.fingerprint(FirstAid.KnockdownMode.CHANCE, 0.35).equals(KnockdownRules.fingerprint(FirstAid.KnockdownMode.CHANCE, 0.5)), "MODULE coefficient matters in CHANCE");

// overflow tracking
DamageablePart head = new DamageablePart(4, true);
check(head.damage(10F, null, false, 0F) == 6F && head.getKnockdownOverflow() == 6F && head.currentHealth == 0F, "MODULE critical overflow must be recorded");
head.damage(2F, null, false, 0F);
check(head.getKnockdownOverflow() == 8F, "MODULE overflow must accumulate within a hit");
head.resetKnockdownOverflow();
check(head.getKnockdownOverflow() == 0F, "MODULE overflow must reset");
DamageablePart arm = new DamageablePart(4, false);
arm.damage(10F, null, false, 0F);
check(arm.getKnockdownOverflow() == 0F, "MODULE non-critical parts must not record overflow");
DamageablePart friendly = new DamageablePart(4, true);
friendly.damage(10F, null, false, 1F);
check(friendly.getKnockdownOverflow() == 0F && friendly.currentHealth == 1F, "MODULE friendly distribution keeps the part alive and records nothing");

// post-damage decisions
Object[][] cases = {
    {FirstAid.KnockdownMode.ALWAYS, 0.35, 40F, 0.99F, true},
    {FirstAid.KnockdownMode.NEVER, 0.35, 0F, 0.0F, false},
    {FirstAid.KnockdownMode.CHANCE, 0.35, 0F, 0.999F, true},
    {FirstAid.KnockdownMode.CHANCE, 0.35, 4F, (float) Math.exp(-0.35) - 0.01F, true},
    {FirstAid.KnockdownMode.CHANCE, 0.35, 4F, (float) Math.exp(-0.35) + 0.01F, false},
    {FirstAid.KnockdownMode.CHANCE, 0.0, 400F, 0.999F, true},
    {FirstAid.KnockdownMode.CHANCE, 5.0, 40F, 0.01F, false},
};
for (Object[] c : cases) {
    FirstAid.knockdownMode = (FirstAid.KnockdownMode) c[0];
    FirstAid.knockdownOverflowCoefficient = (Double) c[1];
    PlayerDamageModel model = new PlayerDamageModel();
    ServerPlayer player = new ServerPlayer();
    player.random.next = (Float) c[3];
    model.HEAD.damage(4F + (Float) c[2], player, false, 0F);
    model.handlePostDamage(player, null);
    check(model.criticalConditionActive == (Boolean) c[4], "MODULE " + Arrays.toString(c) + " downed=" + model.criticalConditionActive);
    check(model.HEAD.getKnockdownOverflow() == 0F, "MODULE decision must consume the overflow");
    check(!model.criticalConditionActive || model.unconsciousTicks == 3000, "MODULE downed players must get the 150 s critical timer");
}
KnockdownRules.setForcedRollForTesting(0.999F);
FirstAid.knockdownMode = FirstAid.KnockdownMode.CHANCE; FirstAid.knockdownOverflowCoefficient = 5.0;
PlayerDamageModel forced = new PlayerDamageModel(); ServerPlayer forcedPlayer = new ServerPlayer(); forcedPlayer.random.next = 0F;
forced.HEAD.damage(4F, forcedPlayer, false, 0F); forced.handlePostDamage(forcedPlayer, null);
check(forced.criticalConditionActive, "MODULE forced roll still downs without overflow");
forced = new PlayerDamageModel(); forced.HEAD.damage(44F, forcedPlayer, false, 0F); forced.handlePostDamage(forcedPlayer, null);
check(!forced.criticalConditionActive, "MODULE forced roll must override the player random");
KnockdownRules.setForcedRollForTesting(null);

FirstAid.knockdownMode = FirstAid.KnockdownMode.ALWAYS;
PlayerDamageModel downed = new PlayerDamageModel(); ServerPlayer downedPlayer = new ServerPlayer();
downed.HEAD.damage(4F, downedPlayer, false, 0F); downed.handlePostDamage(downedPlayer, null);
FirstAid.knockdownMode = FirstAid.KnockdownMode.NEVER;
downed.handlePostDamage(downedPlayer, null);
check(downed.criticalConditionActive, "MODULE switching to NEVER must not affect players who are already down");
PlayerDamageModel both = new PlayerDamageModel();
FirstAid.knockdownMode = FirstAid.KnockdownMode.ALWAYS;
both.HEAD.damage(4F, downedPlayer, false, 0F); both.BODY.damage(6F, downedPlayer, false, 0F); both.handlePostDamage(downedPlayer, null);
check(!both.criticalConditionActive, "MODULE head and body together must stay fatal");
CommonUtils.totem = true;
PlayerDamageModel totemModel = new PlayerDamageModel(); totemModel.HEAD.damage(4F, downedPlayer, false, 0F); totemModel.handlePostDamage(downedPlayer, null);
check(!totemModel.criticalConditionActive && CommonUtils.totemsUsed == 1, "MODULE ALWAYS keeps the totem before downing");
FirstAid.knockdownMode = FirstAid.KnockdownMode.NEVER;
totemModel = new PlayerDamageModel(); totemModel.HEAD.damage(4F, downedPlayer, false, 0F); totemModel.handlePostDamage(downedPlayer, null);
check(CommonUtils.totemsUsed == 1, "MODULE NEVER leaves the totem to the death path");
CommonUtils.totem = false;

// notices, persisted per world
Path world = Files.createTempDirectory("firstaid-knockdown-world");
server.world = world;
FirstAid.knockdownMode = FirstAid.KnockdownMode.CHANCE; FirstAid.knockdownOverflowCoefficient = 0.35;
KnockdownNotices.load(server);
ServerPlayer alice = new ServerPlayer(), bob = new ServerPlayer();
server.players.add(alice);
KnockdownNotices.onLogin(alice);
check(alice.notices == 1, "MODULE first join must show the notice");
KnockdownNotices.acknowledge(alice);
Path file = world.resolve("firstaid").resolve("knockdown_notice.json");
check(Files.readString(file).contains(alice.uuid.toString()) && Files.readString(file).contains("CHANCE;k=0.35"), "MODULE acknowledgement must be saved");
KnockdownNotices.onLogin(alice);
check(alice.notices == 1, "MODULE acknowledged players must not see the notice again");
KnockdownNotices.unload(); KnockdownNotices.load(server);
KnockdownNotices.onLogin(alice);
check(alice.notices == 1, "MODULE acknowledgement must survive a restart");
KnockdownNotices.broadcastIfChanged(server);
check(alice.notices == 1, "MODULE unchanged settings must not broadcast");

// commands with real Brigadier
CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
KnockdownCommands.register(dispatcher);
CommandSourceStack admin = new CommandSourceStack(2), denied = new CommandSourceStack(0);
boolean rejected = false;
try { dispatcher.execute("firstaid knockdown never", denied); } catch (CommandSyntaxException e) { rejected = true; }
check(rejected && FirstAid.knockdownMode == FirstAid.KnockdownMode.CHANCE, "MODULE non-operators must not change the mode");
dispatcher.execute("firstaid knockdown", admin);
check(admin.messages.get(0).key.equals("firstaid.command.knockdown.query") && admin.messages.get(1).key.equals("firstaid.command.knockdown.samples"), "MODULE query must report mode and samples");
int persisted = FirstAidConfig.persisted;
dispatcher.execute("firstaid knockdown never", admin);
check(FirstAid.knockdownMode == FirstAid.KnockdownMode.NEVER && FirstAidConfig.persisted == persisted + 1 && alice.notices == 2, "MODULE mode change must persist and notify online players");
KnockdownNotices.acknowledge(alice);
dispatcher.execute("firstaid knockdown never", admin);
check(alice.notices == 2, "MODULE re-applying the same mode must not notify again");
dispatcher.execute("firstaid knockdown coefficient 1.5", admin);
check(FirstAid.knockdownOverflowCoefficient == 1.5 && alice.notices == 2 && admin.messages.get(admin.messages.size() - 1).key.equals("firstaid.command.knockdown.coefficient.inactive"),
      "MODULE coefficient changes outside CHANCE persist silently");
rejected = false;
try { dispatcher.execute("firstaid knockdown coefficient 6", admin); } catch (CommandSyntaxException e) { rejected = true; }
check(rejected && FirstAid.knockdownOverflowCoefficient == 1.5, "MODULE coefficient above 5 must be rejected");
dispatcher.execute("firstaid knockdown chance", admin);
check(alice.notices == 3, "MODULE switching to CHANCE must notify");
dispatcher.execute("firstaid knockdown coefficient 0.5", admin);
check(alice.notices == 4, "MODULE coefficient change in CHANCE must notify");
KnockdownNotices.acknowledge(alice);
KnockdownNotices.onLogin(bob);
check(bob.notices == 1, "MODULE players who joined later see the current notice");
KnockdownNotices.unload();
FirstAid.knockdownMode = FirstAid.KnockdownMode.ALWAYS;
KnockdownNotices.load(server);
KnockdownNotices.onLogin(alice);
check(alice.notices == 5, "MODULE a config file change between restarts must notify on login");
Files.writeString(file, "{not json");
int warnings = Log.warnings;
KnockdownNotices.unload(); KnockdownNotices.load(server);
check(Log.warnings == warnings + 1, "MODULE malformed storage must warn");
KnockdownNotices.onLogin(alice);
check(alice.notices == 6, "MODULE malformed storage must fall back to showing the notice");
System.out.println("MODULE: knockdown mode PASS");
"""


def source_assertions(module):
    base = ROOT / module / "src/main/java/ichttt/mods/firstaid"
    client = ROOT / module / ("src/client/java/ichttt/mods/firstaid/client" if module.startswith("fabric") else "src/main/java/ichttt/mods/firstaid/client")
    read = lambda p: p.read_text(encoding="utf-8")
    config = read(base / "FirstAidConfig.java")
    assert re.search(r'"knockdownMode", FirstAid\.KnockdownMode\.CHANCE', config), module + " config default"
    assert '"knockdownOverflowCoefficient", FirstAid.DEFAULT_KNOCKDOWN_OVERFLOW_COEFFICIENT' in config, module + " coefficient default"
    assert "FirstAid.knockdownMode = SERVER.knockdownMode.get()" in config and re.search(r"SERVER\.knockdownMode\.set\(FirstAid\.knockdownMode\)|setServerValue\(SERVER\.knockdownMode, FirstAid\.knockdownMode\)", config), module + " config mirror"
    events = read(base / "common/EventHandler.java")
    for needle in ("KnockdownNotices.load(", "KnockdownNotices.onLogin(", "KnockdownNotices.unload()", "FirstAid.knockdownMode = FirstAid.KnockdownMode.CHANCE", "/firstaid knockdown chance"):
        assert needle in events, module + " EventHandler missing " + needle
    assert "KnockdownCommands.register(dispatcher)" in read(base / "common/FirstAidCommand.java"), module + " command registration"
    assert "KnockdownNotices.broadcastIfChanged(source.getServer())" in read(base / "common/MedicalSettingCommands.java"), module + " reset broadcast"
    assert "playerDamageModel.resetKnockdownOverflow()" in read(base / "common/damagesystem/distribution/DamageDistribution.java"), module + " per-hit reset"
    request = read(base / "common/network/MessageClientRequest.java")
    assert re.search(r"ATTEMPT_SELF_DEFIB,\s*KNOCKDOWN_NOTICE_ACK;", request) and "KnockdownNotices.acknowledge(player)" in request, module + " ack request"
    if module.startswith("fabric"):
        assert "MessageKnockdownNotice.TYPE" in read(base / "common/network/FirstAidNetworking.java"), module + " payload type"
        assert "MessageKnockdownNoticeHandler::handle" in read(client / "network/FirstAidClientNetworking.java"), module + " client receiver"
    elif module.startswith("neoforge"):
        assert "MessageKnockdownNotice::handle" in read(base / "common/network/SimpleFirstAidChannel.java"), module + " payload registration"
    else:
        main = read(base / "FirstAid.java")
        assert "MessageKnockdownNotice.class" in main and 'NETWORKING_MINOR = "1"' in main, module + " channel registration"
    client_events = read(client / "ClientEventHandler.java")
    assert "KnockdownNoticeClient.tick(mc)" in client_events and client_events.count("KnockdownNoticeClient.reset()") == 2, module + " client hooks"
    screen = read(client / "gui/KnockdownNoticeScreen.java")
    assert "shouldCloseOnEsc() {\n        return false;" in screen.replace("\r\n", "\n") and "KNOCKDOWN_NOTICE_ACK" in screen, module + " screen"
    for lang in ("en_us", "zh_cn"):
        text = json.loads((ROOT / module / "src/main/resources/assets/firstaid/lang" / (lang + ".json")).read_text(encoding="utf-8"))
        missing = [key for key in LANG_KEYS if key not in text]
        assert not missing, module + " " + lang + " missing " + str(missing)
        # every %s in the notice text must have an argument and vice versa
        assert text["firstaid.knockdown.notice.chance.detail"].count("%s") == 1 and text["firstaid.knockdown.notice.always.detail"].count("%s") == 0, module + " " + lang + " placeholders"


def main():
    brigadier = jar("com.mojang/brigadier", "brigadier-*.jar")
    gson = jar("com.google.code.gson/gson/2.10.1", "gson-2.10.1.jar")
    classpath = str(brigadier) + ";" + str(gson)
    for module in MODULES:
        source_assertions(module)
        base = ROOT / module / "src/main/java/ichttt/mods/firstaid"
        read = lambda rel: (base / rel).read_text(encoding="utf-8")
        part = read("common/damagesystem/DamageablePart.java")
        model = read("common/damagesystem/PlayerDamageModel.java")
        stubs = STUBS.replace("KNOCKDOWN_FIELD", re.search(r"private float knockdownOverflow;", part).group(0))
        stubs = stubs.replace("DAMAGE_METHOD", method(part, "public float damage(float amount, @Nullable Player player, boolean applyDebuff, float minHealth)").replace("@Override", ""))
        stubs = stubs.replace("GET_OVERFLOW", method(part, "public float getKnockdownOverflow()"))
        stubs = stubs.replace("RESET_OVERFLOW", method(part, "public void resetKnockdownOverflow()"))
        stubs = stubs.replace("HANDLE_POST_DAMAGE", method(model, "public void handlePostDamage("))
        stubs = stubs.replace("RESET_MODEL_OVERFLOW", method(model, "public void resetKnockdownOverflow()"))
        stubs = stubs.replace("CRITICAL_HELPERS", "".join(method(model, "private boolean " + name + "()") for name in
                                                          ("hasCriticalPartCollapsed", "hasAllCriticalPartsCollapsed", "hasNoRemainingBodyHealth")))
        classes = "".join(nested(read(rel), name) for rel, name in (
            ("common/KnockdownRules.java", "KnockdownRules"),
            ("common/KnockdownCommands.java", "KnockdownCommands"),
            ("common/KnockdownNotices.java", "KnockdownNotices")))
        source = ("import java.io.*; import java.nio.charset.*; import java.nio.file.*; import java.util.*; import java.util.concurrent.*; import java.util.function.*;"
                  " import com.google.gson.*; import com.mojang.brigadier.*; import com.mojang.brigadier.arguments.*; import com.mojang.brigadier.builder.*;"
                  " import com.mojang.brigadier.exceptions.*; public class KnockdownModeCheck {" + stubs + classes
                  + "public static void main(String[] args) throws Exception {" + TESTS.replace("MODULE", module) + "}}")
        with tempfile.TemporaryDirectory(prefix="firstaid-knockdown-") as directory:
            path = Path(directory) / "KnockdownModeCheck.java"
            path.write_text(source, encoding="utf-8")
            subprocess.run(["javac", "-encoding", "UTF-8", "-nowarn", "-cp", classpath, "-d", directory, str(path)], check=True)
            subprocess.run(["java", "-cp", directory + ";" + classpath, "KnockdownModeCheck"], check=True)


if __name__ == "__main__":
    main()

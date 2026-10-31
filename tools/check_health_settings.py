"""Check maintained health commands with real Brigadier and stubbed Minecraft APIs."""
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


STUBS = r"""
static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
static class Value<T> { T value; final T defaultValue; Value(T v){value=v;defaultValue=v;} T get(){return value;} void set(T v){value=v;} void reset(){value=defaultValue;} }
enum EnumPlayerPart {
    HEAD, LEFT_ARM, LEFT_LEG, LEFT_FOOT, BODY, RIGHT_ARM, RIGHT_LEG, RIGHT_FOOT;
    static final EnumPlayerPart[] VALUES=values(); String getSerializedName(){return name().toLowerCase(Locale.ROOT);}
}
static class Component { String key; static Component translatable(String key,Object... args){var c=new Component();c.key=key;return c;} }
static class Permissions { static int COMMANDS_GAMEMASTER=2; }
static class CommandSourceStack {
    int permission, feedback; String failure; CommandSourceStack(int p){permission=p;}
    boolean hasPermission(int p){return permission>=p;} CommandSourceStack permissions(){return this;}
    Server getServer(){return server;} void sendSuccess(Supplier<Component> s,boolean broadcast){feedback++;}
    void sendFailure(Component c){failure=c.key;}
}
static class Commands {
    static LiteralArgumentBuilder<CommandSourceStack> literal(String name){return LiteralArgumentBuilder.literal(name);}
    static <T> RequiredArgumentBuilder<CommandSourceStack,T> argument(String name,ArgumentType<T> type){return RequiredArgumentBuilder.argument(name,type);}
}
static class Server {
    List<Player> players=new ArrayList<>(); Server getPlayerList(){return this;} List<Player> getPlayers(){return players;}
    Server getPackRepository(){return this;} List<String> getSelectedIds(){return List.of();}
    CompletableFuture<Void> reload=CompletableFuture.completedFuture(null); boolean throwOnReload;
    CompletableFuture<Void> reloadResources(List<String> ids){if(throwOnReload)throw new IllegalStateException("reload failed");return reload;}
    void execute(Runnable r){r.run();}
}
static Server server=new Server();
static class Level { boolean isClientSide; boolean isClientSide(){return isClientSide;} }
static class Player {
    Level level=new Level(); float maxHealth=20; PlayerDamageModel model; int syncs, configSyncs;
    Level level(){return level;} float getMaxHealth(){return maxHealth;}
}
static class CommonUtils {
    static PlayerDamageModel getDamageModel(Player p){return p.model;}
    static void syncDamageModel(Player p){p.syncs++;}
}
static class FirstAidNetworking {
    static void sendServerConfig(Player p){p.configSyncs++;}
    static void sendDamageModelSync(Player p,Object model,boolean scale){p.syncs++;}
}
static class FirstAid { static Log LOGGER=new Log(); }
static class Log { void info(Object... args){} void error(Object... args){} }
static class KnockdownNotices { static int broadcasts; static void broadcastIfChanged(Server s){broadcasts++;} }
static class FirstAidCommand {
    static int ruleUpdates;
    static void refreshNaturalRegenState(CommandSourceStack source){ruleUpdates++;}
    static void syncServerConfig(CommandSourceStack source){server.players.forEach(p->p.configSyncs++);}
    static void syncCommandSettings(CommandSourceStack source){syncServerConfig(source);}
}
static class AbstractDamageablePart {
    final EnumPlayerPart part; final int initialMaxHealth=4; float currentHealth=3; int maxHealth=4;
    Object activeHealer=new Object(); AbstractDamageablePart(EnumPlayerPart p){part=p;}
    int getMaxHealth(){return maxHealth;}
    SET_MAX_HEALTH
}
static class PlayerDamageModel implements Iterable<AbstractDamageablePart> {
    List<AbstractDamageablePart> parts=Arrays.stream(EnumPlayerPart.VALUES).map(AbstractDamageablePart::new).toList();
    float prevScaleFactor; int scheduled, rescueRefreshes, painRefreshes; public Iterator<AbstractDamageablePart> iterator(){return parts.iterator();}
    void scheduleResync(){scheduled++;}
    void refreshRescueWakeUpState(Player p){rescueRefreshes++;} void refreshPainState(Player p){painRefreshes++;}
    REFRESH_MAX_HEALTH
    RUN_SCALE_LOGIC
}
"""

TESTS = r"""
CommandDispatcher<CommandSourceStack> dispatcher=new CommandDispatcher<>();
MedicalSettingCommands.register(dispatcher);
var admin=new CommandSourceStack(2); var denied=new CommandSourceStack(1);
Player player=new Player(); player.model=new PlayerDamageModel(); server.players.add(player);
var injured=player.model.parts.get(0); Object treatment=injured.activeHealer;
for(var part:EnumPlayerPart.VALUES) {
    String command="firstaid health "+part.getSerializedName();
    int saved=FirstAidConfig.saved, syncs=player.syncs;
    check(dispatcher.execute(command,admin)==1,"query result");
    check(FirstAidConfig.saved==saved && player.syncs==syncs,"query must not change state");
    try {dispatcher.execute(command+" 8",denied);throw new AssertionError("permission bypass");}
    catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){}
    check(FirstAidConfig.saved==saved,"denied write must not persist");
    for(String value:List.of("1","0","-1","13","2147483648","NaN","2.5")) {
        try {dispatcher.execute(command+" "+value,admin);throw new AssertionError("accepted invalid "+value);}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){}
    }
    check(dispatcher.execute(command+" 8",admin)==1,"set result");
    check(FirstAidConfig.getPartMaxHealth(part).get()==8,"correct config field");
    check(player.model.parts.get(part.ordinal()).getMaxHealth()==8,"online update");
    check(FirstAidConfig.saved==saved+1 && player.syncs==syncs+1,"save and sync");
}
check(injured.currentHealth==3 && injured.activeHealer==treatment,"preserve injury and treatment");
dispatcher.execute("firstaid health head 2",admin);
check(injured.currentHealth==2 && injured.getMaxHealth()==2,"lowering maximum clamps health");
dispatcher.execute("firstaid health head 12",admin);
check(injured.currentHealth==2,"raising maximum must not heal");
FirstAidConfig.SERVER.capMaxHealth.set(false); player.maxHealth=40;
player.model.runScaleLogic(player);
check(injured.getMaxHealth()==24,"attribute scaling uses updated config");
player.maxHealth=20; player.model.runScaleLogic(player);
check(injured.getMaxHealth()==12,"reset scaling must not restore initial config");
FirstAidConfig.SERVER.scaleMaxHealth.set(false); player.maxHealth=60;
dispatcher.execute("firstaid health head 6",admin);
check(injured.getMaxHealth()==6 && injured.currentHealth==2,"disabled scaling still refreshes");
var returning=new Player(); returning.model=new PlayerDamageModel();
returning.model.parts.get(0).maxHealth=12; returning.model.runScaleLogic(returning);
check(returning.model.parts.get(0).getMaxHealth()==6,"offline saved maximum is refreshed on join");
check(returning.model.scheduled>0,"recalculated maximum must sync");
player.level.isClientSide=true; injured.maxHealth=10; player.model.refreshMaxHealth(player);
check(injured.getMaxHealth()==10,"client must retain authoritative maximum");
int beforeReset=FirstAidConfig.saved;
FirstAidConfig.GENERAL.debug.set(true);
try {dispatcher.execute("firstaid reset",denied);throw new AssertionError("reset permission bypass");}
catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){}
check(FirstAidConfig.saved==beforeReset,"unauthorized reset must not write");
player.level.isClientSide=false; player.maxHealth=20;
int syncs=player.syncs, configs=player.configSyncs;
dispatcher.execute("firstaid reset",admin);
check(FirstAidConfig.saved==beforeReset+1,"reset saves once");
check(injured.getMaxHealth()==4 && injured.currentHealth==2 && injured.activeHealer==treatment,"reset must preserve player state");
check(player.syncs==syncs+1 && player.configSyncs==configs+1,"reset must sync settings and model");
check(player.model.rescueRefreshes==1 && player.model.painRefreshes==1 && FirstAidCommand.ruleUpdates==1,"reset refreshes runtime state");
check(FirstAidConfig.GENERAL.debug.get()==true,"reset does not touch client/general settings");
FirstAidConfig.failOnReset=true; beforeReset=FirstAidConfig.saved;
check(dispatcher.execute("firstaid reset",admin)==0 && FirstAidConfig.saved==beforeReset,"save failure result");
check("firstaid.command.reset.failed".equals(admin.failure),"save failure feedback");
FirstAidConfig.failOnReset=false;
server.reload=new CompletableFuture<>();
dispatcher.execute("firstaid reset",admin); beforeReset=FirstAidConfig.saved;
check(dispatcher.execute("firstaid reset",admin)==0 && FirstAidConfig.saved==beforeReset,"busy reset must not write again");
check("firstaid.command.setting.busy".equals(admin.failure),"busy feedback");
server.reload.completeExceptionally(new IllegalStateException("reload failure"));
check("firstaid.command.reset.reload_failed".equals(admin.failure),"asynchronous failure feedback");
server.reload=CompletableFuture.completedFuture(null);
check(dispatcher.execute("firstaid reset",admin)==1,"failure clears busy state");
server.throwOnReload=true;
check(dispatcher.execute("firstaid reset",admin)==0,"synchronous reload failure result");
check("firstaid.command.reset.reload_failed".equals(admin.failure),"synchronous failure feedback");
server.throwOnReload=false;
check(dispatcher.execute("firstaid reset",admin)==1,"synchronous failure clears busy state");
System.out.println("MODULE: health and reset permissions, live state, synchronization, busy/failure handling PASS");
"""

brigadier = next((Path.home() / ".gradle/caches/modules-2/files-2.1/com.mojang/brigadier/1.3.10").rglob("brigadier-*.jar"))
for module in MODULES:
    base = ROOT / module / "src/main/java/ichttt/mods/firstaid"
    config = (base / "FirstAidConfig.java").read_text(encoding="utf-8")
    mapper = re.sub(r"(?:ConfigValue<Integer>|(?:Forge|Mod)ConfigSpec.IntValue)", "Value<Integer>",
                    method(config, "public static "+("ConfigValue<Integer>" if module.startswith("fabric") else
                           "ForgeConfigSpec.IntValue" if module.startswith("forge") else "ModConfigSpec.IntValue")+" getPartMaxHealth("))
    fields = re.findall(r"case \w+ -> SERVER\.(\w+)", mapper)
    commands = (base / "common/MedicalSettingCommands.java").read_text(encoding="utf-8")
    commands = commands[commands.index("public final class MedicalSettingCommands"):].replace("public final class", "static final class", 1)
    extras = "".join("Value<Integer> "+f+"=new Value<>(4);" for f in fields)
    for field in re.findall(r"SERVER\.(\w+)", commands):
        if field not in fields and field != "scaleMaxHealth":
            fields.append(field)
            extras += "Value<"+("Double" if field.endswith("Gain") else "Integer")+"> "+field+"=new Value<>("+("0.0" if field.endswith("Gain") else "1")+");"
    model = (base / "common/damagesystem/PlayerDamageModel.java").read_text(encoding="utf-8")
    part = (base / "common/damagesystem/DamageablePart.java").read_text(encoding="utf-8")
    stubs = STUBS.replace("SET_MAX_HEALTH", method(part, "public void setMaxHealth("))
    stubs = stubs.replace("REFRESH_MAX_HEALTH", method(model, "public void refreshMaxHealth("))
    stubs = stubs.replace("RUN_SCALE_LOGIC", method(model, "public void runScaleLogic("))
    reset_stub = "static boolean failOnReset; static void resetServerSettings(){if(failOnReset)throw new IllegalStateException(\"save failure\");try{for(var f:Settings.class.getDeclaredFields())((Value<?>)f.get(SERVER)).reset();}catch(ReflectiveOperationException e){throw new AssertionError(e);}saved++;}"
    config_stub = "static class FirstAidConfig { static int saved; static void persistCommandSettings(){saved++;} "+reset_stub+"static Settings SERVER=new Settings(),GENERAL=new Settings(); static class Settings {"+extras+"Value<Boolean> scaleMaxHealth=new Value<>(true), capMaxHealth=new Value<>(true), debug=new Value<>(false); }"+mapper+"}"
    for lang in ("en_us", "zh_cn"):
        text = json.loads((ROOT / module / "src/main/resources/assets/firstaid/lang" / (lang+".json")).read_text(encoding="utf-8"))
        assert all(key in text for key in ("firstaid.command.health.query", "firstaid.command.health.set", "firstaid.tip.commands.health.label", "firstaid.tip.commands.health.detail",
                                          "firstaid.command.reset.done", "firstaid.command.reset.failed", "firstaid.command.reset.reload_failed",
                                          "firstaid.tip.commands.reset.label", "firstaid.tip.commands.reset.detail"))
    source = "import java.util.*; import java.util.function.*; import java.util.concurrent.*; import com.mojang.brigadier.*; import com.mojang.brigadier.arguments.*; import com.mojang.brigadier.builder.*; public class HealthSettingsCheck {"+stubs+config_stub+commands+"public static void main(String[] args) throws Exception {"+TESTS.replace("MODULE",module)+"}}"
    with tempfile.TemporaryDirectory(prefix="firstaid-health-") as directory:
        path = Path(directory) / "HealthSettingsCheck.java"
        path.write_text(source, encoding="utf-8")
        subprocess.run(["javac", "-cp", str(brigadier), "-d", directory, str(path)], check=True)
        subprocess.run(["java", "-cp", directory+";"+str(brigadier), "HealthSettingsCheck"], check=True)

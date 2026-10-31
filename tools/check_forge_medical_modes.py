"""Run Forge medical-mode source with real Brigadier and small Minecraft API stubs.

From the repository root: python tools/check_forge_medical_modes.py
Requires a JDK and the Brigadier jar already downloaded by the module's Gradle build.
"""
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "forge1.20.1"
BASE = MODULE / "src/main/java/ichttt/mods/firstaid"
FIELDS = ("medicineEffectMode", "medicineTimingMultiplier", "injuryDebuffMode",
          "lowInjuryDebuffDamageScale", "lowInjuryDebuffAmplifierScale", "lowInjuryDebuffDurationScale")


def block(source, signature, opening="{", closing="}"):
    start = source.index(signature)
    end = source.index(opening, start) + 1
    depth = 1
    while depth:
        depth += (source[end] == opening) - (source[end] == closing)
        end += 1
    return source[start:end]


def read(relative):
    return (BASE / relative).read_text(encoding="utf-8")


def nested(relative, declaration):
    source = read(relative)
    return block(source, declaration).replace("public class", "static class", 1).replace(
        "public abstract class", "static abstract class", 1)


STUBS = r"""
@interface Nonnull {} @interface Nullable {}
static int checks;
static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
static void same(float a,float b){check(Math.abs(a-b)<0.0001F,a+" != "+b);}
interface UnmodifiableConfig {Map<String,Object> valueMap();}
record ConfigTree(Map<String,Object> valueMap) implements UnmodifiableConfig {}
static class ForgeConfigSpec {
    static class ConfigValue<T>{T value,defaultValue;int cacheClears;
        ConfigValue(T v){value=defaultValue=v;}T get(){return value;}void set(T v){value=v;}
        T getDefault(){return defaultValue;}void clearCache(){cacheClears++;}}
}
static class Value<T> extends ForgeConfigSpec.ConfigValue<T>{Value(T v){super(v);}}
static class Spec {UnmodifiableConfig values;UnmodifiableConfig getValues(){return values;}void save(){FirstAidConfig.saved++;}}
static class Builder {
    Map<String,Object> values=new LinkedHashMap<>();
    Builder comment(String s){return this;}
    <T extends Enum<T>> Value<T> defineEnum(String k,T v){var value=new Value<>(v);values.put(k,value);return value;}
    Value<Double> defineInRange(String k,double v,double min,double max){var value=new Value<>(v);values.put(k,value);return value;}
}
record ResourceLocation(String id){static ResourceLocation tryParse(String s){return new ResourceLocation(s);}public String toString(){return id;}}
static class MobEffect {} static class SoundEvent {}
static class ForgeRegistries {
    static Effects MOB_EFFECTS=new Effects();static Sounds SOUND_EVENTS=new Sounds();
    static class Effects {MobEffect getValue(ResourceLocation id){return new MobEffect();}ResourceLocation getKey(MobEffect e){return new ResourceLocation("test:injury");}}
    static class Sounds {SoundEvent getValue(ResourceLocation id){return new SoundEvent();}}
}
static class MobEffectInstance {MobEffect effect;int duration,amplifier;
    MobEffectInstance(MobEffect e,int d,int a,boolean ambient,boolean visible){effect=e;duration=d;amplifier=a;}}
static class Player {int tickCount;PlayerDamageModel model=new PlayerDamageModel();boolean hasEffect(MobEffect e){return model.last!=null;}}
static class ServerPlayer extends Player {}
enum EnumPlayerPart { HEAD, BODY }
static class EnumDebuffSlot {EnumPlayerPart[] playerParts=EnumPlayerPart.values();}
static class AbstractDamageablePart {int maxHealth=10;float currentHealth=10;boolean canCauseDeath;int getMaxHealth(){return maxHealth;}}
static class KnockdownNotices {static void broadcastIfChanged(Object server){}}
static class AbstractPlayerDamageModel {Part[] parts={new Part(),new Part()};AbstractDamageablePart getFromEnum(EnumPlayerPart p){return parts[p.ordinal()];}}
static class PlayerDamageModel extends AbstractPlayerDamageModel {MobEffectInstance last;float relief;int applications,refreshes;
    void refreshMaxHealth(Player p){refreshes++;}void refreshRescueWakeUpState(Player p){refreshes++;}void refreshPainState(Player p){refreshes++;}
    float getAdrenalineDebuffRelief(){return relief;}
    void applyTrackedInjuryEffect(Player p,String id,MobEffectInstance e){last=e;applications++;}
    void replaceTrackedInjuryEffect(Player p,String id,MobEffectInstance e){last=e;applications++;}}
static class CommonUtils {static AbstractPlayerDamageModel getDamageModel(Player p){return p.model;}static void checkClient(Object ctx){}static void syncDamageModel(Player p){}}
static class Level {boolean isClientSide;}
static class ItemStack {}
interface IDebuff {void handleDamageTaken(float d,float h,ServerPlayer p);void handleHealing(float d,float h,ServerPlayer p);default void update(Player p,float h){}}
static class Probe implements IDebuff {int hits,heals,updates;float amount,fraction;
    public void handleDamageTaken(float a,float h,ServerPlayer p){hits++;amount=a;fraction=h;}
    public void handleHealing(float a,float h,ServerPlayer p){heals++;amount=a;fraction=h;}
    public void update(Player p,float h){updates++;fraction=h;}}
record ConstantDebuffEntry(float healthFractionThreshold,int effectAmplifier){}
record OnHitDebuffEntry(float damageTakenThreshold,int effectDuration,float healthFractionThreshold){}
static class MessagePlayHurtSound {int duration;MessagePlayHurtSound(SoundEvent s,int d){duration=d;}}
static class PacketDistributor {static PacketDistributor PLAYER=new PacketDistributor();Object with(Supplier<ServerPlayer> p){return p.get();}}
static class Network {int sends;Object last;void send(Object target,Object message){sends++;last=message;}}
static class Server {List<ServerPlayer> players=List.of(new ServerPlayer());Server getPlayerList(){return this;}List<ServerPlayer> getPlayers(){return players;}
    java.util.concurrent.CompletableFuture<Void> reload=java.util.concurrent.CompletableFuture.completedFuture(null);
    Server getPackRepository(){return this;}List<String> getSelectedIds(){return List.of();}
    java.util.concurrent.CompletableFuture<Void> reloadResources(List<String> ids){return reload;}void execute(Runnable r){r.run();}}
static class CommandSourceStack {int permission;Server server=new Server();CommandSourceStack(int p){permission=p;}
    boolean hasPermission(int p){return permission>=p;}Server getServer(){return server;}void sendSuccess(Supplier<Component> s,boolean b){}void sendFailure(Component c){}}
static class Log {void error(Object... args){}}
static class FirstAidCommand {static void refreshNaturalRegenState(CommandSourceStack s){}static void syncCommandSettings(CommandSourceStack s){SettingsCommands.syncCommandSettings(s);}}
static class Component {static Component translatable(String k,Object... args){return new Component();}}
static class Commands {static LiteralArgumentBuilder<CommandSourceStack> literal(String s){return LiteralArgumentBuilder.literal(s);}
    static <T> RequiredArgumentBuilder<CommandSourceStack,T> argument(String s,ArgumentType<T> t){return RequiredArgumentBuilder.argument(s,t);}}
static class ServerLifecycleHooks {static Object server;static Object getCurrentServer(){return server;}}
static class NetworkEvent {static class Context {void enqueueWork(Runnable r){r.run();}}}
static class FriendlyByteBuf {List<Object> data=new ArrayList<>();int index;
    void writeBoolean(boolean v){data.add(v);}boolean readBoolean(){return (Boolean)data.get(index++);}
    void writeFloat(float v){data.add(v);}float readFloat(){return (Float)data.get(index++);}
    void writeUtf(String v,int max){data.add(v);}String readUtf(int max){return (String)data.get(index++);}
    void writeEnum(Enum<?> e){data.add(e);} <T extends Enum<T>> T readEnum(Class<T> type){return type.cast(data.get(index++));}}
"""

TESTS = r"""
same(FirstAid.medicineTimingMultiplier,1);
check(FirstAidConfig.SERVER.medicineEffectMode.get()==FirstAid.MedicineEffectMode.REALISTIC,"default medicine mode");
check(FirstAidConfig.SERVER.injuryDebuffMode.get()==FirstAid.InjuryDebuffMode.NORMAL,"default debuff mode");
same(FirstAidConfig.SERVER.lowInjuryDebuffDamageScale.get().floatValue(),0.4F);
same(FirstAidConfig.SERVER.lowInjuryDebuffAmplifierScale.get().floatValue(),0.5F);
same(FirstAidConfig.SERVER.lowInjuryDebuffDurationScale.get().floatValue(),0.5F);
var dispatcher=new CommandDispatcher<CommandSourceStack>();
SettingsCommands.register(dispatcher);ResetCommands.register(dispatcher);var admin=new CommandSourceStack(2);var denied=new CommandSourceStack(1);
for(var mode:FirstAid.MedicineEffectMode.values()){
    String command="firstaid medicineeffect "+mode.name().toLowerCase(Locale.ROOT);
    int saved=FirstAidConfig.saved,sends=FirstAid.NETWORKING.sends;
    try{dispatcher.execute(command,denied);throw new AssertionError("permission bypass");}catch(CommandSyntaxException expected){}
    check(FirstAidConfig.saved==saved,"denied command persisted");
    check(dispatcher.execute(command,admin)==1,"preset command");
    check(FirstAid.medicineEffectMode==mode && FirstAidConfig.SERVER.medicineEffectMode.get()==mode,"preset persisted");
    same(FirstAid.medicineTimingMultiplier,mode.getTimingMultiplier());
    check(FirstAidConfig.saved==saved+1 && FirstAid.NETWORKING.sends==sends+1,"preset save/sync");
    var healer=new PartHealer(()->FirstAid.scaleMedicalTimingTicks(360),()->4,new ItemStack());
    int interval=Math.round(360*mode.getTimingMultiplier());
    for(int i=1;i<interval;i++)check(!healer.tick(),"early heal");
    check(healer.tick() && healer.getHealsDone()==1,"healing interval");
}
for(String value:List.of("0","-1","20.1","Infinity","-Infinity","bad")){
    int saved=FirstAidConfig.saved;
    try{dispatcher.execute("firstaid medicineeffect multiplier "+value,admin);throw new AssertionError("invalid multiplier "+value);}catch(CommandSyntaxException expected){}
    check(FirstAidConfig.saved==saved,"invalid command persisted");
}
int savedBeforeNaN=FirstAidConfig.saved;
try{check(dispatcher.execute("firstaid medicineeffect multiplier NaN",admin)==0,"NaN accepted");}catch(CommandSyntaxException expected){}
check(FirstAidConfig.saved==savedBeforeNaN,"NaN persisted");
for(String value:List.of("0.01","0.75","20")){
    check(dispatcher.execute("firstaid medicineeffect multiplier "+value,admin)==1,"custom multiplier");
    same(FirstAid.medicineTimingMultiplier,Float.parseFloat(value));
}
FirstAid.medicineTimingMultiplier=0.01F;check(FirstAid.scaleMedicalTimingTicks(1)==1,"minimum interval");
FirstAid.medicineTimingMultiplier=0.5F;check(FirstAid.scaleMedicalTimingTicks(3)==2,"tick rounding");
var player=new ServerPlayer();
for(var mode:FirstAid.InjuryDebuffMode.values()){
    String command="firstaid injurydebuff "+mode.name().toLowerCase(Locale.ROOT);
    try{dispatcher.execute(command,denied);throw new AssertionError("debuff permission bypass");}catch(CommandSyntaxException expected){}
    check(dispatcher.execute(command,admin)==1,"debuff command");
    var probe=new Probe();var part=new Part();part.debuffs=new IDebuff[]{probe};
    same(part.damage(6,player,true,0),0);same(part.currentHealth,4);
    if(mode==FirstAid.InjuryDebuffMode.OFF)check(probe.hits==0,"off damage signal");
    else {same(probe.amount,mode==FirstAid.InjuryDebuffMode.LOW?2.4F:6);same(probe.fraction,mode==FirstAid.InjuryDebuffMode.LOW?0.7F:0.4F);}
    same(part.heal(2,player,true),0);same(part.currentHealth,6);
    if(mode==FirstAid.InjuryDebuffMode.OFF)check(probe.heals==0,"off healing signal");
    else same(probe.amount,mode==FirstAid.InjuryDebuffMode.LOW?0.8F:2);
    part.tick(new Level(),player,true,false);
    check(probe.updates==(mode==FirstAid.InjuryDebuffMode.OFF?0:1),"debuff tick gate");
    same(part.damage(20,player,false,2),16);same(part.currentHealth,2);
    var hit=new OnHitDebuff(new ResourceLocation("test:injury"),List.of(new OnHitDebuffEntry(2,40,0)),null,80);
    player.model.last=null;player.model.applications=0;player.tickCount=0;
    hit.handleDamageTaken(6,1,player);
    if(mode==FirstAid.InjuryDebuffMode.OFF)check(player.model.last==null,"off on-hit");
    else {
        check(player.model.last.duration==(mode==FirstAid.InjuryDebuffMode.LOW?20:40),"hit duration");
        player.tickCount=79;hit.handleDamageTaken(6,1,player);check(player.model.applications==1,"hit cooldown");
        player.tickCount=80;hit.handleDamageTaken(6,1,player);check(player.model.applications==2,"hit cooldown expiry");
    }
    for(float relief:new float[]{0,0.2F}){
        player.model.relief=relief;player.model.last=null;
        var constant=new ConstantDebuff(new ResourceLocation("minecraft:slowness"),List.of(new ConstantDebuffEntry(1,5)));
        constant.update(player,0);
        if(mode==FirstAid.InjuryDebuffMode.OFF)check(player.model.last==null,"off constant");
        else check(player.model.last.amplifier==(mode==FirstAid.InjuryDebuffMode.LOW?2:relief==0?4:3),"constant amplifier/adrenaline");
    }
    var sharedProbe=new Probe();var shared=new SharedDebuff(sharedProbe,new EnumDebuffSlot());
    for(var sharedPart:player.model.parts)sharedPart.currentHealth=4;
    shared.tick(player);same(sharedProbe.fraction,mode==FirstAid.InjuryDebuffMode.LOW?0.7F:0.4F);
}
FirstAid.medicineTimingMultiplier=0.75F;FirstAid.injuryDebuffMode=FirstAid.InjuryDebuffMode.LOW;
FirstAid.lowInjuryDebuffDamageScale=0.2F;FirstAid.lowInjuryDebuffAmplifierScale=0.3F;FirstAid.lowInjuryDebuffDurationScale=0.4F;
FirstAidConfig.persistCommandSettings();FirstAid.medicineTimingMultiplier=10;FirstAid.injuryDebuffMode=FirstAid.InjuryDebuffMode.OFF;
FirstAid.lowInjuryDebuffDamageScale=1;FirstAid.lowInjuryDebuffAmplifierScale=1;FirstAid.lowInjuryDebuffDurationScale=1;
FirstAidConfig.applyCommandSettings();same(FirstAid.medicineTimingMultiplier,0.75F);check(FirstAid.injuryDebuffMode==FirstAid.InjuryDebuffMode.LOW,"config roundtrip");
same(FirstAid.lowInjuryDebuffDamageScale,0.2F);same(FirstAid.lowInjuryDebuffAmplifierScale,0.3F);same(FirstAid.lowInjuryDebuffDurationScale,0.4F);
var buffer=new FriendlyByteBuf();MessageSyncCommandSettings.current().encode(buffer);
var packet=new MessageSyncCommandSettings(buffer);check(buffer.index==buffer.data.size(),"packet fields consumed");
FirstAid.medicineTimingMultiplier=2;FirstAid.injuryDebuffMode=FirstAid.InjuryDebuffMode.OFF;
FirstAid.lowInjuryDebuffDamageScale=1;FirstAid.lowInjuryDebuffAmplifierScale=1;FirstAid.lowInjuryDebuffDurationScale=1;
MessageSyncCommandSettings.Handler.onMessage(packet,NetworkEvent.Context::new);
same(FirstAid.medicineTimingMultiplier,0.75F);check(FirstAid.injuryDebuffMode==FirstAid.InjuryDebuffMode.LOW,"remote settings");
same(FirstAid.lowInjuryDebuffDamageScale,0.2F);same(FirstAid.lowInjuryDebuffAmplifierScale,0.3F);same(FirstAid.lowInjuryDebuffDurationScale,0.4F);
ServerLifecycleHooks.server=new Object();FirstAid.medicineTimingMultiplier=2;
MessageSyncCommandSettings.Handler.onMessage(packet,NetworkEvent.Context::new);same(FirstAid.medicineTimingMultiplier,2);
ServerLifecycleHooks.server=null;
FirstAid.medicineTimingMultiplier=Float.NaN;FirstAid.lowInjuryDebuffDamageScale=Float.POSITIVE_INFINITY;
buffer=new FriendlyByteBuf();MessageSyncCommandSettings.current().encode(buffer);
MessageSyncCommandSettings.Handler.onMessage(new MessageSyncCommandSettings(buffer),NetworkEvent.Context::new);
same(FirstAid.medicineTimingMultiplier,1);same(FirstAid.lowInjuryDebuffDamageScale,0.4F);
dispatcher.execute("firstaid medicineeffect casual",admin);dispatcher.execute("firstaid injurydebuff off",admin);
int savedBeforeReset=FirstAidConfig.saved,sendsBeforeReset=FirstAid.NETWORKING.sends;
try{dispatcher.execute("firstaid reset",denied);throw new AssertionError("reset permission bypass");}catch(CommandSyntaxException expected){}
check(FirstAidConfig.saved==savedBeforeReset,"denied reset persisted");
check(dispatcher.execute("firstaid reset",admin)==1,"reset command");
check(FirstAidConfig.saved==savedBeforeReset+1 && FirstAid.NETWORKING.sends==sendsBeforeReset+1,"reset save/sync");
check(FirstAid.medicineEffectMode==FirstAid.MedicineEffectMode.REALISTIC,"reset medicine mode");
check(FirstAid.injuryDebuffMode==FirstAid.InjuryDebuffMode.NORMAL,"reset debuff mode");
same(FirstAid.medicineTimingMultiplier,1);same(FirstAid.lowInjuryDebuffDamageScale,0.4F);
same(FirstAid.lowInjuryDebuffAmplifierScale,0.5F);same(FirstAid.lowInjuryDebuffDurationScale,0.5F);
same(FirstAidConfig.SERVER.medicineTimingMultiplier.get().floatValue(),1);
same(FirstAidConfig.SERVER.lowInjuryDebuffDamageScale.get().floatValue(),0.4F);
same(FirstAidConfig.SERVER.lowInjuryDebuffAmplifierScale.get().floatValue(),0.5F);
same(FirstAidConfig.SERVER.lowInjuryDebuffDurationScale.get().floatValue(),0.5F);
check(FirstAidConfig.SERVER.medicineEffectMode.get()==FirstAid.MedicineEffectMode.REALISTIC,"reset persisted medicine mode");
check(FirstAidConfig.SERVER.injuryDebuffMode.get()==FirstAid.InjuryDebuffMode.NORMAL,"reset persisted debuff mode");
check(FirstAidConfig.SERVER.medicineTimingMultiplier.cacheClears==1,"reset cached config");
check(admin.server.players.get(0).model.refreshes==3,"reset refreshes online players");
var resetWire=new FriendlyByteBuf();((MessageSyncCommandSettings)FirstAid.NETWORKING.last).encode(resetWire);
FirstAid.medicineTimingMultiplier=2;FirstAid.injuryDebuffMode=FirstAid.InjuryDebuffMode.OFF;
MessageSyncCommandSettings.Handler.onMessage(new MessageSyncCommandSettings(resetWire),NetworkEvent.Context::new);
same(FirstAid.medicineTimingMultiplier,1);check(FirstAid.injuryDebuffMode==FirstAid.InjuryDebuffMode.NORMAL,"reset client sync");
admin.server.reload=new java.util.concurrent.CompletableFuture<>();
check(dispatcher.execute("firstaid reset",admin)==1,"reset starts reload");
int savedDuringReload=FirstAidConfig.saved;
check(dispatcher.execute("firstaid reset",admin)==0,"concurrent reset blocked");
check(FirstAidConfig.saved==savedDuringReload,"busy reset must not persist");
admin.server.reload.complete(null);check(!ResetCommands.reloadPending,"reset reload completes");
System.out.println("Forge medical modes: "+checks+" checks PASS (presets, healing, LOW/OFF, cooldown, permissions, config, sync, reset)");
"""


def main():
    assert block("void sample(){if(true){}} trailing", "void sample()") == "void sample(){if(true){}}"
    assert block('.then(call("x")).tail()', '.then(', "(", ")") == '.then(call("x"))'
    aid, config, commands = read("FirstAid.java"), read("FirstAidConfig.java"), read("common/FirstAidCommand.java")
    fields = "\n".join(re.search(r"public static [^;]+\b" + f + r"\s*=[^;]+;", aid)[0] for f in FIELDS)
    enums = block(aid, "public enum MedicineEffectMode") + block(aid, "public enum InjuryDebuffMode")
    definitions = "\n".join(re.search(r"\b" + f + r" = builder[\s\S]*?\.define(?:Enum|InRange)\([^;]+;", config)[0] for f in FIELDS)
    # Check the actual validation ranges, not just the runtime fixtures.
    for key, limits in (("medicineTimingMultiplier", "1D, 0.01D, 20D"),
                        ("lowInjuryDebuffDamageScale", "0.4D, 0D, 1D"),
                        ("lowInjuryDebuffAmplifierScale", "0.5D, 0D, 1D"),
                        ("lowInjuryDebuffDurationScale", "0.5D, 0D, 1D")):
        assert f'.defineInRange("{key}", {limits})' in definitions, key
    declarations = "\n".join(re.search(r"public final ForgeConfigSpec\.[^;]+\b" + f + ";", config)[0] for f in FIELDS)
    declarations = declarations.replace("ForgeConfigSpec.DoubleValue", "Value<Double>").replace("ForgeConfigSpec.EnumValue", "Value")
    apply_lines = "\n".join(line for line in block(config, "public static void applyCommandSettings()") .splitlines()
                            if any(f"FirstAid.{f} =" in line for f in FIELDS))
    persist_lines = "\n".join(line for line in block(config, "public static void persistCommandSettings()") .splitlines()
                              if any(f"SERVER.{f}.set(" in line for f in FIELDS))
    assert len(apply_lines.splitlines()) == len(persist_lines.splitlines()) == len(FIELDS)
    branches = "".join(block(commands, f'.then(Commands.literal("{name}")', "(", ")")
                       for name in ("medicineeffect", "injurydebuff"))
    methods = "\n".join(block(commands, signature) for signature in (
        "private static int setMedicineEffectMode(", "private static int setMedicineTimingMultiplier(",
        "private static int setInjuryDebuffMode(", "static void syncCommandSettings("))
    permission = re.search(r"\.requires\(source -> source.hasPermission\(\d+\)\)", commands)[0]
    medical_commands = read("common/MedicalSettingCommands.java")
    reset_permission = re.search(r"\.requires\(source -> source.hasPermission\(\d+\)\)", medical_commands)[0]
    reset_branch = block(medical_commands, '.then(Commands.literal("reset")', "(", ")")
    reset_methods = "\n".join(block(config, signature) for signature in (
        "public static void resetServerSettings()", "private static void resetValues(", "private static <T> void resetValue("))
    part_source = read("common/damagesystem/DamageablePart.java")
    part_methods = "\n".join(block(part_source, s) for s in (
        "public float heal(", "public float damage(float amount, @Nullable Player player, boolean applyDebuff, float minHealth)",
        "public void tick(Level world, Player player, boolean tickDebuffs, boolean pauseHealing)",
        "private static float softenDebuffHealthFraction(")) .replace("@Override", "")
    classes = f"""
    static class FirstAid {{ {fields} {enums} {block(aid, 'public static int scaleMedicalTimingTicks(')}
        static Network NETWORKING=new Network();static Log LOGGER=new Log();
        static boolean enablePainVignette=true,enablePainFovCompression=true,
        enablePainAudioEffects=true,projectileSuppressionEnabled=true,lowSuppressionEnabled;
        static float adrenalineReliefMultiplier=1.7F;static Set<ResourceLocation> suppressionEntityBlacklist=new HashSet<>();
        static void setSuppressionEntityBlacklist(List<ResourceLocation> v){{suppressionEntityBlacklist=new HashSet<>(v);}}
    }}
    static class FirstAidConfig {{static int saved;static Spec serverSpec=new Spec();static Settings SERVER=new Settings();
        static class Settings {{{declarations} Settings(){{Builder builder=new Builder();{definitions}
            serverSpec.values=new ConfigTree(Map.of("Medical",new ConfigTree(builder.values),"ignored","not a setting"));}}}}
        static void applyCommandSettings(){{{apply_lines}}}
        static void persistCommandSettings(){{{persist_lines}saved++;}}
        {reset_methods}
    }}
    static class SettingsCommands {{static void register(CommandDispatcher<CommandSourceStack> dispatcher){{
        dispatcher.register(Commands.literal("firstaid"){permission}{branches});}}{methods}}}
    static class ResetCommands {{static boolean reloadPending;
        static void register(CommandDispatcher<CommandSourceStack> dispatcher){{dispatcher.register(Commands.literal("firstaid"){reset_permission}{reset_branch});}}
        {block(medical_commands, 'private static int resetSettings(')}}}
    static class Part extends AbstractDamageablePart {{IDebuff[] debuffs;AbstractPartHealer activeHealer;float knockdownOverflow;{part_methods}}}
    """
    for relative, declaration in (("api/damagesystem/AbstractPartHealer.java", "public abstract class AbstractPartHealer"),
                                  ("common/damagesystem/PartHealer.java", "public class PartHealer"),
                                  ("common/damagesystem/debuff/AbstractDebuff.java", "public abstract class AbstractDebuff"),
                                  ("common/damagesystem/debuff/ConstantDebuff.java", "public class ConstantDebuff"),
                                  ("common/damagesystem/debuff/OnHitDebuff.java", "public class OnHitDebuff"),
                                  ("common/damagesystem/debuff/SharedDebuff.java", "public class SharedDebuff"),
                                  ("common/network/MessageSyncCommandSettings.java", "public class MessageSyncCommandSettings")):
        classes += nested(relative, declaration)
    registry = read("common/RegistryObjects.java")
    for item in ("bandage", "plaster"):
        assert f"FirstAid.scaleMedicalTimingTicks(server.{item}.secondsPerHeal.get() * 20)" in registry
    model = read("common/damagesystem/PlayerDamageModel.java")
    assert "FirstAid.injuryDebuffMode == FirstAid.InjuryDebuffMode.OFF" in block(model, "public void applyTrackedInjuryEffect(")
    assert "painSuppressed || FirstAid.injuryDebuffMode == FirstAid.InjuryDebuffMode.OFF" in model
    for relative, signature in (("common/EventHandler.java", "public static void onServerStop("),
                                ("client/ClientEventHandler.java", "public static void onDisconnect(")):
        reset = block(read(relative), signature)
        assert all(f"FirstAid.{f} =" in reset for f in FIELDS), relative
    for language in ("en_us", "zh_cn"):
        strings = json.loads((MODULE / f"src/main/resources/assets/firstaid/lang/{language}.json").read_text(encoding="utf-8"))
        assert "firstaid.command.medicineeffect.multiplier" in strings
    classpath = MODULE / "build/classpath/runServer_minecraftClasspath.txt"
    jars = [Path(p) for p in classpath.read_text(encoding="utf-8").split(os.pathsep)
            if Path(p).name.startswith("brigadier-")] if classpath.exists() else []
    if not jars:
        jars = sorted((Path.home() / ".gradle/caches/modules-2/files-2.1/com.mojang/brigadier/1.1.8").rglob("brigadier-*.jar"))
    if not jars:
        raise SystemExit("Brigadier not cached; build forge1.20.1 with its Gradle wrapper first.")
    jdk = MODULE / ".jdk/jdk-17/bin"
    java = str(jdk / "java.exe") if (jdk / "java.exe").exists() else shutil.which("java")
    javac = str(jdk / "javac.exe") if (jdk / "javac.exe").exists() else shutil.which("javac")
    if not java or not javac:
        raise SystemExit("A JDK with java and javac is required.")
    source = """import java.util.*;import java.util.function.*;
    import com.mojang.brigadier.*;import com.mojang.brigadier.arguments.*;
    import com.mojang.brigadier.builder.*;import com.mojang.brigadier.exceptions.*;
    public class ForgeMedicalModesCheck {""" + STUBS + classes + "public static void main(String[] args)throws Exception{" + TESTS + "}}"
    with tempfile.TemporaryDirectory(prefix="firstaid-forge-modes-") as directory:
        file = Path(directory) / "ForgeMedicalModesCheck.java"
        file.write_text(source, encoding="utf-8")
        subprocess.run([javac, "-encoding", "UTF-8", "-cp", str(jars[-1]), "-d", directory, str(file)], check=True)
        subprocess.run([java, "-cp", directory + os.pathsep + str(jars[-1]), "ForgeMedicalModesCheck"], check=True)


if __name__ == "__main__":
    main()

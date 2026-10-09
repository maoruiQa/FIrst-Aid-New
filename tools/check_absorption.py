"""Exercise production shield/HUD methods with small game API stubs: python tools/check_absorption.py."""
from pathlib import Path
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
    @interface Nonnull {} @interface Nullable {}
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    static void same(float a, float b) { check(Math.abs(a-b) < 0.0001F); }
    static class Mth { static int ceil(float v) { return (int)Math.ceil(v); } }
    static class Value<T> { T v; Value(T v) {this.v=v;} T get() {return v;} }
    static class FirstAidConfig { static General GENERAL=new General(); static class General {Value<Boolean> debug=new Value<>(false);} }
    static class FirstAid { static Log LOGGER=new Log(); }
    static class Log { void info(Object... args) {} void error(Object... args) {} void warn(Object... args) {} }
    static class LoggingMarkers {static String DAMAGE_DISTRIBUTION="damage";}
    enum EnumPlayerPart { HEAD, BODY; static final EnumPlayerPart[] VALUES=values(); }
    enum EquipmentSlot { HEAD }
    static class RegistryObjects {static Value<Object> MORPHINE_EFFECT=new Value<>(new Object());}
    static class Stats { static Object DAMAGE_TAKEN=new Object(); }
    static class CombatTracker { void recordDamage(Object s, float damage) {} }
    static class DamageSource {SourceType type(){return new SourceType();} float getFoodExhaustion(){return 0;} }
    static class SourceType {String msgId(){return "test";} }
    static class TestSource extends DamageSource {SourceType type(){return new SourceType();} }
    static class ArmorUtils {
        static float applyGlobalPotionModifiers(Player p, DamageSource s, float damage) {return damage;}
        static float applyArmor(Player p,Object a,DamageSource s,float damage,Object slot){return damage;}
        static float applyEnchantmentModifiers(Player p,Object slot,DamageSource s,float damage){return damage;}
    }
    static class ForgeHooks {static float onLivingDamage(Player p,DamageSource s,float damage){return damage;} }
    static class FirstAidLivingDamageEvent {FirstAidLivingDamageEvent(Object... args){} }
    static class MinecraftForge {
        static Bus EVENT_BUS=new Bus(); static class Bus {boolean cancel; boolean post(Object event){return cancel;}}
    }
    static class CompoundTag {float[] health; CompoundTag(float[] h){health=h;} }
    interface IDebuff {void handleDamageTaken(float damage,float health,ServerPlayer player);}
    static class AbstractDamageablePart {
        int maxHealth=10; float currentHealth=10; IDebuff[] debuffs;
        float damage(float amount,Player p,boolean debuff,float min){throw new AssertionError();}
        float damage(float amount,Player p,boolean debuff){return damage(amount,p,debuff,0);}
    }
    static class AbstractPlayerDamageModel implements Iterable<AbstractDamageablePart> {
        List<AbstractDamageablePart> parts=new ArrayList<>();
        AbstractPlayerDamageModel(){for(int i=0;i<EnumPlayerPart.VALUES.length;i++)parts.add(new Part());}
        AbstractDamageablePart getFromEnum(EnumPlayerPart p){return parts.get(p.ordinal());}
        public Iterator<AbstractDamageablePart> iterator(){return parts.iterator();}
        CompoundTag serializeNBT(){
            float[] health=new float[parts.size()];
            for(int i=0;i<health.length;i++)health[i]=parts.get(i).currentHealth;
            return new CompoundTag(health);
        }
        void deserializeNBT(CompoundTag tag){for(int i=0;i<parts.size();i++)parts.get(i).currentHealth=tag.health[i];}
        boolean isDead(Player p){return false;}
    }
    static class PlayerDamageModel extends AbstractPlayerDamageModel {
        void registerAdrenalineDamage(Player p,PlayerDamageModel before){}
        void registerDamageFeedback(Player p,DamageSource s,PlayerDamageModel before){}
        void handlePostDamage(Player p,DamageSource s){} void syncVanillaHealth(Player p){}
    }
    static class AttributeInstance {double getValue(){return 20;}}
    static class Attributes {static Object MAX_HEALTH=new Object();}
    static class Player {
        float absorption; boolean hardcore, poison, wither, frozen; int shieldWrites;
        AbstractPlayerDamageModel model=new PlayerDamageModel();
        float getAbsorptionAmount(){return absorption;}
        void setAbsorptionAmount(float amount){check(Float.isFinite(amount)&&amount>=0);absorption=amount;shieldWrites++;}
        float getHealth(){return 20;} AttributeInstance getAttribute(Object attr){return new AttributeInstance();}
        Object getItemBySlot(Object slot){return new Object();}
        boolean hasEffect(Object e){return e==MobEffects.POISON&&poison||e==MobEffects.WITHER&&wither;}
        boolean isFullyFrozen(){return frozen;} Level level(){return new Level(hardcore);}
        void causeFoodExhaustion(float v){} CombatTracker getCombatTracker(){return new CombatTracker();}
        void awardStat(Object stat,int amount){}
    }
    static class ServerPlayer extends Player {}
    static class Level {boolean hardcore;Level(boolean v){hardcore=v;} Level getLevelData(){return this;} boolean isHardcore(){return hardcore;} }
    static class MobEffects {static Object POISON=new Object(), WITHER=new Object();}
    static class CommonUtils {
        static int syncs; static AbstractPlayerDamageModel getDamageModel(Player p){return p.model;}
        static void syncDamageModel(ServerPlayer p){syncs++;}
        static void syncDamageModel(ServerPlayer p,AbstractPlayerDamageModel m,boolean scale){syncs++;}
        static void syncPart(ServerPlayer p,AbstractDamageablePart part){} static void killPlayer(Object... args){}
    }
    static class Pair<A,B> {A a; B b;Pair(A a,B b){this.a=a;this.b=b;} A getLeft(){return a;} B getRight(){return b;} }
    interface IDamageDistributionAlgorithm {
        float distributeDamage(float damage,Player player,DamageSource source,boolean addStat);
        default boolean skipGlobalPotionModifiers(){return false;}
    }
    static class Gui {int leftHeight=39; Gui hud=this;}
    static class ForgeGui extends Gui {}
    static class Minecraft {static Minecraft INSTANCE=new Minecraft();Player player;static Minecraft getInstance(){return INSTANCE;}}
    static class RenderPipelines {static Object GUI_TEXTURED=new Object();}
    static class ResourceLocation {
        String name;ResourceLocation(String s){name=s;} static ResourceLocation withDefaultNamespace(String s){return new ResourceLocation(s);}
        public String toString(){return name;}
    }
    static class HealthRenderUtils {static Object GUI_ICONS_LOCATION=new Object();}
    record Draw(String sprite,int x,int y,int w,int h) {}
    static class GuiGraphics {
        List<Draw> draws=new ArrayList<>();
        void blitSprite(Object s,int x,int y,int w,int h){draws.add(new Draw(s.toString(),x,y,w,h));}
        void blitSprite(Object pipeline,Object s,int x,int y,int w,int h){blitSprite(s,x,y,w,h);}
        void blit(Object texture,int x,int y,int u,int v,int w,int h){draws.add(new Draw((u==16?"container":u==169?"absorbing_half":"absorbing_full")+(v==45?"_hardcore":""),x,y,w,h));}
    }
    static class GuiGraphicsExtractor extends GuiGraphics {}
"""

base = ROOT / "forge1.20.1/src/main/java/ichttt/mods/firstaid"
damage = (base / "common/damagesystem/distribution/DamageDistribution.java").read_text(encoding="utf-8")
part = (base / "common/damagesystem/DamageablePart.java").read_text(encoding="utf-8")
classes = ["static class Part extends AbstractDamageablePart {"
           + method(part, "public float damage(float amount, @Nullable Player player, boolean applyDebuff, float minHealth)")
           + method(part, "public void setAbsorption(") + method(part, "public float getAbsorption(") + "}"]
classes.append("static class DamageDistribution implements IDamageDistributionAlgorithm {"
               + method(damage, "public static float handleDamageTaken(")
               + method(damage, "private static boolean isNoKillRandomDistribution(")
               + method(damage, "static float consumeGlobalAbsorption(")
               + method(damage, "protected float distributeDamageOnParts(")
               + method(damage, "public float distributeDamage(")
               + "float minHealth(Player p,AbstractDamageablePart part){return 0;}"
               + "List<Pair<EquipmentSlot,EnumPlayerPart[]>> getPartList(){return List.of(new Pair<>(EquipmentSlot.HEAD,new EnumPlayerPart[]{EnumPlayerPart.HEAD}));} }")
classes.append("static class RandomDamageDistributionAlgorithm extends DamageDistribution {"
               "static RandomDamageDistributionAlgorithm NEAREST_KILL=new RandomDamageDistributionAlgorithm();"
               "boolean isNoKill(){return false;}"
               "List<Pair<EquipmentSlot,EnumPlayerPart[]>> getPartList(){return List.of(new Pair<>(EquipmentSlot.HEAD,EnumPlayerPart.VALUES));} }")
direct = (base / "common/damagesystem/distribution/DirectDamageDistributionAlgorithm.java").read_text(encoding="utf-8")
classes.append("static class Direct implements IDamageDistributionAlgorithm {EnumPlayerPart part=EnumPlayerPart.HEAD;boolean debuff=true;"
               + method(direct, "public float distributeDamage(") + "}")
equal = (base / "common/damagesystem/distribution/EqualDamageDistributionAlgorithm.java").read_text(encoding="utf-8")
classes.append("static class Equal implements IDamageDistributionAlgorithm {boolean tryNoKill=false;"
               "float reduceDamage(float d,Player p,DamageSource s){return d;}"
               + method(equal, "private float distributeOnParts(")
               + method(equal, "public float distributeDamage(") + "}")
helper = (ROOT / "fabric1.21.1/src/client/java/ichttt/mods/firstaid/client/util/HeartSpriteHelper.java").read_text(encoding="utf-8")
classes.append("static class HeartSpriteHelper {" + method(helper, "public static ResourceLocation container(")
               + method(helper, "public static ResourceLocation heart(") + method(helper, "private static ResourceLocation sprite(") + "}")

tests = [r"""
    for(IDamageDistributionAlgorithm algorithm:List.of(new DamageDistribution(),new Direct(),new Equal())) {
        for(float shield:new float[]{0,0.25F,1,4,12}) for(float hit:new float[]{0,0.25F,1,4,6}) {
            ServerPlayer p=new ServerPlayer();p.absorption=shield;
            int[] callbacks={0};float[] reported={0};
            for(AbstractDamageablePart limb:p.model) limb.debuffs=new IDebuff[]{(d,h,who)->{callbacks[0]++;reported[0]+=d;}};
            float left=DamageDistribution.handleDamageTaken(algorithm,p.model,hit,p,new TestSource(),true,true);
            same(left,0);same(p.absorption,Math.max(0,shield-hit));
            float health=p.model.parts.get(0).currentHealth+p.model.parts.get(1).currentHealth;
            same(20-health,Math.max(0,hit-shield));same(reported[0],20-health);
            if(hit<=shield)check(callbacks[0]==0);
            check(p.shieldWrites<2);
        }
    }
    for(IDamageDistributionAlgorithm algorithm:List.of(new DamageDistribution(),new Direct(),new Equal())) {
        ServerPlayer p=new ServerPlayer();p.absorption=4;
        MinecraftForge.EVENT_BUS.cancel=true;
        DamageDistribution.handleDamageTaken(algorithm,p.model,16,p,new TestSource(),true,true);
        same(p.absorption,4);for(AbstractDamageablePart limb:p.model)same(limb.currentHealth,10);
        MinecraftForge.EVENT_BUS.cancel=false;
    }
    ServerPlayer overflow=new ServerPlayer();overflow.absorption=4;
    same(DamageDistribution.handleDamageTaken(new DamageDistribution(),overflow.model,16,overflow,new TestSource(),true,true),0);
    same(overflow.model.parts.get(0).currentHealth,0);same(overflow.model.parts.get(1).currentHealth,8);
    check(overflow.shieldWrites==1);
    Part legacy=new Part();legacy.setAbsorption(8);same(legacy.getAbsorption(),0);
    legacy.damage(3,new ServerPlayer(),false,0);same(legacy.currentHealth,7);
    System.out.println("PASS shared absorption: standard/direct/equal, fractions, full/partial shield, overflow, redistribution, callbacks and event rollback");
"""]
for index, module in enumerate(MODULES):
    client = ROOT / module / ("src/client/java" if module.startswith("fabric") else "src/main/java") / "ichttt/mods/firstaid/client"
    source = (client / "gui/FirstaidIngameGui.java").read_text(encoding="utf-8")
    source = source[source.index("public final class FirstaidIngameGui"):]
    source = source.replace("public final class FirstaidIngameGui", f"static final class Hearts{index}").replace("private FirstaidIngameGui()", f"private Hearts{index}()")
    classes.append(source)
    modern = "26." in module
    graphic = "GuiGraphicsExtractor" if modern else "GuiGraphics"
    gui = "ForgeGui" if module == "forge1.20.1" else "Gui"
    call = f"Hearts{index}.renderHealth(" + ("" if modern and module.startswith("fabric") else "gui,") + "320,240,g);"
    entry = client.parent / "mixin/client/GuiMixin.java" if module.startswith("fabric") else client / "ClientEventHandler.java"
    hook = method(entry.read_text(encoding="utf-8"), "private void firstaid$" + ("extractHearts(" if modern else "renderHearts(")) if module.startswith("fabric") else method(entry.read_text(encoding="utf-8"), "public static void preRender(")
    assert "vanillaHealthBarMode" not in hook and "VanillaHealthCalculation" not in hook, module
    assert "ci.cancel();" in hook or "event.setCanceled(true);" in hook, module
    tests.append(f"""
        for(float amount:new float[]{{0,0.25F,1,2,3,20,21,23,60}}) for(boolean hardcore:new boolean[]{{false,true}}) {{
            Player p=new Player();p.absorption=amount;p.hardcore=hardcore;p.poison=p.wither=p.frozen=true;
            Minecraft.INSTANCE.player=p;{gui} gui=new {gui}();{graphic} g=new {graphic}();
            {call}
            int units=(int)Math.ceil(amount),hearts=(units+1)/2,rows=(hearts+9)/10,rowHeight=Math.max(10-(rows-2),3);
            check(g.draws.size()==hearts*2);
            for(int i=0;i<hearts;i++) {{
                Draw bg=g.draws.get(i*2),heart=g.draws.get(i*2+1);
                check(bg.sprite.contains("container")&&heart.sprite.contains("absorbing"));
                check(!heart.sprite.contains("poison")&&!heart.sprite.contains("wither")&&!heart.sprite.contains("frozen"));
                check(heart.sprite.contains("hardcore")==hardcore);
                check(heart.sprite.contains("half")== (i==hearts-1&&units%2==1));
                check(heart.x==69+i%10*8&&heart.y==201-i/10*rowHeight);
                check(bg.x==heart.x&&bg.y==heart.y&&heart.w==9&&heart.h==9);
            }}
        }}
        System.out.println("PASS {module}: absorption-only HUD, half hearts, left anchor, rows, hardcore and status-independent gold sprites; all config modes intercepted");
    """)

# The native player save/data is authoritative, never overwritten by legacy limb copies.
wrapper = (base / "common/SynchedEntityDataWrapper.java").read_text(encoding="utf-8")
assert "DATA_PLAYER_ABSORPTION_ID" not in wrapper
assert 'putFloat("absorption"' not in part and 'nbt.getFloat("absorption"' not in part
update = (base / "common/network/MessageUpdatePart.java").read_text(encoding="utf-8")
assert "this.absorption = 0.0F" in update and "setAbsorption(message.absorption)" not in update
network = (base / "FirstAid.java").read_text(encoding="utf-8")
assert 'NETWORKING_MAJOR = "5."' in network and 'NETWORKING_MINOR = "0"' in network

java = "import java.util.*; public class AbsorptionRegression {" + STUBS + "\n".join(classes)
java += "public static void main(String[] args){" + "\n".join(tests) + "}}"
with tempfile.TemporaryDirectory(prefix="firstaid-absorption-") as tmp:
    path = Path(tmp) / "AbsorptionRegression.java"
    path.write_text(java, encoding="utf-8")
    result = subprocess.run(["java", "-Dfile.encoding=UTF-8", str(path)], capture_output=True, text=True)
    print(result.stdout, end="")
    if result.returncode:
        print(result.stderr, end="")
        lines = java.splitlines()
        for number in re.findall(r"AbsorptionRegression.java:(\d+)", result.stderr):
            print(f"Java {number}: {lines[int(number)-1].strip()}")
        raise SystemExit(result.returncode)
print("PASS native absorption delegation, legacy save/wire retirement and Forge protocol 5.0")

"""Exercise maintained damage/healing implementations with small Minecraft API stubs."""
from pathlib import Path
import json
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


def algorithm(source):
    source = source[source.index("public class "):].replace("public class ", "static class ", 1)
    codec_start = source.index("public static final ")
    source = source[:codec_start] + source[source.index(";", codec_start) + 1:]
    codec = method(source, "public " + ("MapCodec" if "public MapCodec" in source else "Codec"))
    return source.replace("@Override\n", "").replace(codec, "")


STUBS = r"""
@interface Nonnull {} @interface Nullable {}
static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
static void same(float a, float b) { check(Math.abs(a-b)<0.0002F, a+" != "+b); }
enum EquipmentSlot { HEAD, CHEST, LEGS, FEET, MAINHAND, OFFHAND }
static class Pair<L,R> {
    L l; R r; Pair(L l,R r){this.l=l;this.r=r;} static <L,R> Pair<L,R> of(L l,R r){return new Pair<>(l,r);}
    L getLeft(){return l;} L getKey(){return l;} R getRight(){return r;}
}
static class Value<T> { T value; Value(T value){this.value=value;} T get(){return value;} }
static class FirstAidConfig {
    static General GENERAL=new General(); static class General { Value<Boolean> debug=new Value<>(false); }
}
static class Log { void info(Object... args){} void warn(Object... args){} void error(Object... args){} }
static class FirstAid { static Log LOGGER=new Log(); static boolean shouldUseFriendlyRandomDistribution(){return false;} }
static class LoggingMarkers { static Object DAMAGE_DISTRIBUTION=new Object(); }
static class Stats { static Object DAMAGE_TAKEN=new Object(); }
static class SourceType {String msgId(){return "test";}}
static class DamageSource {SourceType type(){return new SourceType();}}
static class Level {boolean isClientSide; boolean isClientSide(){return isClientSide;}}
static class Player {
    AbstractPlayerDamageModel model=new AbstractPlayerDamageModel(); Level level(){return new Level();}
    Object getItemBySlot(EquipmentSlot slot){return null;} void awardStat(Object stat,int amount){} int getId(){return 0;}
    boolean hasEffect(Object effect){return false;}
}
static class RegistryObjects {static Value<Object> MORPHINE_EFFECT=new Value<>(new Object());}
static class ServerPlayer extends Player {}
static class MessageUpdatePart {MessageUpdatePart(Object... args){}}
static class FirstAidNetworking {static void sendPartUpdate(Object... args){}}
static class ArmorUtils {
    static float applyArmor(Player p,Object item,DamageSource source,float damage,EquipmentSlot slot){return damage;}
    static float applyEnchantmentModifiers(Player p,EquipmentSlot slot,DamageSource s,float damage){return damage;}
}
static class ForgeHooks {static float onLivingDamage(Player p,DamageSource s,float damage){return damage;}}
static class AbstractDamageablePart {
    EnumPlayerPart part; float currentHealth=100; boolean canCauseDeath; int hits;
    AbstractDamageablePart(EnumPlayerPart part){this.part=part;}
    int getMaxHealth(){return 100;}
    float heal(float amount,Player player,boolean debuff){float applied=Math.min(amount,100-currentHealth);currentHealth+=applied;return amount-applied;}
    float damage(float amount,Player player,boolean debuff,float minimum){float applied=Math.min(amount,Math.max(0,currentHealth-minimum));currentHealth-=applied;if(applied>0)hits++;return amount-applied;}
}
static class AbstractPlayerDamageModel {
    AbstractDamageablePart[] parts=Arrays.stream(EnumPlayerPart.VALUES).map(AbstractDamageablePart::new).toArray(AbstractDamageablePart[]::new);
    AbstractDamageablePart getFromEnum(EnumPlayerPart p){return parts[p.ordinal()];}
}
static class PlayerDamageModel extends AbstractPlayerDamageModel {
    boolean isPainSuppressed(Player player){return false;} float getIncomingPartDamageMultiplier(AbstractDamageablePart p){return 1;}
}
static class CommonUtils {
    static final EquipmentSlot[] ARMOR_SLOTS={EquipmentSlot.FEET,EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD};
    static AbstractPlayerDamageModel getDamageModel(Player p){return p.model;}
    static void syncPart(ServerPlayer player,AbstractDamageablePart part){}
    static void syncDamageModel(ServerPlayer player){}
    static boolean isValidArmorSlot(EquipmentSlot slot){return slot.ordinal()<4;}
    static List<EnumPlayerPart> getPartListForSlot(EquipmentSlot slot){return Arrays.stream(EnumPlayerPart.VALUES).filter(p->p.slot==slot).toList();}
}
static float total(Player p){float total=0;for(var part:p.model.parts)total+=part.currentHealth;return total;}
"""

TESTS = r"""
Player player=new ServerPlayer(); DamageSource source=new DamageSource();
var feet=new StandardDamageDistributionAlgorithm(Map.of(EquipmentSlot.FEET,List.of(EnumPlayerPart.LEFT_FOOT)),false,true);
for(var part:player.model.parts)part.currentHealth=0;
player.model.getFromEnum(EnumPlayerPart.HEAD).currentHealth=100;
same(feet.distributeDamage(2,player,source,false),0);
same(player.model.getFromEnum(EnumPlayerPart.HEAD).currentHealth,99);
for(var part:player.model.parts)part.currentHealth=0;
player.model.getFromEnum(EnumPlayerPart.LEFT_LEG).currentHealth=.25F;
player.model.getFromEnum(EnumPlayerPart.HEAD).currentHealth=100;
feet.distributeDamage(2,player,source,false);
same(player.model.getFromEnum(EnumPlayerPart.HEAD).currentHealth,99.15625F);
for(var part:player.model.parts)part.currentHealth=0;
player.model.getFromEnum(EnumPlayerPart.BODY).currentHealth=100;
feet.distributeDamage(2,player,source,false);
same(player.model.getFromEnum(EnumPlayerPart.BODY).currentHealth,98.96F);
RandomDamageDistributionAlgorithm.RANDOM.setSeed(17);
int[] choices=new int[8]; int splitHits=0;
for(int trial=0;trial<512;trial++) {
    for(var part:player.model.parts){part.currentHealth=100;part.hits=0;}
    same(RandomDamageDistributionAlgorithm.PER_POINT.distributeDamage(2.5F,player,source,false),0);
    same(total(player),797.5F);
    int hits=0;for(var part:player.model.parts){if(part.hits>0){choices[part.part.ordinal()]++;hits++;}}
    if(hits>1)splitHits++;
}
check(splitHits>400,"each point must reroll its target");
for(int choice:choices)check(choice>100,"all eight parts must be reachable");
for(var part:player.model.parts)part.currentHealth=0;
player.model.getFromEnum(EnumPlayerPart.RIGHT_ARM).currentHealth=.3F;
same(RandomDamageDistributionAlgorithm.PER_POINT.distributeDamage(2.5F,player,source,false),2.2F);
same(total(player),0);
for(var part:player.model.parts)part.currentHealth=98.75F;
for(int pulse=0;pulse<4;pulse++)manageHealth(.75F,player.model,player,false,false);
same(total(player),793);
check(Arrays.stream(player.model.parts).mapToDouble(p->Math.ceil(p.currentHealth)).sum()>792,"four regeneration pulses must visibly heal");
for(int trial=0;trial<128;trial++) {
    for(var part:player.model.parts)part.currentHealth=100;
    player.model.getFromEnum(EnumPlayerPart.LEFT_ARM).currentHealth=99.9F;
    player.model.getFromEnum(EnumPlayerPart.RIGHT_ARM).currentHealth=98;
    float before=total(player);
    manageHealth(.75F,player.model,player,false,false);
    same(total(player)-before,.75F);
}
System.out.println("MODULE: overflow, per-point randomness, fractional damage, healing conservation PASS");
"""

for module in MODULES:
    base = ROOT / module / "src/main/java/ichttt/mods/firstaid"
    distribution = base / "common/damagesystem/distribution"
    enum = (base / "api/enums/EnumPlayerPart.java").read_text(encoding="utf-8")
    enum_code = "enum EnumPlayerPart { HEAD(EquipmentSlot.HEAD), LEFT_ARM(EquipmentSlot.CHEST), LEFT_LEG(EquipmentSlot.LEGS), LEFT_FOOT(EquipmentSlot.FEET), BODY(EquipmentSlot.CHEST), RIGHT_ARM(EquipmentSlot.CHEST), RIGHT_LEG(EquipmentSlot.LEGS), RIGHT_FOOT(EquipmentSlot.FEET); static final EnumPlayerPart[] VALUES=values(); final EquipmentSlot slot; EnumPlayerPart(EquipmentSlot slot){this.slot=slot;}"
    enum_code += method(enum, "public List<EnumPlayerPart> getOverflowTargets(")
    enum_code += method(enum, "public float getOverflowTransferFactor(")
    if module == "forge1.20.1":
        enum_code += "public List<EnumPlayerPart> getNeighbours(){List<EnumPlayerPart> result=new ArrayList<>();result.addAll(getNeighboursDown());result.addAll(getNeighboursUp());result.addAll(getNeighboursLeft());result.addAll(getNeighboursRight());return result;}"
        for direction in ("Up", "Down", "Left", "Right"):
            enum_code += method(enum, "private List<EnumPlayerPart> getNeighbours" + direction + "(")
    enum_code += "}"
    damage = (distribution / "DamageDistribution.java").read_text(encoding="utf-8")
    damage_code = "abstract static class DamageDistribution { protected float minHealth(Player p,AbstractDamageablePart part){return 0;} static float consumeGlobalAbsorption(Player p,float damage){return damage;} static float consumeGlobalAbsorption(AbstractPlayerDamageModel model,Player p,float damage){return damage;} protected abstract List<Pair<EquipmentSlot,EnumPlayerPart[]>> getPartList();"
    for signature in ("static float getIncomingPartDamageMultiplier(", "static float restoreOriginalDamageScale(",
                      "protected float distributeDamageOnParts(", "public float distributeDamage("):
        if signature in damage:
            damage_code += method(damage, signature)
    damage_code += "}"
    health = (distribution / "HealthDistribution.java").read_text(encoding="utf-8")
    heal_method = method(health, "public static void manageHealth(")
    # Networking is covered by the real GameTest; this harness exercises allocation only.
    heal_method = heal_method[:heal_method.rindex("if (sendChanges)")] + "}"
    regen = (base / "mixin/RegenerationMobEffectMixin.java").read_text(encoding="utf-8")
    mixins = json.loads((ROOT / module / "src/main/resources/firstaid.mixins.json").read_text(encoding="utf-8"))["mixins"]
    assert "RegenerationMobEffectMixin" in mixins and "HealthDistribution.addRandomHealth(" in regen
    assert "player.getHealth()" not in regen and "allowOtherHealingItems.get()" in regen
    java = "import java.util.*; import java.util.Map.Entry; import static java.util.Collections.*; public class DamageHealingRegression {" + STUBS + enum_code + damage_code
    for name in ("StandardDamageDistributionAlgorithm", "RandomDamageDistributionAlgorithm"):
        java += algorithm((distribution / (name + ".java")).read_text(encoding="utf-8"))
    java += "static final List<EnumPlayerPart> parts=new ArrayList<>(Arrays.asList(EnumPlayerPart.VALUES));" + heal_method
    tests = TESTS.replace("MODULE", module)
    if module == "forge1.20.1":
        # Forge already transfers without attenuation, which also satisfies the 50% loss ceiling.
        tests = tests.replace("currentHealth,99)", "currentHealth,98)").replace("currentHealth,99.15625F)", "currentHealth,98.25F)").replace("currentHealth,98.96F)", "currentHealth,98)")
    java += "public static void main(String[] args){" + tests + "}}"
    with tempfile.TemporaryDirectory() as tmp:
        source = Path(tmp) / "DamageHealingRegression.java"
        source.write_text(java, encoding="utf-8")
        subprocess.run(["java", "-Dfile.encoding=UTF-8", str(source)], check=True)

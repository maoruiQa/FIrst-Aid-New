"""Run production feedback code against minimal game API stubs: python tools/check_tinnitus.py."""
from pathlib import Path
import re
import json
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MODULES = ("forge1.20.1", "fabric1.21.1", "neoforge1.21.1", "fabric26.2",
           "neoforge26.2", "fabric26.3", "neoforge26.3")

# The join prompt is the user-facing entry point for the personal command.
for module in MODULES:
    event = (ROOT / module / "src/main/java/ichttt/mods/firstaid/common/EventHandler.java").read_text(encoding="utf-8")
    assert 'firstaid.tip.commands.tinnitus.label' in event
    assert '"/firstaid tinnitus"' in event
    client_handler = (ROOT / module / ("src/client/java" if module.startswith("fabric") else "src/main/java")
                      / "ichttt/mods/firstaid/client/ClientEventHandler.java").read_text(encoding="utf-8")
    assert 'firstaid.login.tinnitus' in client_handler
    commands = (ROOT / module / ("src/client/java" if module.startswith("fabric") else "src/main/java")
                / "ichttt/mods/firstaid/client/TinnitusCommands.java").read_text(encoding="utf-8")
    assert 'literal("tinnitus")' in commands and 'IntegerArgumentType.integer(1, 400)' in commands
    if module.startswith("fabric"):
        assert 'class TinnitusRoot' in commands and 'dispatcherUnknownCommand' in commands
    hooks = (ROOT / module / ("src/client/java" if module.startswith("fabric") else "src/main/java")
             / "ichttt/mods/firstaid/client/ClientHooks.java").read_text(encoding="utf-8")
    assert 'TinnitusCommands.register' in hooks or 'TinnitusCommands.register' in client_handler
    for locale in ("en_us.json", "zh_cn.json"):
        lang = ROOT / module / "src/main/resources/assets/firstaid/lang" / locale
        values = json.loads(lang.read_text(encoding="utf-8"))
        assert values["firstaid.tip.commands.tinnitus.label"]
        assert values["firstaid.tip.commands.tinnitus.detail"]


def method(source, signature):
    start = source.index(signature)
    end = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


STUBS = r"""
    static class Entity { int tickCount; }
    static class Player extends Entity {
        boolean alive = true; Level world = new Level(); Map<Object,MobEffectInstance> effects = new HashMap<>();
        boolean isAlive() { return alive; } Level level() { return world; }
        MobEffectInstance getEffect(Object effect) { return effects.get(effect); }
        boolean hasEffect(Object effect) { return effects.containsKey(effect); }
        void addEffect(MobEffectInstance effect) { effects.put(effect.effect,effect); }
    }
    static class ServerPlayer extends Player {}
    interface IDebuff { void handleDamageTaken(float damage, float health, ServerPlayer player); }
    static class LocalPlayer extends Player {}
    static class Level { boolean isClientSide; boolean isClientSide() { return isClientSide; } }
    static class DamageSource { boolean explosion; boolean is(Object tag) { return explosion; } }
    static class MobEffects {
        static final Object ABSORPTION="absorption", DIG_SPEED="haste", HASTE=DIG_SPEED,
            DAMAGE_BOOST="strength", STRENGTH=DAMAGE_BOOST, MOVEMENT_SPEED="speed", SPEED=MOVEMENT_SPEED,
            REGENERATION="regen", POISON="poison", WITHER="wither";
    }
    static class MobEffectInstance {
        Object effect; int duration, amplifier;
        MobEffectInstance(Object e, int d, int a, boolean ambient, boolean visible) { effect=e; duration=d; amplifier=a; }
        int getDuration() { return duration; } int getAmplifier() { return amplifier; }
    }
    static class FirstAid {
        enum InjuryDebuffMode { NORMAL, LOW, OFF }
        static InjuryDebuffMode injuryDebuffMode=InjuryDebuffMode.NORMAL;
        static float lowInjuryDebuffDurationScale=0.5F;
        static float lowInjuryDebuffDamageScale=0.4F;
        static class Network { void send(Object target, Object packet) {} }
        static Network NETWORKING = new Network();
        static boolean isSynced = true, lowSuppressionEnabled, enablePainFovCompression = true, enablePainAudioEffects = true;
        static float lowSuppressionMultiplier = 0.4F;
        static float adrenalineReliefMultiplier = 1.7F;
        static float suppressionDisplayCurve(float v) { return Math.max(0,Math.min(1,v)); }
        static float getSuppressionDisplayMultiplier() { return lowSuppressionEnabled ? Math.max(0,Math.min(1,lowSuppressionMultiplier*1.25F)) : 0.75F; }
    }
    static class Mth {
        static float clamp(float v, float lo, float hi) { return Math.max(lo,Math.min(hi,v)); }
        static int clamp(int v, int lo, int hi) { return Math.max(lo,Math.min(hi,v)); }
    }
    static class Value<T> { T value; Value(T v) { value=v; } T get() { return value; } void set(T v) { value=v; } }
    static class FirstAidConfig {
        static Client CLIENT = new Client(); static Server SERVER = new Server();
        static class Server { Value<Boolean> scaleMaxHealth = new Value<>(true); }
        static class Client {
            Value<Boolean> tinnitusEnabled = new Value<>(true), enableSounds = new Value<>(true);
            Value<Integer> tinnitusDurationPercent = new Value<>(75);
        }
    }
    static class AbstractDamageablePart {
        int part; float currentHealth=10; AbstractDamageablePart(int p) { part=p; }
        int getMaxHealth() { return 10; }
    }
    enum EnumPlayerPart { HEAD, BODY }
    static class AbstractPlayerDamageModel implements Iterable<AbstractDamageablePart> {
        List<AbstractDamageablePart> parts = new ArrayList<>(); AbstractDamageablePart HEAD;
        AbstractPlayerDamageModel() { for (int i=0;i<8;i++) parts.add(new AbstractDamageablePart(i)); HEAD=parts.get(0); }
        AbstractDamageablePart getFromEnum(int p) { return parts.get(p); }
        AbstractDamageablePart getFromEnum(EnumPlayerPart p) { return parts.get(p==EnumPlayerPart.HEAD ? 0 : 4); }
        public Iterator<AbstractDamageablePart> iterator() { return parts.iterator(); }
    }
    static class PlayerDamageModel extends AbstractPlayerDamageModel {
        static final float PAIN_HARD_CAP = 1.85F, CHRONIC_SOFT_CAP = 1, ACUTE_BREAKTHROUGH = 0.25F;
        float chronicPainIntensity, acutePainIntensity;
        boolean muted; float suppression; int holdTicks, heartbeatId;
        boolean isAudioMuted() { return muted; }
        int getPainLevel() { return 0; } float getAcutePainIntensity() { return 0; }
        float getSuppressionIntensity() { return suppression; } int getSuppressionHoldTicks() { return holdTicks; }
        boolean isWithdrawalEpisodeActive() { return false; } float getAddictionNormalized() { return 0; }
        boolean isPainSuppressed(Player p) { return false; }
        PAIN_VISUAL_METHODS
        int cueId, cueDuration; float cueSeverity;
        int getTinnitusCueId() { return cueId; } float getTinnitusCueSeverity() { return cueSeverity; }
        int getTinnitusCueDurationTicks() { return cueDuration; } int getAdrenalineHeartbeatTriggerId() { return heartbeatId; }
        int removedWeakness; void replaceTrackedInjuryEffect(Player p, String id, MobEffectInstance replacement) { removedWeakness++; }
    }
    static class CommonUtils {
        static PlayerDamageModel model = new PlayerDamageModel();
        static AbstractPlayerDamageModel getDamageModel(Player player) { return model; }
        static float getVisibleHealthRatio(AbstractDamageablePart p) { return p.currentHealth/p.getMaxHealth(); }
        static int syncs; static void syncDamageModel(ServerPlayer player) { syncs++; }
    }
    static class FirstAidNetworking {
        static void sendDamageModelSync(ServerPlayer p, AbstractPlayerDamageModel m, boolean scale) { CommonUtils.syncs++; }
    }
    static class PacketDistributor { static PacketDistributor PLAYER=new PacketDistributor(); Object with(java.util.function.Supplier<ServerPlayer> p) { return p; } }
    static class MessagePlayHurtSound { MessagePlayHurtSound(SoundEvent sound, int duration) {} }
    record OnHitDebuffEntry(float damageTakenThreshold, int effectDuration, float healthFractionThreshold) {}
    record ConstantDebuffEntry(float healthFractionThreshold, int effectAmplifier) {}
    static class RandomSource { static RandomSource create() { return new RandomSource(); } }
    static class SoundEvent {}
    enum SoundSource { MASTER, UI, PLAYERS }
    static class SoundInstance {
        enum Attenuation { NONE, LINEAR }
        float volume=0.3F, pitch=1.0F; double x=2, y=3, z=4; boolean relative, looping; Object attenuation;
        Object getSource() { return this; } float getVolume() { return volume; } float getPitch() { return pitch; }
        double getX() { return x; } double getY() { return y; } double getZ() { return z; }
        boolean isRelative() { return relative; } Object getAttenuation() { return attenuation; }
        boolean isStopped() { return false; }
        boolean canPlaySound() { return true; }
    }
    static class AbstractSoundInstance extends SoundInstance {
        boolean resolved;
        AbstractSoundInstance(SoundEvent event, Object source, RandomSource random) {}
        @Override float getVolume() { if (!resolved) throw new IllegalStateException("Sound is not resolved"); return volume; }
        @Override float getPitch() { if (!resolved) throw new IllegalStateException("Sound is not resolved"); return pitch; }
    }
    interface TickableSoundInstance { void tick(); boolean isStopped(); }
    static class SoundManager {
        List<SoundInstance> playing = new ArrayList<>();
        void play(SoundInstance s) { if (s instanceof AbstractSoundInstance a) a.resolved=true; playing.add(s); } void stop(SoundInstance s) { playing.remove(s); }
        void tick() {
            for (SoundInstance s : List.copyOf(playing)) if (s instanceof TickableSoundInstance t) t.tick();
            playing.removeIf(SoundInstance::isStopped);
        }
    }
    static class Minecraft {
        static Minecraft INSTANCE = new Minecraft(); LocalPlayer player = new LocalPlayer(); Level level = new Level();
        SoundManager manager = new SoundManager(); static Minecraft getInstance() { return INSTANCE; }
        SoundManager getSoundManager() { return manager; }
        boolean paused; boolean isPaused() { return paused; }
    }
    static void stepSounds(Runnable cleanup) { Minecraft.INSTANCE.manager.tick(); cleanup.run(); }
    static class ViewportEvent {
        static class ComputeFov {
            double fov; ComputeFov(float v) { fov=v; }
            double getFOV() { return fov; } void setFOV(double v) { fov=v; }
        }
        static class ComputeCameraAngles {
            float yaw=12, pitch=-8, roll, partialTick;
            double getPartialTick() { return partialTick; }
            float getYaw() { return yaw; } float getPitch() { return pitch; } float getRoll() { return roll; }
            void setYaw(float v) { yaw=v; } void setPitch(float v) { pitch=v; } void setRoll(float v) { roll=v; }
        }
    }
    record CameraAngles(float yaw, float pitch, float roll) {}
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    static void same(float a, float b) { check(Math.abs(a-b) < 0.000001F); }
"""

pain_model = (ROOT / "forge1.20.1/src/main/java/ichttt/mods/firstaid/common/damagesystem/PlayerDamageModel.java").read_text(encoding="utf-8")
pain_methods = "\n".join(method(pain_model, signature) for signature in (
    "public float getAdrenalinePainRelief()", "public float getPainVisualStrength(boolean painSuppressed)",
    "public float getPainFovStrength(boolean painSuppressed)"))
STUBS = STUBS.replace("PAIN_VISUAL_METHODS", pain_methods.replace("this.suppressionIntensity", "this.suppression"))

classes, tests = [], []
part = (ROOT / "forge1.20.1/src/main/java/ichttt/mods/firstaid/common/damagesystem/DamageablePart.java").read_text(encoding="utf-8")
classes.append("static class AbsorptionPart { int maxHealth=10; float currentHealth=10; boolean canCauseDeath; float knockdownOverflow; IDebuff[] debuffs; "
               + method(part, "public float damage(float amount, @Nullable Player player, boolean applyDebuff, float minHealth)").replace("@Nullable ", "")
               + method(part, "private static float softenDebuffHealthFraction(") + "}")
tests.append("""
    for (float damage : new float[]{0.25F,2,4,6,12}) {
        AbsorptionPart part=new AbsorptionPart();
        float[] callback={-1,-1}; part.debuffs=new IDebuff[]{(amount,health,player)->{callback[0]=amount; callback[1]=health;}};
        float actual=Math.min(10,damage);
        part.damage(damage,new ServerPlayer(),true,0);
        same(callback[0],actual); same(callback[1],(10-actual)/10); same(part.currentHealth,10-actual);
    }
    System.out.println("PASS Forge limb injury callbacks use actual health lost after overflow; shared shield checked by check_absorption.py");
""")
for index, module in enumerate(MODULES):
    client = ROOT / module / ("src/client/java" if module.startswith("fabric") else "src/main/java") / "ichttt/mods/firstaid/client"
    sound = (client / "TinnitusSoundController.java").read_text(encoding="utf-8")
    sound = sound[sound.index("public final class TinnitusSoundController"):]
    sound = sound.replace("public final class", "static final class", 1).replace("TinnitusSoundController", f"Sound{index}")
    classes.append(sound)
    feedback = (client / "SuppressionFeedbackController.java").read_text(encoding="utf-8")
    signature = re.search(r"public [\w.]+ applyCameraAngles", feedback)[0]
    camera = method(feedback, signature).replace("SuppressionFeedbackController.CameraAngles", "CameraAngles").replace("@Nullable ", "")
    tick = method(feedback, "public void tick(Minecraft client)")
    clear = method(feedback, re.search(r"private void clear\(", feedback)[0]).replace("@Nullable ", "")
    near_miss = method(feedback, "public void onNearMiss")
    approach = method(feedback, "private static float approach")
    fov = method(feedback, "public " + ("float applyFov" if module.startswith("fabric") else "void applyFov"))
    constants = "\n".join(re.findall(r"private static final float \w+ = [^;]+;", feedback))
    fields = """
        float suppressionIntensity, audioMuffleStrength, musicDetuneStrength, tinnitusStrength, shakeStrength,
              painFovCompression, yawImpulse, pitchImpulse;
        int holdTicks, lastTinnitusCueId, hallucinationCooldownTicks, localMuteTicks;
        Level trackedLevel; SoundInstance activeTinnitusSound;
        int tinnitusPlays; void stopActiveTinnitusSound() {} void playTinnitusSound(float severity, int duration) { tinnitusPlays++; }
        void tickWithdrawalHallucinations(Player p, Level l, boolean active, float addiction) {}
    """
    sample = ("return applyCameraAngles(Minecraft.INSTANCE.player, partial, 12, -8);"
              if module.startswith("fabric") else
              "ViewportEvent.ComputeCameraAngles e = new ViewportEvent.ComputeCameraAngles(); e.partialTick=partial; "
              "applyCameraAngles(e); return new CameraAngles(e.yaw,e.pitch,e.roll);")
    sample_fov = ("return applyFov(base);" if module.startswith("fabric") else
                  "ViewportEvent.ComputeFov e=new ViewportEvent.ComputeFov(base); applyFov(e); return (float)e.fov;")
    classes.append(f"static class Camera{index} {{ {fields} {constants} {camera} {tick} {clear} {near_miss} {approach} {fov} "
                   f"CameraAngles sample(float partial) {{ {sample} }} float fov(float base) {{ {sample_fov} }} }}"
                   .replace("TinnitusSoundController", f"Sound{index}"))
    model_dir = ROOT / module / "src/main/java/ichttt/mods/firstaid"
    model = (model_dir / "common/damagesystem/PlayerDamageModel.java").read_text(encoding="utf-8")
    for signature in ("public float getAdrenalinePainRelief()", "public float getPainVisualStrength(boolean painSuppressed)",
                      "public float getPainFovStrength(boolean painSuppressed)"):
        assert re.sub(r"\s+", "", method(model, signature)) == re.sub(r"\s+", "", method(pain_model, signature)), module
    model_methods = "\n".join(method(model, signature) for signature in (
        "public void registerDamageFeedback(", "public void registerTinnitusCue(", "private void tickDotTinnitusState(",
        "public void applyAdrenalineInjection(", "private void forceMaxSuppression(Player player)",
        "private void forceMaxSuppression(Player player, int holdTicks)", "private void refreshSuppressionSnapshot()",
        "private int calculateAdrenalineLevel(", "public static int getAdrenalineDuration()"))
    model_methods = model_methods.replace("net.minecraft.tags.DamageTypeTags.IS_EXPLOSION", '"explosion"')
    model_constants = "\n".join(re.findall(
        r"private static final (?:float|int) (?:MAX_SUPPRESSION_INTENSITY|FORCE_MAX_SUPPRESSION_HOLD_TICKS|MAX_ADRENALINE_LEVEL) = [^;]+;", model))
    model_fields = """
        int audioMuteTicks, tinnitusCueId, tinnitusCueDurationTicks, lastHeadTinnitusTick=-1, resyncs, suppressionHoldTicks, suppressionDecayTicker,
            adrenalineTicks, adrenalineLevel, adrenalineInjectionTicks, adrenalineHeartbeatTriggerId;
        float tinnitusCueSeverity, suppressionIntensity; boolean poisonEffectActive, witherEffectActive;
        void scheduleResync() { resyncs++; } void refreshAdrenalineCombatAttributes(Player p) {}
    """
    classes.append(f"static class Feedback{index} extends AbstractPlayerDamageModel {{ {model_constants} {model_fields} {model_methods} }}"
                   .replace("PlayerDamageModel.getAdrenalineDuration()", f"Feedback{index}.getAdrenalineDuration()"))
    item = (model_dir / "common/items/ItemAdrenalineInjector.java").read_text(encoding="utf-8")
    context = (model_dir / "api/medicine/MedicineUseContext.java").read_text(encoding="utf-8")
    assert "context.applyAdrenalineInjection();" in method(item, "public void applyMedicine(")
    assert "playerDamageModel.applyAdrenalineInjection(this.player);" in method(context, "public void applyAdrenalineInjection()")
    fallback = method(context, "public void applyAdrenalineInjection()")
    for extra_effect in ("RegistryObjects.PAINKILLER_EFFECT", "MobEffects.SPEED", "MobEffects.MOVEMENT_SPEED", "MobEffects.STRENGTH", "MobEffects.DAMAGE_BOOST", "MobEffects.HASTE", "MobEffects.DIG_SPEED"):
        assert extra_effect not in fallback, (module, "injector fallback still grants", extra_effect)
    heartbeat = (client / "HeartbeatSoundController.java").read_text(encoding="utf-8")
    heartbeat_constants = "\n".join(re.findall(r"private static final (?:float|int) \w+ = [^;]+;", heartbeat))
    heartbeat_methods = "\n".join(method(heartbeat, signature) for signature in (
        "public void tick(Minecraft minecraft)", "public void clear()", "public void beginMute(",
        "private boolean isSuppressed(", "private boolean isCriticalPartRed("))
    if "private static float getVisibleHealthRatio(" in heartbeat:
        heartbeat_methods += method(heartbeat, "private static float getVisibleHealthRatio(")
    loop_methods = "\n".join(method(heartbeat, signature) for signature in (
        "private void keepAlive(", "private void triggerPulse()", "private void beginFadeOut()",
        "private void stopImmediately()", "public boolean isStopped()", "public void tick()",
        "public float getVolume()", "public float getPitch()"))
    classes.append(f"""static class Heartbeat{index} {{ {heartbeat_constants} {heartbeat_methods}
        HeartbeatLoopSound activeHeartbeat; int lastAdrenalineHeartbeatTriggerId=-1, localMuteTicks;
        HeartbeatLoopSound ensureHeartbeat(Minecraft mc, LocalPlayer p) {{
            if (activeHeartbeat==null) {{ activeHeartbeat=new HeartbeatLoopSound(); activeHeartbeat.player=p; mc.manager.play(activeHeartbeat); }}
            return activeHeartbeat;
        }}
        static class HeartbeatLoopSound extends SoundInstance {{ {loop_methods}
            LocalPlayer player; int activeHoldTicks, pulseHoldTicks, fadeTicksRemaining=-1; boolean stopped; float strength;
        }}
    }}""")
    on_hit = (model_dir / "common/damagesystem/debuff/OnHitDebuff.java").read_text(encoding="utf-8")
    classes.append(f"""static class Hit{index} {{ {method(on_hit, 'public void handleDamageTaken(')}
        List<OnHitDebuffEntry> timeBoundaries; int cooldownTicks=80, lastTriggerTick=-1, applications, duration;
        Object effect="injury"; SoundEvent sound;
        void applyInjuryEffect(Player p, MobEffectInstance effect) {{ applications++; duration=effect.duration; }}
        FirstAid.InjuryDebuffMode getDebuffMode() {{ return FirstAid.injuryDebuffMode; }}
        int scaleDurationForMode(int d) {{ return Math.max(1,Math.round(d*FirstAid.lowInjuryDebuffDurationScale)); }}
    }}""")
    constant = (model_dir / "common/damagesystem/debuff/ConstantDebuff.java").read_text(encoding="utf-8")
    classes.append(f"""static class Constant{index} {{ {method(constant, 'private void syncMultiplier(')}
        {method(constant, 'public void handleHealing(')}
        List<ConstantDebuffEntry> amplifierBoundaries=List.of(new ConstantDebuffEntry(0.3F,1));
        int ticks, activeMultiplier; String injuryEffectId="minecraft:weakness";
    }}""")
    data = ROOT / module / "src/main/resources/data/firstaid/firstaid/debuffs"
    assert not (data / "nausea_head.json").exists() and not (data / "nausea_body.json").exists()
    hit_cases = {
        "blindness_head.json": [(0.5,1,0),(2,1,0),(4,1,20),(6,1,30),(0.25,0.2,20),(0.01,0.1,30)],
        "darkness_head.json": [(0.25,1,0),(2,1,40),(4,1,50),(0.25,0.4,40),(0.01,0.2,50),(0.01,0.401,0)],
        "darkness_body.json": [(0.25,1,0),(2,1,0),(4,1,40),(0.01,0.2,40),(0.01,0.201,0)],
        "mining_fatigue_head.json": [(0.25,1,0),(2,1,40),(4,1,60),(0.25,0.4,40),(0.01,0.2,60)],
        "slowness_body.json": [(0.01,0.5,0),(2,1,20),(4,1,30),(0.01,0.35,20),(0.01,0.15,30)]}
    for name, cases in hit_cases.items():
        definition = json.loads((data / name).read_text(encoding="utf-8"))
        if name.startswith("darkness_"):
            assert definition["type"] == "firstaid:on_hit" and definition["potionName"] == "darkness"
            assert definition["debuffSlot"] == name.removeprefix("darkness_").removesuffix(".json")
            assert all(0 < b["effectDuration"] <= 50 for b in definition["timeBoundaries"])
        entries = ",".join(f"new OnHitDebuffEntry({b['damageTakenThreshold']}F,{b['effectDuration']},{b['healthFractionThreshold']}F)"
                           for b in definition["timeBoundaries"])
        for damage, ratio, duration in cases + [(0,0.1,0)]:
            tests.append(f"""{{ Hit{index} hit=new Hit{index}(); hit.timeBoundaries=List.of({entries});
                hit.handleDamageTaken({damage}F,{ratio}F,new ServerPlayer());
                check(hit.applications=={int(duration > 0)} && hit.duration=={duration}); }}""")
        low_mode = f"""
            FirstAid.injuryDebuffMode=FirstAid.InjuryDebuffMode.LOW; Hit{index} mild=new Hit{index}(); mild.timeBoundaries=hit.timeBoundaries;
            mild.handleDamageTaken(6,1,p); check(mild.duration=={definition['timeBoundaries'][0]['effectDuration']//2});
            FirstAid.injuryDebuffMode=FirstAid.InjuryDebuffMode.OFF; Hit{index} off=new Hit{index}(); off.timeBoundaries=hit.timeBoundaries;
            off.handleDamageTaken(6,0.1F,p); check(off.applications==0); FirstAid.injuryDebuffMode=FirstAid.InjuryDebuffMode.NORMAL;
        """
        tests.append(f"""{{ Hit{index} hit=new Hit{index}(); hit.timeBoundaries=List.of({entries}); ServerPlayer p=new ServerPlayer();
            hit.handleDamageTaken(6,1,p); p.tickCount=79; hit.handleDamageTaken(6,1,p); check(hit.applications==1);
            p.tickCount=80; hit.handleDamageTaken(6,1,p); check(hit.applications==2);
            p.tickCount=0; hit.handleDamageTaken(6,1,p); check(hit.applications==3);
            Hit{index} second=new Hit{index}(); second.timeBoundaries=hit.timeBoundaries;
            second.handleDamageTaken(6,1,new ServerPlayer()); check(second.applications==1);
            {low_mode}
        }}""")
    # Native rendering and Fabric mixins use different interfaces for the same calculation.
    apply = (f"CameraAngles angles = cam.applyCameraAngles(mc.player, partial, 12, -8);"
             if module.startswith("fabric") else
             "ViewportEvent.ComputeCameraAngles event = new ViewportEvent.ComputeCameraAngles(); event.partialTick=partial; "
             "cam.applyCameraAngles(event); CameraAngles angles = new CameraAngles(event.yaw,event.pitch,event.roll);")
    tests.append(f"""
        {{
            ServerPlayer serverPlayer = new ServerPlayer(); DamageSource hit = new DamageSource();
            for (int part=0;part<8;part++) for (float loss : new float[]{{0,0.5F,1,3.999F,4,Math.nextUp(4.0F),4.01F,6,10}}) {{
                Feedback{index} after=new Feedback{index}(); AbstractPlayerDamageModel before=new AbstractPlayerDamageModel();
                after.getFromEnum(part).currentHealth-=loss;
                after.registerDamageFeedback(serverPlayer,hit,before);
                boolean expected=part==0 && loss>4;
                check(after.tinnitusCueId==(expected ? 1 : 0));
                check(after.resyncs==(expected ? 1 : 0));
            }}
            Feedback{index} allParts=new Feedback{index}();
            for (int part=1;part<8;part++) allParts.getFromEnum(part).currentHealth=0;
            allParts.HEAD.currentHealth=6; // Exactly 4 head damage plus 70 damage elsewhere cannot trigger.
            allParts.registerDamageFeedback(serverPlayer,hit,new AbstractPlayerDamageModel()); check(allParts.tinnitusCueId==0);
            Feedback{index} worn=new Feedback{index}();
            for (int step=1;step<=32;step++) {{
                AbstractPlayerDamageModel before=new AbstractPlayerDamageModel(); before.HEAD.currentHealth=worn.HEAD.currentHealth;
                worn.HEAD.currentHealth-=0.25F; serverPlayer.tickCount=step*20;
                worn.registerDamageFeedback(serverPlayer,hit,before);
                check(worn.tinnitusCueId==(step>=24 ? 1 : 0));
            }}
            check(worn.tinnitusCueDurationTicks==60); // Slow attrition can trigger short ringing without a >4-point hit.
            int oldCue=worn.tinnitusCueId; AbstractPlayerDamageModel sameHead=new AbstractPlayerDamageModel();
            sameHead.HEAD.currentHealth=worn.HEAD.currentHealth; worn.getFromEnum(1).currentHealth-=2;
            worn.registerDamageFeedback(serverPlayer,hit,sameHead); check(worn.tinnitusCueId==oldCue);
            Feedback{index} concussion=new Feedback{index}(); concussion.HEAD.currentHealth=2;
            for (int time : new int[]{{100,101,299,300}}) {{
                serverPlayer.tickCount=time; concussion.registerDamageFeedback(serverPlayer,hit,new AbstractPlayerDamageModel());
                check(concussion.tinnitusCueId==(time==300 ? 2 : 1));
            }}
            check(concussion.tinnitusCueDurationTicks==100); serverPlayer.tickCount=0;
            Constant{index} weakness=new Constant{index}(); weakness.syncMultiplier(0.29F); check(weakness.activeMultiplier==1);
            CommonUtils.model.removedWeakness=0; weakness.handleHealing(1,0.3F,serverPlayer);
            check(weakness.activeMultiplier==0 && CommonUtils.model.removedWeakness==1);
            for (int guard=0;guard<3;guard++) {{
                Feedback{index} after=new Feedback{index}(); after.HEAD.currentHealth=4;
                after.audioMuteTicks=guard==0 ? 60 : 0; serverPlayer.world.isClientSide=guard==1;
                after.registerDamageFeedback(serverPlayer,hit,guard==2 ? null : new AbstractPlayerDamageModel());
                check(after.tinnitusCueId==0); serverPlayer.world.isClientSide=false;
            }}
            hit.explosion=true;
            for (float loss : new float[]{{0,0.001F,0.1F,1,4}}) {{
                Feedback{index} after=new Feedback{index}(); after.getFromEnum(1).currentHealth-=loss;
                after.registerDamageFeedback(serverPlayer,hit,new AbstractPlayerDamageModel());
                check(after.tinnitusCueId==(loss>0.05F ? 1 : 0));
            }}
            Feedback{index} dot=new Feedback{index}();
            for (Object effect : new Object[]{{MobEffects.POISON,MobEffects.WITHER}}) {{
                serverPlayer.addEffect(new MobEffectInstance(effect,100,0,false,false));
                int previous=dot.tinnitusCueId; dot.tickDotTinnitusState(serverPlayer); check(dot.tinnitusCueId==previous+1);
                dot.tickDotTinnitusState(serverPlayer); check(dot.tinnitusCueId==previous+1);
                serverPlayer.effects.clear(); dot.tickDotTinnitusState(serverPlayer);
            }}
            for (float pressure : new float[]{{0,0.2F,0.9F,1}}) for (int hold : new int[]{{0,40,400}}) {{
                Feedback{index} injected=new Feedback{index}(); injected.suppressionIntensity=pressure;
                injected.suppressionHoldTicks=hold; injected.suppressionDecayTicker=9; CommonUtils.syncs=0;
                injected.applyAdrenalineInjection(serverPlayer);
                same(injected.suppressionIntensity,1); check(injected.suppressionHoldTicks==300 && injected.suppressionDecayTicker==0);
                check(injected.adrenalineTicks==200 && injected.adrenalineLevel==5 && injected.adrenalineInjectionTicks==700);
                check(injected.adrenalineHeartbeatTriggerId==1 && injected.resyncs>0 && CommonUtils.syncs==1);
                check(injected.tinnitusCueId==0); // Max suppression itself must not ring ears.
                check(!serverPlayer.hasEffect(MobEffects.HASTE) && !serverPlayer.hasEffect(MobEffects.STRENGTH) && !serverPlayer.hasEffect(MobEffects.SPEED));
            }}
            stepSounds(() -> Sound{index}.tick(mc));
            for (int percent : new int[]{{1,75,100,400}}) {{
                FirstAidConfig.CLIENT.tinnitusDurationPercent.set(percent);
                SoundInstance shortRing=Sound{index}.play(new SoundEvent(),0.3F,0.36F,60);
                same(shortRing.pitch,0.36F); same(shortRing.volume,0.3F);
                int ticks=(int)Math.ceil(60*percent/100.0D);
                float previous=shortRing.getVolume();
                for (int t=0;t<ticks-1;t++) {{
                    stepSounds(() -> Sound{index}.tick(mc));
                    float volume=shortRing.getVolume();
                    check(volume>0 && volume<=previous);
                    if (ticks-t-1<Math.min(40,ticks)) check(volume<previous);
                    previous=volume;
                }}
                check(mc.manager.playing.size()==1); stepSounds(() -> Sound{index}.tick(mc)); check(mc.manager.playing.isEmpty());
                same(shortRing.getVolume(),0);
            }}
            FirstAidConfig.CLIENT.tinnitusDurationPercent.set(75);
            check(Sound{index}.durationTicks(1,75)==159);
            check(Sound{index}.durationTicks(1,100)==212);
            check(Sound{index}.durationTicks(1,400)==848);
            check(Sound{index}.durationTicks(0.36F,75)==318);
            check(Sound{index}.durationTicks(4,100)==106);
            check(Sound{index}.durationTicks(1,1)==3);
            for (int invalid : new int[]{{0,401}}) {{
                try {{ Sound{index}.durationTicks(1,invalid); throw new AssertionError(); }}
                catch (IllegalArgumentException expected) {{}}
            }}
            FirstAidConfig.CLIENT.tinnitusDurationPercent.set(75);
            SoundInstance original = new SoundInstance();
            SoundInstance injury = Sound{index}.playAt(new SoundEvent(), original.volume, original.pitch, 2, 3, 4);
            SoundInstance morphine = Sound{index}.playAt(new SoundEvent(), original.volume, original.pitch, 2, 3, 4);
            same(injury.pitch,original.pitch); same(injury.volume,original.volume);
            check(injury.x==2 && injury.y==3 && injury.z==4 && !injury.looping);
            check(mc.manager.playing.size()==2);
            for (int t=0;t<158;t++) stepSounds(() -> Sound{index}.tick(mc));
            check(mc.manager.playing.size()==2);
            stepSounds(() -> Sound{index}.tick(mc)); check(mc.manager.playing.isEmpty());
            // Sound-engine ticks alone must expire every source, even if feedback ticks are skipped.
            for (int percent : new int[]{{1,75,100,400}}) for (float pitch : new float[]{{0.162F,0.36F,0.7F,1,1.35F}}) {{
                FirstAidConfig.CLIENT.tinnitusDurationPercent.set(percent);
                SoundInstance ring=Sound{index}.play(new SoundEvent(),0.3F,pitch);
                int duration=Sound{index}.durationTicks(pitch,percent);
                float previous=ring.getVolume();
                for (int t=0;t<duration-1;t++) {{
                    mc.manager.tick();
                    float volume=ring.getVolume();
                    check(volume>0 && volume<=previous);
                    if (duration-t-1<Math.min(40,duration)) check(volume<previous);
                    else same(volume,0.3F);
                    previous=volume;
                }}
                check(!ring.isStopped() && mc.manager.playing.contains(ring));
                mc.manager.tick();
                check(ring.isStopped() && !ring.canPlaySound() && !ring.looping && mc.manager.playing.isEmpty());
                same(ring.getVolume(),0);
                for (int t=0;t<100;t++) ((TickableSoundInstance)ring).tick();
                check(ring.isStopped() && !ring.canPlaySound() && !ring.looping);
                Sound{index}.tick(mc);
            }}
            FirstAidConfig.CLIENT.tinnitusDurationPercent.set(400);
            SoundInstance canceled=Sound{index}.play(new SoundEvent(),0.3F,1);
            Sound{index}.stop(canceled);
            check(canceled.isStopped() && !canceled.canPlaySound() && !canceled.looping); same(canceled.getVolume(),0);
            SoundInstance paused=Sound{index}.play(new SoundEvent(),0.3F,1);
            mc.paused=true; Sound{index}.tick(mc); mc.paused=false;
            check(paused.isStopped() && !paused.canPlaySound() && mc.manager.playing.isEmpty());
            SoundInstance disconnected=Sound{index}.play(new SoundEvent(),0.3F,1);
            LocalPlayer player=mc.player; mc.player=null; Sound{index}.tick(mc); mc.player=player;
            check(disconnected.isStopped() && !disconnected.canPlaySound() && mc.manager.playing.isEmpty());
            FirstAidConfig.CLIENT.tinnitusDurationPercent.set(400);
            check(Sound{index}.play(new SoundEvent(),original.volume,original.pitch).looping);
            FirstAidConfig.CLIENT.tinnitusDurationPercent.set(1);
            for (int t=0;t<847;t++) stepSounds(() -> Sound{index}.tick(mc));
            check(mc.manager.playing.size()==1); // Existing duration does not change.
            stepSounds(() -> Sound{index}.tick(mc)); check(mc.manager.playing.isEmpty());
            Sound{index}.play(new SoundEvent(),original.volume,original.pitch);
            FirstAidConfig.CLIENT.tinnitusEnabled.set(false); stepSounds(() -> Sound{index}.tick(mc));
            check(mc.manager.playing.isEmpty() && Sound{index}.play(new SoundEvent(),original.volume,original.pitch)==null);
            FirstAidConfig.CLIENT.tinnitusEnabled.set(true);
            Sound{index}.play(new SoundEvent(),original.volume,original.pitch); mc.player.alive=false; stepSounds(() -> Sound{index}.tick(mc));
            check(mc.manager.playing.isEmpty()); mc.player.alive=true;
            Sound{index}.play(new SoundEvent(),original.volume,original.pitch); mc.level=new Level(); stepSounds(() -> Sound{index}.tick(mc));
            check(mc.manager.playing.isEmpty());
            Sound{index}.beginMute(3); check(Sound{index}.play(new SoundEvent(),original.volume,original.pitch)==null);
            for (int t=0;t<3;t++) stepSounds(() -> Sound{index}.tick(mc));
            check(Sound{index}.play(new SoundEvent(),original.volume,original.pitch)!=null); Sound{index}.clear();
            CommonUtils.model.muted=true; check(Sound{index}.play(new SoundEvent(),original.volume,original.pitch)==null);
            CommonUtils.model.muted=false;
            Camera{index} feedback=new Camera{index}();
            for (int cue=1;cue<=3;cue++) {{
                CommonUtils.model.cueId=cue; CommonUtils.model.cueSeverity=0.7F;
                CommonUtils.model.cueDuration=(cue==2 ? 60 : 0); // Blast, head injury, poison/wither.
                for (int t=0;t<2000;t++) feedback.tick(mc);
                check(feedback.tinnitusPlays==cue); // Repeated snapshots must never replay the same cue.
            }}
            feedback.localMuteTicks=60; CommonUtils.model.cueId=4;
            feedback.tick(mc); for (int t=0;t<2000;t++) feedback.tick(mc);
            check(feedback.tinnitusPlays==3); // Muted events are consumed, never replayed later.
            CommonUtils.model.cueId=0; CommonUtils.model.cueSeverity=0; CommonUtils.model.cueDuration=0;
            Heartbeat{index} heart=new Heartbeat{index}(); CommonUtils.model.suppression=0; CommonUtils.model.holdTicks=0;
            CommonUtils.model.heartbeatId=0; heart.tick(mc); check(heart.activeHeartbeat==null); // No phantom join pulse.
            CommonUtils.model.suppression=0.05F; heart.tick(mc); check(heart.activeHeartbeat==null);
            float lastVolume=0, lastPitch=0;
            for (float pressure : new float[]{{Math.nextUp(0.05F),0.1F,0.25F,0.5F,0.75F,1}}) {{
                CommonUtils.model.suppression=pressure;
                for (int t=0;t<200;t++) {{ heart.tick(mc); heart.activeHeartbeat.tick(); }}
                float volume=heart.activeHeartbeat.getVolume(), pitch=heart.activeHeartbeat.getPitch();
                check(volume>=lastVolume && pitch>=lastPitch && volume>=0.4F && volume<=1 && pitch>=0.95F && pitch<=1.2F);
                lastVolume=volume; lastPitch=pitch;
            }}
            same(lastVolume,1); same(lastPitch,1.2F);
            CommonUtils.model.suppression=0; float fadeVolume=1;
            for (int t=0;t<70;t++) {{
                heart.tick(mc);
                if (heart.activeHeartbeat!=null) {{ heart.activeHeartbeat.tick(); float v=heart.activeHeartbeat.getVolume(); check(v<=fadeVolume); fadeVolume=v; }}
            }}
            check(heart.activeHeartbeat==null); mc.manager.playing.removeIf(SoundInstance::isStopped); heart.clear();
            CommonUtils.model.suppression=1; heart.tick(mc); check(heart.activeHeartbeat!=null);
            CommonUtils.model.muted=true; heart.tick(mc); check(heart.activeHeartbeat==null); CommonUtils.model.muted=false;
            heart.tick(mc); heart.beginMute(3); check(heart.activeHeartbeat==null);
            CommonUtils.model.heartbeatId++; heart.tick(mc); heart.tick(mc); check(heart.activeHeartbeat==null);
            CommonUtils.model.suppression=0; heart.tick(mc); check(heart.activeHeartbeat==null);
            CommonUtils.model.suppression=1; heart.tick(mc); mc.player.alive=false; heart.tick(mc); check(heart.activeHeartbeat==null);
            mc.player.alive=true; CommonUtils.model.suppression=0; heart.clear();
            Camera{index} cam = new Camera{index}();
            for (boolean mild : new boolean[]{{false,true}}) {{
                FirstAid.lowSuppressionEnabled=mild;
                for (float pressure : new float[]{{0,0.2F,0.5F,0.8F,1}}) {{
                    CommonUtils.model.suppression=pressure; CommonUtils.model.holdTicks=200;
                    Camera{index} healthy=new Camera{index}();
                    for (int t=0;t<200;t++) healthy.tick(mc);
                    same(healthy.fov(90),90); // Healthy pressure must never narrow FOV.
                }}
            }}
            float[] painPressures={{0,0.2F,0.5F,0.8F,1}}, expectedPainFovs={{80.4F,80.4F,86.112F,90,90}};
            CommonUtils.model.chronicPainIntensity=0.8F;
            for (int p=0;p<painPressures.length;p++) {{
                CommonUtils.model.suppression=painPressures[p];
                Camera{index} injured=new Camera{index}();
                for (int t=0;t<200;t++) injured.tick(mc);
                check(Math.abs(injured.fov(90)-expectedPainFovs[p])<0.00001F);
            }}
            CommonUtils.model.chronicPainIntensity=0; CommonUtils.model.suppression=0; CommonUtils.model.holdTicks=0;
            for (float pressure : new float[]{{0,0.2F,0.5F,1}}) for (float pain : new float[]{{0,4,12}}) {{
                cam.suppressionIntensity=pressure; cam.painFovCompression=pain;
                float expected=90-pain, before=cam.fov(90);
                same(before,expected);
                for (int shot=0;shot<1000;shot++) cam.onNearMiss(mc.player,1,1,1);
                same(cam.fov(90),before); // Near-miss packets cannot add any FOV impulse.
                cam.yawImpulse=0; cam.pitchImpulse=0;
            }}
            cam.painFovCompression=12;
            same(cam.fov(40), {30 if module == 'forge1.20.1' else 44}); // Preserve each loader's original pain-only FOV floor.
            for (float suppression : new float[]{{0,0.2F,0.6F,1,2}})
            for (float shake : new float[]{{0,0.3F,0.9F}}) for (int tick=0;tick<120;tick++)
            for (float partial : new float[]{{0,0.25F,0.75F}}) {{
                mc.player.tickCount=tick; cam.shakeStrength=shake;
                cam.suppressionIntensity=suppression;
                cam.yawImpulse=0.3F; cam.pitchImpulse=-0.2F;
                {apply}
                ViewportEvent.ComputeCameraAngles ref = new ViewportEvent.ComputeCameraAngles(); ref.partialTick=partial;
                Camera0 reference = new Camera0(); reference.shakeStrength=shake;
                reference.suppressionIntensity=suppression;
                reference.yawImpulse=0.3F; reference.pitchImpulse=-0.2F;
                reference.applyCameraAngles(ref);
                same(angles.yaw(),ref.yaw); same(angles.pitch(),ref.pitch); same(angles.roll(),ref.roll);
                same(angles.roll(),FirstAid.suppressionDisplayCurve(suppression) * 1.6F * (float)Math.sin((tick+partial)*2.24F));
                same(angles.yaw(),12 + shake * 0.28F * (float)Math.sin((tick+partial)*0.7F) + 0.3F);
                same(angles.pitch(),-8 + shake * 0.07F * (float)Math.cos((tick+partial)*0.9F) - 0.2F);
            }}
            // All axes must oscillate about zero, without a persistent horizon tilt.
            cam.yawImpulse=0; cam.pitchImpulse=0; cam.shakeStrength=0.3F;
            cam.suppressionIntensity=1;
            for (int axis=0;axis<3;axis++) {{
                double sum=0; float min=0, max=0;
                float frequency=axis==0 ? 0.7F : axis==1 ? 0.9F : 2.24F, amplitude=axis==0 ? 0.084F : axis==1 ? 0.021F : 1.6F;
                for (int i=0;i<360;i++) {{
                    float time=(float)(2*Math.PI*i/(frequency*360));
                    mc.player.tickCount=(int)time;
                    CameraAngles angles=cam.sample(time-(int)time);
                    float offset=axis==0 ? angles.yaw()-12 : axis==1 ? angles.pitch()+8 : angles.roll();
                    sum+=offset; min=Math.min(min,offset); max=Math.max(max,offset);
                }}
                check(Math.abs(sum/360)<0.000001 && min < -amplitude+0.001F && max > amplitude-0.001F);
                check(min >= -amplitude-0.000001F && max <= amplitude+0.000001F);
            }}
            for (boolean mild : new boolean[]{{false,true}})
            for (float severity : new float[]{{0,0.4F,1}}) for (float side : new float[]{{-1,1}})
            for (float vertical : new float[]{{-1,0,1}}) {{
                FirstAid.lowSuppressionEnabled=mild;
                Camera{index} actual=new Camera{index}(); Camera0 reference=new Camera0();
                actual.tick(mc); reference.tick(mc);
                for (int t=0;t<160;t++) {{
                    mc.player.tickCount=t;
                    CommonUtils.model.suppression=t<60 ? 0.8F : 0;
                    CommonUtils.model.holdTicks=t<60 ? 20 : 0;
                    actual.tick(mc); reference.tick(mc);
                    if (t<60) for (int shot=0;shot<3;shot++) {{
                        actual.onNearMiss(mc.player,severity,side,vertical);
                        reference.onNearMiss(mc.player,severity,side,vertical);
                    }}
                    same(actual.shakeStrength,reference.shakeStrength);
                    same(actual.yawImpulse,reference.yawImpulse);
                    same(actual.pitchImpulse,reference.pitchImpulse);
                    check(Math.abs(actual.yawImpulse)<=0.22F && Math.abs(actual.pitchImpulse)<=0.06F);
                    for (float partial : new float[]{{0,0.25F,0.75F}}) {{
                        CameraAngles a=actual.sample(partial), b=reference.sample(partial);
                        same(a.yaw(),b.yaw()); same(a.pitch(),b.pitch()); same(a.roll(),b.roll());
                        check(Math.abs(a.yaw()-12)<=actual.shakeStrength*0.28F+0.220001F);
                        check(Math.abs(a.pitch()+8)<=actual.shakeStrength*0.07F+0.060001F);
                        check(Math.abs(a.roll())<=FirstAid.suppressionDisplayCurve(actual.suppressionIntensity)*1.6F+0.000001F);
                        if (t==159) {{ same(a.yaw(),12); same(a.pitch(),-8); same(a.roll(),0); }}
                    }}
                }}
                actual.onNearMiss(mc.player,1,side,1); mc.player.alive=false; actual.tick(mc);
                same(actual.sample(0).yaw(),12); same(actual.sample(0).pitch(),-8); same(actual.sample(0).roll(),0);
                mc.player.alive=true;
                actual.onNearMiss(mc.player,1,side,1); mc.level=new Level(); actual.tick(mc);
                same(actual.sample(0).yaw(),12); same(actual.sample(0).pitch(),-8); same(actual.sample(0).roll(),0);
            }}
            FirstAid.lowSuppressionEnabled=false;
            System.out.println("PASS {module}: tinnitus fades to zero before native expiry, cancellation rejects restart, pause/disconnect cleanup and duplicate-cue rejection; gradual-injury and strong-hit triggers, short head/torso darkness, debuff tiers/cooldowns and healing, short configurable tinnitus, pressure-scaled heartbeat and cleanup, adrenaline injection/sync; 5400 formula + 17280 camera comparisons; reduced screen strength, no suppression/near-miss FOV, pain FOV follows partial adrenaline relief");
        }}
    """)
    module_test = tests.pop()
    classes.append(f"static void checkModule{index}() {{ Minecraft mc=Minecraft.INSTANCE; {module_test} }}")
    tests.append(f"checkModule{index}();")

java = ("import java.util.*; public class FeedbackRegression {" + STUBS + "\n".join(classes)
        + "public static void main(String[] args) { Minecraft mc=Minecraft.INSTANCE;"
        + "\n".join(tests) + "} }")
with tempfile.TemporaryDirectory(prefix="firstaid-feedback-") as tmp:
    source = Path(tmp) / "FeedbackRegression.java"
    source.write_text(java, encoding="utf-8")
    result = subprocess.run(["java", "-Dfile.encoding=UTF-8", str(source)], capture_output=True, text=True)
    print(result.stdout, end="")
    if result.returncode:
        print(result.stderr, end="")
        lines = java.splitlines()
        for line in re.findall(r"FeedbackRegression.java:(\d+)", result.stderr):
            number = int(line)
            print(f"Java {number}: " + lines[number-1].strip())
        raise SystemExit(result.returncode)

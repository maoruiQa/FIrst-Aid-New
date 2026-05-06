/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ichttt.mods.firstaid.FirstAid
 *  ichttt.mods.firstaid.FirstAidConfig
 *  ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel
 *  ichttt.mods.firstaid.common.RegistryObjects
 *  ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel
 *  ichttt.mods.firstaid.common.util.CommonUtils
 *  javax.annotation.Nullable
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.client.resources.sounds.SimpleSoundInstance
 *  net.minecraft.client.resources.sounds.Sound
 *  net.minecraft.client.resources.sounds.SoundInstance
 *  net.minecraft.client.resources.sounds.SoundInstance$Attenuation
 *  net.minecraft.client.sounds.SoundManager
 *  net.minecraft.client.sounds.WeighedSoundEvents
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.resources.Identifier
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.Level
 */
package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class SuppressionFeedbackController {
    private static final Identifier TINNITUS_SOUND = Identifier.fromNamespaceAndPath((String)"firstaid", (String)"debuff.heartbeat");
    private static final Set<Identifier> INTERNAL_SOUNDS = Set.of(TINNITUS_SOUND);
    private static final float PAIN_FOV_MAX_REDUCTION = 12.0f;
    private static final float PAIN_FOV_GAIN = 0.18f;
    private static final float PAIN_FOV_DECAY = 0.04f;
    private static final float SUPPRESSION_FOV_MIN = 30.0f;
    private static final float LOW_SUPPRESSION_MULTIPLIER = 0.4f;
    private static final int SEVERE_PAIN_LEVEL = 4;
    private static final int SEVERE_PAIN_SOUND_COOLDOWN_TICKS = 60;
    private float suppressionIntensity;
    private int holdTicks;
    private float audioMuffleStrength;
    private float tinnitusStrength;
    private float shakeStrength;
    private float sustainedFovCompression;
    private float painFovCompression;
    private float rollImpulse;
    private float yawImpulse;
    private float pitchImpulse;
    private float fovImpulse;
    private long soundCooldownUntilGameTime;
    private int lastPainLevel;
    @Nullable
    private Level trackedLevel;

    public void tick(Minecraft client) {
        PlayerDamageModel model;
        AbstractPlayerDamageModel damageModel;
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null || !player.isAlive() || !FirstAid.isSynced) {
            this.clear((Level)level);
            return;
        }
        if (this.trackedLevel != level) {
            this.clear((Level)level);
            this.trackedLevel = level;
        }
        PlayerDamageModel playerDamageModel = (damageModel = CommonUtils.getDamageModel((Player)player)) instanceof PlayerDamageModel ? (model = (PlayerDamageModel)damageModel) : null;
        int painLevel = playerDamageModel == null ? 0 : playerDamageModel.getPainLevel();
        float suppressionScale = FirstAid.lowSuppressionEnabled ? 0.4f : 1.0f;
        this.suppressionIntensity = (playerDamageModel == null ? 0.0f : playerDamageModel.getSuppressionIntensity()) * suppressionScale;
        this.holdTicks = playerDamageModel == null ? 0 : playerDamageModel.getSuppressionHoldTicks();
        boolean painSuppressed = player.hasEffect(RegistryObjects.MORPHINE_EFFECT) || player.hasEffect(RegistryObjects.PAINKILLER_EFFECT);
        float targetPainFov = painSuppressed || playerDamageModel == null ? 0.0f : playerDamageModel.getPainVisualStrength() * 12.0f;
        boolean holding = this.holdTicks > 0;
        float targetMuffle = holding ? Math.max(0.88f, this.suppressionIntensity * 1.12f) : this.suppressionIntensity * 0.98f;
        float targetTinnitus = holding ? Math.max(0.48f, this.suppressionIntensity * 0.64f) : this.suppressionIntensity * 0.42f;
        float targetShake = holding ? 0.38f + this.suppressionIntensity * 0.55f : this.suppressionIntensity * 0.34f;
        float targetFovCompression = holding ? 4.4f + this.suppressionIntensity * 7.0f : this.suppressionIntensity * 3.6f;
        this.audioMuffleStrength = SuppressionFeedbackController.approach(this.audioMuffleStrength, targetMuffle, targetMuffle > this.audioMuffleStrength ? 0.22f : 0.025f);
        this.tinnitusStrength = SuppressionFeedbackController.approach(this.tinnitusStrength, targetTinnitus, targetTinnitus > this.tinnitusStrength ? 0.12f : 0.02f);
        this.shakeStrength = SuppressionFeedbackController.approach(this.shakeStrength, targetShake, targetShake > this.shakeStrength ? 0.1f : 0.015f);
        this.sustainedFovCompression = SuppressionFeedbackController.approach(this.sustainedFovCompression, targetFovCompression, targetFovCompression > this.sustainedFovCompression ? 0.16f : 0.03f);
        this.painFovCompression = SuppressionFeedbackController.approach(this.painFovCompression, targetPainFov, targetPainFov > this.painFovCompression ? 0.18f : 0.04f);
        this.rollImpulse *= holding ? 0.95f : 0.88f;
        this.yawImpulse *= 0.85f;
        this.pitchImpulse *= 0.85f;
        this.fovImpulse *= holding ? 0.93f : 0.83f;
        if (((Boolean)FirstAidConfig.CLIENT.enableSounds.get()).booleanValue() && painLevel >= 4 && this.lastPainLevel < 4 && level.getGameTime() >= this.soundCooldownUntilGameTime) {
            this.soundCooldownUntilGameTime = level.getGameTime() + 60L;
            this.playTinnitusSound(0.52f + 0.12f * (float)Math.min(2, painLevel - 4));
        }
        this.lastPainLevel = painLevel;
    }

    public void clear() {
        this.clear(null);
    }

    public float getSuppressionIntensity() {
        return this.suppressionIntensity;
    }

    public int getHoldTicks() {
        return this.holdTicks;
    }

    public float getVisualStrength() {
        if (this.suppressionIntensity <= 0.0f && this.shakeStrength <= 0.0f) {
            return 0.0f;
        }
        return Mth.clamp((float)Math.max(this.suppressionIntensity, this.shakeStrength * 0.2f), (float)0.0f, (float)1.0f);
    }

    public float getAudioMuffleStrength() {
        return this.audioMuffleStrength;
    }

    public float getTinnitusStrength() {
        return this.tinnitusStrength;
    }

    public float getShakeStrength() {
        return this.shakeStrength;
    }

    public float getSustainedFovCompression() {
        return this.sustainedFovCompression;
    }

    public void onNearMiss(Player player, float severity, float lateralSign, float verticalSign) {
        this.shakeStrength = Math.max(this.shakeStrength, 0.34f + severity * 0.34f);
        this.rollImpulse += lateralSign * (1.2f + severity * 2.0f);
        this.yawImpulse += lateralSign * (0.35f + severity * 0.75f);
        this.pitchImpulse += verticalSign * (0.18f + severity * 0.42f);
        this.fovImpulse += 2.0f + severity * 4.2f;
        if (!((Boolean)FirstAidConfig.CLIENT.enableSounds.get()).booleanValue()) {
            return;
        }
        Level level = player.level();
        if (level.getGameTime() >= this.soundCooldownUntilGameTime) {
            this.soundCooldownUntilGameTime = level.getGameTime() + (long)Mth.ceil((float)(8.0f + (1.0f - severity) * 12.0f));
            this.playTinnitusSound(severity);
        }
    }

    public CameraAngles applyCameraAngles(@Nullable Entity entity, float partialTick, float yaw, float pitch) {
        if (this.suppressionIntensity <= 0.01f && this.shakeStrength <= 0.01f && Math.abs(this.rollImpulse) <= 0.01f && Math.abs(this.yawImpulse) <= 0.01f && Math.abs(this.pitchImpulse) <= 0.01f) {
            return new CameraAngles(yaw, pitch, 0.0f);
        }
        CameraCarrier cameraCarrier = new CameraCarrier(partialTick, entity, this.suppressionIntensity, this.shakeStrength);
        float oscillation = cameraCarrier.oscillation();
        float newYaw = yaw + this.yawImpulse + oscillation * 0.2f;
        float newPitch = pitch + this.pitchImpulse + oscillation * 0.14f;
        float newRoll = this.rollImpulse + oscillation * 0.8f + this.suppressionIntensity * 0.55f + this.shakeStrength * 0.18f;
        return new CameraAngles(newYaw, newPitch, newRoll);
    }

    public float applyFov(float baseFov) {
        if (this.suppressionIntensity <= 0.01f && this.fovImpulse <= 0.01f && this.sustainedFovCompression <= 0.01f && this.painFovCompression <= 0.01f) {
            return baseFov;
        }
        float suppressionTunnelVision = this.sustainedFovCompression + this.fovImpulse;
        float tunnelVision = suppressionTunnelVision + this.painFovCompression;
        float minFov = suppressionTunnelVision > 0.01f ? 30.0f : 44.0f;
        return Math.max(minFov, baseFov - tunnelVision);
    }

    @Nullable
    public SoundInstance maybeMuffle(@Nullable SoundInstance original) {
        if (!((Boolean)FirstAidConfig.CLIENT.enableSounds.get()).booleanValue()) {
            return original;
        }
        if (original == null || this.audioMuffleStrength <= 0.01f || original instanceof MuffledSoundInstance) {
            return original;
        }
        Identifier soundId = original.getIdentifier();
        if (INTERNAL_SOUNDS.contains(soundId) || original.getSource() == SoundSource.MASTER) {
            return original;
        }
        float volumeScale = 1.0f - this.audioMuffleStrength * 0.55f;
        float pitchScale = 1.0f - this.audioMuffleStrength * 0.32f;
        return new MuffledSoundInstance(original, volumeScale, pitchScale);
    }

    private void playTinnitusSound(float severity) {
        Minecraft client = Minecraft.getInstance();
        SoundManager soundManager = client.getSoundManager();
        SoundEvent tinnitus = (SoundEvent)BuiltInRegistries.SOUND_EVENT.getValue(TINNITUS_SOUND);
        if (tinnitus != null) {
            soundManager.play((SoundInstance)SimpleSoundInstance.forUI((SoundEvent)tinnitus, (float)(0.18f + severity * 0.28f), (float)(0.96f + severity * 0.08f)));
        }
    }

    private void clear(@Nullable Level level) {
        this.trackedLevel = level;
        this.suppressionIntensity = 0.0f;
        this.holdTicks = 0;
        this.audioMuffleStrength = 0.0f;
        this.tinnitusStrength = 0.0f;
        this.shakeStrength = 0.0f;
        this.sustainedFovCompression = 0.0f;
        this.painFovCompression = 0.0f;
        this.rollImpulse = 0.0f;
        this.yawImpulse = 0.0f;
        this.pitchImpulse = 0.0f;
        this.fovImpulse = 0.0f;
        this.soundCooldownUntilGameTime = 0L;
        this.lastPainLevel = 0;
    }

    private static float approach(float current, float target, float delta) {
        if (current < target) {
            return Math.min(target, current + delta);
        }
        return Math.max(target, current - delta);
    }

    public record CameraAngles(float yaw, float pitch, float roll) {
    }

    private record CameraCarrier(double partialTick, @Nullable Entity entity, float suppressionIntensity, float shakeStrength) {
        private float oscillation() {
            if (this.entity == null) {
                return 0.0f;
            }
            float time = (float)((double)this.entity.tickCount + this.partialTick);
            float low = (float)Math.sin(time * 0.65f);
            float high = (float)Math.sin(time * 2.75f);
            return (low * 0.35f + high * 0.65f) * (this.shakeStrength * 0.65f + this.suppressionIntensity * 0.18f);
        }
    }

    private static final class MuffledSoundInstance
    implements SoundInstance {
        private final SoundInstance delegate;
        private final float volumeScale;
        private final float pitchScale;

        private MuffledSoundInstance(SoundInstance delegate, float volumeScale, float pitchScale) {
            this.delegate = delegate;
            this.volumeScale = volumeScale;
            this.pitchScale = pitchScale;
        }

        public Identifier getIdentifier() {
            return this.delegate.getIdentifier();
        }

        public WeighedSoundEvents resolve(SoundManager soundManager) {
            return this.delegate.resolve(soundManager);
        }

        public Sound getSound() {
            return this.delegate.getSound();
        }

        public SoundSource getSource() {
            return this.delegate.getSource();
        }

        public boolean isLooping() {
            return this.delegate.isLooping();
        }

        public boolean isRelative() {
            return this.delegate.isRelative();
        }

        public int getDelay() {
            return this.delegate.getDelay();
        }

        public float getVolume() {
            return this.delegate.getVolume() * this.volumeScale;
        }

        public float getPitch() {
            return this.delegate.getPitch() * this.pitchScale;
        }

        public double getX() {
            return this.delegate.getX();
        }

        public double getY() {
            return this.delegate.getY();
        }

        public double getZ() {
            return this.delegate.getZ();
        }

        public SoundInstance.Attenuation getAttenuation() {
            return this.delegate.getAttenuation();
        }

        public boolean canStartSilent() {
            return this.delegate.canStartSilent();
        }

        public boolean canPlaySound() {
            return this.delegate.canPlaySound();
        }
    }
}


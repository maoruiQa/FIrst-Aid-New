/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import javax.annotation.Nullable;
import java.util.Set;

public final class SuppressionFeedbackController {
    private static final Identifier TINNITUS_SOUND = Identifier.fromNamespaceAndPath(FirstAid.MODID, "debuff.tinnitus");
    private static final Identifier HEARTBEAT_SOUND = Identifier.fromNamespaceAndPath(FirstAid.MODID, "debuff.heartbeat");
    private static final Set<Identifier> INTERNAL_SOUNDS = Set.of(TINNITUS_SOUND, HEARTBEAT_SOUND);
    private static final float PAIN_FOV_MAX_REDUCTION = 12.0F;
    private static final float PAIN_FOV_GAIN = 0.18F;
    private static final float PAIN_FOV_DECAY = 0.04F;
    private static final float SUPPRESSION_FOV_MIN = 30.0F;
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
    private @Nullable Level trackedLevel;

    public void tick(Minecraft client) {
        Player player = client.player;
        Level level = client.level;
        if (player == null || level == null || !player.isAlive() || !FirstAid.isSynced) {
            clear(level);
            return;
        }
        if (trackedLevel != level) {
            clear(level);
            trackedLevel = level;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        PlayerDamageModel playerDamageModel = damageModel instanceof PlayerDamageModel model ? model : null;
        int painLevel = playerDamageModel == null ? 0 : playerDamageModel.getPainLevel();
        float suppressionScale = FirstAid.lowSuppressionEnabled ? FirstAid.lowSuppressionMultiplier : 1.0F;
        suppressionIntensity = (playerDamageModel == null ? 0.0F : playerDamageModel.getSuppressionIntensity()) * suppressionScale;
        holdTicks = playerDamageModel == null ? 0 : playerDamageModel.getSuppressionHoldTicks();
        boolean painSuppressed = player.hasEffect(RegistryObjects.MORPHINE_EFFECT)
                || player.hasEffect(RegistryObjects.PAINKILLER_EFFECT);
        float targetPainFov = painSuppressed || playerDamageModel == null
                ? 0.0F
                : playerDamageModel.getPainVisualStrength() * PAIN_FOV_MAX_REDUCTION;

        boolean holding = holdTicks > 0;
        float targetMuffle = holding
                ? Math.max(0.88F, suppressionIntensity * 1.12F)
                : suppressionIntensity * 0.98F;
        float targetTinnitus = holding
                ? Math.max(0.48F, suppressionIntensity * 0.64F)
                : suppressionIntensity * 0.42F;
        float targetShake = holding
                ? 0.38F + suppressionIntensity * 0.55F
                : suppressionIntensity * 0.34F;
        float targetFovCompression = holding
                ? 4.4F + suppressionIntensity * 7.0F
                : suppressionIntensity * 3.6F;

        audioMuffleStrength = approach(audioMuffleStrength, targetMuffle, targetMuffle > audioMuffleStrength ? 0.22F : 0.025F);
        tinnitusStrength = approach(tinnitusStrength, targetTinnitus, targetTinnitus > tinnitusStrength ? 0.12F : 0.02F);
        shakeStrength = approach(shakeStrength, targetShake, targetShake > shakeStrength ? 0.10F : 0.015F);
        sustainedFovCompression = approach(sustainedFovCompression, targetFovCompression, targetFovCompression > sustainedFovCompression ? 0.16F : 0.03F);
        painFovCompression = approach(painFovCompression, targetPainFov, targetPainFov > painFovCompression ? PAIN_FOV_GAIN : PAIN_FOV_DECAY);

        rollImpulse *= holding ? 0.95F : 0.88F;
        yawImpulse *= 0.85F;
        pitchImpulse *= 0.85F;
        fovImpulse *= holding ? 0.93F : 0.83F;

        if (FirstAidConfig.CLIENT.enableSounds.get() && painLevel >= SEVERE_PAIN_LEVEL && lastPainLevel < SEVERE_PAIN_LEVEL
                && level.getGameTime() >= soundCooldownUntilGameTime) {
            soundCooldownUntilGameTime = level.getGameTime() + SEVERE_PAIN_SOUND_COOLDOWN_TICKS;
            playTinnitusSound(0.52F + 0.12F * Math.min(2, painLevel - SEVERE_PAIN_LEVEL));
        }

        lastPainLevel = painLevel;
    }

    public void clear() {
        clear(null);
    }

    public float getSuppressionIntensity() {
        return suppressionIntensity;
    }

    public int getHoldTicks() {
        return holdTicks;
    }

    public float getVisualStrength() {
        if (suppressionIntensity <= 0.0F && shakeStrength <= 0.0F) {
            return 0.0F;
        }
        return Mth.clamp(Math.max(suppressionIntensity, shakeStrength * 0.20F), 0.0F, 1.0F);
    }

    public float getAudioMuffleStrength() {
        return audioMuffleStrength;
    }

    public float getTinnitusStrength() {
        return tinnitusStrength;
    }

    public float getShakeStrength() {
        return shakeStrength;
    }

    public float getSustainedFovCompression() {
        return sustainedFovCompression;
    }

    public void onNearMiss(Player player, float severity, float lateralSign, float verticalSign) {
        shakeStrength = Math.max(shakeStrength, 0.34F + severity * 0.34F);
        rollImpulse += lateralSign * (1.2F + severity * 2.0F);
        yawImpulse += lateralSign * (0.35F + severity * 0.75F);
        pitchImpulse += verticalSign * (0.18F + severity * 0.42F);
        fovImpulse += 2.0F + severity * 4.2F;

        if (!FirstAidConfig.CLIENT.enableSounds.get()) {
            return;
        }

        Level level = player.level();
        if (level.getGameTime() >= soundCooldownUntilGameTime) {
            soundCooldownUntilGameTime = level.getGameTime() + Mth.ceil(8 + (1.0F - severity) * 12.0F);
            playTinnitusSound(severity);
        }
    }

    public void applyCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (suppressionIntensity <= 0.01F && shakeStrength <= 0.01F) {
            return;
        }
        CameraCarrier cameraCarrier = new CameraCarrier(event.getPartialTick(), event.getCamera().entity(), suppressionIntensity, shakeStrength);
        float oscillation = cameraCarrier.oscillation();
        event.setYaw(event.getYaw() + yawImpulse + oscillation * 0.20F);
        event.setPitch(event.getPitch() + pitchImpulse + oscillation * 0.14F);
        event.setRoll(event.getRoll() + rollImpulse + oscillation * 0.80F + suppressionIntensity * 0.55F + shakeStrength * 0.18F);
    }

    public void applyFov(ViewportEvent.ComputeFov event) {
        if (suppressionIntensity <= 0.01F && fovImpulse <= 0.01F && sustainedFovCompression <= 0.01F && painFovCompression <= 0.01F) {
            return;
        }
        float suppressionTunnelVision = sustainedFovCompression + fovImpulse;
        float tunnelVision = suppressionTunnelVision + painFovCompression;
        float minFov = suppressionTunnelVision > 0.01F ? SUPPRESSION_FOV_MIN : 44.0F;
        event.setFOV(Math.max(minFov, event.getFOV() - tunnelVision));
    }

    public void onPlaySound(PlaySoundEvent event) {
        if (!FirstAidConfig.CLIENT.enableSounds.get()) {
            return;
        }
        SoundInstance original = event.getSound();
        if (original == null || audioMuffleStrength <= 0.01F || original instanceof MuffledSoundInstance) {
            return;
        }
        Identifier identifier = original.getIdentifier();
        if (INTERNAL_SOUNDS.contains(identifier) || original.getSource() == SoundSource.MASTER) {
            return;
        }
        float volumeScale = 1.0F - audioMuffleStrength * 0.55F;
        float pitchScale = 1.0F - audioMuffleStrength * 0.32F;
        event.setSound(new MuffledSoundInstance(original, volumeScale, pitchScale));
    }

    private void playTinnitusSound(float severity) {
        Minecraft client = Minecraft.getInstance();
        SoundManager soundManager = client.getSoundManager();
        SoundEvent tinnitus = BuiltInRegistries.SOUND_EVENT.getValue(TINNITUS_SOUND);
        if (tinnitus != null) {
            soundManager.play(SimpleSoundInstance.forUI(tinnitus, 0.18F + severity * 0.28F, 0.96F + severity * 0.08F));
        }
    }

    private void clear(@Nullable Level level) {
        trackedLevel = level;
        suppressionIntensity = 0.0F;
        holdTicks = 0;
        audioMuffleStrength = 0.0F;
        tinnitusStrength = 0.0F;
        shakeStrength = 0.0F;
        sustainedFovCompression = 0.0F;
        painFovCompression = 0.0F;
        rollImpulse = 0.0F;
        yawImpulse = 0.0F;
        pitchImpulse = 0.0F;
        fovImpulse = 0.0F;
        soundCooldownUntilGameTime = 0L;
        lastPainLevel = 0;
    }

    private static float approach(float current, float target, float delta) {
        if (current < target) {
            return Math.min(target, current + delta);
        }
        return Math.max(target, current - delta);
    }

    private record CameraCarrier(double partialTick, net.minecraft.world.entity.Entity entity, float suppressionIntensity, float shakeStrength) {
        private float oscillation() {
            if (entity == null) {
                return 0.0F;
            }
            float time = (float) (entity.tickCount + partialTick);
            float low = (float) Math.sin(time * 0.65F);
            float high = (float) Math.sin(time * 2.75F);
            return (low * 0.35F + high * 0.65F) * (shakeStrength * 0.65F + suppressionIntensity * 0.18F);
        }
    }

    private static final class MuffledSoundInstance implements SoundInstance {
        private final SoundInstance delegate;
        private final float volumeScale;
        private final float pitchScale;

        private MuffledSoundInstance(SoundInstance delegate, float volumeScale, float pitchScale) {
            this.delegate = delegate;
            this.volumeScale = volumeScale;
            this.pitchScale = pitchScale;
        }

        @Override
        public Identifier getIdentifier() {
            return delegate.getIdentifier();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager soundManager) {
            return delegate.resolve(soundManager);
        }

        @Override
        public Sound getSound() {
            return delegate.getSound();
        }

        @Override
        public SoundSource getSource() {
            return delegate.getSource();
        }

        @Override
        public boolean isLooping() {
            return delegate.isLooping();
        }

        @Override
        public boolean isRelative() {
            return delegate.isRelative();
        }

        @Override
        public int getDelay() {
            return delegate.getDelay();
        }

        @Override
        public float getVolume() {
            return delegate.getVolume() * volumeScale;
        }

        @Override
        public float getPitch() {
            return delegate.getPitch() * pitchScale;
        }

        @Override
        public double getX() {
            return delegate.getX();
        }

        @Override
        public double getY() {
            return delegate.getY();
        }

        @Override
        public double getZ() {
            return delegate.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return delegate.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return delegate.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            return delegate.canPlaySound();
        }
    }
}

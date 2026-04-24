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
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;

import javax.annotation.Nullable;
import java.util.Set;

public final class SuppressionFeedbackController {
    private static final ResourceLocation TINNITUS_SOUND = new ResourceLocation(FirstAid.MODID, "debuff.tinnitus");
    private static final ResourceLocation HEARTBEAT_SOUND = new ResourceLocation(FirstAid.MODID, "debuff.heartbeat");
    private static final Set<ResourceLocation> INTERNAL_SOUNDS = Set.of(TINNITUS_SOUND, HEARTBEAT_SOUND);
    private static final float PAIN_FOV_MAX_REDUCTION = 12.0F;
    private static final float PAIN_FOV_GAIN = 0.18F;
    private static final float PAIN_FOV_DECAY = 0.04F;
    private static final float LOW_SUPPRESSION_MULTIPLIER = 0.4F;
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

        PlayerDamageModel playerDamageModel = CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model ? model : null;
        int painLevel = playerDamageModel == null ? 0 : playerDamageModel.getPainLevel();
        float suppressionScale = FirstAid.lowSuppressionEnabled ? LOW_SUPPRESSION_MULTIPLIER : 1.0F;
        suppressionIntensity = (playerDamageModel == null ? 0.0F : playerDamageModel.getSuppressionIntensity()) * suppressionScale;
        holdTicks = playerDamageModel == null ? 0 : playerDamageModel.getSuppressionHoldTicks();

        boolean painSuppressed = player.hasEffect(RegistryObjects.MORPHINE_EFFECT.get())
                || player.hasEffect(RegistryObjects.PAINKILLER_EFFECT.get());
        float targetPainFov = painSuppressed || playerDamageModel == null
                ? 0.0F
                : playerDamageModel.getPainVisualStrength() * PAIN_FOV_MAX_REDUCTION;

        boolean holding = holdTicks > 0;
        float targetMuffle = holding ? Math.max(0.88F, suppressionIntensity * 1.12F) : suppressionIntensity * 0.98F;
        float targetTinnitus = holding ? Math.max(0.48F, suppressionIntensity * 0.64F) : suppressionIntensity * 0.42F;
        float targetShake = holding ? 0.38F + suppressionIntensity * 0.55F : suppressionIntensity * 0.34F;
        float targetFovCompression = holding ? 4.4F + suppressionIntensity * 7.0F : suppressionIntensity * 3.6F;

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

    public float getVisualStrength() {
        if (suppressionIntensity <= 0.0F && shakeStrength <= 0.0F) {
            return 0.0F;
        }
        return Mth.clamp(Math.max(suppressionIntensity, shakeStrength * 0.20F), 0.0F, 1.0F);
    }

    public float getTinnitusStrength() {
        return tinnitusStrength;
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

        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = (float) event.getPartialTick();
        float time = (minecraft.player == null ? 0.0F : minecraft.player.tickCount) + partialTick;
        float baseRoll = shakeStrength * (0.45F + 1.1F * (float) Math.sin(time * 0.35F));
        float baseYaw = shakeStrength * 0.35F * (float) Math.sin(time * 0.35F);
        float basePitch = shakeStrength * 0.22F * (float) Math.cos(time * 0.45F);

        event.setRoll(event.getRoll() + baseRoll + rollImpulse);
        event.setYaw(event.getYaw() + baseYaw + yawImpulse);
        event.setPitch(event.getPitch() + basePitch + pitchImpulse);
    }

    public void applyFov(ViewportEvent.ComputeFov event) {
        double reduction = sustainedFovCompression + painFovCompression + fovImpulse;
        if (reduction <= 0.01D) {
            return;
        }
        event.setFOV(Math.max(30.0D, event.getFOV() - reduction));
    }

    public void onPlaySound(PlaySoundEvent event) {
        SoundInstance original = event.getSound();
        if (original == null || audioMuffleStrength <= 0.01F || original instanceof MuffledSoundInstance) {
            return;
        }

        ResourceLocation location = original.getLocation();
        if (location == null || INTERNAL_SOUNDS.contains(location)) {
            return;
        }

        float volumeScale = Mth.clamp(1.0F - (audioMuffleStrength * 0.55F), 0.18F, 1.0F);
        float pitchScale = Mth.clamp(1.0F - (audioMuffleStrength * 0.20F), 0.65F, 1.0F);
        event.setSound(new MuffledSoundInstance(original, volumeScale, pitchScale));
    }

    private void playTinnitusSound(float severity) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(RegistryObjects.TINNITUS.get(), 0.18F + severity * 0.28F, 0.96F + severity * 0.08F));
    }

    private void clear(@Nullable Level level) {
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
        soundCooldownUntilGameTime = level == null ? 0L : level.getGameTime();
        lastPainLevel = 0;
        trackedLevel = level;
    }

    private static float approach(float current, float target, float delta) {
        if (target > current) {
            return Math.min(target, current + delta);
        }
        return Math.max(target, current - delta);
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
        public ResourceLocation getLocation() {
            return delegate.getLocation();
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

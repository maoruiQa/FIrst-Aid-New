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
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public final class SuppressionFeedbackController {
    private static final ResourceLocation TINNITUS_SOUND = new ResourceLocation(FirstAid.MODID, "debuff.tinnitus");
    private static final ResourceLocation HEARTBEAT_SOUND = new ResourceLocation(FirstAid.MODID, "debuff.heartbeat");
    private static final Set<ResourceLocation> INTERNAL_SOUNDS = Set.of(TINNITUS_SOUND, HEARTBEAT_SOUND);
    private static final float PAIN_FOV_MAX_REDUCTION = 12.0F;
    private static final float PAIN_FOV_HARD_MAX_REDUCTION = 22.0F;
    private static final float PAIN_FOV_GAIN = 0.22F;
    private static final float PAIN_FOV_DECAY = 0.05F;
    /** Only overpowering suppression may ring ears (issue #7). */
    private static final float OVERPOWER_SUPPRESSION_TINNITUS_THRESHOLD = 0.85F;
    private static final int OVERPOWER_SUPPRESSION_TINNITUS_COOLDOWN_TICKS = 100;
    private static final float MUSIC_DETUNE_ADDICTION_THRESHOLD = 0.35F;

    /**
     * Vanilla hallucination stingers during withdrawal (sudden, non-looping).
     * Frequency scales with addiction severity while an episode is active.
     */
    private static final List<ResourceLocation> WITHDRAWAL_HALLUCINATION_SOUND_IDS = List.of(
            new ResourceLocation("minecraft", "block.portal.ambient"),
            new ResourceLocation("minecraft", "block.portal.trigger"),
            new ResourceLocation("minecraft", "block.portal.travel"),
            new ResourceLocation("minecraft", "entity.generic.explode"),
            new ResourceLocation("minecraft", "entity.creeper.primed"),
            new ResourceLocation("minecraft", "entity.ghast.ambient"),
            new ResourceLocation("minecraft", "entity.ghast.scream"),
            new ResourceLocation("minecraft", "entity.ghast.warn"),
            new ResourceLocation("minecraft", "entity.enderman.ambient"),
            new ResourceLocation("minecraft", "entity.enderman.scream"),
            new ResourceLocation("minecraft", "entity.enderman.stare"),
            new ResourceLocation("minecraft", "entity.enderman.teleport"),
            new ResourceLocation("minecraft", "ambient.cave"),
            new ResourceLocation("minecraft", "entity.warden.heartbeat"),
            new ResourceLocation("minecraft", "entity.warden.nearby_close"),
            new ResourceLocation("minecraft", "block.sculk_shrieker.shriek"),
            new ResourceLocation("minecraft", "entity.phantom.ambient"),
            new ResourceLocation("minecraft", "entity.phantom.swoop"),
            new ResourceLocation("minecraft", "entity.elder_guardian.curse"),
            new ResourceLocation("minecraft", "block.respawn_anchor.ambient"),
            new ResourceLocation("minecraft", "block.respawn_anchor.deplete"),
            new ResourceLocation("minecraft", "entity.vex.ambient"),
            new ResourceLocation("minecraft", "entity.vex.charge"),
            new ResourceLocation("minecraft", "entity.illusioner.mirror_move"),
            new ResourceLocation("minecraft", "entity.illusioner.cast_spell"),
            new ResourceLocation("minecraft", "entity.wither.ambient"),
            new ResourceLocation("minecraft", "entity.zombie_villager.cure"),
            new ResourceLocation("minecraft", "block.beacon.deactivate")
    );
    private float suppressionIntensity;
    private int holdTicks;
    private float audioMuffleStrength;
    private float musicDetuneStrength;
    private float tinnitusStrength;
    private float shakeStrength;
    private float sustainedFovCompression;
    private float painFovCompression;
    private float rollImpulse;
    private float yawImpulse;
    private float pitchImpulse;
    private float fovImpulse;
    private long soundCooldownUntilGameTime;
    private int lastTinnitusCueId;
    private boolean wasOverpowerSuppression;
    private int hallucinationCooldownTicks;
    private int localMuteTicks;
    private @Nullable Level trackedLevel;
    private @Nullable SoundInstance activeTinnitusSound;

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
        if (localMuteTicks > 0) {
            --localMuteTicks;
        }

        PlayerDamageModel playerDamageModel = CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model ? model : null;
        int painLevel = playerDamageModel == null ? 0 : playerDamageModel.getPainLevel();
        float acutePain = playerDamageModel == null ? 0.0F : playerDamageModel.getAcutePainIntensity();
        float suppressionScale = FirstAid.lowSuppressionEnabled ? FirstAid.lowSuppressionMultiplier : 1.0F;
        suppressionIntensity = (playerDamageModel == null ? 0.0F : playerDamageModel.getSuppressionIntensity()) * suppressionScale;
        holdTicks = playerDamageModel == null ? 0 : playerDamageModel.getSuppressionHoldTicks();

        boolean withdrawalActive = playerDamageModel != null && playerDamageModel.isWithdrawalEpisodeActive();

        float addictionNorm = playerDamageModel == null ? 0.0F : playerDamageModel.getAddictionNormalized();

        boolean painSuppressed = playerDamageModel != null && playerDamageModel.isPainSuppressed(player);
        float targetPainFov = 0.0F;
        if (playerDamageModel != null && FirstAid.enablePainFovCompression) {
            float visual = playerDamageModel.getPainVisualStrength(painSuppressed);
            if (visual > 0.01F) {
                float soft = Mth.clamp(visual, 0.0F, 1.0F) * PAIN_FOV_MAX_REDUCTION;
                float hardExtra = Math.max(0.0F, visual - 1.0F) / (PlayerDamageModel.PAIN_HARD_CAP - 1.0F)
                        * (PAIN_FOV_HARD_MAX_REDUCTION - PAIN_FOV_MAX_REDUCTION);
                targetPainFov = soft + hardExtra;
            }
        }

        boolean holding = holdTicks > 0;
        float targetMuffle = (holding ? 0.55F : 0.42F) * suppressionIntensity;
        float targetMusicDetune = 0.0F;
        if (withdrawalActive && addictionNorm >= MUSIC_DETUNE_ADDICTION_THRESHOLD) {
            targetMusicDetune = 0.22F + (addictionNorm - MUSIC_DETUNE_ADDICTION_THRESHOLD)
                    / (1.0F - MUSIC_DETUNE_ADDICTION_THRESHOLD) * 0.55F;
        }
        boolean audioMuted = localMuteTicks > 0 || (playerDamageModel != null && playerDamageModel.isAudioMuted());
        float tinnitusProgress = Mth.clamp((suppressionIntensity - 0.70F) / 0.30F, 0.0F, 1.0F);
        float targetTinnitus = audioMuted ? 0.0F : 0.57F * tinnitusProgress;
        if (withdrawalActive && !audioMuted) {
            targetTinnitus = Math.max(targetTinnitus, 0.10F + addictionNorm * 0.22F);
        }
        float targetShake = (holding ? 0.30F : 0.10F) * FirstAid.suppressionDisplayCurve(suppressionIntensity);
        float targetFovCompression = (holding ? 3.2F : 1.0F) * FirstAid.suppressionDisplayCurve(suppressionIntensity);

        audioMuffleStrength = approach(audioMuffleStrength, targetMuffle, targetMuffle > audioMuffleStrength ? 0.18F : 0.020F);
        musicDetuneStrength = approach(musicDetuneStrength, targetMusicDetune, targetMusicDetune > musicDetuneStrength ? 0.10F : 0.03F);
        tinnitusStrength = approach(tinnitusStrength, targetTinnitus, targetTinnitus > tinnitusStrength ? 0.14F : 0.06F);
        shakeStrength = approach(shakeStrength, targetShake, targetShake > shakeStrength ? 0.10F : 0.015F);
        sustainedFovCompression = approach(sustainedFovCompression, targetFovCompression, targetFovCompression > sustainedFovCompression ? 0.16F : 0.03F);
        painFovCompression = approach(painFovCompression, targetPainFov, targetPainFov > painFovCompression ? PAIN_FOV_GAIN : PAIN_FOV_DECAY);

        rollImpulse *= holding ? 0.95F : 0.88F;
        yawImpulse *= 0.85F;
        pitchImpulse *= 0.85F;
        fovImpulse *= holding ? 0.93F : 0.83F;

        if (!audioMuted && FirstAidConfig.CLIENT.enableSounds.get() && FirstAid.enablePainAudioEffects) {
            int cueId = playerDamageModel == null ? 0 : playerDamageModel.getTinnitusCueId();
            float cueSeverity = playerDamageModel == null ? 0.0F : playerDamageModel.getTinnitusCueSeverity();
            if (cueId > 0 && cueId != lastTinnitusCueId && cueSeverity > 0.01F) {
                lastTinnitusCueId = cueId;
                playTinnitusSound(cueSeverity);
            }

            boolean overpower = suppressionIntensity >= OVERPOWER_SUPPRESSION_TINNITUS_THRESHOLD;
            if (overpower && !wasOverpowerSuppression && level.getGameTime() >= soundCooldownUntilGameTime) {
                soundCooldownUntilGameTime = level.getGameTime() + OVERPOWER_SUPPRESSION_TINNITUS_COOLDOWN_TICKS;
                playTinnitusSound(0.57F * tinnitusProgress);
            }
            wasOverpowerSuppression = overpower;
        }

        tickWithdrawalHallucinations(player, level, withdrawalActive, addictionNorm);

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

    public void beginMute(int ticks) {
        localMuteTicks = Math.max(localMuteTicks, Math.max(0, ticks));
        stopActiveTinnitusSound();
        tinnitusStrength = 0.0F;
    }

    public void onNearMiss(Player player, float severity, float lateralSign, float verticalSign) {
        // Visual/camera feedback only — near-miss no longer rings ears (issue #7).
        shakeStrength = Math.max(shakeStrength, 0.34F + severity * 0.34F);
        rollImpulse += lateralSign * (1.2F + severity * 2.0F);
        yawImpulse += lateralSign * (0.35F + severity * 0.75F);
        pitchImpulse += verticalSign * (0.18F + severity * 0.42F);
        fovImpulse += 2.0F + severity * 4.2F;
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
        if (!FirstAidConfig.CLIENT.enableSounds.get()) {
            return;
        }
        SoundInstance original = event.getSound();
        if (original == null || original instanceof MuffledSoundInstance) {
            return;
        }

        ResourceLocation location = original.getLocation();
        if (location != null && INTERNAL_SOUNDS.contains(location)) {
            return;
        }

        boolean isMusic = original.getSource() == SoundSource.MUSIC || original.getSource() == SoundSource.RECORDS;
        if (isMusic) {
            if (musicDetuneStrength <= 0.01F) {
                return;
            }
            float volumeScale = Mth.clamp(1.0F - musicDetuneStrength * 0.35F, 0.35F, 1.0F);
            float pitchScale = Mth.clamp(1.0F - musicDetuneStrength * 0.38F, 0.55F, 1.0F);
            event.setSound(new MuffledSoundInstance(original, volumeScale, pitchScale));
            return;
        }
        if (audioMuffleStrength <= 0.01F) {
            return;
        }
        float volumeScale = Mth.clamp(1.0F - audioMuffleStrength * 0.40F, 0.45F, 1.0F);
        event.setSound(new MuffledSoundInstance(original, volumeScale, 1.0F));
    }

    private void playTinnitusSound(float severity) {
        if (severity <= 0.01F) return;
        Minecraft client = Minecraft.getInstance();
        SoundManager soundManager = client.getSoundManager();
        SoundEvent tinnitus = RegistryObjects.TINNITUS.get();
        if (tinnitus == null) {
            return;
        }
        stopActiveTinnitusSound();
        SimpleSoundInstance instance = SimpleSoundInstance.forUI(
                tinnitus,
                0.36F * severity,
                0.96F + severity * 0.08F
        );
        activeTinnitusSound = instance;
        soundManager.play(instance);
    }

    private void stopActiveTinnitusSound() {
        if (activeTinnitusSound == null) {
            return;
        }
        Minecraft.getInstance().getSoundManager().stop(activeTinnitusSound);
        activeTinnitusSound = null;
    }

    private void clear(@Nullable Level level) {
        stopActiveTinnitusSound();
        suppressionIntensity = 0.0F;
        holdTicks = 0;
        audioMuffleStrength = 0.0F;
        musicDetuneStrength = 0.0F;
        tinnitusStrength = 0.0F;
        shakeStrength = 0.0F;
        sustainedFovCompression = 0.0F;
        painFovCompression = 0.0F;
        rollImpulse = 0.0F;
        yawImpulse = 0.0F;
        pitchImpulse = 0.0F;
        fovImpulse = 0.0F;
        soundCooldownUntilGameTime = level == null ? 0L : level.getGameTime();
        lastTinnitusCueId = 0;
        wasOverpowerSuppression = false;
        localMuteTicks = 0;
        hallucinationCooldownTicks = 0;
        trackedLevel = level;
    }


    private void tickWithdrawalHallucinations(Player player, Level level, boolean withdrawalActive, float addictionNorm) {
        if (!withdrawalActive || !FirstAidConfig.CLIENT.enableSounds.get()) {
            hallucinationCooldownTicks = Math.max(0, hallucinationCooldownTicks - 1);
            return;
        }
        if (hallucinationCooldownTicks > 0) {
            hallucinationCooldownTicks--;
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        float chance = 0.015F + addictionNorm * addictionNorm * 0.14F;
        RandomSource random = level.random;
        if (random.nextFloat() > chance) {
            return;
        }
        playWithdrawalHallucination(player, level, addictionNorm, random);
        int minGap = Math.round(Mth.lerp(addictionNorm, 20 * 12, 20 * 2.5F));
        int extra = random.nextInt(Math.max(1, Math.round(Mth.lerp(addictionNorm, 20 * 10, 20 * 3))));
        hallucinationCooldownTicks = minGap + extra;
    }

    private void playWithdrawalHallucination(Player player, Level level, float addictionNorm, RandomSource random) {
        if (WITHDRAWAL_HALLUCINATION_SOUND_IDS.isEmpty()) {
            return;
        }
        ResourceLocation id = WITHDRAWAL_HALLUCINATION_SOUND_IDS.get(random.nextInt(WITHDRAWAL_HALLUCINATION_SOUND_IDS.size()));
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(id);
        if (sound == null) {
            return;
        }
        float volume = 0.35F + random.nextFloat() * 0.45F + addictionNorm * 0.15F;
        float pitch = 0.65F + random.nextFloat() * 0.70F;
        double ox = (random.nextDouble() - 0.5D) * 2.4D;
        double oy = random.nextDouble() * 1.2D;
        double oz = (random.nextDouble() - 0.5D) * 2.4D;
        level.playLocalSound(
                player.getX() + ox,
                player.getY() + player.getEyeHeight() * 0.6D + oy,
                player.getZ() + oz,
                sound,
                SoundSource.AMBIENT,
                volume,
                pitch,
                false
        );
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

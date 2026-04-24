package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class HeartbeatSoundController {
    private static final int FADE_OUT_TICKS = 100;
    private static final int ACTIVE_HOLD_TICKS = 2;
    private static final int PULSE_HOLD_TICKS = 2;
    private static final float RED_HEALTH_THRESHOLD = 0.25F;
    private static final float ACTIVE_SUPPRESSION_THRESHOLD = 0.01F;

    private @Nullable HeartbeatLoopSound activeHeartbeat;
    private int lastAdrenalineHeartbeatTriggerId = -1;

    public void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (!FirstAidConfig.CLIENT.enableSounds.get() || player == null || !player.isAlive() || minecraft.level == null || !FirstAid.isSynced) {
            clear();
            return;
        }

        PlayerDamageModel playerDamageModel = CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model ? model : null;
        boolean sustainedHeartbeat = playerDamageModel != null && (isCriticalPartRed(playerDamageModel) || isSuppressed(playerDamageModel));
        int triggerId = playerDamageModel == null ? -1 : playerDamageModel.getAdrenalineHeartbeatTriggerId();
        boolean pulseHeartbeat = triggerId != -1 && triggerId != lastAdrenalineHeartbeatTriggerId;
        lastAdrenalineHeartbeatTriggerId = triggerId;
        if (sustainedHeartbeat || pulseHeartbeat) {
            HeartbeatLoopSound heartbeat = ensureHeartbeat(minecraft, player);
            if (heartbeat != null) {
                if (sustainedHeartbeat) {
                    heartbeat.keepAlive();
                }
                if (pulseHeartbeat) {
                    heartbeat.triggerPulse();
                }
            }
        } else if (activeHeartbeat != null) {
            activeHeartbeat.beginFadeOut();
        }

        if (activeHeartbeat != null && activeHeartbeat.isStopped()) {
            activeHeartbeat = null;
        }
    }

    public void clear() {
        if (activeHeartbeat != null) {
            activeHeartbeat.stopImmediately();
            Minecraft.getInstance().getSoundManager().stop(activeHeartbeat);
            activeHeartbeat = null;
        }
        lastAdrenalineHeartbeatTriggerId = -1;
    }

    private boolean isCriticalPartRed(PlayerDamageModel model) {
        return CommonUtils.getVisibleHealthRatio(model.getFromEnum(EnumPlayerPart.HEAD)) <= RED_HEALTH_THRESHOLD
                || CommonUtils.getVisibleHealthRatio(model.getFromEnum(EnumPlayerPart.BODY)) <= RED_HEALTH_THRESHOLD;
    }

    private boolean isSuppressed(PlayerDamageModel model) {
        return model.getSuppressionHoldTicks() > 0 || model.getSuppressionIntensity() > ACTIVE_SUPPRESSION_THRESHOLD;
    }

    private @Nullable HeartbeatLoopSound ensureHeartbeat(Minecraft minecraft, LocalPlayer player) {
        if (activeHeartbeat != null) {
            if (!activeHeartbeat.isStopped()) {
                return activeHeartbeat;
            }
            minecraft.getSoundManager().stop(activeHeartbeat);
            activeHeartbeat = null;
        }

        SoundEvent heartbeatEvent = RegistryObjects.HEARTBEAT.value();
        HeartbeatLoopSound heartbeat = new HeartbeatLoopSound(player, heartbeatEvent);
        minecraft.getSoundManager().play(heartbeat);
        activeHeartbeat = heartbeat;
        return heartbeat;
    }

    private static final class HeartbeatLoopSound implements TickableSoundInstance {
        private static final float BASE_VOLUME = 0.92F;
        private static final float PULSE_VOLUME = 1.0F;

        private final LocalPlayer player;
        private final Identifier location;
        private Sound sound = SoundManager.EMPTY_SOUND;
        private int activeHoldTicks;
        private int pulseHoldTicks;
        private int fadeTicksRemaining = -1;
        private boolean stopped;

        private HeartbeatLoopSound(LocalPlayer player, SoundEvent event) {
            this.player = player;
            this.location = event.location();
        }

        private void keepAlive() {
            activeHoldTicks = ACTIVE_HOLD_TICKS;
            fadeTicksRemaining = -1;
        }

        private void triggerPulse() {
            pulseHoldTicks = Math.max(pulseHoldTicks, PULSE_HOLD_TICKS);
            fadeTicksRemaining = -1;
        }

        private void beginFadeOut() {
            if (fadeTicksRemaining < 0 && activeHoldTicks <= 0 && pulseHoldTicks <= 0) {
                fadeTicksRemaining = FADE_OUT_TICKS;
            }
        }

        private void stopImmediately() {
            stopped = true;
        }

        @Override
        public boolean isStopped() {
            return stopped || !player.isAlive();
        }

        @Override
        public void tick() {
            if (!player.isAlive()) {
                stopped = true;
                return;
            }

            boolean active = activeHoldTicks > 0 || pulseHoldTicks > 0;
            if (activeHoldTicks > 0) {
                activeHoldTicks--;
            }
            if (pulseHoldTicks > 0) {
                pulseHoldTicks--;
            }

            if (active) {
                fadeTicksRemaining = -1;
            } else if (fadeTicksRemaining < 0) {
                fadeTicksRemaining = FADE_OUT_TICKS;
            } else if (fadeTicksRemaining > 0) {
                fadeTicksRemaining--;
                if (fadeTicksRemaining == 0) {
                    stopped = true;
                }
            }
        }

        @Nonnull
        @Override
        public Identifier getIdentifier() {
            return location;
        }

        @Nullable
        @Override
        public WeighedSoundEvents resolve(@Nonnull SoundManager soundManager) {
            WeighedSoundEvents events = soundManager.getSoundEvent(location);
            if (events != null) {
                sound = events.getSound(SoundInstance.createUnseededRandom());
            }
            return events;
        }

        @Nonnull
        @Override
        public Sound getSound() {
            return sound;
        }

        @Nonnull
        @Override
        public SoundSource getSource() {
            return SoundSource.PLAYERS;
        }

        @Override
        public boolean isLooping() {
            return true;
        }

        @Override
        public boolean isRelative() {
            return true;
        }

        @Override
        public int getDelay() {
            return 0;
        }

        @Override
        public float getVolume() {
            if (activeHoldTicks > 0) {
                return BASE_VOLUME;
            }
            if (pulseHoldTicks > 0) {
                return PULSE_VOLUME;
            }
            if (fadeTicksRemaining < 0) {
                return 0.0F;
            }
            return BASE_VOLUME * (fadeTicksRemaining / (float) FADE_OUT_TICKS);
        }

        @Override
        public float getPitch() {
            return 1.0F;
        }

        @Override
        public double getX() {
            return 0.0D;
        }

        @Override
        public double getY() {
            return 0.0D;
        }

        @Override
        public double getZ() {
            return 0.0D;
        }

        @Nonnull
        @Override
        public Attenuation getAttenuation() {
            return Attenuation.NONE;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}

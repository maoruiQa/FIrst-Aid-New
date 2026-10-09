package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

public final class HeartbeatSoundController {
   private static final int FADE_OUT_TICKS = 60;
   private static final int ACTIVE_HOLD_TICKS = 2;
   private static final int PULSE_HOLD_TICKS = 2;
   private static final float RED_HEALTH_THRESHOLD = 0.25F;
   /** Ignore tiny residual suppression after rescue (issue #7). */
   private static final float ACTIVE_SUPPRESSION_THRESHOLD = 0.05F;
   @Nullable
   private HeartbeatLoopSound activeHeartbeat;
   private int lastAdrenalineHeartbeatTriggerId = -1;
   private int localMuteTicks;

   public void tick(Minecraft minecraft) {
      LocalPlayer player = minecraft.player;
      if (!(Boolean)FirstAidConfig.CLIENT.enableSounds.get() || player == null || !player.isAlive() || minecraft.level == null || !FirstAid.isSynced) {
         this.clear();
         return;
      }

      if (this.localMuteTicks > 0) {
         --this.localMuteTicks;
         if (this.activeHeartbeat != null) {
            this.activeHeartbeat.stopImmediately();
            minecraft.getSoundManager().stop(this.activeHeartbeat);
            this.activeHeartbeat = null;
         }
         PlayerDamageModel mutedModel = CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model ? model : null;
         this.lastAdrenalineHeartbeatTriggerId = mutedModel == null ? -1 : mutedModel.getAdrenalineHeartbeatTriggerId();
         return;
      }

      PlayerDamageModel playerDamageModel = CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model ? model : null;
      if (playerDamageModel != null && playerDamageModel.isAudioMuted()) {
         if (this.activeHeartbeat != null) {
            this.activeHeartbeat.stopImmediately();
            minecraft.getSoundManager().stop(this.activeHeartbeat);
            this.activeHeartbeat = null;
         }
         this.lastAdrenalineHeartbeatTriggerId = playerDamageModel.getAdrenalineHeartbeatTriggerId();
         return;
      }

      boolean sustainedHeartbeat = playerDamageModel != null && (this.isCriticalPartRed(playerDamageModel) || this.isSuppressed(playerDamageModel));
        float strength = playerDamageModel == null ? 0.0F : FirstAid.suppressionDisplayCurve(
                (playerDamageModel.getSuppressionIntensity() - ACTIVE_SUPPRESSION_THRESHOLD) / (1.0F - ACTIVE_SUPPRESSION_THRESHOLD));
        if (playerDamageModel != null && isCriticalPartRed(playerDamageModel)) strength = Math.max(strength, 0.65F);
      int triggerId = playerDamageModel == null ? -1 : playerDamageModel.getAdrenalineHeartbeatTriggerId();
      boolean pulseHeartbeat = this.lastAdrenalineHeartbeatTriggerId >= 0 && triggerId > this.lastAdrenalineHeartbeatTriggerId;
      this.lastAdrenalineHeartbeatTriggerId = triggerId;
      if (sustainedHeartbeat || pulseHeartbeat) {
         HeartbeatLoopSound heartbeat = this.ensureHeartbeat(minecraft, player);
         if (heartbeat != null) {
            if (sustainedHeartbeat) {
               heartbeat.keepAlive(strength);
            }

            if (pulseHeartbeat) {
               heartbeat.triggerPulse();
            }
         }
      } else if (this.activeHeartbeat != null) {
         this.activeHeartbeat.beginFadeOut();
      }

      if (this.activeHeartbeat != null && this.activeHeartbeat.isStopped()) {
         this.activeHeartbeat = null;
      }
   }

   public void beginMute(int ticks) {
      this.localMuteTicks = Math.max(this.localMuteTicks, Math.max(0, ticks));
      if (this.activeHeartbeat != null) {
         this.activeHeartbeat.stopImmediately();
         Minecraft.getInstance().getSoundManager().stop(this.activeHeartbeat);
         this.activeHeartbeat = null;
      }
   }

   public void clear() {
      if (this.activeHeartbeat != null) {
         this.activeHeartbeat.stopImmediately();
         Minecraft.getInstance().getSoundManager().stop(this.activeHeartbeat);
         this.activeHeartbeat = null;
      }

      this.lastAdrenalineHeartbeatTriggerId = -1;
      this.localMuteTicks = 0;
   }

   private boolean isCriticalPartRed(PlayerDamageModel model) {
      return CommonUtils.getVisibleHealthRatio(model.getFromEnum(EnumPlayerPart.HEAD)) <= RED_HEALTH_THRESHOLD
         || CommonUtils.getVisibleHealthRatio(model.getFromEnum(EnumPlayerPart.BODY)) <= RED_HEALTH_THRESHOLD;
   }

   private boolean isSuppressed(PlayerDamageModel model) {
        return model.getSuppressionIntensity() > ACTIVE_SUPPRESSION_THRESHOLD;
    }

   @Nullable
   private HeartbeatLoopSound ensureHeartbeat(Minecraft minecraft, LocalPlayer player) {
      if (this.activeHeartbeat != null) {
         if (!this.activeHeartbeat.isStopped()) {
            return this.activeHeartbeat;
         }

         minecraft.getSoundManager().stop(this.activeHeartbeat);
         this.activeHeartbeat = null;
      }

      SoundEvent heartbeatEvent = RegistryObjects.HEARTBEAT.value();
      HeartbeatLoopSound heartbeat = new HeartbeatLoopSound(player, heartbeatEvent);
      minecraft.getSoundManager().play(heartbeat);
      this.activeHeartbeat = heartbeat;
      return heartbeat;
   }

   private static final class HeartbeatLoopSound implements TickableSoundInstance {
      private static final float BASE_VOLUME = 0.40F;

      private final LocalPlayer player;
      private final Identifier location;
      private Sound sound = SoundManager.EMPTY_SOUND;
      private WeighedSoundEvents soundEvent;
      private int activeHoldTicks;
      private int pulseHoldTicks;
      private int fadeTicksRemaining = -1;
      private boolean stopped;
        private float strength;

      private HeartbeatLoopSound(LocalPlayer player, SoundEvent event) {
         this.player = player;
         this.location = event.location();
      }

      private void keepAlive(float targetStrength) {
            strength = Math.max(strength - 0.025F, Math.min(strength + 0.04F, targetStrength));
         this.activeHoldTicks = ACTIVE_HOLD_TICKS;
         this.fadeTicksRemaining = -1;
      }

      private void triggerPulse() {
         this.pulseHoldTicks = Math.max(this.pulseHoldTicks, PULSE_HOLD_TICKS);
         this.fadeTicksRemaining = -1;
      }

      private void beginFadeOut() {
         if (this.fadeTicksRemaining < 0 && this.activeHoldTicks <= 0 && this.pulseHoldTicks <= 0) {
            this.fadeTicksRemaining = FADE_OUT_TICKS;
         }
      }

      private void stopImmediately() {
         this.stopped = true;
      }

      @Override
      public boolean isStopped() {
         return this.stopped || !this.player.isAlive();
      }

      @Override
      public void tick() {
         if (!this.player.isAlive()) {
            this.stopped = true;
            return;
         }

         boolean active = this.activeHoldTicks > 0 || this.pulseHoldTicks > 0;
         if (this.activeHoldTicks > 0) {
            this.activeHoldTicks--;
         }

         if (this.pulseHoldTicks > 0) {
            this.pulseHoldTicks--;
         }

         if (active) {
            this.fadeTicksRemaining = -1;
         } else if (this.fadeTicksRemaining < 0) {
            this.fadeTicksRemaining = FADE_OUT_TICKS;
         } else if (this.fadeTicksRemaining > 0) {
            this.fadeTicksRemaining--;
            if (this.fadeTicksRemaining == 0) {
               this.stopped = true;
            }
         }
      }

      @Nonnull
      @Override
      public Identifier getIdentifier() {
         return this.location;
      }

      @Nullable
      @Override
      public WeighedSoundEvents getOrResolve(@Nonnull SoundManager soundManager) {
         WeighedSoundEvents events = soundManager.getSoundEvent(this.location);
         this.soundEvent = events;
         if (events != null) {
            this.sound = events.getSound(SoundInstance.createUnseededRandom());
         }

         return events;
      }

      @Nullable
      @Override
      public WeighedSoundEvents getSoundEvent() {
         return this.soundEvent;
      }

      @Nonnull
      @Override
      public Sound getSound() {
         return this.sound;
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
            float volume = BASE_VOLUME + 0.60F * strength;
            if (activeHoldTicks > 0 || pulseHoldTicks > 0) return volume;
            if (fadeTicksRemaining < 0) return volume;
            return volume * (fadeTicksRemaining / (float) FADE_OUT_TICKS);
        }

      @Override
      public float getPitch() {
            return 0.95F + 0.25F * strength;
        }

      @Override
      public double getX() {
         return 0.0;
      }

      @Override
      public double getY() {
         return 0.0;
      }

      @Override
      public double getZ() {
         return 0.0;
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

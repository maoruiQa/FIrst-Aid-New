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
   private static final int FADE_OUT_TICKS = 100;
   private static final int ACTIVE_HOLD_TICKS = 2;
   private static final int PULSE_HOLD_TICKS = 2;
   private static final float RED_HEALTH_THRESHOLD = 0.25F;
   private static final float ACTIVE_SUPPRESSION_THRESHOLD = 0.01F;
   @Nullable
   private HeartbeatLoopSound activeHeartbeat;
   private int lastAdrenalineHeartbeatTriggerId = -1;

   public void tick(Minecraft minecraft) {
      LocalPlayer player = minecraft.player;
      if (!(Boolean)FirstAidConfig.CLIENT.enableSounds.get() || player == null || !player.isAlive() || minecraft.level == null || !FirstAid.isSynced) {
         this.clear();
         return;
      }

      PlayerDamageModel playerDamageModel = CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model ? model : null;
      boolean sustainedHeartbeat = playerDamageModel != null && (this.isCriticalPartRed(playerDamageModel) || this.isSuppressed(playerDamageModel));
      int triggerId = playerDamageModel == null ? -1 : playerDamageModel.getAdrenalineHeartbeatTriggerId();
      boolean pulseHeartbeat = triggerId != -1 && triggerId != this.lastAdrenalineHeartbeatTriggerId;
      this.lastAdrenalineHeartbeatTriggerId = triggerId;
      if (sustainedHeartbeat || pulseHeartbeat) {
         HeartbeatLoopSound heartbeat = this.ensureHeartbeat(minecraft, player);
         if (heartbeat != null) {
            if (sustainedHeartbeat) {
               heartbeat.keepAlive();
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

   public void clear() {
      if (this.activeHeartbeat != null) {
         this.activeHeartbeat.stopImmediately();
         Minecraft.getInstance().getSoundManager().stop(this.activeHeartbeat);
         this.activeHeartbeat = null;
      }

      this.lastAdrenalineHeartbeatTriggerId = -1;
   }

   private boolean isCriticalPartRed(PlayerDamageModel model) {
      return CommonUtils.getVisibleHealthRatio(model.getFromEnum(EnumPlayerPart.HEAD)) <= RED_HEALTH_THRESHOLD
         || CommonUtils.getVisibleHealthRatio(model.getFromEnum(EnumPlayerPart.BODY)) <= RED_HEALTH_THRESHOLD;
   }

   private boolean isSuppressed(PlayerDamageModel model) {
      return model.getSuppressionHoldTicks() > 0 || model.getSuppressionIntensity() > ACTIVE_SUPPRESSION_THRESHOLD;
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
      public WeighedSoundEvents resolve(@Nonnull SoundManager soundManager) {
         WeighedSoundEvents events = soundManager.getSoundEvent(this.location);
         if (events != null) {
            this.sound = events.getSound(SoundInstance.createUnseededRandom());
         }

         return events;
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
         if (this.activeHoldTicks > 0) {
            return BASE_VOLUME;
         }

         if (this.pulseHoldTicks > 0) {
            return PULSE_VOLUME;
         }

         if (this.fadeTicksRemaining < 0) {
            return 0.0F;
         }

         return BASE_VOLUME * (this.fadeTicksRemaining / (float)FADE_OUT_TICKS);
      }

      @Override
      public float getPitch() {
         return 1.0F;
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

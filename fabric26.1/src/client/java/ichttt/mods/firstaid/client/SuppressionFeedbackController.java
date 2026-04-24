package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
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
   private static final Identifier TINNITUS_SOUND = Identifier.fromNamespaceAndPath("firstaid", "debuff.tinnitus");
   private static final Identifier HEARTBEAT_SOUND = Identifier.fromNamespaceAndPath("firstaid", "debuff.heartbeat");
   private static final Set<Identifier> INTERNAL_SOUNDS = Set.of(TINNITUS_SOUND, HEARTBEAT_SOUND);
   private static final float PAIN_FOV_MAX_REDUCTION = 12.0F;
   private static final float PAIN_FOV_GAIN = 0.18F;
   private static final float PAIN_FOV_DECAY = 0.04F;
   private static final float SUPPRESSION_FOV_MIN = 30.0F;
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
   @Nullable
   private Level trackedLevel;

   public void tick(Minecraft client) {
      Player player = client.player;
      Level level = client.level;
      if (player != null && level != null && player.isAlive() && FirstAid.isSynced) {
         if (this.trackedLevel != level) {
            this.clear(level);
            this.trackedLevel = level;
         }

         PlayerDamageModel playerDamageModel = CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model ? model : null;
         int painLevel = playerDamageModel == null ? 0 : playerDamageModel.getPainLevel();
         float suppressionScale = FirstAid.lowSuppressionEnabled ? 0.4F : 1.0F;
         this.suppressionIntensity = (playerDamageModel == null ? 0.0F : playerDamageModel.getSuppressionIntensity()) * suppressionScale;
         this.holdTicks = playerDamageModel == null ? 0 : playerDamageModel.getSuppressionHoldTicks();
         boolean painSuppressed = player.hasEffect(RegistryObjects.MORPHINE_EFFECT) || player.hasEffect(RegistryObjects.PAINKILLER_EFFECT);
         float targetPainFov = !painSuppressed && playerDamageModel != null ? playerDamageModel.getPainVisualStrength() * 12.0F : 0.0F;
         boolean holding = this.holdTicks > 0;
         float targetMuffle = holding ? Math.max(0.88F, this.suppressionIntensity * 1.12F) : this.suppressionIntensity * 0.98F;
         float targetTinnitus = holding ? Math.max(0.48F, this.suppressionIntensity * 0.64F) : this.suppressionIntensity * 0.42F;
         float targetShake = holding ? 0.38F + this.suppressionIntensity * 0.55F : this.suppressionIntensity * 0.34F;
         float targetFovCompression = holding ? 4.4F + this.suppressionIntensity * 7.0F : this.suppressionIntensity * 3.6F;
         this.audioMuffleStrength = approach(this.audioMuffleStrength, targetMuffle, targetMuffle > this.audioMuffleStrength ? 0.22F : 0.025F);
         this.tinnitusStrength = approach(this.tinnitusStrength, targetTinnitus, targetTinnitus > this.tinnitusStrength ? 0.12F : 0.02F);
         this.shakeStrength = approach(this.shakeStrength, targetShake, targetShake > this.shakeStrength ? 0.1F : 0.015F);
         this.sustainedFovCompression = approach(
            this.sustainedFovCompression, targetFovCompression, targetFovCompression > this.sustainedFovCompression ? 0.16F : 0.03F
         );
         this.painFovCompression = approach(this.painFovCompression, targetPainFov, targetPainFov > this.painFovCompression ? 0.18F : 0.04F);
         this.rollImpulse *= holding ? 0.95F : 0.88F;
         this.yawImpulse *= 0.85F;
         this.pitchImpulse *= 0.85F;
         this.fovImpulse *= holding ? 0.93F : 0.83F;
         if ((Boolean)FirstAidConfig.CLIENT.enableSounds.get()
            && painLevel >= 4
            && this.lastPainLevel < 4
            && level.getGameTime() >= this.soundCooldownUntilGameTime) {
            this.soundCooldownUntilGameTime = level.getGameTime() + 60L;
            this.playTinnitusSound(0.52F + 0.12F * Math.min(2, painLevel - 4));
         }

         this.lastPainLevel = painLevel;
      } else {
         this.clear(level);
      }
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
      return this.suppressionIntensity <= 0.0F && this.shakeStrength <= 0.0F
         ? 0.0F
         : Mth.clamp(Math.max(this.suppressionIntensity, this.shakeStrength * 0.2F), 0.0F, 1.0F);
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
      this.shakeStrength = Math.max(this.shakeStrength, 0.34F + severity * 0.34F);
      this.rollImpulse += lateralSign * (1.2F + severity * 2.0F);
      this.yawImpulse += lateralSign * (0.35F + severity * 0.75F);
      this.pitchImpulse += verticalSign * (0.18F + severity * 0.42F);
      this.fovImpulse += 2.0F + severity * 4.2F;
      if ((Boolean)FirstAidConfig.CLIENT.enableSounds.get()) {
         Level level = player.level();
         if (level.getGameTime() >= this.soundCooldownUntilGameTime) {
            this.soundCooldownUntilGameTime = level.getGameTime() + Mth.ceil(8.0F + (1.0F - severity) * 12.0F);
            this.playTinnitusSound(severity);
         }
      }
   }

   public SuppressionFeedbackController.CameraAngles applyCameraAngles(@Nullable Entity entity, float partialTick, float yaw, float pitch) {
      if (this.suppressionIntensity <= 0.01F
         && this.shakeStrength <= 0.01F
         && Math.abs(this.rollImpulse) <= 0.01F
         && Math.abs(this.yawImpulse) <= 0.01F
         && Math.abs(this.pitchImpulse) <= 0.01F) {
         return new SuppressionFeedbackController.CameraAngles(yaw, pitch, 0.0F);
      } else {
         SuppressionFeedbackController.CameraCarrier cameraCarrier = new SuppressionFeedbackController.CameraCarrier(
            (double)partialTick, entity, this.suppressionIntensity, this.shakeStrength
         );
         float oscillation = cameraCarrier.oscillation();
         float newYaw = yaw + this.yawImpulse + oscillation * 0.2F;
         float newPitch = pitch + this.pitchImpulse + oscillation * 0.14F;
         float newRoll = this.rollImpulse + oscillation * 0.8F + this.suppressionIntensity * 0.55F + this.shakeStrength * 0.18F;
         return new SuppressionFeedbackController.CameraAngles(newYaw, newPitch, newRoll);
      }
   }

   public float applyFov(float baseFov) {
      if (this.suppressionIntensity <= 0.01F && this.fovImpulse <= 0.01F && this.sustainedFovCompression <= 0.01F && this.painFovCompression <= 0.01F) {
         return baseFov;
      } else {
         float suppressionTunnelVision = this.sustainedFovCompression + this.fovImpulse;
         float tunnelVision = suppressionTunnelVision + this.painFovCompression;
         float minFov = suppressionTunnelVision > 0.01F ? 30.0F : 44.0F;
         return Math.max(minFov, baseFov - tunnelVision);
      }
   }

   @Nullable
   public SoundInstance maybeMuffle(@Nullable SoundInstance original) {
      if (!(Boolean)FirstAidConfig.CLIENT.enableSounds.get()) {
         return original;
      } else if (original != null && !(this.audioMuffleStrength <= 0.01F) && !(original instanceof SuppressionFeedbackController.MuffledSoundInstance)) {
         Identifier soundId = original.getIdentifier();
         if (!INTERNAL_SOUNDS.contains(soundId) && original.getSource() != SoundSource.MASTER) {
            float volumeScale = 1.0F - this.audioMuffleStrength * 0.55F;
            float pitchScale = 1.0F - this.audioMuffleStrength * 0.32F;
            return new SuppressionFeedbackController.MuffledSoundInstance(original, volumeScale, pitchScale);
         } else {
            return original;
         }
      } else {
         return original;
      }
   }

   private void playTinnitusSound(float severity) {
      Minecraft client = Minecraft.getInstance();
      SoundManager soundManager = client.getSoundManager();
      SoundEvent tinnitus = (SoundEvent)BuiltInRegistries.SOUND_EVENT.getValue(TINNITUS_SOUND);
      if (tinnitus != null) {
         soundManager.play(SimpleSoundInstance.forUI(tinnitus, 0.18F + severity * 0.28F, 0.96F + severity * 0.08F));
      }
   }

   private void clear(@Nullable Level level) {
      this.trackedLevel = level;
      this.suppressionIntensity = 0.0F;
      this.holdTicks = 0;
      this.audioMuffleStrength = 0.0F;
      this.tinnitusStrength = 0.0F;
      this.shakeStrength = 0.0F;
      this.sustainedFovCompression = 0.0F;
      this.painFovCompression = 0.0F;
      this.rollImpulse = 0.0F;
      this.yawImpulse = 0.0F;
      this.pitchImpulse = 0.0F;
      this.fovImpulse = 0.0F;
      this.soundCooldownUntilGameTime = 0L;
      this.lastPainLevel = 0;
   }

   private static float approach(float current, float target, float delta) {
      return current < target ? Math.min(target, current + delta) : Math.max(target, current - delta);
   }

   public record CameraAngles(float yaw, float pitch, float roll) {
   }

   private record CameraCarrier(double partialTick, @Nullable Entity entity, float suppressionIntensity, float shakeStrength) {
      private float oscillation() {
         if (this.entity == null) {
            return 0.0F;
         } else {
            float time = (float)(this.entity.tickCount + this.partialTick);
            float low = (float)Math.sin(time * 0.65F);
            float high = (float)Math.sin(time * 2.75F);
            return (low * 0.35F + high * 0.65F) * (this.shakeStrength * 0.65F + this.suppressionIntensity * 0.18F);
         }
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

      public Attenuation getAttenuation() {
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

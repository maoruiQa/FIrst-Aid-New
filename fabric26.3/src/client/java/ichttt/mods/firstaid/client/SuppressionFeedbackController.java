package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.List;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class SuppressionFeedbackController {
   private static final Identifier TINNITUS_SOUND = Identifier.fromNamespaceAndPath("firstaid", "debuff.tinnitus");
   private static final Identifier HEARTBEAT_SOUND = Identifier.fromNamespaceAndPath("firstaid", "debuff.heartbeat");
   private static final Set<Identifier> INTERNAL_SOUNDS = Set.of(TINNITUS_SOUND, HEARTBEAT_SOUND);
   private static final float PAIN_FOV_MAX_REDUCTION = 12.0F;
    private static final float PAIN_FOV_HARD_MAX_REDUCTION = 22.0F;
   private static final float PAIN_FOV_GAIN = 0.22F;
   private static final float PAIN_FOV_DECAY = 0.05F;
   private static final float SUPPRESSION_FOV_MIN = 30.0F;
   private static final int SEVERE_PAIN_LEVEL = 4;
   private static final float ACUTE_TINNITUS_THRESHOLD = 0.85F;
   private static final int SEVERE_PAIN_SOUND_COOLDOWN_TICKS = 60;

   /**
    * Vanilla hallucination stingers during withdrawal (sudden, non-looping).
    * Frequency scales with addiction severity while an episode is active.
    */
   private static final List<Identifier> WITHDRAWAL_HALLUCINATION_SOUND_IDS = List.of(
           Identifier.withDefaultNamespace("block.portal.ambient"),
           Identifier.withDefaultNamespace("block.portal.trigger"),
           Identifier.withDefaultNamespace("block.portal.travel"),
           Identifier.withDefaultNamespace("entity.generic.explode"),
           Identifier.withDefaultNamespace("entity.creeper.primed"),
           Identifier.withDefaultNamespace("entity.ghast.ambient"),
           Identifier.withDefaultNamespace("entity.ghast.scream"),
           Identifier.withDefaultNamespace("entity.ghast.warn"),
           Identifier.withDefaultNamespace("entity.enderman.ambient"),
           Identifier.withDefaultNamespace("entity.enderman.scream"),
           Identifier.withDefaultNamespace("entity.enderman.stare"),
           Identifier.withDefaultNamespace("entity.enderman.teleport"),
           Identifier.withDefaultNamespace("ambient.cave"),
           Identifier.withDefaultNamespace("entity.warden.heartbeat"),
           Identifier.withDefaultNamespace("entity.warden.nearby_close"),
           Identifier.withDefaultNamespace("block.sculk_shrieker.shriek"),
           Identifier.withDefaultNamespace("entity.phantom.ambient"),
           Identifier.withDefaultNamespace("entity.phantom.swoop"),
           Identifier.withDefaultNamespace("entity.elder_guardian.curse"),
           Identifier.withDefaultNamespace("block.respawn_anchor.ambient"),
           Identifier.withDefaultNamespace("block.respawn_anchor.deplete"),
           Identifier.withDefaultNamespace("entity.vex.ambient"),
           Identifier.withDefaultNamespace("entity.vex.charge"),
           Identifier.withDefaultNamespace("entity.illusioner.mirror_move"),
           Identifier.withDefaultNamespace("entity.illusioner.cast_spell"),
           Identifier.withDefaultNamespace("entity.wither.ambient"),
           Identifier.withDefaultNamespace("entity.zombie_villager.cure"),
           Identifier.withDefaultNamespace("block.beacon.deactivate")
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
   @Nullable
   private Level trackedLevel;
   @Nullable
   private SoundInstance activeTinnitusSound;

   public void tick(Minecraft client) {
      Player player = client.player;
      Level level = client.level;
      if (player != null && level != null && player.isAlive() && FirstAid.isSynced) {
         if (this.trackedLevel != level) {
            this.clear(level);
            this.trackedLevel = level;
         }
         if (this.localMuteTicks > 0) {
            --this.localMuteTicks;
         }

         PlayerDamageModel playerDamageModel = CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model ? model : null;
         float suppressionScale = FirstAid.lowSuppressionEnabled ? FirstAid.lowSuppressionMultiplier : 1.0F;
         this.suppressionIntensity = (playerDamageModel == null ? 0.0F : playerDamageModel.getSuppressionIntensity()) * suppressionScale;
         this.holdTicks = playerDamageModel == null ? 0 : playerDamageModel.getSuppressionHoldTicks();
         boolean withdrawalActive = playerDamageModel != null && playerDamageModel.isWithdrawalEpisodeActive();
         float addictionNorm = playerDamageModel == null ? 0.0F : playerDamageModel.getAddictionNormalized();
         boolean audioMuted = this.localMuteTicks > 0 || (playerDamageModel != null && playerDamageModel.isAudioMuted());
         boolean painSuppressed = player.hasEffect(RegistryObjects.PAINKILLER_EFFECT)
            || player.hasEffect(RegistryObjects.MORPHINE_EFFECT);
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
         boolean holding = this.holdTicks > 0;
         float targetMuffle = holding ? Math.max(0.35F, this.suppressionIntensity * 0.55F) : this.suppressionIntensity * 0.42F;
         float targetMusicDetune = 0.0F;
         if (withdrawalActive && addictionNorm >= 0.35F) {
            targetMusicDetune = 0.22F + (addictionNorm - 0.35F) / 0.65F * 0.55F;
         }
         float targetTinnitus = 0.0F;
         if (!audioMuted && this.suppressionIntensity >= 0.85F) {
            targetTinnitus = 0.22F + (this.suppressionIntensity - 0.85F) / 0.15F * 0.35F;
         }
         if (withdrawalActive && !audioMuted) {
            targetTinnitus = Math.max(targetTinnitus, 0.10F + addictionNorm * 0.22F);
         }
         float targetShake = holding ? 0.12F + this.suppressionIntensity * 0.18F : this.suppressionIntensity * 0.10F;
         float targetFovCompression = holding ? 1.2F + this.suppressionIntensity * 2.0F : this.suppressionIntensity * 1.0F;
         this.audioMuffleStrength = approach(this.audioMuffleStrength, targetMuffle, targetMuffle > this.audioMuffleStrength ? 0.18F : 0.020F);
         this.musicDetuneStrength = approach(this.musicDetuneStrength, targetMusicDetune, targetMusicDetune > this.musicDetuneStrength ? 0.10F : 0.03F);
         this.tinnitusStrength = approach(this.tinnitusStrength, targetTinnitus, targetTinnitus > this.tinnitusStrength ? 0.14F : 0.06F);
         this.shakeStrength = approach(this.shakeStrength, targetShake, targetShake > this.shakeStrength ? 0.1F : 0.015F);
         this.sustainedFovCompression = approach(
            this.sustainedFovCompression, targetFovCompression, targetFovCompression > this.sustainedFovCompression ? 0.16F : 0.03F
         );
         this.painFovCompression = approach(this.painFovCompression, targetPainFov, targetPainFov > this.painFovCompression ? 0.18F : 0.04F);
         this.rollImpulse *= holding ? 0.95F : 0.88F;
         this.yawImpulse *= 0.85F;
         this.pitchImpulse *= 0.85F;
         this.fovImpulse *= holding ? 0.93F : 0.83F;
         if (!audioMuted && (Boolean)FirstAidConfig.CLIENT.enableSounds.get() && FirstAid.enablePainAudioEffects) {
            int cueId = playerDamageModel == null ? 0 : playerDamageModel.getTinnitusCueId();
            float cueSeverity = playerDamageModel == null ? 0.0F : playerDamageModel.getTinnitusCueSeverity();
            if (cueId > 0 && cueId != this.lastTinnitusCueId && cueSeverity > 0.01F) {
               this.lastTinnitusCueId = cueId;
               this.playTinnitusSound(cueSeverity);
            }
            boolean overpower = this.suppressionIntensity >= 0.85F;
            if (overpower && !this.wasOverpowerSuppression && level.getGameTime() >= this.soundCooldownUntilGameTime) {
               this.soundCooldownUntilGameTime = level.getGameTime() + 100L;
               this.playTinnitusSound(0.28F + (this.suppressionIntensity - 0.85F) * 0.35F);
            }
            this.wasOverpowerSuppression = overpower;
         }

         this.tickWithdrawalHallucinations(player, level, withdrawalActive, addictionNorm);
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

   public void beginMute(int ticks) {
      this.localMuteTicks = Math.max(this.localMuteTicks, Math.max(0, ticks));
      this.stopActiveTinnitusSound();
      this.tinnitusStrength = 0.0F;
   }

   public void onNearMiss(Player player, float severity, float lateralSign, float verticalSign) {
      // Visual/camera feedback only — near-miss no longer rings ears (issue #7).
      this.shakeStrength = Math.max(this.shakeStrength, 0.34F + severity * 0.34F);
      this.rollImpulse += lateralSign * (1.2F + severity * 2.0F);
      this.yawImpulse += lateralSign * (0.35F + severity * 0.75F);
      this.pitchImpulse += verticalSign * (0.18F + severity * 0.42F);
      this.fovImpulse += 2.0F + severity * 4.2F;
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
      if (!(Boolean)FirstAidConfig.CLIENT.enableSounds.get() || original == null
            || original instanceof SuppressionFeedbackController.MuffledSoundInstance) {
         return original;
      }
      Identifier soundId = original.getIdentifier();
      if (INTERNAL_SOUNDS.contains(soundId) || original.getSource() == SoundSource.MASTER) {
         return original;
      }
      boolean isMusic = original.getSource() == SoundSource.MUSIC || original.getSource() == SoundSource.RECORDS;
      if (isMusic) {
         if (this.musicDetuneStrength <= 0.01F) {
            return original;
         }
         float volumeScale = Mth.clamp(1.0F - this.musicDetuneStrength * 0.35F, 0.35F, 1.0F);
         float pitchScale = Mth.clamp(1.0F - this.musicDetuneStrength * 0.38F, 0.55F, 1.0F);
         return new SuppressionFeedbackController.MuffledSoundInstance(original, volumeScale, pitchScale);
      }
      if (this.audioMuffleStrength <= 0.01F) {
         return original;
      }
      float volumeScale = Mth.clamp(1.0F - this.audioMuffleStrength * 0.40F, 0.45F, 1.0F);
      return new SuppressionFeedbackController.MuffledSoundInstance(original, volumeScale, 1.0F);
   }

   private void playTinnitusSound(float severity) {
      Minecraft client = Minecraft.getInstance();
      SoundManager soundManager = client.getSoundManager();
      SoundEvent tinnitus = (SoundEvent)BuiltInRegistries.SOUND_EVENT.getValue(TINNITUS_SOUND);
      if (tinnitus == null) {
         return;
      }
      this.stopActiveTinnitusSound();
      SimpleSoundInstance instance = SimpleSoundInstance.forUI(tinnitus, 0.14F + severity * 0.22F, 0.96F + severity * 0.08F);
      this.activeTinnitusSound = instance;
      soundManager.play(instance);
   }

   private void stopActiveTinnitusSound() {
      if (this.activeTinnitusSound == null) {
         return;
      }
      Minecraft.getInstance().getSoundManager().stop(this.activeTinnitusSound);
      this.activeTinnitusSound = null;
   }

   private void tickWithdrawalHallucinations(Player player, Level level, boolean withdrawalActive, float addictionNorm) {
      if (!withdrawalActive || !(Boolean)FirstAidConfig.CLIENT.enableSounds.get()) {
         this.hallucinationCooldownTicks = Math.max(0, this.hallucinationCooldownTicks - 1);
         return;
      }
      if (this.hallucinationCooldownTicks > 0) {
         this.hallucinationCooldownTicks--;
         return;
      }
      // Check once per second. Mild addiction is rare; severe is much more common.
      if (player.tickCount % 20 != 0) {
         return;
      }
      float chance = 0.015F + addictionNorm * addictionNorm * 0.14F;
      RandomSource random = player.getRandom();
      if (random.nextFloat() > chance) {
         return;
      }
      this.playWithdrawalHallucination(player, level, addictionNorm, random);
      // Min gap: ~12s at low addiction, ~2.5s at full addiction.
      int minGap = Math.round(Mth.lerp(addictionNorm, 20 * 12, 20 * 2.5F));
      int extra = random.nextInt(Math.max(1, Math.round(Mth.lerp(addictionNorm, 20 * 10, 20 * 3))));
      this.hallucinationCooldownTicks = minGap + extra;
   }

   private void playWithdrawalHallucination(Player player, Level level, float addictionNorm, RandomSource random) {
      if (WITHDRAWAL_HALLUCINATION_SOUND_IDS.isEmpty()) {
         return;
      }
      Identifier id = WITHDRAWAL_HALLUCINATION_SOUND_IDS.get(random.nextInt(WITHDRAWAL_HALLUCINATION_SOUND_IDS.size()));
      SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(id);
      if (sound == null) {
         return;
      }
      float volume = 0.35F + random.nextFloat() * 0.45F + addictionNorm * 0.15F;
      float pitch = 0.65F + random.nextFloat() * 0.70F;
      // Slight spatial offset so it feels "beside the ear", not always centered.
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

   private void clear(@Nullable Level level) {
      this.stopActiveTinnitusSound();
      this.trackedLevel = level;
      this.suppressionIntensity = 0.0F;
      this.holdTicks = 0;
      this.audioMuffleStrength = 0.0F;
      this.musicDetuneStrength = 0.0F;
      this.tinnitusStrength = 0.0F;
      this.shakeStrength = 0.0F;
      this.sustainedFovCompression = 0.0F;
      this.painFovCompression = 0.0F;
      this.rollImpulse = 0.0F;
      this.yawImpulse = 0.0F;
      this.pitchImpulse = 0.0F;
      this.fovImpulse = 0.0F;
      this.soundCooldownUntilGameTime = 0L;
      this.lastTinnitusCueId = 0;
      this.wasOverpowerSuppression = false;
      this.localMuteTicks = 0;
      this.hallucinationCooldownTicks = 0;
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

      public WeighedSoundEvents getOrResolve(SoundManager soundManager) {
         return this.delegate.getOrResolve(soundManager);
      }

      public WeighedSoundEvents getSoundEvent() {
         return this.delegate.getSoundEvent();
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

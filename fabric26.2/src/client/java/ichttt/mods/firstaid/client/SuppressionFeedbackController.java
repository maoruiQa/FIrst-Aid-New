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
   private float painFovCompression;
   private float yawImpulse;
   private float pitchImpulse;
   private int lastTinnitusCueId;
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
         float suppressionScale = FirstAid.getSuppressionDisplayMultiplier();
         this.suppressionIntensity = (playerDamageModel == null ? 0.0F : playerDamageModel.getSuppressionIntensity()) * suppressionScale;
         this.holdTicks = playerDamageModel == null ? 0 : playerDamageModel.getSuppressionHoldTicks();
         boolean withdrawalActive = playerDamageModel != null && playerDamageModel.isWithdrawalEpisodeActive();
         float addictionNorm = playerDamageModel == null ? 0.0F : playerDamageModel.getAddictionNormalized();
         boolean audioMuted = this.localMuteTicks > 0 || (playerDamageModel != null && playerDamageModel.isAudioMuted());
         boolean painSuppressed = playerDamageModel != null && playerDamageModel.isPainSuppressed(player);
         float targetPainFov = 0.0F;
         if (playerDamageModel != null && FirstAid.enablePainFovCompression) {
            float visual = playerDamageModel.getPainFovStrength(painSuppressed);
            if (visual > 0.01F) {
               float soft = Mth.clamp(visual, 0.0F, 1.0F) * PAIN_FOV_MAX_REDUCTION;
               float hardExtra = Math.max(0.0F, visual - 1.0F) / (PlayerDamageModel.PAIN_HARD_CAP - 1.0F)
                  * (PAIN_FOV_HARD_MAX_REDUCTION - PAIN_FOV_MAX_REDUCTION);
               targetPainFov = soft + hardExtra;
            }
         }
         boolean holding = this.holdTicks > 0;
         float targetMuffle = (holding ? 0.55F : 0.42F) * this.suppressionIntensity;
         float targetMusicDetune = 0.0F;
         if (withdrawalActive && addictionNorm >= 0.35F) {
            targetMusicDetune = 0.22F + (addictionNorm - 0.35F) / 0.65F * 0.55F;
         }
         float tinnitusProgress = Mth.clamp((this.suppressionIntensity - 0.70F) / 0.30F, 0.0F, 1.0F);
        float targetTinnitus = audioMuted ? 0.0F : 0.57F * tinnitusProgress;
        if (withdrawalActive && !audioMuted) {
            targetTinnitus = Math.max(targetTinnitus, 0.10F + addictionNorm * 0.22F);
         }
         float targetShake = (holding ? 0.30F : 0.10F) * FirstAid.suppressionDisplayCurve(this.suppressionIntensity);

         this.audioMuffleStrength = approach(this.audioMuffleStrength, targetMuffle, targetMuffle > this.audioMuffleStrength ? 0.18F : 0.020F);
         this.musicDetuneStrength = approach(this.musicDetuneStrength, targetMusicDetune, targetMusicDetune > this.musicDetuneStrength ? 0.10F : 0.03F);
         this.tinnitusStrength = approach(this.tinnitusStrength, targetTinnitus, targetTinnitus > this.tinnitusStrength ? 0.14F : 0.06F);
         this.shakeStrength = approach(this.shakeStrength, targetShake, targetShake > this.shakeStrength ? 0.1F : 0.015F);
         this.painFovCompression = approach(this.painFovCompression, targetPainFov, targetPainFov > this.painFovCompression ? 0.18F : 0.04F);
         this.yawImpulse *= 0.85F;
         this.pitchImpulse *= 0.85F;
         int cueId = playerDamageModel == null ? 0 : playerDamageModel.getTinnitusCueId();
         float cueSeverity = playerDamageModel == null ? 0.0F : playerDamageModel.getTinnitusCueSeverity();
         boolean newCue = cueId > 0 && cueId != this.lastTinnitusCueId;
         this.lastTinnitusCueId = cueId;
         if (newCue && !audioMuted && FirstAidConfig.CLIENT.enableSounds.get() && FirstAid.enablePainAudioEffects) {
             this.playTinnitusSound(cueSeverity, playerDamageModel == null ? 0 : playerDamageModel.getTinnitusCueDurationTicks());
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
      return 0.0F;
   }

   public void beginMute(int ticks) {
        TinnitusSoundController.beginMute(ticks);
      this.localMuteTicks = Math.max(this.localMuteTicks, Math.max(0, ticks));
      this.stopActiveTinnitusSound();
      this.tinnitusStrength = 0.0F;
   }

   public void onNearMiss(Player player, float severity, float lateralSign, float verticalSign) {
      // Visual/camera feedback only — near-miss no longer rings ears (issue #7).
      this.shakeStrength = Math.max(this.shakeStrength, 0.34F + severity * 0.34F);
      this.yawImpulse = Mth.clamp(this.yawImpulse + lateralSign * (0.07F + severity * 0.15F), -0.22F, 0.22F);
      this.pitchImpulse = Mth.clamp(this.pitchImpulse + verticalSign * (0.018F + severity * 0.042F), -0.06F, 0.06F);
   }

   public SuppressionFeedbackController.CameraAngles applyCameraAngles(@Nullable Entity entity, float partialTick, float yaw, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        float time = (minecraft.player == null ? 0.0F : minecraft.player.tickCount) + partialTick;
        float baseYaw = this.shakeStrength * 0.28F * (float) Math.sin(time * 0.7F);
        float baseRoll = FirstAid.suppressionDisplayCurve(this.suppressionIntensity) * 1.6F * (float) Math.sin(time * 2.24F);
        float basePitch = this.shakeStrength * 0.07F * (float) Math.cos(time * 0.9F);
        return new CameraAngles(yaw + baseYaw + this.yawImpulse, pitch + basePitch + this.pitchImpulse, baseRoll);
    }

   public float applyFov(float baseFov) {
      if (painFovCompression <= 0.01F) return baseFov;
      return Math.max(44.0F, baseFov - painFovCompression);
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

   private void playTinnitusSound(float severity, int baseDurationTicks) {
        if (severity <= 0.01F) return;
      SoundEvent tinnitus = (SoundEvent)BuiltInRegistries.SOUND_EVENT.getValue(TINNITUS_SOUND);
      if (tinnitus == null) {
         return;
      }
      this.stopActiveTinnitusSound();
      this.activeTinnitusSound = TinnitusSoundController.play(tinnitus, baseDurationTicks > 0 ? 0.18F + severity * 0.12F : 0.96F + severity * 0.08F, 0.36F * severity, baseDurationTicks);
   }

   private void stopActiveTinnitusSound() {
      if (this.activeTinnitusSound == null) {
         return;
      }
      TinnitusSoundController.stop(this.activeTinnitusSound);
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
        TinnitusSoundController.clear();
      this.stopActiveTinnitusSound();
      this.trackedLevel = level;
      this.suppressionIntensity = 0.0F;
      this.holdTicks = 0;
      this.audioMuffleStrength = 0.0F;
      this.musicDetuneStrength = 0.0F;
      this.tinnitusStrength = 0.0F;
      this.shakeStrength = 0.0F;
      this.painFovCompression = 0.0F;
      this.yawImpulse = 0.0F;
      this.pitchImpulse = 0.0F;
      this.lastTinnitusCueId = 0;
      this.localMuteTicks = 0;
      this.hallucinationCooldownTicks = 0;
   }

   private static float approach(float current, float target, float delta) {
      return current < target ? Math.min(target, current + delta) : Math.max(target, current - delta);
   }

   public record CameraAngles(float yaw, float pitch, float roll) {
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

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class DebuffTimedSound implements TickableSoundInstance {
   private static final float volumeMultiplier = 1.25F;
   private static final Map<SoundEvent, DebuffTimedSound> ACTIVE_SOUNDS = new HashMap<>();
   private final float minusPerTick;
   private final int debuffDuration;
   private final Identifier soundLocation;
   private final SoundEvent event;
   private final WeakReference<LocalPlayer> player;
   private final RandomSource random = SoundInstance.createUnseededRandom();
   private Sound sound;
   private float volume = 1.25F;
   private int ticks;

   public static void playHurtSound(SoundEvent event, int duration) {
      if ((Boolean)FirstAidConfig.CLIENT.enableSounds.get()) {
         SoundManager soundHandler = Minecraft.getInstance().getSoundManager();
         DebuffTimedSound matchingSound = ACTIVE_SOUNDS.get(event);
         if (matchingSound != null) {
            if (!matchingSound.isStopped()) {
               soundHandler.stop(matchingSound);
            }

            ACTIVE_SOUNDS.remove(event);
         }

         DebuffTimedSound newSound = new DebuffTimedSound(event, duration);
         soundHandler.play(newSound);
         ACTIVE_SOUNDS.put(event, newSound);
      }
   }

   public DebuffTimedSound(SoundEvent event, int debuffDuration) {
      this.event = event;
      this.soundLocation = event.location();
      this.player = new WeakReference<>(Minecraft.getInstance().player);
      this.debuffDuration = Integer.min(300, debuffDuration);
      this.minusPerTick = 1.0F / this.debuffDuration * 1.25F;
   }

   public boolean isStopped() {
      LocalPlayer player = this.player.get();
      boolean done = player == null || this.ticks >= this.debuffDuration || player.getHealth() <= 0.0F;
      if (done) {
         ACTIVE_SOUNDS.remove(this.event);
      }

      return done;
   }

   @Nonnull
   public Identifier getIdentifier() {
      return this.soundLocation;
   }

   @Nullable
   public WeighedSoundEvents resolve(@Nonnull SoundManager handler) {
      WeighedSoundEvents soundEventAccessor = handler.getSoundEvent(this.soundLocation);
      if (soundEventAccessor == null) {
         FirstAid.LOGGER.warn("Missing sound for location " + this.soundLocation);
         this.sound = SoundManager.EMPTY_SOUND;
      } else {
         this.sound = soundEventAccessor.getSound(this.random);
      }

      return soundEventAccessor;
   }

   @Nonnull
   public Sound getSound() {
      return this.sound;
   }

   @Nonnull
   public SoundSource getSource() {
      return SoundSource.PLAYERS;
   }

   public boolean isLooping() {
      return false;
   }

   public boolean isRelative() {
      return false;
   }

   public int getDelay() {
      return 0;
   }

   public float getVolume() {
      return this.volume;
   }

   public float getPitch() {
      return 1.0F;
   }

   public double getX() {
      LocalPlayer player = this.player.get();
      return player == null ? 0.0 : player.getX();
   }

   public double getY() {
      LocalPlayer player = this.player.get();
      return player == null ? 0.0 : player.getY();
   }

   public double getZ() {
      LocalPlayer player = this.player.get();
      return player == null ? 0.0 : player.getZ();
   }

   @Nonnull
   public Attenuation getAttenuation() {
      return Attenuation.NONE;
   }

   public void tick() {
      this.ticks++;
      this.volume = Math.max(0.15F, this.volume - this.minusPerTick);
   }
}

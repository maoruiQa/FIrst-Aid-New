package ichttt.mods.firstaid.common.damagesystem.debuff;

import ichttt.mods.firstaid.FirstAid;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;

public class OnHitDebuff extends AbstractDebuff {
   @Nonnull
   private final List<OnHitDebuffEntry> timeBoundaries;
   @Nullable
   private final SoundEvent sound;

   public OnHitDebuff(@Nonnull Identifier potionName, @Nonnull List<OnHitDebuffEntry> timeBoundaries, @Nullable Identifier soundEvent) {
      super(potionName);
      this.timeBoundaries = timeBoundaries;
      this.sound = soundEvent == null ? null : Objects.requireNonNull((SoundEvent)BuiltInRegistries.SOUND_EVENT.getValue(soundEvent));
   }

   @Override
   public void handleDamageTaken(float damage, float healthFraction, ServerPlayer player) {
      FirstAid.InjuryDebuffMode mode = this.getDebuffMode();
      if (mode != FirstAid.InjuryDebuffMode.OFF) {
         int value = -1;

         for (OnHitDebuffEntry entry : this.timeBoundaries) {
            if (damage >= entry.damageTakenThreshold()) {
               value = Math.max(value, entry.effectDuration());
               int duration = entry.effectDuration();
               if (mode == FirstAid.InjuryDebuffMode.LOW) {
                  duration = this.scaleDurationForMode(duration);
               }

               player.addEffect(new MobEffectInstance(this.effect, duration, 0, false, false));
            }
         }
      }
   }

   @Override
   public void handleHealing(float healingDone, float healthFraction, ServerPlayer player) {
   }
}

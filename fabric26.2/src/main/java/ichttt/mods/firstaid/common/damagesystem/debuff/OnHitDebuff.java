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
    private final int cooldownTicks;
    private int lastTriggerTick = -1;

   public OnHitDebuff(@Nonnull Identifier potionName, @Nonnull List<OnHitDebuffEntry> timeBoundaries, @Nullable Identifier soundEvent) {
        this(potionName, timeBoundaries, soundEvent, 0);
    }

    public OnHitDebuff(@Nonnull Identifier potionName, @Nonnull List<OnHitDebuffEntry> timeBoundaries, @Nullable Identifier soundEvent, int cooldownTicks) {
      super(potionName);
      this.timeBoundaries = timeBoundaries;
      this.sound = soundEvent == null ? null : Objects.requireNonNull((SoundEvent)BuiltInRegistries.SOUND_EVENT.getValue(soundEvent));
      this.cooldownTicks = Math.max(0, cooldownTicks);
    }

   @Override
   public void handleDamageTaken(float damage, float healthFraction, ServerPlayer player) {
        if (player == null || !Float.isFinite(damage) || damage <= 0.0F) return;
        FirstAid.InjuryDebuffMode mode = getDebuffMode();
        if (mode == FirstAid.InjuryDebuffMode.OFF) return;
        int value = -1;
        for (OnHitDebuffEntry entry : timeBoundaries) {
            if (damage >= entry.damageTakenThreshold() || (entry.healthFractionThreshold() > 0.0F
                    && healthFraction >= 0.0F && healthFraction <= entry.healthFractionThreshold())) {
                value = Math.max(value, entry.effectDuration());
            }
        }
        if (value <= 0 || (cooldownTicks > 0 && lastTriggerTick >= 0 && player.tickCount >= lastTriggerTick
                && (long) player.tickCount - lastTriggerTick < cooldownTicks)) return;
        int duration = (mode == FirstAid.InjuryDebuffMode.LOW ? scaleDurationForMode(value) : value);
        this.applyInjuryEffect(player, new MobEffectInstance(effect, duration, 0, false, false));
        lastTriggerTick = player.tickCount;
    }

   @Override
   public void handleHealing(float healingDone, float healthFraction, ServerPlayer player) {
   }
}

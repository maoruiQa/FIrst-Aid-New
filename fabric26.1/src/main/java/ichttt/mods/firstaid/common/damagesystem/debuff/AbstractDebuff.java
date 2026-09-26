package ichttt.mods.firstaid.common.damagesystem.debuff;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import java.util.Objects;
import javax.annotation.Nonnull;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

public abstract class AbstractDebuff implements IDebuff {
   @Nonnull
   public final Holder<MobEffect> effect;
   @Nonnull
   private final Identifier effectId;

   public AbstractDebuff(@Nonnull Identifier potionName) {
      this.effectId = Objects.requireNonNull(potionName);
      this.effect = BuiltInRegistries.MOB_EFFECT.wrapAsHolder((MobEffect)Objects.requireNonNull((MobEffect)BuiltInRegistries.MOB_EFFECT.getValue(potionName)));
   }

   protected final FirstAid.InjuryDebuffMode getDebuffMode() {
      return FirstAid.getInjuryDebuffMode(this.effectId);
   }

   protected final int scaleAmplifierForMode(int amplifier) {
      return amplifier <= 0 ? amplifier : Math.max(0, Math.round(amplifier * FirstAid.lowInjuryDebuffAmplifierScale));
   }

   protected final int scaleDurationForMode(int duration) {
      return duration <= 1 ? duration : Math.max(1, Math.round(duration * FirstAid.lowInjuryDebuffDurationScale));
   }

    protected final void applyInjuryEffect(Player player, MobEffectInstance instance) {
        if (CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model) {
            model.applyTrackedInjuryEffect(player, this.effectId.toString(), instance);
        }
    }
}

package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.common.damagesystem.distribution.HealthDistribution;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.Arrays;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityHealMixin {
   @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
   private void firstaid$heal(float amount, CallbackInfo ci) {
      LivingEntity entity = (LivingEntity)this;
      if (entity instanceof Player player) {
         if (!entity.isDeadOrDying() && CommonUtils.hasDamageModel(entity)) {
            ci.cancel();
            if (!entity.level().isClientSide() && FirstAidConfig.SERVER.allowOtherHealingItems.get()) {
               boolean fromFood = Arrays.stream(Thread.currentThread().getStackTrace())
                  .anyMatch(stackTraceElement -> stackTraceElement.getClassName().equals(FoodData.class.getName()));
               float adjusted;
               if (fromFood) {
                  if (!FirstAidConfig.SERVER.allowNaturalRegeneration.get()) {
                     return;
                  }

                  adjusted = amount * (float)FirstAidConfig.SERVER.naturalRegenMultiplier.get().doubleValue();
               } else {
                  adjusted = amount * (float)FirstAidConfig.SERVER.otherRegenMultiplier.get().doubleValue();
               }

               if (FirstAidConfig.GENERAL.debug.get()) {
                  CommonUtils.debugLogStacktrace("External healing: : " + adjusted);
               }

               HealthDistribution.distributeHealth(adjusted, player, true);
            }
         }
      }
   }
}

package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.common.damagesystem.distribution.HealthDistribution;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(LivingEntity.class)
public abstract class LivingEntityHealMixin {
    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void firstaid$heal(float amount, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof Player player)) {
            return;
        }
        if (entity.isDeadOrDying() || !CommonUtils.hasDamageModel(entity)) {
            return;
        }
        ci.cancel();
        if (entity.level().isClientSide() || !FirstAidConfig.SERVER.allowOtherHealingItems.get()) {
            return;
        }

        float adjusted = amount;
        boolean fromFood = Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(stackTraceElement -> stackTraceElement.getClassName().equals(FoodData.class.getName()));
        if (fromFood) {
            adjusted = adjusted * (float) (double) FirstAidConfig.SERVER.naturalRegenMultiplier.get();
        } else {
            adjusted = adjusted * (float) (double) FirstAidConfig.SERVER.otherRegenMultiplier.get();
        }
        if (FirstAidConfig.GENERAL.debug.get()) {
            CommonUtils.debugLogStacktrace("External healing: : " + adjusted);
        }
        if (fromFood) {
            HealthDistribution.applyNaturalRegen(adjusted, player, true);
        } else {
            HealthDistribution.distributeHealth(adjusted, player, true);
        }
    }
}

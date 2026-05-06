/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.food.FoodData
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.common.damagesystem.distribution.HealthDistribution;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.Arrays;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={LivingEntity.class})
public abstract class LivingEntityHealMixin {
    @Inject(method={"heal"}, at={@At(value="HEAD")}, cancellable=true)
    private void firstaid$heal(float amount, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity)this;
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        if (entity.isDeadOrDying() || !CommonUtils.hasDamageModel((Entity)entity)) {
            return;
        }
        ci.cancel();
        if (entity.level().isClientSide() || !FirstAidConfig.SERVER.allowOtherHealingItems.get().booleanValue()) {
            return;
        }
        float adjusted = amount;
        boolean fromFood = Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(stackTraceElement -> stackTraceElement.getClassName().equals(FoodData.class.getName()));
        if (fromFood) {
            if (!FirstAidConfig.SERVER.allowNaturalRegeneration.get().booleanValue()) {
                return;
            }
            adjusted *= (float)FirstAidConfig.SERVER.naturalRegenMultiplier.get().doubleValue();
        } else {
            adjusted *= (float)FirstAidConfig.SERVER.otherRegenMultiplier.get().doubleValue();
        }
        if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
            CommonUtils.debugLogStacktrace("External healing: : " + adjusted);
        }
        HealthDistribution.distributeHealth(adjusted, player, true);
    }
}


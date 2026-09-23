/*
 * FirstAid
 * Copyright (C) 2017-2024
 */

package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2: MilkBucketItem class was removed; hook completeUsingItem and detect milk by item id.
 * Clears FirstAid morphine ticks / pending so status cannot stick after milk.
 */
@Mixin(LivingEntity.class)
public class MilkBucketItemMixin {
    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void firstaid$clearPainSuppressantsAfterMilk(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() || !(self instanceof Player player)) {
            return;
        }
        // At HEAD the use item is still the milk bucket.
        if (!self.getUseItem().is(Items.MILK_BUCKET)) {
            return;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel instanceof PlayerDamageModel playerDamageModel) {
            playerDamageModel.clearPainSuppressants(player);
        }
    }

    @Inject(method = "completeUsingItem", at = @At("RETURN"))
    private void firstaid$clearPainSuppressantsAfterMilkReturn(CallbackInfo ci) {
        // RETURN safety net: some paths clear use item before HEAD can observe milk.
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() || !(self instanceof Player player)) {
            return;
        }
        // Only act if model still thinks morphine is pending/active (avoid clearing unrelated finishes).
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (!(damageModel instanceof PlayerDamageModel playerDamageModel)) {
            return;
        }
        if (playerDamageModel.getMorphineTicks() > 0 || player.hasEffect(ichttt.mods.firstaid.common.RegistryObjects.MORPHINE_EFFECT)) {
            // If milk just ran, vanilla already wiped effects; keep model aligned.
            // Without a reliable milk flag at RETURN we only re-clear when effect is gone but ticks remain.
            if (!player.hasEffect(ichttt.mods.firstaid.common.RegistryObjects.MORPHINE_EFFECT)
                    && playerDamageModel.getMorphineTicks() > 0) {
                playerDamageModel.clearPainSuppressants(player);
            }
        }
    }
}

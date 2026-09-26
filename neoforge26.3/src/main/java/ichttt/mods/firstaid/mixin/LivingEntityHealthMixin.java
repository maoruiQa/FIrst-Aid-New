package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityHealthMixin {

    @Inject(method = "getAbsorptionAmount", at = @At("HEAD"), cancellable = true)
    private void firstaid$getAbsorptionAmount(CallbackInfoReturnable<Float> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof Player player)) {
            return;
        }
        if (CommonUtils.isVanillaAbsorptionSuppressed()) {
            return;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel != null) {
            Float absorption = damageModel.getAbsorption();
            if (absorption != null) {
                cir.setReturnValue(absorption);
            }
        }
    }

    @Inject(method = "setAbsorptionAmount", at = @At("HEAD"))
    private void firstaid$setAbsorptionAmount(float absorptionAmount, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof Player player) || entity.level().isClientSide()) {
            return;
        }
        if (CommonUtils.isVanillaAbsorptionSuppressed()) {
            return;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) {
            return;
        }

        damageModel.setAbsorption(absorptionAmount);
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            CommonUtils.syncDamageModel(serverPlayer);
        }
    }
}

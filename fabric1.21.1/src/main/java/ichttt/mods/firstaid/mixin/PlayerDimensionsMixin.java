package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PlayerDimensionsMixin {
    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void firstaid$getDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (!((Object) this instanceof Player player)) {
            return;
        }
        if (player.isPassenger()) {
            return;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getExistingDamageModel(player);
        if (damageModel instanceof PlayerDamageModel playerDamageModel && playerDamageModel.isUnconscious()) {
            cir.setReturnValue(PlayerDamageModel.getUnconsciousDimensions(playerDamageModel.shouldUseCrampedUnconsciousDimensions(player)));
        }
    }
}

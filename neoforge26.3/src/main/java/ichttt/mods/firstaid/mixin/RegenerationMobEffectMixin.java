package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.common.damagesystem.distribution.HealthDistribution;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.effect.RegenerationMobEffect")
public abstract class RegenerationMobEffectMixin {
    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void firstaid$applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player player) || !CommonUtils.hasDamageModel(player)) {
            return;
        }
        cir.setReturnValue(true);
        if (!player.level().isClientSide() && !player.isDeadOrDying() && FirstAidConfig.SERVER.allowOtherHealingItems.get()) {
            HealthDistribution.addRandomHealth((float) (double) FirstAidConfig.SERVER.otherRegenMultiplier.get(), player, true);
        }
    }
}

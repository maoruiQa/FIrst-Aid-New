package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class LivingEntityDamageMixin {

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void firstaid$hurtServer(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        CommonUtils.pushActiveDamageSource(source);
        Player player = (Player) (Object) this;
        Boolean result = EventHandler.preHandleCustomPlayerDamage(player, source, amount);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void firstaid$hurtServerReturn(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        CommonUtils.popActiveDamageSource();
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
    private void firstaid$actuallyHurt(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (EventHandler.handleCustomPlayerDamage(player, source, amount)) {
            ci.cancel();
        }
    }
}

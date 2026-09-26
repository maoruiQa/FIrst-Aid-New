package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.common.potion.PotionPoisonPatched;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.effect.PoisonMobEffect")
public abstract class PoisonMobEffectMixin {
    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void firstaid$applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = PotionPoisonPatched.applyFirstAidTick(level, entity);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}

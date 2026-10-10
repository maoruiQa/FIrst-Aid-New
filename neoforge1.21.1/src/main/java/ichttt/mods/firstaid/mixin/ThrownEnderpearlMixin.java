package ichttt.mods.firstaid.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ThrownEnderpearl.class)
public abstract class ThrownEnderpearlMixin {
    @ModifyArg(method = "onHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), index = 0)
    private DamageSource firstaid$pearlDamage(DamageSource source) {
        // Older versions share fall damage with pearls; preserve its modifiers while marking the projectile.
        return new DamageSource(source.typeHolder(), (Entity) (Object) this, source.getEntity());
    }
}

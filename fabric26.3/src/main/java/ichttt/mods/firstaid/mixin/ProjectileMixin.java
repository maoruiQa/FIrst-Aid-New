package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.common.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {
   @Inject(method = "onHit", at = @At("HEAD"))
   private void firstaid$onHit(HitResult result, CallbackInfo ci) {
      if (result.getType() == Type.ENTITY) {
         Entity entity = ((EntityHitResult)result).getEntity();
         if (!entity.level().isClientSide() && entity instanceof Player player) {
            EventHandler.recordProjectileHit(player, (Projectile)(Object)this, result.getLocation());
         }
      }
   }
}

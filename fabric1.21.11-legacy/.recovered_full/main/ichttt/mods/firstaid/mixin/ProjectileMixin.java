/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.entity.projectile.Projectile
 *  net.minecraft.world.phys.EntityHitResult
 *  net.minecraft.world.phys.HitResult
 *  net.minecraft.world.phys.HitResult$Type
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.common.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Projectile.class})
public abstract class ProjectileMixin {
    @Inject(method={"onHit"}, at={@At(value="HEAD")})
    private void firstaid$onHit(HitResult result, CallbackInfo ci) {
        if (result.getType() != HitResult.Type.ENTITY) {
            return;
        }
        Entity entity = ((EntityHitResult)result).getEntity();
        if (entity.level().isClientSide() || !(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        EventHandler.recordProjectileHit(player, (Entity)((Projectile)this), result);
    }
}


/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.EntityDimensions
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.Pose
 *  net.minecraft.world.entity.player.Player
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
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

@Mixin(value={LivingEntity.class})
public abstract class PlayerDimensionsMixin {
    @Inject(method={"getDimensions"}, at={@At(value="HEAD")}, cancellable=true)
    private void firstaid$getDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        PlayerDamageModel playerDamageModel;
        PlayerDimensionsMixin playerDimensionsMixin = this;
        if (!(playerDimensionsMixin instanceof Player)) {
            return;
        }
        Player player = (Player)playerDimensionsMixin;
        if (player.isPassenger()) {
            return;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getExistingDamageModel(player);
        if (damageModel instanceof PlayerDamageModel && (playerDamageModel = (PlayerDamageModel)damageModel).isUnconscious()) {
            cir.setReturnValue((Object)PlayerDamageModel.getUnconsciousDimensions(playerDamageModel.shouldUseCrampedUnconsciousDimensions(player)));
        }
    }
}


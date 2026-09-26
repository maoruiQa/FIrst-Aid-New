/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Speeds crawl limb animation while downed so limb cadence matches the reduced crawl speed.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    private static final float CRAWL_ANIM_SWING_MULT = 2.85F;
    private static final float CRAWL_ANIM_AMOUNT_MULT = 2.50F;
    private static final float CRAWL_ANIM_AMOUNT_MIN = 0.45F;

    @ModifyVariable(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private float firstaid$boostCrawlLimbSwing(float limbSwing, LivingEntity entity) {
        return isDownedCrawlAnimating(entity) ? limbSwing * CRAWL_ANIM_SWING_MULT : limbSwing;
    }

    @ModifyVariable(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1
    )
    private float firstaid$boostCrawlLimbSwingAmount(float limbSwingAmount, LivingEntity entity) {
        if (!isDownedCrawlAnimating(entity)) {
            return limbSwingAmount;
        }
        if (limbSwingAmount <= 0.01F) {
            // Still advance a visible crawl when input is forward but walk amount is tiny.
            return entity instanceof Player player && isTryingToCrawlForward(player)
                    ? CRAWL_ANIM_AMOUNT_MIN
                    : limbSwingAmount;
        }
        return Mth.clamp(Math.max(limbSwingAmount * CRAWL_ANIM_AMOUNT_MULT, CRAWL_ANIM_AMOUNT_MIN), 0.0F, 1.0F);
    }

    private static boolean isDownedCrawlAnimating(LivingEntity entity) {
        if (!(entity instanceof Player player) || !player.isAlive()) {
            return false;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        return damageModel instanceof PlayerDamageModel model && model.isUnconscious();
    }

    private static boolean isTryingToCrawlForward(Player player) {
        return player.zza > 0.01F
                || player.getDeltaMovement().horizontalDistanceSqr() > 1.0E-5D;
    }
}

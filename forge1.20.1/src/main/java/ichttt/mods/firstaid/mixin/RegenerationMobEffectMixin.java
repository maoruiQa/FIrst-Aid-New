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

package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.common.damagesystem.distribution.HealthDistribution;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.effect.RegenerationMobEffect")
public abstract class RegenerationMobEffectMixin {
    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void firstaid$applyEffectTick(LivingEntity entity, int amplifier, CallbackInfo ci) {
        if (!(entity instanceof Player player) || !CommonUtils.hasDamageModel(player)) {
            return;
        }
        ci.cancel();
        if (!player.level().isClientSide() && !player.isDeadOrDying() && FirstAidConfig.SERVER.allowOtherHealingItems.get()) {
            HealthDistribution.addRandomHealth((float) (double) FirstAidConfig.SERVER.otherRegenMultiplier.get(), player, true);
        }
    }
}

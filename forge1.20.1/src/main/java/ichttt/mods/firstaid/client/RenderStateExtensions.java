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

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class RenderStateExtensions {
    private RenderStateExtensions() {
    }

    public static boolean shouldApplyUnconsciousAttributes(LivingEntity entity) {
        if (entity == null || entity.isPassenger()) {
            return false;
        }
        if (!(entity instanceof Player player)) {
            return false;
        }
        return CommonUtils.getExistingDamageModel(player) instanceof PlayerDamageModel playerDamageModel
                && playerDamageModel.isUnconscious();
    }

    public static float getCollapseProgress(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return 1.0F;
        }
        return CommonUtils.getExistingDamageModel(player) instanceof PlayerDamageModel playerDamageModel
                ? playerDamageModel.getCollapseAnimationProgress()
                : 1.0F;
    }
}

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

package ichttt.mods.firstaid.common.apiimpl;

import ichttt.mods.firstaid.api.healing.HealingItemApiHelper;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import ichttt.mods.firstaid.client.ClientHooks;

public class HealingItemApiHelperImpl extends HealingItemApiHelper {
    private static final HealingItemApiHelperImpl INSTANCE = new HealingItemApiHelperImpl();

    public static void init() {
        HealingItemApiHelper.setImpl(INSTANCE);
    }

    @Nonnull
    @Override
    public InteractionResult onItemRightClick(ItemHealing itemHealing, Level world, Player player, InteractionHand hand) {
        if (world.isClientSide()) {
            boolean consumed = ClientHooks.beginApplyHealthUse(hand) || ClientHooks.showGuiApplyHealth(hand);
            return consumed ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }
}


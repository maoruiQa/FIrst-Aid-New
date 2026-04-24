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

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.healing.HealingItemApiHelper;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.client.ClientHooks;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nonnull;

public class HealingItemApiHelperImpl extends HealingItemApiHelper {
    private static final HealingItemApiHelperImpl INSTANCE = new HealingItemApiHelperImpl();

    public static void init() {
        HealingItemApiHelper.setImpl(INSTANCE);
    }

    @Nonnull
    @Override
    public InteractionResultHolder<ItemStack> onItemRightClick(ItemHealing itemHealing, Level world, Player player, InteractionHand hand) {
        if (world.isClientSide) {
            boolean[] consumed = new boolean[1];
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> consumed[0] = ClientHooks.beginApplyHealthUse(hand) || ClientHooks.showGuiApplyHealth(hand));
            return new InteractionResultHolder<>(consumed[0] ? InteractionResult.SUCCESS : InteractionResult.FAIL, player.getItemInHand(hand));
        }
        return new InteractionResultHolder<>(canUseHealingItem(player) ? InteractionResult.SUCCESS : InteractionResult.FAIL, player.getItemInHand(hand));
    }

    private static boolean canUseHealingItem(Player player) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) {
            return false;
        }

        for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
            var damageablePart = damageModel.getFromEnum(part);
            if (damageablePart.activeHealer == null && !CommonUtils.isPartVisuallyFull(damageablePart)) {
                return true;
            }
        }

        return false;
    }
}

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

package ichttt.mods.firstaid.common.items;

import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;

public class ItemPainkillers extends Item {

    public ItemPainkillers(Item.Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    @Nonnull
    public ItemStack finishUsingItem(@Nonnull ItemStack stack, @Nonnull Level world, @Nonnull LivingEntity entityLiving) {
        if (entityLiving instanceof Player player) {
            if (CommonUtils.getDamageModel(player) instanceof PlayerDamageModel playerDamageModel) {
                playerDamageModel.queuePainkillerActivation();
            } else {
                player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT, PlayerDamageModel.getPainkillerDuration(), 0, false, false));
            }
        }
        if (!(entityLiving instanceof Player) || !((Player) entityLiving).getAbilities().instabuild) {
            stack.shrink(1);
        }

        return stack;
    }

    @Override
    @Nonnull
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.EAT;
    }

    @Override
    @Nonnull
    public InteractionResult use(Level world, Player player, @Nonnull InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }
}

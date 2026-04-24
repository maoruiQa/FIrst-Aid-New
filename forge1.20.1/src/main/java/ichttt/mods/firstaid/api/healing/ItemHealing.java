/*
 * FirstAid API
 * Copyright (c) 2017-2024
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

package ichttt.mods.firstaid.api.healing;

import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Base class for custom healing items. Handles the logic for healing automatically.
 * Registry name, stack size and other behavior can be specified individually.
 * Will automatically register this item to the registry as well
 */
public abstract class ItemHealing extends Item {

    /**
     * Creates a new healing item, and registers it to the registry.
     * @param time The time it takes in the GUI in ms
     * @param healerFunction The function to create a new healer from the GUI
     */
    public static ItemHealing create(Item.Properties builder, Function<ItemStack, AbstractPartHealer> healerFunction, Function<ItemStack, Integer> time) {
        return create(builder, healerFunction, time, stack -> null, stack -> ApplySoundMode.ON_COMPLETE);
    }

    public static ItemHealing create(
            Item.Properties builder,
            Function<ItemStack, AbstractPartHealer> healerFunction,
            Function<ItemStack, Integer> time,
            Function<ItemStack, SoundEvent> applySoundEventFunction,
            Function<ItemStack, ApplySoundMode> applySoundModeFunction
    ) {
        return new ItemHealing(builder, healerFunction, time) {
            @Override
            public AbstractPartHealer createNewHealer(ItemStack stack) {
                return healerFunction.apply(stack);
            }

            @Override
            public int getApplyTime(ItemStack stack) {
                return time.apply(stack);
            }

            @Nullable
            @Override
            public SoundEvent getApplySoundEvent(ItemStack stack) {
                return applySoundEventFunction.apply(stack);
            }

            @Override
            public ApplySoundMode getApplySoundMode(ItemStack stack) {
                return applySoundModeFunction.apply(stack);
            }
        };
    }


    protected ItemHealing(Item.Properties builder, Function<ItemStack, AbstractPartHealer> healerFunction, Function<ItemStack, Integer> time) {
        super(builder);
    }

    @Nonnull
    @Override
    public InteractionResultHolder<ItemStack> use(Level worldIn, Player playerIn, @Nonnull InteractionHand handIn) {
        return HealingItemApiHelper.INSTANCE.onItemRightClick(this, worldIn, playerIn, handIn);
    }

    @Nonnull
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Nullable
    public SoundEvent getApplySoundEvent(ItemStack stack) {
        return null;
    }

    public ApplySoundMode getApplySoundMode(ItemStack stack) {
        return ApplySoundMode.ON_COMPLETE;
    }

    public abstract AbstractPartHealer createNewHealer(ItemStack stack);

    public abstract int getApplyTime(ItemStack stack);

    public void onTreatmentStarted(PartHealingContext context) {
    }

    public void onHealPulse(PartHealingContext context) {
    }

    public void onTreatmentCompleted(PartHealingContext context) {
    }

    public enum ApplySoundMode {
        WHILE_USING,
        ON_COMPLETE
    }
}



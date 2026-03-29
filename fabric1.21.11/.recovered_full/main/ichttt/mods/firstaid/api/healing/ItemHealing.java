/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.Level
 */
package ichttt.mods.firstaid.api.healing;

import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.healing.HealingItemApiHelper;
import java.util.function.Function;
import javax.annotation.Nonnull;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public abstract class ItemHealing
extends Item {
    public static ItemHealing create(Item.Properties builder, final Function<ItemStack, AbstractPartHealer> healerFunction, final Function<ItemStack, Integer> time) {
        return new ItemHealing(builder, healerFunction, time){

            @Override
            public AbstractPartHealer createNewHealer(ItemStack stack) {
                return (AbstractPartHealer)healerFunction.apply(stack);
            }

            @Override
            public int getApplyTime(ItemStack stack) {
                return (Integer)time.apply(stack);
            }
        };
    }

    protected ItemHealing(Item.Properties builder, Function<ItemStack, AbstractPartHealer> healerFunction, Function<ItemStack, Integer> time) {
        super(builder);
    }

    @Nonnull
    public InteractionResult use(Level worldIn, Player playerIn, @Nonnull InteractionHand handIn) {
        return HealingItemApiHelper.INSTANCE.onItemRightClick(this, worldIn, playerIn, handIn);
    }

    public abstract AbstractPartHealer createNewHealer(ItemStack var1);

    public abstract int getApplyTime(ItemStack var1);
}


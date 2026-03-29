/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.effect.MobEffectInstance
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.ItemUseAnimation
 *  net.minecraft.world.level.Level
 */
package ichttt.mods.firstaid.common.items;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import javax.annotation.Nonnull;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

public class ItemPainkillers
extends Item {
    public ItemPainkillers(Item.Properties properties) {
        super(properties.stacksTo(16));
    }

    @Nonnull
    public ItemStack finishUsingItem(@Nonnull ItemStack stack, @Nonnull Level world, @Nonnull LivingEntity entityLiving) {
        if (entityLiving instanceof Player) {
            Player player = (Player)entityLiving;
            AbstractPlayerDamageModel abstractPlayerDamageModel = CommonUtils.getDamageModel(player);
            if (abstractPlayerDamageModel instanceof PlayerDamageModel) {
                PlayerDamageModel playerDamageModel = (PlayerDamageModel)abstractPlayerDamageModel;
                playerDamageModel.queuePainkillerActivation();
            } else {
                player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT, PlayerDamageModel.getPainkillerDuration(), 0, false, false));
            }
        }
        stack.shrink(1);
        return stack;
    }

    @Nonnull
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.EAT;
    }

    @Nonnull
    public InteractionResult use(Level world, Player player, @Nonnull InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.SUCCESS;
    }

    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }
}


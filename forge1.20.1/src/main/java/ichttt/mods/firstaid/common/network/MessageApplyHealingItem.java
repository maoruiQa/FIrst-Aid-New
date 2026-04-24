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

package ichttt.mods.firstaid.common.network;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class MessageApplyHealingItem {
    private final EnumPlayerPart part;
    private final InteractionHand hand;

    public MessageApplyHealingItem(FriendlyByteBuf buffer) {
        this.part = EnumPlayerPart.VALUES[buffer.readByte()];
        this.hand = buffer.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public MessageApplyHealingItem(EnumPlayerPart part, InteractionHand hand) {
        this.part = part;
        this.hand = hand;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(part.ordinal());
        buf.writeBoolean(hand == InteractionHand.MAIN_HAND);
    }

    public static class Handler {

        public static void onMessage(final MessageApplyHealingItem message, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context ctx = supplier.get();
            ServerPlayer player = CommonUtils.checkServer(ctx);
            ctx.enqueueWork(() -> {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
                if (damageModel == null) return;
                ItemStack stack = player.getItemInHand(message.hand);
                if (stack.isEmpty() || stack.getCount() < 1) {
                    return;
                }

                ItemStack healerStack = stack.copy();
                healerStack.setCount(1);
                AbstractPartHealer healer = null;
                if (healerStack.getItem() instanceof ItemHealing itemHealing) {
                    healer = itemHealing.createNewHealer(healerStack);
                }
                if (healer == null) {
                    FirstAid.LOGGER.warn(LoggingMarkers.NETWORK, "Player {} has invalid item in hand {} while it should be an healing item", player.getName(), ForgeRegistries.ITEMS.getKey(stack.getItem()));
                    player.sendSystemMessage(Component.literal("Unable to apply healing item!"));
                    return;
                }
                AbstractDamageablePart damageablePart = damageModel.getFromEnum(message.part);
                if (damageablePart.activeHealer != null || CommonUtils.isPartVisuallyFull(damageablePart)) {
                    return;
                }

                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                damageablePart.activeHealer = healer;
                damageModel.scheduleResync();
                CommonUtils.syncDamageModel(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
            });
        }
    }
}

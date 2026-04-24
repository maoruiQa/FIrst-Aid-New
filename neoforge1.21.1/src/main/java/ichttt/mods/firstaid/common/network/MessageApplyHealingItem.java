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
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.api.healing.PartHealingContext;
import ichttt.mods.firstaid.common.init.FirstAidDataAttachments;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class MessageApplyHealingItem implements CustomPacketPayload {
    public static final Type<MessageApplyHealingItem> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "apply_healing_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageApplyHealingItem> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE,
            message -> (byte) message.part.ordinal(),
            ByteBufCodecs.BOOL,
            message -> message.hand == InteractionHand.MAIN_HAND,
            (partOrdinal, mainHand) -> new MessageApplyHealingItem(EnumPlayerPart.VALUES[partOrdinal], mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND));

    private final EnumPlayerPart part;
    private final InteractionHand hand;

    public MessageApplyHealingItem(EnumPlayerPart part, InteractionHand hand) {
        this.part = part;
        this.hand = hand;
    }

    @Override
    public Type<MessageApplyHealingItem> type() {
        return TYPE;
    }

    public static class Handler {

        public static void onMessage(final MessageApplyHealingItem message, IPayloadContext context) {
            ServerPlayer player = (ServerPlayer) context.player();
            context.enqueueWork(() -> {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
                if (damageModel == null) {
                    return;
                }

                ItemStack stack = player.getItemInHand(message.hand);
                if (stack.isEmpty() || stack.getCount() < 1) {
                    return;
                }

                ItemStack healerStack = stack.copyWithCount(1);
                ItemHealing itemHealing = healerStack.getItem() instanceof ItemHealing healingItem ? healingItem : null;
                AbstractPartHealer healer = itemHealing == null ? null : itemHealing.createNewHealer(healerStack);

                if (healer == null) {
                    FirstAid.LOGGER.warn(LoggingMarkers.NETWORK, "Player {} has invalid item in hand {} while it should be an healing item", player.getName(), BuiltInRegistries.ITEM.getKey(stack.getItem()));
                    player.sendSystemMessage(Component.literal("Unable to apply healing item!"));
                    return;
                }

                AbstractDamageablePart damageablePart = damageModel.getFromEnum(message.part);
                if (damageablePart.activeHealer != null || CommonUtils.isPartVisuallyFull(damageablePart) || stack.getCount() < 1) {
                    return;
                }

                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }

                damageablePart.activeHealer = healer;
                itemHealing.onTreatmentStarted(new PartHealingContext(player, player.level(), healerStack, damageModel, damageablePart, healer));
                damageModel.scheduleResync();
                CommonUtils.syncDamageModel(player);
            });
        }
    }
}



/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$Context
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.network.RegistryFriendlyByteBuf
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.codec.ByteBufCodecs
 *  net.minecraft.network.codec.StreamCodec
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 *  net.minecraft.resources.Identifier
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 */
package ichttt.mods.firstaid.common.network;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class MessageApplyHealingItem
implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MessageApplyHealingItem> TYPE = new CustomPacketPayload.Type(Identifier.fromNamespaceAndPath((String)"firstaid", (String)"apply_healing_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageApplyHealingItem> STREAM_CODEC = StreamCodec.composite((StreamCodec)ByteBufCodecs.BYTE, message -> (byte)message.part.ordinal(), (StreamCodec)ByteBufCodecs.BOOL, message -> message.hand == InteractionHand.MAIN_HAND, (partOrdinal, mainHand) -> new MessageApplyHealingItem(EnumPlayerPart.VALUES[partOrdinal], mainHand != false ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND));
    private final EnumPlayerPart part;
    private final InteractionHand hand;

    public MessageApplyHealingItem(EnumPlayerPart part, InteractionHand hand) {
        this.part = part;
        this.hand = hand;
    }

    public CustomPacketPayload.Type<MessageApplyHealingItem> type() {
        return TYPE;
    }

    public static void handle(MessageApplyHealingItem message, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel((Player)player);
            if (damageModel == null) {
                return;
            }
            ItemStack stack = player.getItemInHand(message.hand);
            AbstractPartHealer healer = null;
            Item patt0$temp = stack.getItem();
            if (patt0$temp instanceof ItemHealing) {
                ItemHealing itemHealing = (ItemHealing)patt0$temp;
                healer = itemHealing.createNewHealer(stack);
            }
            if (healer == null) {
                FirstAid.LOGGER.warn(LoggingMarkers.NETWORK, "Player {} has invalid item in hand {} while it should be an healing item", (Object)player.getName(), (Object)BuiltInRegistries.ITEM.getKey((Object)stack.getItem()));
                player.sendSystemMessage((Component)Component.literal((String)"Unable to apply healing item!"));
                return;
            }
            AbstractDamageablePart damageablePart = damageModel.getFromEnum(message.part);
            if (damageablePart.activeHealer != null || damageablePart.currentHealth >= (float)damageablePart.getMaxHealth()) {
                return;
            }
            stack.shrink(1);
            damageablePart.activeHealer = healer;
            damageModel.scheduleResync();
            FirstAidNetworking.sendDamageModelSync(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
        });
    }
}


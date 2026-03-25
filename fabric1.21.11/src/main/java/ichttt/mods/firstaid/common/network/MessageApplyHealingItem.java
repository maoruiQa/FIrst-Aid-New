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
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class MessageApplyHealingItem implements CustomPacketPayload {
   public static final Type<MessageApplyHealingItem> TYPE = new Type(Identifier.fromNamespaceAndPath("firstaid", "apply_healing_item"));
   public static final StreamCodec<RegistryFriendlyByteBuf, MessageApplyHealingItem> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.BYTE,
      message -> (byte)message.part.ordinal(),
      ByteBufCodecs.BOOL,
      message -> message.hand == InteractionHand.MAIN_HAND,
      (partOrdinal, mainHand) -> new MessageApplyHealingItem(
         EnumPlayerPart.VALUES[partOrdinal], mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND
      )
   );
   private final EnumPlayerPart part;
   private final InteractionHand hand;

   public MessageApplyHealingItem(EnumPlayerPart part, InteractionHand hand) {
      this.part = part;
      this.hand = hand;
   }

   public Type<MessageApplyHealingItem> type() {
      return TYPE;
   }

   public static void handle(MessageApplyHealingItem message, Context context) {
      ServerPlayer player = context.player();
      context.server()
         .execute(
            () -> {
               AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
               if (damageModel != null) {
                  ItemStack stack = player.getItemInHand(message.hand);
                  AbstractPartHealer healer = null;
                  if (stack.getItem() instanceof ItemHealing itemHealing) {
                     healer = itemHealing.createNewHealer(stack);
                  }

                  if (healer == null) {
                     FirstAid.LOGGER
                        .warn(
                           LoggingMarkers.NETWORK,
                           "Player {} has invalid item in hand {} while it should be an healing item",
                           player.getName(),
                           BuiltInRegistries.ITEM.getKey(stack.getItem())
                        );
                     player.sendSystemMessage(Component.literal("Unable to apply healing item!"));
                  } else {
                     AbstractDamageablePart damageablePart = damageModel.getFromEnum(message.part);
                     if (damageablePart.activeHealer == null && !(damageablePart.currentHealth >= damageablePart.getMaxHealth())) {
                        stack.shrink(1);
                        damageablePart.activeHealer = healer;
                        damageModel.scheduleResync();
                        FirstAidNetworking.sendDamageModelSync(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
                     }
                  }
               }
            }
         );
   }
}

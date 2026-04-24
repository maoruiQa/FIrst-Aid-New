package ichttt.mods.firstaid.common.network;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.CapProvider;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public class MessageClientRequest implements CustomPacketPayload {
   public static final Type<MessageClientRequest> TYPE = new Type(Identifier.fromNamespaceAndPath("firstaid", "client_request"));
   public static final StreamCodec<RegistryFriendlyByteBuf, MessageClientRequest> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.BYTE, message -> (byte)message.type.ordinal(), ordinal -> new MessageClientRequest(MessageClientRequest.RequestType.TYPES[ordinal])
   );
   private final MessageClientRequest.RequestType type;

   public MessageClientRequest(MessageClientRequest.RequestType type) {
      this.type = type;
   }

   public Type<MessageClientRequest> type() {
      return TYPE;
   }

   public static void handle(MessageClientRequest message, Context context) {
      ServerPlayer player = context.player();
      context.server().execute(() -> {
         if (message.type == MessageClientRequest.RequestType.TUTORIAL_COMPLETE) {
            CapProvider.tutorialDone.add(player.getName().getString());
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel == null) {
               return;
            }

            damageModel.hasTutorial = true;
            FirstAidNetworking.sendDamageModelSync(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
         } else if (message.type == MessageClientRequest.RequestType.REQUEST_REFRESH) {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel == null) {
               return;
            }

            FirstAidNetworking.sendDamageModelSync(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
            FirstAidNetworking.sendServerConfig(player);
         } else if (message.type == MessageClientRequest.RequestType.GIVE_UP) {
            if (CommonUtils.getDamageModel(player) instanceof PlayerDamageModel playerDamageModel) {
               playerDamageModel.giveUp(player);
            }
         } else if (message.type == MessageClientRequest.RequestType.ATTEMPT_RESCUE) {
            EventHandler.attemptImmediateRescue(player);
         } else if (message.type == MessageClientRequest.RequestType.ATTEMPT_EXECUTION) {
            EventHandler.attemptImmediateExecution(player);
         }
      });
   }

   public static enum RequestType {
      TUTORIAL_COMPLETE,
      REQUEST_REFRESH,
      GIVE_UP,
      ATTEMPT_RESCUE,
      ATTEMPT_EXECUTION;

      private static final MessageClientRequest.RequestType[] TYPES = values();
   }
}

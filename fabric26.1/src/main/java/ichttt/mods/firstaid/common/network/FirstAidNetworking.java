package ichttt.mods.firstaid.common.network;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class FirstAidNetworking {
   private static boolean typesRegistered = false;
   private static boolean serverHandlersRegistered = false;

   private FirstAidNetworking() {
   }

   public static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("firstaid", path);
   }

   public static void registerCommon() {
      if (!typesRegistered) {
         typesRegistered = true;
         PayloadTypeRegistry.serverboundPlay().register(MessageApplyHealingItem.TYPE, MessageApplyHealingItem.STREAM_CODEC);
         PayloadTypeRegistry.serverboundPlay().register(MessageClientRequest.TYPE, MessageClientRequest.STREAM_CODEC);
         PayloadTypeRegistry.clientboundPlay().register(MessageUpdatePart.TYPE, MessageUpdatePart.STREAM_CODEC);
         PayloadTypeRegistry.clientboundPlay().register(MessageSyncDamageModel.TYPE, MessageSyncDamageModel.STREAM_CODEC);
         PayloadTypeRegistry.clientboundPlay().register(MessageSyncServerConfig.TYPE, MessageSyncServerConfig.STREAM_CODEC);
      }

      if (!serverHandlersRegistered) {
         serverHandlersRegistered = true;
         ServerPlayNetworking.registerGlobalReceiver(MessageApplyHealingItem.TYPE, MessageApplyHealingItem::handle);
         ServerPlayNetworking.registerGlobalReceiver(MessageClientRequest.TYPE, MessageClientRequest::handle);
      }
   }

   public static void sendDamageModelSync(ServerPlayer player, AbstractPlayerDamageModel model, boolean scaleMaxHealth) {
      MessageSyncDamageModel payload = new MessageSyncDamageModel(player.getId(), model, scaleMaxHealth);
      ServerPlayNetworking.send(player, payload);

      for (ServerPlayer trackingPlayer : PlayerLookup.tracking(player)) {
         if (trackingPlayer != player) {
            ServerPlayNetworking.send(trackingPlayer, payload);
         }
      }
   }

   public static void sendPartUpdate(ServerPlayer player, MessageUpdatePart payload) {
      ServerPlayNetworking.send(player, payload);

      for (ServerPlayer trackingPlayer : PlayerLookup.tracking(player)) {
         if (trackingPlayer != player) {
            ServerPlayNetworking.send(trackingPlayer, payload);
         }
      }
   }

   public static void sendServerConfig(ServerPlayer player) {
      ServerPlayNetworking.send(player, new MessageSyncServerConfig(FirstAidConfig.serializeServerBundle()));
   }
}

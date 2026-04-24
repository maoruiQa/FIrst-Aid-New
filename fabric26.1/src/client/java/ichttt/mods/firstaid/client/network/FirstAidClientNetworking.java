package ichttt.mods.firstaid.client.network;

import ichttt.mods.firstaid.common.network.MessageSyncDamageModel;
import ichttt.mods.firstaid.common.network.MessageSyncServerConfig;
import ichttt.mods.firstaid.common.network.MessageUpdatePart;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class FirstAidClientNetworking {
   private static boolean handlersRegistered = false;

   private FirstAidClientNetworking() {
   }

   public static void registerClient() {
      if (!handlersRegistered) {
         handlersRegistered = true;
         ClientPlayNetworking.registerGlobalReceiver(MessageUpdatePart.TYPE, MessageUpdatePartHandler::handle);
         ClientPlayNetworking.registerGlobalReceiver(MessageSyncDamageModel.TYPE, MessageSyncDamageModelHandler::handle);
         ClientPlayNetworking.registerGlobalReceiver(MessageSyncServerConfig.TYPE, MessageSyncServerConfigHandler::handle);
      }
   }

   public static void sendToServer(CustomPacketPayload message) {
      ClientPlayNetworking.send(message);
   }
}

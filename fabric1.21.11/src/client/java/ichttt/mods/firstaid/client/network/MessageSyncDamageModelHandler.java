package ichttt.mods.firstaid.client.network;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.HUDHandler;
import ichttt.mods.firstaid.common.CapProvider;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.network.MessageSyncDamageModel;
import ichttt.mods.firstaid.common.network.MessageClientRequest.RequestType;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class MessageSyncDamageModelHandler {
   private MessageSyncDamageModelHandler() {
   }

   public static void handle(MessageSyncDamageModel message, Context context) {
      context.client().execute(() -> {
         Minecraft mc = context.client();
         if (mc.level != null && mc.player != null) {
            Player targetPlayer = resolveTargetPlayer(mc, message.entityId());
            if (targetPlayer == null) {
               if (message.entityId() == mc.player.getId()) {
                  FirstAid.isSynced = false;
                  FirstAid.LOGGER.debug(LoggingMarkers.NETWORK, "Local player sync arrived before the client player entity was ready, requesting refresh");
                  FirstAidClientNetworking.sendToServer(new MessageClientRequest(RequestType.REQUEST_REFRESH));
               } else {
                  FirstAid.LOGGER.debug(LoggingMarkers.NETWORK, "Ignoring damage model sync because entity {} is not loaded yet", message.entityId());
               }
            } else {
               AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(targetPlayer);
               if (damageModel == null) {
                  if (targetPlayer == mc.player) {
                     FirstAid.isSynced = false;
                     FirstAid.LOGGER.debug(LoggingMarkers.NETWORK, "Damage model missing during sync, requesting refresh");
                     FirstAidClientNetworking.sendToServer(new MessageClientRequest(RequestType.REQUEST_REFRESH));
                  }
               } else {
                  if (message.shouldScaleMaxHealth()) {
                     damageModel.runScaleLogic(targetPlayer);
                  }

                  damageModel.deserializeNBT(message.playerDamageModel());
                  if (targetPlayer == mc.player && damageModel.hasTutorial) {
                     CapProvider.tutorialDone.add(mc.player.getName().getString());
                  }

                  if (targetPlayer == mc.player) {
                     HUDHandler.INSTANCE.ticker = 200;
                     FirstAid.isSynced = true;
                  }

                  FirstAid.LOGGER.debug(LoggingMarkers.NETWORK, "Sync complete");
               }
            }
         } else {
            FirstAid.isSynced = false;
            FirstAid.LOGGER.debug(LoggingMarkers.NETWORK, "Ignoring damage model sync because the local player is not ready yet");
         }
      });
   }

   private static Player resolveTargetPlayer(Minecraft mc, int entityId) {
      if (mc.player != null && mc.player.getId() == entityId) {
         return mc.player;
      }

      return mc.level.getEntity(entityId) instanceof Player targetPlayer ? targetPlayer : null;
   }
}

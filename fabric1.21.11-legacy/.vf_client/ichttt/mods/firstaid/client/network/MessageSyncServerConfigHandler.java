package ichttt.mods.firstaid.client.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.common.network.MessageSyncServerConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context;

public final class MessageSyncServerConfigHandler {
   private MessageSyncServerConfigHandler() {
   }

   public static void handle(MessageSyncServerConfig message, Context context) {
      context.client().execute(() -> {
         try {
            JsonElement element = JsonParser.parseString(message.payload());
            if (element != null && element.isJsonObject()) {
               FirstAidConfig.applyServerBundle(element.getAsJsonObject());
            }
         } catch (Exception var2) {
            FirstAid.LOGGER.warn("Failed to parse synced server config: {}", var2.getMessage());
         }
      });
   }
}

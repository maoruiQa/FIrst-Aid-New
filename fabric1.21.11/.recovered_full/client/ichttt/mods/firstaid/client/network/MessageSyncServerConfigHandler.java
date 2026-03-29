/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  ichttt.mods.firstaid.FirstAid
 *  ichttt.mods.firstaid.FirstAidConfig
 *  ichttt.mods.firstaid.common.network.MessageSyncServerConfig
 *  net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking$Context
 */
package ichttt.mods.firstaid.client.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.common.network.MessageSyncServerConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MessageSyncServerConfigHandler {
    private MessageSyncServerConfigHandler() {
    }

    public static void handle(MessageSyncServerConfig message, ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            try {
                JsonElement element = JsonParser.parseString((String)message.payload());
                if (element != null && element.isJsonObject()) {
                    FirstAidConfig.applyServerBundle((JsonObject)element.getAsJsonObject());
                }
            }
            catch (Exception e) {
                FirstAid.LOGGER.warn("Failed to parse synced server config: {}", (Object)e.getMessage());
            }
        });
    }
}


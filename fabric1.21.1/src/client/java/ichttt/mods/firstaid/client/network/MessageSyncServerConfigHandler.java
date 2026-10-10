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

package ichttt.mods.firstaid.client.network;

import com.google.gson.JsonElement;
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
            // Local server settings are shared; ignore delayed copies of older commands.
            if (context.client().getSingleplayerServer() != null) return;
            try {
                JsonElement element = JsonParser.parseString(message.payload());
                if (element != null && element.isJsonObject()) {
                    FirstAidConfig.applyServerBundle(element.getAsJsonObject());
                }
            } catch (Exception e) {
                FirstAid.LOGGER.warn("Failed to parse synced server config: {}", e.getMessage());
            }
        });
    }
}

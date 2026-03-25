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
        if (handlersRegistered) {
            return;
        }
        handlersRegistered = true;
        ClientPlayNetworking.registerGlobalReceiver(MessageUpdatePart.TYPE, MessageUpdatePartHandler::handle);
        ClientPlayNetworking.registerGlobalReceiver(MessageSyncDamageModel.TYPE, MessageSyncDamageModelHandler::handle);
        ClientPlayNetworking.registerGlobalReceiver(MessageSyncServerConfig.TYPE, MessageSyncServerConfigHandler::handle);
    }

    public static void sendToServer(CustomPacketPayload message) {
        ClientPlayNetworking.send(message);
    }
}

/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ichttt.mods.firstaid.common.network.MessageSyncDamageModel
 *  ichttt.mods.firstaid.common.network.MessageSyncServerConfig
 *  net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 */
package ichttt.mods.firstaid.client.network;

import ichttt.mods.firstaid.client.network.MessageSyncDamageModelHandler;
import ichttt.mods.firstaid.client.network.MessageSyncServerConfigHandler;
import ichttt.mods.firstaid.common.network.MessageSyncDamageModel;
import ichttt.mods.firstaid.common.network.MessageSyncServerConfig;
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
        ClientPlayNetworking.registerGlobalReceiver((CustomPacketPayload.Type)MessageSyncDamageModel.TYPE, MessageSyncDamageModelHandler::handle);
        ClientPlayNetworking.registerGlobalReceiver((CustomPacketPayload.Type)MessageSyncServerConfig.TYPE, MessageSyncServerConfigHandler::handle);
    }

    public static void sendToServer(CustomPacketPayload message) {
        ClientPlayNetworking.send((CustomPacketPayload)message);
    }
}


/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
 *  net.fabricmc.fabric.api.networking.v1.PlayerLookup
 *  net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.resources.Identifier
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.Entity
 */
package ichttt.mods.firstaid.common.network;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.network.MessageApplyHealingItem;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.network.MessageSyncDamageModel;
import ichttt.mods.firstaid.common.network.MessageSyncServerConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class FirstAidNetworking {
    private static boolean typesRegistered = false;
    private static boolean serverHandlersRegistered = false;

    private FirstAidNetworking() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath((String)"firstaid", (String)path);
    }

    public static void registerCommon() {
        if (!typesRegistered) {
            typesRegistered = true;
            PayloadTypeRegistry.playC2S().register(MessageApplyHealingItem.TYPE, MessageApplyHealingItem.STREAM_CODEC);
            PayloadTypeRegistry.playC2S().register(MessageClientRequest.TYPE, MessageClientRequest.STREAM_CODEC);
            PayloadTypeRegistry.playS2C().register(MessageSyncDamageModel.TYPE, MessageSyncDamageModel.STREAM_CODEC);
            PayloadTypeRegistry.playS2C().register(MessageSyncServerConfig.TYPE, MessageSyncServerConfig.STREAM_CODEC);
        }
        if (!serverHandlersRegistered) {
            serverHandlersRegistered = true;
            ServerPlayNetworking.registerGlobalReceiver(MessageApplyHealingItem.TYPE, MessageApplyHealingItem::handle);
            ServerPlayNetworking.registerGlobalReceiver(MessageClientRequest.TYPE, MessageClientRequest::handle);
        }
    }

    public static void sendDamageModelSync(ServerPlayer player, AbstractPlayerDamageModel model, boolean scaleMaxHealth) {
        MessageSyncDamageModel payload = new MessageSyncDamageModel(player.getId(), model, scaleMaxHealth);
        ServerPlayNetworking.send((ServerPlayer)player, (CustomPacketPayload)payload);
        for (ServerPlayer trackingPlayer : PlayerLookup.tracking((Entity)player)) {
            if (trackingPlayer == player) continue;
            ServerPlayNetworking.send((ServerPlayer)trackingPlayer, (CustomPacketPayload)payload);
        }
    }

    public static void sendServerConfig(ServerPlayer player) {
        ServerPlayNetworking.send((ServerPlayer)player, (CustomPacketPayload)new MessageSyncServerConfig(FirstAidConfig.serializeServerBundle()));
    }
}


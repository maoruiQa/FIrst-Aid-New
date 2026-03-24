package ichttt.mods.firstaid.common.network;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class FirstAidNetworking {

    private static boolean typesRegistered = false;
    private static boolean serverHandlersRegistered = false;

    private FirstAidNetworking() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, path);
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

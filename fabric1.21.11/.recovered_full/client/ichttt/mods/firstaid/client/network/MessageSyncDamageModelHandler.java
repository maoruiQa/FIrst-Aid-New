/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ichttt.mods.firstaid.FirstAid
 *  ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel
 *  ichttt.mods.firstaid.common.CapProvider
 *  ichttt.mods.firstaid.common.network.MessageClientRequest
 *  ichttt.mods.firstaid.common.network.MessageClientRequest$RequestType
 *  ichttt.mods.firstaid.common.network.MessageSyncDamageModel
 *  ichttt.mods.firstaid.common.util.CommonUtils
 *  ichttt.mods.firstaid.common.util.LoggingMarkers
 *  net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking$Context
 *  net.minecraft.client.Minecraft
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.world.entity.player.Player
 */
package ichttt.mods.firstaid.client.network;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.HUDHandler;
import ichttt.mods.firstaid.client.network.FirstAidClientNetworking;
import ichttt.mods.firstaid.common.CapProvider;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.network.MessageSyncDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public final class MessageSyncDamageModelHandler {
    private MessageSyncDamageModelHandler() {
    }

    public static void handle(MessageSyncDamageModel message, ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            Minecraft mc = context.client();
            if (mc.level == null || mc.player == null) {
                FirstAid.isSynced = false;
                FirstAid.LOGGER.debug(LoggingMarkers.NETWORK, "Ignoring damage model sync because the local player is not ready yet");
                return;
            }
            Player targetPlayer = (Player)mc.level.getEntity(message.entityId());
            if (targetPlayer == null) {
                FirstAid.LOGGER.debug(LoggingMarkers.NETWORK, "Ignoring damage model sync because entity {} is not loaded yet", (Object)message.entityId());
                return;
            }
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel((Player)targetPlayer);
            if (damageModel == null) {
                if (targetPlayer == mc.player) {
                    FirstAid.isSynced = false;
                    FirstAid.LOGGER.debug(LoggingMarkers.NETWORK, "Damage model missing during sync, requesting refresh");
                    FirstAidClientNetworking.sendToServer((CustomPacketPayload)new MessageClientRequest(MessageClientRequest.RequestType.REQUEST_REFRESH));
                }
                return;
            }
            if (message.shouldScaleMaxHealth()) {
                damageModel.runScaleLogic(targetPlayer);
            }
            damageModel.deserializeNBT((Object)message.playerDamageModel());
            if (targetPlayer == mc.player && damageModel.hasTutorial) {
                CapProvider.tutorialDone.add(mc.player.getName().getString());
            }
            if (targetPlayer == mc.player) {
                HUDHandler.INSTANCE.ticker = 200;
                FirstAid.isSynced = true;
            }
            FirstAid.LOGGER.debug(LoggingMarkers.NETWORK, "Sync complete");
        });
    }
}


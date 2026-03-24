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

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.HUDHandler;
import ichttt.mods.firstaid.common.CapProvider;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.network.MessageSyncDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class MessageSyncDamageModelHandler {
    private MessageSyncDamageModelHandler() {
    }

    public static void handle(MessageSyncDamageModel message, ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            Minecraft mc = context.client();
            if (mc.player == null) {
                FirstAid.isSynced = false;
                FirstAid.LOGGER.debug(LoggingMarkers.NETWORK, "Ignoring damage model sync because the local player is not ready yet");
                return;
            }
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
            if (damageModel == null) {
                FirstAid.isSynced = false;
                FirstAid.LOGGER.debug(LoggingMarkers.NETWORK, "Damage model missing during sync, requesting refresh");
                FirstAidClientNetworking.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.REQUEST_REFRESH));
                return;
            }
            if (message.shouldScaleMaxHealth()) {
                damageModel.runScaleLogic(mc.player);
            }
            damageModel.deserializeNBT(message.playerDamageModel());
            if (damageModel.hasTutorial) {
                CapProvider.tutorialDone.add(mc.player.getName().getString());
            }
            HUDHandler.INSTANCE.ticker = 200;
            FirstAid.isSynced = true;
            FirstAid.LOGGER.debug(LoggingMarkers.NETWORK, "Sync complete");
        });
    }
}

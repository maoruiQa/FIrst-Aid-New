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

package ichttt.mods.firstaid.common.network;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.CapProvider;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class MessageClientRequest implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MessageClientRequest> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "client_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageClientRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE,
            message -> (byte) message.type.ordinal(),
            ordinal -> new MessageClientRequest(RequestType.TYPES[ordinal]));

    private final RequestType type;

    public MessageClientRequest(RequestType type) {
        this.type = type;
    }

    @Override
    public CustomPacketPayload.Type<MessageClientRequest> type() {
        return TYPE;
    }

    public enum RequestType {
        TUTORIAL_COMPLETE, REQUEST_REFRESH, GIVE_UP, ATTEMPT_RESCUE, ATTEMPT_EXECUTION;

        private static final RequestType[] TYPES = values();
    }

    public static void handle(MessageClientRequest message, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            if (message.type == RequestType.TUTORIAL_COMPLETE) {
                CapProvider.tutorialDone.add(player.getName().getString());
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
                if (damageModel == null) return;
                damageModel.hasTutorial = true;
                FirstAidNetworking.sendDamageModelSync(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
            } else if (message.type == RequestType.REQUEST_REFRESH) {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
                if (damageModel == null) return;
                FirstAidNetworking.sendDamageModelSync(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
                FirstAidNetworking.sendServerConfig(player);
            } else if (message.type == RequestType.GIVE_UP) {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
                if (damageModel instanceof ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel playerDamageModel) {
                    playerDamageModel.giveUp(player);
                }
            } else if (message.type == RequestType.ATTEMPT_RESCUE) {
                EventHandler.attemptImmediateRescue(player);
            } else if (message.type == RequestType.ATTEMPT_EXECUTION) {
                EventHandler.attemptImmediateExecution(player);
            }
        });
    }
}


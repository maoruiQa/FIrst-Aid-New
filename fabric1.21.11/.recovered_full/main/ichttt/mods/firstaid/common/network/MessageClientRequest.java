/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking$Context
 *  net.minecraft.network.RegistryFriendlyByteBuf
 *  net.minecraft.network.codec.ByteBufCodecs
 *  net.minecraft.network.codec.StreamCodec
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 *  net.minecraft.resources.Identifier
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.player.Player
 */
package ichttt.mods.firstaid.common.network;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.CapProvider;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class MessageClientRequest
implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MessageClientRequest> TYPE = new CustomPacketPayload.Type(Identifier.fromNamespaceAndPath((String)"firstaid", (String)"client_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageClientRequest> STREAM_CODEC = StreamCodec.composite((StreamCodec)ByteBufCodecs.BYTE, message -> (byte)message.type.ordinal(), ordinal -> new MessageClientRequest(RequestType.TYPES[ordinal]));
    private final RequestType type;

    public MessageClientRequest(RequestType type) {
        this.type = type;
    }

    public CustomPacketPayload.Type<MessageClientRequest> type() {
        return TYPE;
    }

    public static void handle(MessageClientRequest message, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            if (message.type == RequestType.TUTORIAL_COMPLETE) {
                CapProvider.tutorialDone.add(player.getName().getString());
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel((Player)player);
                if (damageModel == null) {
                    return;
                }
                damageModel.hasTutorial = true;
                FirstAidNetworking.sendDamageModelSync(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
            } else if (message.type == RequestType.REQUEST_REFRESH) {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel((Player)player);
                if (damageModel == null) {
                    return;
                }
                FirstAidNetworking.sendDamageModelSync(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
                FirstAidNetworking.sendServerConfig(player);
            } else if (message.type == RequestType.GIVE_UP) {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel((Player)player);
                if (damageModel instanceof PlayerDamageModel) {
                    PlayerDamageModel playerDamageModel = (PlayerDamageModel)damageModel;
                    playerDamageModel.giveUp((Player)player);
                }
            } else if (message.type == RequestType.ATTEMPT_RESCUE) {
                EventHandler.attemptImmediateRescue(player);
            }
        });
    }

    public static enum RequestType {
        TUTORIAL_COMPLETE,
        REQUEST_REFRESH,
        GIVE_UP,
        ATTEMPT_RESCUE;

        private static final RequestType[] TYPES;

        static {
            TYPES = RequestType.values();
        }
    }
}


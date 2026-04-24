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
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MessageSyncDamageModel {
    private final int entityId;
    private final CompoundTag playerDamageModel;
    private final boolean scaleMaxHealth;

    public MessageSyncDamageModel(FriendlyByteBuf buffer) {
        this.entityId = buffer.readVarInt();
        this.playerDamageModel = buffer.readNbt();
        this.scaleMaxHealth = buffer.readBoolean();
    }

    public MessageSyncDamageModel(int entityId, AbstractPlayerDamageModel damageModel, boolean scaleMaxHealth) {
        this.entityId = entityId;
        this.playerDamageModel = damageModel.serializeNBT();
        this.scaleMaxHealth = scaleMaxHealth;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeNbt(this.playerDamageModel);
        buffer.writeBoolean(scaleMaxHealth);
    }

    public static final class Handler {

        public static void onMessage(MessageSyncDamageModel message, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context ctx = supplier.get();
            CommonUtils.checkClient(ctx);
            ctx.enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null || mc.player == null) {
                    FirstAid.isSynced = false;
                    return;
                }
                Player targetPlayer = mc.player.getId() == message.entityId
                        ? mc.player
                        : mc.level.getEntity(message.entityId) instanceof Player player ? player : null;
                if (targetPlayer == null) {
                    if (message.entityId == mc.player.getId()) {
                        FirstAid.isSynced = false;
                        FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.Type.REQUEST_REFRESH));
                    }
                    return;
                }
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(targetPlayer);
                if (damageModel == null) {
                    if (targetPlayer == mc.player) {
                        FirstAid.isSynced = false;
                        FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.Type.REQUEST_REFRESH));
                    }
                    return;
                }
                boolean wasUnconscious = damageModel instanceof PlayerDamageModel playerDamageModel && playerDamageModel.isUnconscious();
                if (message.scaleMaxHealth) {
                    damageModel.runScaleLogic(targetPlayer);
                }
                damageModel.deserializeNBT(message.playerDamageModel);
                if (damageModel instanceof PlayerDamageModel playerDamageModel) {
                    playerDamageModel.refreshClientUnconsciousPose(targetPlayer);
                }
                boolean isUnconscious = damageModel instanceof PlayerDamageModel playerDamageModel && playerDamageModel.isUnconscious();
                if (wasUnconscious != isUnconscious) {
                    targetPlayer.refreshDimensions();
                }
                if (targetPlayer == mc.player) {
                    FirstAid.isSynced = true;
                }
            });
        }
    }
}

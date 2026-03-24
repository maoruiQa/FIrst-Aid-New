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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class MessageSyncDamageModel implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MessageSyncDamageModel> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "sync_damage_model"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageSyncDamageModel> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            message -> message.entityId,
            ByteBufCodecs.COMPOUND_TAG,
            message -> message.playerDamageModel,
            ByteBufCodecs.BOOL,
            message -> message.scaleMaxHealth,
            MessageSyncDamageModel::new);

    private final int entityId;
    private final CompoundTag playerDamageModel;
    private final boolean scaleMaxHealth;

    private MessageSyncDamageModel(int entityId, CompoundTag playerDamageModel, boolean scaleMaxHealth) {
        this.entityId = entityId;
        this.playerDamageModel = playerDamageModel;
        this.scaleMaxHealth = scaleMaxHealth;
    }

    public MessageSyncDamageModel(int entityId, AbstractPlayerDamageModel damageModel, boolean scaleMaxHealth) {
        this(entityId, damageModel.serializeNBT(), scaleMaxHealth);
    }

    public int entityId() {
        return entityId;
    }

    public CompoundTag playerDamageModel() {
        return playerDamageModel;
    }

    public boolean shouldScaleMaxHealth() {
        return scaleMaxHealth;
    }

    @Override
    public CustomPacketPayload.Type<MessageSyncDamageModel> type() {
        return TYPE;
    }

}

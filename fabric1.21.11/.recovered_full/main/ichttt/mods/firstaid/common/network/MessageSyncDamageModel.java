/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.RegistryFriendlyByteBuf
 *  net.minecraft.network.codec.ByteBufCodecs
 *  net.minecraft.network.codec.StreamCodec
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 *  net.minecraft.resources.Identifier
 */
package ichttt.mods.firstaid.common.network;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class MessageSyncDamageModel
implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MessageSyncDamageModel> TYPE = new CustomPacketPayload.Type(Identifier.fromNamespaceAndPath((String)"firstaid", (String)"sync_damage_model"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageSyncDamageModel> STREAM_CODEC = StreamCodec.composite((StreamCodec)ByteBufCodecs.VAR_INT, message -> message.entityId, (StreamCodec)ByteBufCodecs.COMPOUND_TAG, message -> message.playerDamageModel, (StreamCodec)ByteBufCodecs.BOOL, message -> message.scaleMaxHealth, MessageSyncDamageModel::new);
    private final int entityId;
    private final CompoundTag playerDamageModel;
    private final boolean scaleMaxHealth;

    private MessageSyncDamageModel(int entityId, CompoundTag playerDamageModel, boolean scaleMaxHealth) {
        this.entityId = entityId;
        this.playerDamageModel = playerDamageModel;
        this.scaleMaxHealth = scaleMaxHealth;
    }

    public MessageSyncDamageModel(int entityId, AbstractPlayerDamageModel damageModel, boolean scaleMaxHealth) {
        this(entityId, (CompoundTag)damageModel.serializeNBT(), scaleMaxHealth);
    }

    public int entityId() {
        return this.entityId;
    }

    public CompoundTag playerDamageModel() {
        return this.playerDamageModel;
    }

    public boolean shouldScaleMaxHealth() {
        return this.scaleMaxHealth;
    }

    public CustomPacketPayload.Type<MessageSyncDamageModel> type() {
        return TYPE;
    }
}


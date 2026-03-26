package ichttt.mods.firstaid.common.network;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class MessageSyncDamageModel implements CustomPacketPayload {
    private static final String CLIENT_HANDLER_CLASS = "ichttt.mods.firstaid.client.network.ClientSyncDamageModelHandler";
    public static final CustomPacketPayload.Type<MessageSyncDamageModel> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "sync_damage_model"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageSyncDamageModel> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            MessageSyncDamageModel::entityId,
            ByteBufCodecs.COMPOUND_TAG,
            MessageSyncDamageModel::playerDamageModel,
            ByteBufCodecs.BOOL,
            MessageSyncDamageModel::shouldScaleMaxHealth,
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

    public static void handle(MessageSyncDamageModel message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!FMLEnvironment.dist.isClient()) {
                return;
            }
            try {
                Class<?> handlerClass = Class.forName(CLIENT_HANDLER_CLASS);
                handlerClass.getMethod("handle", MessageSyncDamageModel.class).invoke(null, message);
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException("Failed to dispatch MessageSyncDamageModel to the client handler", exception);
            }
        });
    }
}

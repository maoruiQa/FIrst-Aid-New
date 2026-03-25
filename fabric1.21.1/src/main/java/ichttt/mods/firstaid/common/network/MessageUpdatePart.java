package ichttt.mods.firstaid.common.network;

import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class MessageUpdatePart implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MessageUpdatePart> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("firstaid", "update_part"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageUpdatePart> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            MessageUpdatePart::entityId,
            ByteBufCodecs.BYTE,
            MessageUpdatePart::partId,
            ByteBufCodecs.VAR_INT,
            MessageUpdatePart::maxHealth,
            ByteBufCodecs.FLOAT,
            MessageUpdatePart::absorption,
            ByteBufCodecs.FLOAT,
            MessageUpdatePart::currentHealth,
            MessageUpdatePart::new
    );

    private final int entityId;
    private final byte partId;
    private final int maxHealth;
    private final float absorption;
    private final float currentHealth;

    private MessageUpdatePart(int entityId, byte partId, int maxHealth, float absorption, float currentHealth) {
        this.entityId = entityId;
        this.partId = partId;
        this.maxHealth = maxHealth;
        this.absorption = absorption;
        this.currentHealth = currentHealth;
    }

    public MessageUpdatePart(int entityId, AbstractDamageablePart part) {
        this(entityId, (byte) part.part.ordinal(), part.getMaxHealth(), part.getAbsorption(), part.currentHealth);
    }

    public int entityId() {
        return entityId;
    }

    public byte partId() {
        return partId;
    }

    public int maxHealth() {
        return maxHealth;
    }

    public float absorption() {
        return absorption;
    }

    public float currentHealth() {
        return currentHealth;
    }

    @Override
    public CustomPacketPayload.Type<MessageUpdatePart> type() {
        return TYPE;
    }
}

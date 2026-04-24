package ichttt.mods.firstaid.common.network;

import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public final class MessageUpdatePart implements CustomPacketPayload {
   public static final Type<MessageUpdatePart> TYPE = new Type<>(Identifier.fromNamespaceAndPath("firstaid", "update_part"));
   public static final StreamCodec<RegistryFriendlyByteBuf, MessageUpdatePart> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT,
      MessageUpdatePart::entityId,
      ByteBufCodecs.BYTE,
      MessageUpdatePart::partId,
      ByteBufCodecs.VAR_INT,
      MessageUpdatePart::maxHealth,
      ByteBufCodecs.FLOAT,
      MessageUpdatePart::currentHealth,
      MessageUpdatePart::new
   );
   private final int entityId;
   private final byte partId;
   private final int maxHealth;
   private final float currentHealth;

   private MessageUpdatePart(int entityId, byte partId, int maxHealth, float currentHealth) {
      this.entityId = entityId;
      this.partId = partId;
      this.maxHealth = maxHealth;
      this.currentHealth = currentHealth;
   }

   public MessageUpdatePart(int entityId, AbstractDamageablePart part) {
      this(entityId, (byte)part.part.ordinal(), part.getMaxHealth(), part.currentHealth);
   }

   public int entityId() {
      return this.entityId;
   }

   public byte partId() {
      return this.partId;
   }

   public int maxHealth() {
      return this.maxHealth;
   }

   public float currentHealth() {
      return this.currentHealth;
   }

   @Override
   public Type<MessageUpdatePart> type() {
      return TYPE;
   }
}

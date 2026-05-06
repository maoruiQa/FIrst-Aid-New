package ichttt.mods.firstaid.common.network;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public class MessageSyncDamageModel implements CustomPacketPayload {
   public static final Type<MessageSyncDamageModel> TYPE = new Type(Identifier.fromNamespaceAndPath("firstaid", "sync_damage_model"));
   public static final StreamCodec<RegistryFriendlyByteBuf, MessageSyncDamageModel> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT,
      message -> message.entityId,
      ByteBufCodecs.COMPOUND_TAG,
      message -> message.playerDamageModel,
      ByteBufCodecs.BOOL,
      message -> message.scaleMaxHealth,
      MessageSyncDamageModel::new
   );
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
      return this.entityId;
   }

   public CompoundTag playerDamageModel() {
      return this.playerDamageModel;
   }

   public boolean shouldScaleMaxHealth() {
      return this.scaleMaxHealth;
   }

   public Type<MessageSyncDamageModel> type() {
      return TYPE;
   }
}

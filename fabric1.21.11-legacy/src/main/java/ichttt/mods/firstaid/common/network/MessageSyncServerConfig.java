package ichttt.mods.firstaid.common.network;

import com.google.gson.JsonObject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public class MessageSyncServerConfig implements CustomPacketPayload {
   public static final Type<MessageSyncServerConfig> TYPE = new Type(Identifier.fromNamespaceAndPath("firstaid", "sync_server_config"));
   public static final StreamCodec<RegistryFriendlyByteBuf, MessageSyncServerConfig> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.stringUtf8(32767), message -> message.payload, MessageSyncServerConfig::new
   );
   private final String payload;

   private MessageSyncServerConfig(String payload) {
      this.payload = payload;
   }

   public MessageSyncServerConfig(JsonObject payload) {
      this(payload.toString());
   }

   public String payload() {
      return this.payload;
   }

   public Type<MessageSyncServerConfig> type() {
      return TYPE;
   }
}

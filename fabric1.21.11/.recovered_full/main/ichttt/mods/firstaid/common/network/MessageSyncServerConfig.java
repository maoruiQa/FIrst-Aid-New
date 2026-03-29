/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonObject
 *  net.minecraft.network.RegistryFriendlyByteBuf
 *  net.minecraft.network.codec.ByteBufCodecs
 *  net.minecraft.network.codec.StreamCodec
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 *  net.minecraft.resources.Identifier
 */
package ichttt.mods.firstaid.common.network;

import com.google.gson.JsonObject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class MessageSyncServerConfig
implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MessageSyncServerConfig> TYPE = new CustomPacketPayload.Type(Identifier.fromNamespaceAndPath((String)"firstaid", (String)"sync_server_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageSyncServerConfig> STREAM_CODEC = StreamCodec.composite((StreamCodec)ByteBufCodecs.stringUtf8((int)Short.MAX_VALUE), message -> message.payload, MessageSyncServerConfig::new);
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

    public CustomPacketPayload.Type<MessageSyncServerConfig> type() {
        return TYPE;
    }
}


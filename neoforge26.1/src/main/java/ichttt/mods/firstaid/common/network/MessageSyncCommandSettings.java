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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class MessageSyncCommandSettings implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MessageSyncCommandSettings> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FirstAid.MODID, "sync_command_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageSyncCommandSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32767),
            message -> message.suppressionEntityBlacklist,
            MessageSyncCommandSettings::new);

    private final String suppressionEntityBlacklist;

    private MessageSyncCommandSettings(String suppressionEntityBlacklist) {
        this.suppressionEntityBlacklist = suppressionEntityBlacklist;
    }

    public static MessageSyncCommandSettings current() {
        StringBuilder builder = new StringBuilder();
        for (Identifier entry : FirstAid.suppressionEntityBlacklist) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(entry);
        }
        return new MessageSyncCommandSettings(builder.toString());
    }

    @Override
    public CustomPacketPayload.Type<MessageSyncCommandSettings> type() {
        return TYPE;
    }

    public static void handle(MessageSyncCommandSettings message, IPayloadContext context) {
        context.enqueueWork(() -> FirstAid.setSuppressionEntityBlacklist(parseList(message.suppressionEntityBlacklist)));
    }

    private static List<Identifier> parseList(String raw) {
        List<Identifier> values = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String entry : raw.split("\\n")) {
            Identifier id = Identifier.tryParse(entry.trim());
            if (id != null) {
                values.add(id);
            }
        }
        return values;
    }
}

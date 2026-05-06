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
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class MessageSyncCommandSettings {
    private final boolean enablePainVignette;
    private final boolean enablePainFovCompression;
    private final boolean enablePainAudioEffects;
    private final String suppressionEntityBlacklist;

    private MessageSyncCommandSettings(boolean enablePainVignette, boolean enablePainFovCompression, boolean enablePainAudioEffects, String suppressionEntityBlacklist) {
        this.enablePainVignette = enablePainVignette;
        this.enablePainFovCompression = enablePainFovCompression;
        this.enablePainAudioEffects = enablePainAudioEffects;
        this.suppressionEntityBlacklist = suppressionEntityBlacklist;
    }

    public MessageSyncCommandSettings(FriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(32767));
    }

    public static MessageSyncCommandSettings current() {
        StringBuilder builder = new StringBuilder();
        for (ResourceLocation entry : FirstAid.suppressionEntityBlacklist) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(entry);
        }
        return new MessageSyncCommandSettings(
                FirstAid.enablePainVignette,
                FirstAid.enablePainFovCompression,
                FirstAid.enablePainAudioEffects,
                builder.toString());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enablePainVignette);
        buf.writeBoolean(enablePainFovCompression);
        buf.writeBoolean(enablePainAudioEffects);
        buf.writeUtf(suppressionEntityBlacklist, 32767);
    }

    public static class Handler {
        public static void onMessage(MessageSyncCommandSettings message, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context ctx = supplier.get();
            CommonUtils.checkClient(ctx);
            ctx.enqueueWork(() -> {
                FirstAid.enablePainVignette = message.enablePainVignette;
                FirstAid.enablePainFovCompression = message.enablePainFovCompression;
                FirstAid.enablePainAudioEffects = message.enablePainAudioEffects;
                FirstAid.setSuppressionEntityBlacklist(parseList(message.suppressionEntityBlacklist));
            });
        }

        private static List<ResourceLocation> parseList(String raw) {
            List<ResourceLocation> values = new ArrayList<>();
            if (raw == null || raw.isBlank()) {
                return values;
            }
            for (String entry : raw.split("\\n")) {
                ResourceLocation id = ResourceLocation.tryParse(entry.trim());
                if (id != null) {
                    values.add(id);
                }
            }
            return values;
        }
    }
}

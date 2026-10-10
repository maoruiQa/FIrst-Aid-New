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
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class MessageSyncCommandSettings implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MessageSyncCommandSettings> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "sync_command_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageSyncCommandSettings> STREAM_CODEC = StreamCodec.ofMember(MessageSyncCommandSettings::encode, MessageSyncCommandSettings::new);

    private final boolean enablePainVignette;
    private final boolean enablePainFovCompression;
    private final boolean enablePainAudioEffects;
    private final boolean projectileSuppressionEnabled;
    private final boolean lowSuppressionEnabled;
    private final float adrenalineReliefMultiplier;
    private final String suppressionEntityBlacklist;

    private MessageSyncCommandSettings(boolean enablePainVignette, boolean enablePainFovCompression, boolean enablePainAudioEffects, boolean projectileSuppressionEnabled, boolean lowSuppressionEnabled, float adrenalineReliefMultiplier, String suppressionEntityBlacklist) {
        this.enablePainVignette = enablePainVignette;
        this.enablePainFovCompression = enablePainFovCompression;
        this.enablePainAudioEffects = enablePainAudioEffects;
        this.projectileSuppressionEnabled = projectileSuppressionEnabled;
        this.lowSuppressionEnabled = lowSuppressionEnabled;
        this.adrenalineReliefMultiplier = adrenalineReliefMultiplier;
        this.suppressionEntityBlacklist = suppressionEntityBlacklist;
    }

    private MessageSyncCommandSettings(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readFloat(), buffer.readUtf(32767));
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(enablePainVignette);
        buffer.writeBoolean(enablePainFovCompression);
        buffer.writeBoolean(enablePainAudioEffects);
        buffer.writeBoolean(projectileSuppressionEnabled);
        buffer.writeBoolean(lowSuppressionEnabled);
        buffer.writeFloat(adrenalineReliefMultiplier);
        buffer.writeUtf(suppressionEntityBlacklist, 32767);
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
                FirstAid.projectileSuppressionEnabled,
                FirstAid.lowSuppressionEnabled,
                FirstAid.adrenalineReliefMultiplier,
                builder.toString());
    }

    @Override
    public CustomPacketPayload.Type<MessageSyncCommandSettings> type() {
        return TYPE;
    }

    public static void handle(MessageSyncCommandSettings message, IPayloadContext context) {
        context.enqueueWork(() -> {
            // The integrated server already owns these shared settings; an older reply must not overwrite a new command.
            if (ServerLifecycleHooks.getCurrentServer() != null) return;
            FirstAid.enablePainVignette = message.enablePainVignette;
            FirstAid.enablePainFovCompression = message.enablePainFovCompression;
            FirstAid.enablePainAudioEffects = message.enablePainAudioEffects;
            FirstAid.projectileSuppressionEnabled = message.projectileSuppressionEnabled;
            FirstAid.lowSuppressionEnabled = message.lowSuppressionEnabled;
            FirstAid.adrenalineReliefMultiplier = Math.max(0.0F, Math.min(2.0F, message.adrenalineReliefMultiplier));
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

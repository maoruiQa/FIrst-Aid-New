/*
 * FirstAid
 * Copyright (C) 2017-2024
 */

package ichttt.mods.firstaid.common.network;

import ichttt.mods.firstaid.FirstAid;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
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
            ByteBufCodecs.BOOL,
            message -> message.enablePainVignette,
            ByteBufCodecs.BOOL,
            message -> message.enablePainBlur,
            ByteBufCodecs.BOOL,
            message -> message.enablePainFovCompression,
            ByteBufCodecs.BOOL,
            message -> message.enablePainAudioEffects,
            ByteBufCodecs.BOOL,
            message -> message.projectileSuppressionEnabled,
            ByteBufCodecs.BOOL,
            message -> message.lowSuppressionEnabled,
            ByteBufCodecs.FLOAT,
            message -> message.adrenalineReliefMultiplier,
            ByteBufCodecs.stringUtf8(32767),
            message -> message.suppressionEntityBlacklist,
            MessageSyncCommandSettings::new);

    private final boolean enablePainVignette;
    private final boolean enablePainBlur;
    private final boolean enablePainFovCompression;
    private final boolean enablePainAudioEffects;
    private final boolean projectileSuppressionEnabled;
    private final boolean lowSuppressionEnabled;
    private final float adrenalineReliefMultiplier;
    private final String suppressionEntityBlacklist;

    private MessageSyncCommandSettings(
            boolean enablePainVignette,
            boolean enablePainBlur,
            boolean enablePainFovCompression,
            boolean enablePainAudioEffects,
            boolean projectileSuppressionEnabled,
            boolean lowSuppressionEnabled,
            float adrenalineReliefMultiplier,
            String suppressionEntityBlacklist
    ) {
        this.enablePainVignette = enablePainVignette;
        this.enablePainBlur = enablePainBlur;
        this.enablePainFovCompression = enablePainFovCompression;
        this.enablePainAudioEffects = enablePainAudioEffects;
        this.projectileSuppressionEnabled = projectileSuppressionEnabled;
        this.lowSuppressionEnabled = lowSuppressionEnabled;
        this.adrenalineReliefMultiplier = adrenalineReliefMultiplier;
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
        return new MessageSyncCommandSettings(
                FirstAid.enablePainVignette,
                FirstAid.enablePainBlur,
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
            FirstAid.enablePainBlur = message.enablePainBlur;
            FirstAid.enablePainFovCompression = message.enablePainFovCompression;
            FirstAid.enablePainAudioEffects = message.enablePainAudioEffects;
            FirstAid.projectileSuppressionEnabled = message.projectileSuppressionEnabled;
            FirstAid.lowSuppressionEnabled = message.lowSuppressionEnabled;
            FirstAid.adrenalineReliefMultiplier = Math.max(0.0F, Math.min(2.0F, message.adrenalineReliefMultiplier));
            FirstAid.setSuppressionEntityBlacklist(parseList(message.suppressionEntityBlacklist));
        });
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

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
import net.minecraftforge.server.ServerLifecycleHooks;
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
    private final boolean projectileSuppressionEnabled;
    private final boolean lowSuppressionEnabled;
    private final float adrenalineReliefMultiplier;
    private final String suppressionEntityBlacklist;
    private final FirstAid.MedicineEffectMode medicineEffectMode;
    private final float medicineTimingMultiplier;
    private final FirstAid.InjuryDebuffMode injuryDebuffMode;
    private final float lowInjuryDebuffDamageScale;
    private final float lowInjuryDebuffAmplifierScale;
    private final float lowInjuryDebuffDurationScale;

    private MessageSyncCommandSettings(boolean enablePainVignette, boolean enablePainFovCompression, boolean enablePainAudioEffects, boolean projectileSuppressionEnabled, boolean lowSuppressionEnabled, float adrenalineReliefMultiplier, String suppressionEntityBlacklist, FirstAid.MedicineEffectMode medicineEffectMode, float medicineTimingMultiplier, FirstAid.InjuryDebuffMode injuryDebuffMode, float lowInjuryDebuffDamageScale, float lowInjuryDebuffAmplifierScale, float lowInjuryDebuffDurationScale) {
        this.enablePainVignette = enablePainVignette;
        this.enablePainFovCompression = enablePainFovCompression;
        this.enablePainAudioEffects = enablePainAudioEffects;
        this.projectileSuppressionEnabled = projectileSuppressionEnabled;
        this.lowSuppressionEnabled = lowSuppressionEnabled;
        this.adrenalineReliefMultiplier = adrenalineReliefMultiplier;
        this.suppressionEntityBlacklist = suppressionEntityBlacklist;
        this.medicineEffectMode = medicineEffectMode;
        this.medicineTimingMultiplier = medicineTimingMultiplier;
        this.injuryDebuffMode = injuryDebuffMode;
        this.lowInjuryDebuffDamageScale = lowInjuryDebuffDamageScale;
        this.lowInjuryDebuffAmplifierScale = lowInjuryDebuffAmplifierScale;
        this.lowInjuryDebuffDurationScale = lowInjuryDebuffDurationScale;
    }

    public MessageSyncCommandSettings(FriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readFloat(), buffer.readUtf(32767), buffer.readEnum(FirstAid.MedicineEffectMode.class), buffer.readFloat(), buffer.readEnum(FirstAid.InjuryDebuffMode.class), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
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
                builder.toString(),
                FirstAid.medicineEffectMode,
                FirstAid.medicineTimingMultiplier,
                FirstAid.injuryDebuffMode,
                FirstAid.lowInjuryDebuffDamageScale,
                FirstAid.lowInjuryDebuffAmplifierScale,
                FirstAid.lowInjuryDebuffDurationScale);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enablePainVignette);
        buf.writeBoolean(enablePainFovCompression);
        buf.writeBoolean(enablePainAudioEffects);
        buf.writeBoolean(projectileSuppressionEnabled);
        buf.writeBoolean(lowSuppressionEnabled);
        buf.writeFloat(adrenalineReliefMultiplier);
        buf.writeUtf(suppressionEntityBlacklist, 32767);
        buf.writeEnum(medicineEffectMode);
        buf.writeFloat(medicineTimingMultiplier);
        buf.writeEnum(injuryDebuffMode);
        buf.writeFloat(lowInjuryDebuffDamageScale);
        buf.writeFloat(lowInjuryDebuffAmplifierScale);
        buf.writeFloat(lowInjuryDebuffDurationScale);
    }

    public static class Handler {
        public static void onMessage(MessageSyncCommandSettings message, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context ctx = supplier.get();
            CommonUtils.checkClient(ctx);
            ctx.enqueueWork(() -> {
                // The integrated server already owns these shared settings; an older reply must not overwrite a new command.
                if (ServerLifecycleHooks.getCurrentServer() != null) return;
                FirstAid.enablePainVignette = message.enablePainVignette;
                FirstAid.enablePainFovCompression = message.enablePainFovCompression;
                FirstAid.enablePainAudioEffects = message.enablePainAudioEffects;
                FirstAid.projectileSuppressionEnabled = message.projectileSuppressionEnabled;
                FirstAid.lowSuppressionEnabled = message.lowSuppressionEnabled;
                FirstAid.medicineEffectMode = message.medicineEffectMode;
                FirstAid.medicineTimingMultiplier = Float.isFinite(message.medicineTimingMultiplier) ? Math.max(0.01F, Math.min(20F, message.medicineTimingMultiplier)) : 1.0F;
                FirstAid.injuryDebuffMode = message.injuryDebuffMode;
                FirstAid.lowInjuryDebuffDamageScale = Float.isFinite(message.lowInjuryDebuffDamageScale) ? Math.max(0F, Math.min(1F, message.lowInjuryDebuffDamageScale)) : 0.4F;
                FirstAid.lowInjuryDebuffAmplifierScale = Float.isFinite(message.lowInjuryDebuffAmplifierScale) ? Math.max(0F, Math.min(1F, message.lowInjuryDebuffAmplifierScale)) : 0.5F;
                FirstAid.lowInjuryDebuffDurationScale = Float.isFinite(message.lowInjuryDebuffDurationScale) ? Math.max(0F, Math.min(1F, message.lowInjuryDebuffDurationScale)) : 0.5F;
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
}

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

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import java.util.Locale;

public class StatusEffectLayer implements HudRenderCallback {
    public static final StatusEffectLayer INSTANCE = new StatusEffectLayer();

    private static final int GIVE_UP_BAR_WIDTH = 144;
    private static final int GIVE_UP_BAR_HEIGHT = 8;
    private static final int RESCUE_BAR_WIDTH = 144;
    private static final int RESCUE_BAR_HEIGHT = 8;
    private static final float PAIN_GAIN = 0.045F;
    private static final float PAIN_DECAY = 0.015F;
    private static final float PAIN_INTENSITY_MULTIPLIER = 2.0F;
    private static final float PAIN_INTENSITY_MAX = 2.0F;
    private static final int PAIN_BASE_THICKNESS = 20;
    private static final float SUPPRESSION_GAIN = 0.18F;
    private static final float SUPPRESSION_DECAY = 0.012F;
    private static final float SUPPRESSION_INTENSITY_MULTIPLIER = 2.0F;
    private static final float SUPPRESSION_INTENSITY_MAX = 2.0F;

    private float painStrength;
    private float lastPainStrength;
    private float suppressionStrength;
    private float lastSuppressionStrength;

    @Override
    public void onHudRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(minecraft.player);
        if (damageModel == null || !FirstAid.isSynced) {
            return;
        }
        if (!minecraft.player.isAlive() && damageModel.getUnconsciousTicks() <= 0) {
            return;
        }
        PlayerDamageModel playerDamageModel = damageModel instanceof PlayerDamageModel model ? model : null;

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        float deathDanger = playerDamageModel == null ? 0.0F : playerDamageModel.getDeathCountdownDangerProgress();
        boolean painSuppressed = minecraft.player.hasEffect(RegistryObjects.MORPHINE_EFFECT)
                || minecraft.player.hasEffect(RegistryObjects.PAINKILLER_EFFECT);
        float basePain = painSuppressed || playerDamageModel == null ? 0.0F : playerDamageModel.getPainVisualStrength();
        float dangerPain = deathDanger <= 0.0F ? 0.0F : Mth.clamp(0.18F + deathDanger * 0.82F, 0.0F, 1.0F);
        float targetPain = Math.max(basePain, dangerPain);
        SuppressionFeedbackController suppressionFeedbackController = ClientEventHandler.getSuppressionFeedbackController();
        float modelSuppression = playerDamageModel == null ? Math.min(1.0F, damageModel.getAdrenalineTicks() / 200.0F) : playerDamageModel.getSuppressionIntensity();
        float suppressionScale = FirstAid.lowSuppressionEnabled ? 0.4F : 1.0F;
        float targetSuppression = Math.max(modelSuppression, suppressionFeedbackController.getVisualStrength()) * suppressionScale;

        tickStrengths(targetPain, targetSuppression);
        float smoothPain = Mth.lerp(deltaTracker.getGameTimeDeltaTicks(), lastPainStrength, painStrength);
        float smoothSuppression = Mth.lerp(deltaTracker.getGameTimeDeltaTicks(), lastSuppressionStrength, suppressionStrength);
        float pulseTime = minecraft.player.tickCount + deltaTracker.getGameTimeDeltaTicks();

        if (smoothPain > 0.0F) {
            float pulse = deathDanger > 0.0F
                    ? 0.82F + (0.18F + deathDanger * 0.27F) * Mth.sin(pulseTime * (0.08F + deathDanger * 0.04F))
                    : 0.9F + 0.1F * Mth.sin(pulseTime * 0.32F);
            float intensity = Math.min(PAIN_INTENSITY_MAX, smoothPain * PAIN_INTENSITY_MULTIPLIER * pulse);
            float rangeScale = 1.0F + Mth.clamp(smoothPain, 0.0F, 1.0F);
            int thickness = Math.round(PAIN_BASE_THICKNESS * rangeScale);
            renderVignette(guiGraphics, width, height, 138, 24, 24, intensity, thickness);
        }

        if (smoothSuppression > 0.0F) {
            float pulse = 0.90F + 0.10F * Mth.sin((pulseTime * 0.46F) + 0.8F);
            float intensity = Math.min(SUPPRESSION_INTENSITY_MAX, (0.45F + smoothSuppression * 0.75F) * SUPPRESSION_INTENSITY_MULTIPLIER * pulse);
            renderVignette(guiGraphics, width, height, 18, 24, 34, intensity, 30);
            renderVignette(guiGraphics, width, height, 88, 102, 128, Math.min(SUPPRESSION_INTENSITY_MAX, intensity * 0.72F), 18);
            guiGraphics.fill(0, 0, width, height, color(Math.round(12.0F + 48.0F * smoothSuppression * SUPPRESSION_INTENSITY_MULTIPLIER), 16, 18, 22));
        }

        float tinnitusStrength = suppressionFeedbackController.getTinnitusStrength();
        if (tinnitusStrength > 0.0F) {
            float pulse = 0.84F + 0.16F * Mth.sin(pulseTime * 0.77F + 1.3F);
            int alpha = Math.round(Math.min(42.0F, (8.0F + 24.0F * tinnitusStrength) * pulse));
            guiGraphics.fill(0, 0, width, height, color(alpha, 198, 205, 214));
            renderVignette(guiGraphics, width, height, 210, 214, 224, Math.min(0.38F, tinnitusStrength * 0.22F), 16);
        }

        if (damageModel.getUnconsciousTicks() > 0) {
            guiGraphics.fill(0, 0, width, height, color(178, 0, 0, 0));
            renderVignette(guiGraphics, width, height, 0, 0, 0, 0.8F, 24);
            if (deathDanger > 0.0F) {
                renderDeathDangerOverlay(guiGraphics, width, height, deathDanger, pulseTime);
            }
            float partialTick = deltaTracker.getGameTimeDeltaTicks();
            Component title = Component.translatable(playerDamageModel != null
                    ? playerDamageModel.getUnconsciousReasonKey()
                    : damageModel.isCriticalConditionActive() ? "firstaid.gui.critical_condition" : "firstaid.gui.unconscious");
            Component timer = playerDamageModel != null && playerDamageModel.canGiveUp()
                    ? Component.translatable("firstaid.gui.death_countdown_seconds", formatPreciseSeconds(damageModel.getUnconsciousTicks(), partialTick))
                    : Component.translatable("firstaid.gui.unconscious_left",
                            StringUtil.formatTickDuration(damageModel.getUnconsciousTicks(), 20F));
            int centerX = width / 2;
            int centerY = height / 2;
            guiGraphics.drawCenteredString(minecraft.font, title, centerX, centerY - 26, opaque(0xFFF1F1));
            guiGraphics.drawCenteredString(minecraft.font, timer, centerX, centerY - 10, opaque(0xCFCFCF));
            if (playerDamageModel != null && playerDamageModel.canGiveUp()) {
                guiGraphics.drawCenteredString(minecraft.font, Component.translatable("firstaid.gui.waiting_for_rescue"), centerX, centerY + 2, opaque(0xE8D9D9));
                guiGraphics.drawCenteredString(minecraft.font, Component.translatable("firstaid.gui.rescue_help"), centerX, centerY + 14, opaque(0xD8CACA));
                guiGraphics.drawCenteredString(minecraft.font, Component.translatable("firstaid.gui.give_up_hint", ClientHooks.GIVE_UP.getTranslatedKeyMessage()), centerX, centerY + 28, opaque(0xFFB3B3));
                renderGiveUpProgress(guiGraphics, minecraft, centerX, centerY + 44, partialTick);
            }
                    } else if (ClientEventHandler.hasInteractionPrompt()) {
            renderRescuePrompt(guiGraphics, minecraft, width / 2, height / 2 + 24, deltaTracker.getGameTimeDeltaTicks());
        }
    }

    private void tickStrengths(float targetPain, float targetSuppression) {
        lastPainStrength = painStrength;
        lastSuppressionStrength = suppressionStrength;
        painStrength = approachStrength(painStrength, targetPain, PAIN_GAIN, PAIN_DECAY);
        suppressionStrength = approachStrength(suppressionStrength, targetSuppression, SUPPRESSION_GAIN, SUPPRESSION_DECAY);
    }

    private static float approachStrength(float current, float target, float gain, float decay) {
        if (target > current) {
            return Math.min(target, current + gain);
        }
        return Math.max(target, current - decay);
    }

    private static void renderVignette(GuiGraphics guiGraphics, int width, int height, int red, int green, int blue, float intensity, int baseThickness) {
        if (intensity <= 0.0F) {
            return;
        }
        int layers = 7;
        for (int layer = 0; layer < layers; layer++) {
            float progress = (layer + 1) / (float) layers;
            float falloff = 1.0F - progress;
            int thickness = Math.max(4, Math.round(baseThickness * (0.35F + progress * (1.15F + intensity * 0.95F))));
            int alpha = Math.round((8.0F + 76.0F * intensity) * falloff * falloff);
            if (alpha > 0) {
                fillEdge(guiGraphics, width, height, color(alpha, red, green, blue), thickness);
            }
        }
        guiGraphics.fill(0, 0, width, height, color(Math.round(6.0F + (18.0F * intensity)), red, green, blue));
    }

    private static void fillEdge(GuiGraphics guiGraphics, int width, int height, int color, int thickness) {
        guiGraphics.fill(0, 0, width, thickness, color);
        guiGraphics.fill(0, height - thickness, width, height, color);
        guiGraphics.fill(0, thickness, thickness, height - thickness, color);
        guiGraphics.fill(width - thickness, thickness, width, height - thickness, color);
    }

    private static void renderDeathDangerOverlay(GuiGraphics guiGraphics, int width, int height, float deathDanger, float pulseTime) {
        float pulse = 0.72F + (0.20F + deathDanger * 0.24F) * Mth.sin(pulseTime * (0.07F + deathDanger * 0.03F));
        float intensity = Mth.clamp(deathDanger * pulse, 0.0F, 1.0F);
        int redCoverAlpha = Math.round(16.0F + 132.0F * deathDanger);
        guiGraphics.fill(0, 0, width, height, color(redCoverAlpha, 90, 0, 0));
        renderVignette(guiGraphics, width, height, 160, 10, 10, 0.18F + intensity * 0.82F, 28);
    }

    private static void renderGiveUpProgress(GuiGraphics guiGraphics, Minecraft minecraft, int centerX, int top, float partialTick) {
        int left = centerX - GIVE_UP_BAR_WIDTH / 2;
        int right = left + GIVE_UP_BAR_WIDTH;
        int bottom = top + GIVE_UP_BAR_HEIGHT;
        float progress = ClientEventHandler.getGiveUpHoldProgress(partialTick);
        int fillWidth = Math.round((GIVE_UP_BAR_WIDTH - 2) * progress);

        guiGraphics.fill(left, top, right, bottom, color(180, 24, 6, 6));
        guiGraphics.fill(left + 1, top + 1, right - 1, bottom - 1, color(180, 50, 12, 12));
        if (fillWidth > 0) {
            guiGraphics.fill(left + 1, top + 1, left + 1 + fillWidth, bottom - 1, color(220, 186, 32, 32));
        }

        guiGraphics.drawCenteredString(
                minecraft.font,
                Component.translatable(
                        "firstaid.gui.give_up_progress",
                        formatSingleDecimal(ClientEventHandler.getGiveUpHoldSeconds(partialTick)),
                        formatSingleDecimal(ClientEventHandler.getGiveUpHoldDurationSeconds())
                ),
                centerX,
                top + 12,
                opaque(0xFFB3B3)
        );
    }

    private static void renderRescuePrompt(GuiGraphics guiGraphics, Minecraft minecraft, int centerX, int centerY, float partialTick) {
        boolean executionPrompt = ClientEventHandler.isExecutionInteractionPrompt();
        guiGraphics.drawCenteredString(minecraft.font, ClientEventHandler.getInteractionPromptTitle(), centerX, centerY - 26, executionPrompt ? opaque(0xFFD7CC) : opaque(0xE9F7E2));
        guiGraphics.drawCenteredString(minecraft.font, ClientEventHandler.getInteractionPromptDetail(), centerX, centerY - 12, executionPrompt ? opaque(0xEFD2D2) : opaque(0xCFE4C5));
        if (ClientEventHandler.getInteractionHoldDurationSeconds() <= 0.0F) {
            return;
        }

        int left = centerX - RESCUE_BAR_WIDTH / 2;
        int right = left + RESCUE_BAR_WIDTH;
        int top = centerY + 2;
        int bottom = top + RESCUE_BAR_HEIGHT;
        float progress = ClientEventHandler.getInteractionHoldProgress(partialTick);
        int fillWidth = Math.round((RESCUE_BAR_WIDTH - 2) * progress);

        guiGraphics.fill(left, top, right, bottom, executionPrompt ? color(180, 48, 8, 8) : color(180, 10, 38, 14));
        guiGraphics.fill(left + 1, top + 1, right - 1, bottom - 1, executionPrompt ? color(180, 82, 18, 18) : color(180, 24, 74, 28));
        if (fillWidth > 0) {
            guiGraphics.fill(left + 1, top + 1, left + 1 + fillWidth, bottom - 1, executionPrompt ? color(220, 232, 70, 70) : color(220, 126, 214, 110));
        }

        guiGraphics.drawCenteredString(
                minecraft.font,
                ClientEventHandler.getInteractionPromptProgressText(partialTick),
                centerX,
                top + 12,
                executionPrompt ? opaque(0xFFB7A0) : opaque(0xD8F0D0)
        );
    }

    private static String formatPreciseSeconds(int remainingTicks, float partialTick) {
        float seconds = Math.max(0.1F, (Math.max(0, remainingTicks) - Math.max(0.0F, partialTick)) / 20.0F);
        return formatSingleDecimal(seconds);
    }

    private static String formatSingleDecimal(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static int color(int alpha, int red, int green, int blue) {
        return (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | blue & 255;
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }
}

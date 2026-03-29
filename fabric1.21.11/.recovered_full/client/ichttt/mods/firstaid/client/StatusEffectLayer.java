/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ichttt.mods.firstaid.FirstAid
 *  ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel
 *  ichttt.mods.firstaid.common.RegistryObjects
 *  ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel
 *  ichttt.mods.firstaid.common.util.CommonUtils
 *  net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
 *  net.minecraft.client.DeltaTracker
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.util.Mth
 *  net.minecraft.util.StringUtil
 *  net.minecraft.world.entity.player.Player
 */
package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.ClientEventHandler;
import ichttt.mods.firstaid.client.ClientHooks;
import ichttt.mods.firstaid.client.SuppressionFeedbackController;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;

public class StatusEffectLayer
implements HudRenderCallback {
    public static final StatusEffectLayer INSTANCE = new StatusEffectLayer();
    private static final int GIVE_UP_BAR_WIDTH = 144;
    private static final int GIVE_UP_BAR_HEIGHT = 8;
    private static final int RESCUE_BAR_WIDTH = 144;
    private static final int RESCUE_BAR_HEIGHT = 8;
    private static final float PAIN_GAIN = 0.045f;
    private static final float PAIN_DECAY = 0.015f;
    private static final float PAIN_INTENSITY_MULTIPLIER = 2.0f;
    private static final float PAIN_INTENSITY_MAX = 2.0f;
    private static final int PAIN_BASE_THICKNESS = 20;
    private static final float SUPPRESSION_GAIN = 0.18f;
    private static final float SUPPRESSION_DECAY = 0.012f;
    private static final float SUPPRESSION_INTENSITY_MULTIPLIER = 2.0f;
    private static final float SUPPRESSION_INTENSITY_MAX = 2.0f;
    private float painStrength;
    private float lastPainStrength;
    private float suppressionStrength;
    private float lastSuppressionStrength;

    public void onHudRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        float tinnitusStrength;
        float intensity;
        float pulse;
        PlayerDamageModel model;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel((Player)minecraft.player);
        if (damageModel == null || !FirstAid.isSynced) {
            return;
        }
        if (!minecraft.player.isAlive() && damageModel.getUnconsciousTicks() <= 0) {
            return;
        }
        PlayerDamageModel playerDamageModel = damageModel instanceof PlayerDamageModel ? (model = (PlayerDamageModel)damageModel) : null;
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        float deathDanger = playerDamageModel == null ? 0.0f : playerDamageModel.getDeathCountdownDangerProgress();
        boolean painSuppressed = minecraft.player.hasEffect(RegistryObjects.MORPHINE_EFFECT) || minecraft.player.hasEffect(RegistryObjects.PAINKILLER_EFFECT);
        float basePain = painSuppressed || playerDamageModel == null ? 0.0f : playerDamageModel.getPainVisualStrength();
        float dangerPain = deathDanger <= 0.0f ? 0.0f : Mth.clamp((float)(0.18f + deathDanger * 0.82f), (float)0.0f, (float)1.0f);
        float targetPain = Math.max(basePain, dangerPain);
        SuppressionFeedbackController suppressionFeedbackController = ClientEventHandler.getSuppressionFeedbackController();
        float modelSuppression = playerDamageModel == null ? Math.min(1.0f, (float)damageModel.getAdrenalineTicks() / 200.0f) : playerDamageModel.getSuppressionIntensity();
        float suppressionScale = FirstAid.lowSuppressionEnabled ? 0.4f : 1.0f;
        float targetSuppression = Math.max(modelSuppression, suppressionFeedbackController.getVisualStrength()) * suppressionScale;
        this.tickStrengths(targetPain, targetSuppression);
        float smoothPain = Mth.lerp((float)deltaTracker.getGameTimeDeltaTicks(), (float)this.lastPainStrength, (float)this.painStrength);
        float smoothSuppression = Mth.lerp((float)deltaTracker.getGameTimeDeltaTicks(), (float)this.lastSuppressionStrength, (float)this.suppressionStrength);
        float pulseTime = (float)minecraft.player.tickCount + deltaTracker.getGameTimeDeltaTicks();
        if (smoothPain > 0.0f) {
            pulse = deathDanger > 0.0f ? 0.82f + (0.18f + deathDanger * 0.27f) * Mth.sin((double)(pulseTime * (0.08f + deathDanger * 0.04f))) : 0.9f + 0.1f * Mth.sin((double)(pulseTime * 0.32f));
            intensity = Math.min(2.0f, smoothPain * 2.0f * pulse);
            float rangeScale = 1.0f + Mth.clamp((float)smoothPain, (float)0.0f, (float)1.0f);
            int thickness = Math.round(20.0f * rangeScale);
            StatusEffectLayer.renderVignette(guiGraphics, width, height, 138, 24, 24, intensity, thickness);
        }
        if (smoothSuppression > 0.0f) {
            pulse = 0.9f + 0.1f * Mth.sin((double)(pulseTime * 0.46f + 0.8f));
            intensity = Math.min(2.0f, (0.45f + smoothSuppression * 0.75f) * 2.0f * pulse);
            StatusEffectLayer.renderVignette(guiGraphics, width, height, 18, 24, 34, intensity, 30);
            StatusEffectLayer.renderVignette(guiGraphics, width, height, 88, 102, 128, Math.min(2.0f, intensity * 0.72f), 18);
            guiGraphics.fill(0, 0, width, height, StatusEffectLayer.color(Math.round(12.0f + 48.0f * smoothSuppression * 2.0f), 16, 18, 22));
        }
        if ((tinnitusStrength = suppressionFeedbackController.getTinnitusStrength()) > 0.0f) {
            float pulse2 = 0.84f + 0.16f * Mth.sin((double)(pulseTime * 0.77f + 1.3f));
            int alpha = Math.round(Math.min(42.0f, (8.0f + 24.0f * tinnitusStrength) * pulse2));
            guiGraphics.fill(0, 0, width, height, StatusEffectLayer.color(alpha, 198, 205, 214));
            StatusEffectLayer.renderVignette(guiGraphics, width, height, 210, 214, 224, Math.min(0.38f, tinnitusStrength * 0.22f), 16);
        }
        if (damageModel.getUnconsciousTicks() > 0) {
            guiGraphics.fill(0, 0, width, height, StatusEffectLayer.color(178, 0, 0, 0));
            StatusEffectLayer.renderVignette(guiGraphics, width, height, 0, 0, 0, 0.8f, 24);
            if (deathDanger > 0.0f) {
                StatusEffectLayer.renderDeathDangerOverlay(guiGraphics, width, height, deathDanger, pulseTime);
            }
            float partialTick = deltaTracker.getGameTimeDeltaTicks();
            MutableComponent title = Component.translatable((String)(playerDamageModel != null ? playerDamageModel.getUnconsciousReasonKey() : (damageModel.isCriticalConditionActive() ? "firstaid.gui.critical_condition" : "firstaid.gui.unconscious")));
            MutableComponent timer = playerDamageModel != null && playerDamageModel.canGiveUp() ? Component.translatable((String)"firstaid.gui.death_countdown_seconds", (Object[])new Object[]{StatusEffectLayer.formatPreciseSeconds(damageModel.getUnconsciousTicks(), partialTick)}) : Component.translatable((String)"firstaid.gui.unconscious_left", (Object[])new Object[]{StringUtil.formatTickDuration((int)damageModel.getUnconsciousTicks(), (float)20.0f)});
            int centerX = width / 2;
            int centerY = height / 2;
            guiGraphics.drawCenteredString(minecraft.font, (Component)title, centerX, centerY - 26, StatusEffectLayer.opaque(0xFFF1F1));
            guiGraphics.drawCenteredString(minecraft.font, (Component)timer, centerX, centerY - 10, StatusEffectLayer.opaque(0xCFCFCF));
            if (playerDamageModel != null && playerDamageModel.canGiveUp()) {
                guiGraphics.drawCenteredString(minecraft.font, (Component)Component.translatable((String)"firstaid.gui.waiting_for_rescue"), centerX, centerY + 2, StatusEffectLayer.opaque(15260121));
                guiGraphics.drawCenteredString(minecraft.font, (Component)Component.translatable((String)"firstaid.gui.rescue_help"), centerX, centerY + 14, StatusEffectLayer.opaque(14207690));
                guiGraphics.drawCenteredString(minecraft.font, (Component)Component.translatable((String)"firstaid.gui.give_up_hint", (Object[])new Object[]{ClientHooks.GIVE_UP.getTranslatedKeyMessage()}), centerX, centerY + 28, StatusEffectLayer.opaque(0xFFB3B3));
                StatusEffectLayer.renderGiveUpProgress(guiGraphics, minecraft, centerX, centerY + 44, partialTick);
            }
        } else if (ClientEventHandler.hasRescuePrompt()) {
            StatusEffectLayer.renderRescuePrompt(guiGraphics, minecraft, width / 2, height / 2 + 24, deltaTracker.getGameTimeDeltaTicks());
        }
    }

    private void tickStrengths(float targetPain, float targetSuppression) {
        this.lastPainStrength = this.painStrength;
        this.lastSuppressionStrength = this.suppressionStrength;
        this.painStrength = StatusEffectLayer.approachStrength(this.painStrength, targetPain, 0.045f, 0.015f);
        this.suppressionStrength = StatusEffectLayer.approachStrength(this.suppressionStrength, targetSuppression, 0.18f, 0.012f);
    }

    private static float approachStrength(float current, float target, float gain, float decay) {
        if (target > current) {
            return Math.min(target, current + gain);
        }
        return Math.max(target, current - decay);
    }

    private static void renderVignette(GuiGraphics guiGraphics, int width, int height, int red, int green, int blue, float intensity, int baseThickness) {
        if (intensity <= 0.0f) {
            return;
        }
        int layers = 7;
        for (int layer = 0; layer < layers; ++layer) {
            float progress = (float)(layer + 1) / (float)layers;
            float falloff = 1.0f - progress;
            int thickness = Math.max(4, Math.round((float)baseThickness * (0.35f + progress * (1.15f + intensity * 0.95f))));
            int alpha = Math.round((8.0f + 76.0f * intensity) * falloff * falloff);
            if (alpha <= 0) continue;
            StatusEffectLayer.fillEdge(guiGraphics, width, height, StatusEffectLayer.color(alpha, red, green, blue), thickness);
        }
        guiGraphics.fill(0, 0, width, height, StatusEffectLayer.color(Math.round(6.0f + 18.0f * intensity), red, green, blue));
    }

    private static void fillEdge(GuiGraphics guiGraphics, int width, int height, int color, int thickness) {
        guiGraphics.fill(0, 0, width, thickness, color);
        guiGraphics.fill(0, height - thickness, width, height, color);
        guiGraphics.fill(0, thickness, thickness, height - thickness, color);
        guiGraphics.fill(width - thickness, thickness, width, height - thickness, color);
    }

    private static void renderDeathDangerOverlay(GuiGraphics guiGraphics, int width, int height, float deathDanger, float pulseTime) {
        float pulse = 0.72f + (0.2f + deathDanger * 0.24f) * Mth.sin((double)(pulseTime * (0.07f + deathDanger * 0.03f)));
        float intensity = Mth.clamp((float)(deathDanger * pulse), (float)0.0f, (float)1.0f);
        int redCoverAlpha = Math.round(16.0f + 132.0f * deathDanger);
        guiGraphics.fill(0, 0, width, height, StatusEffectLayer.color(redCoverAlpha, 90, 0, 0));
        StatusEffectLayer.renderVignette(guiGraphics, width, height, 160, 10, 10, 0.18f + intensity * 0.82f, 28);
    }

    private static void renderGiveUpProgress(GuiGraphics guiGraphics, Minecraft minecraft, int centerX, int top, float partialTick) {
        int left = centerX - 72;
        int right = left + 144;
        int bottom = top + 8;
        float progress = ClientEventHandler.getGiveUpHoldProgress(partialTick);
        int fillWidth = Math.round(142.0f * progress);
        guiGraphics.fill(left, top, right, bottom, StatusEffectLayer.color(180, 24, 6, 6));
        guiGraphics.fill(left + 1, top + 1, right - 1, bottom - 1, StatusEffectLayer.color(180, 50, 12, 12));
        if (fillWidth > 0) {
            guiGraphics.fill(left + 1, top + 1, left + 1 + fillWidth, bottom - 1, StatusEffectLayer.color(220, 186, 32, 32));
        }
        guiGraphics.drawCenteredString(minecraft.font, (Component)Component.translatable((String)"firstaid.gui.give_up_progress", (Object[])new Object[]{StatusEffectLayer.formatSingleDecimal(ClientEventHandler.getGiveUpHoldSeconds(partialTick)), StatusEffectLayer.formatSingleDecimal(ClientEventHandler.getGiveUpHoldDurationSeconds())}), centerX, top + 12, StatusEffectLayer.opaque(0xFFB3B3));
    }

    private static void renderRescuePrompt(GuiGraphics guiGraphics, Minecraft minecraft, int centerX, int centerY, float partialTick) {
        guiGraphics.drawCenteredString(minecraft.font, ClientEventHandler.getRescuePromptTitle(), centerX, centerY - 26, StatusEffectLayer.opaque(15333346));
        guiGraphics.drawCenteredString(minecraft.font, ClientEventHandler.getRescuePromptDetail(), centerX, centerY - 12, StatusEffectLayer.opaque(13624517));
        int left = centerX - 72;
        int right = left + 144;
        int top = centerY + 2;
        int bottom = top + 8;
        float progress = ClientEventHandler.getRescueHoldProgress(partialTick);
        int fillWidth = Math.round(142.0f * progress);
        guiGraphics.fill(left, top, right, bottom, StatusEffectLayer.color(180, 10, 38, 14));
        guiGraphics.fill(left + 1, top + 1, right - 1, bottom - 1, StatusEffectLayer.color(180, 24, 74, 28));
        if (fillWidth > 0) {
            guiGraphics.fill(left + 1, top + 1, left + 1 + fillWidth, bottom - 1, StatusEffectLayer.color(220, 126, 214, 110));
        }
        guiGraphics.drawCenteredString(minecraft.font, (Component)Component.translatable((String)"firstaid.gui.rescue_progress", (Object[])new Object[]{StatusEffectLayer.formatSingleDecimal(ClientEventHandler.getRescueHoldSeconds(partialTick)), StatusEffectLayer.formatSingleDecimal(ClientEventHandler.getRescueHoldDurationSeconds())}), centerX, top + 12, StatusEffectLayer.opaque(14217424));
    }

    private static String formatPreciseSeconds(int remainingTicks, float partialTick) {
        float seconds = Math.max(0.1f, ((float)Math.max(0, remainingTicks) - Math.max(0.0f, partialTick)) / 20.0f);
        return StatusEffectLayer.formatSingleDecimal(seconds);
    }

    private static String formatSingleDecimal(float value) {
        return String.format(Locale.ROOT, "%.1f", Float.valueOf(value));
    }

    private static int color(int alpha, int red, int green, int blue) {
        return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }
}


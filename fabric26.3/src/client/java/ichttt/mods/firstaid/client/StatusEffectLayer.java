package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.medicine.MedicineStatusDisplay;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.Locale;
import javax.annotation.Nullable;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;

public class StatusEffectLayer implements HudElement {
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

   public void extractRenderState(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player != null && !minecraft.gui.hud.isHidden()) {
         AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(minecraft.player);
         if (damageModel != null && FirstAid.isSynced) {
            if (minecraft.player.isAlive() || damageModel.getUnconsciousTicks() > 0) {
               PlayerDamageModel playerDamageModel = damageModel instanceof PlayerDamageModel model ? model : null;
               int width = minecraft.getWindow().getGuiScaledWidth();
               int height = minecraft.getWindow().getGuiScaledHeight();
               float deathDanger = playerDamageModel == null ? 0.0F : playerDamageModel.getDeathCountdownDangerProgress();
               boolean painSuppressed = minecraft.player.hasEffect(RegistryObjects.MORPHINE_EFFECT)
                  || minecraft.player.hasEffect(RegistryObjects.PAINKILLER_EFFECT);
               float basePain = playerDamageModel != null ? playerDamageModel.getPainVisualStrength(painSuppressed) : 0.0F;
               float dangerPain = deathDanger <= 0.0F ? 0.0F : Mth.clamp(0.18F + deathDanger * 0.82F, 0.0F, 1.0F);
               float targetPain = Math.max(basePain, dangerPain);
               SuppressionFeedbackController suppressionFeedbackController = ClientEventHandler.getSuppressionFeedbackController();
               float modelSuppression = playerDamageModel == null
                  ? Math.min(1.0F, damageModel.getAdrenalineTicks() / 200.0F)
                  : playerDamageModel.getSuppressionIntensity();
               float suppressionScale = FirstAid.lowSuppressionEnabled ? FirstAid.lowSuppressionMultiplier : 1.0F;
               float targetSuppression = Math.max(modelSuppression, suppressionFeedbackController.getVisualStrength()) * suppressionScale;
               this.tickStrengths(targetPain, targetSuppression);
               float smoothPain = Mth.lerp(deltaTracker.getGameTimeDeltaTicks(), this.lastPainStrength, this.painStrength);
               float smoothSuppression = Mth.lerp(deltaTracker.getGameTimeDeltaTicks(), this.lastSuppressionStrength, this.suppressionStrength);
               float pulseTime = minecraft.player.tickCount + deltaTracker.getGameTimeDeltaTicks();
               ClientEventHandler.getPainVisualEffectsController().renderOverlay(guiGraphics, width, height, deltaTracker.getGameTimeDeltaTicks(), pulseTime);
               if (deathDanger > 0.0F && damageModel.getUnconsciousTicks() <= 0) {
                  renderDeathDangerOverlay(guiGraphics, width, height, deathDanger, pulseTime);
               }

               // Soft cool edge only — continuous gray-white rim is drawn by PainVisualEffectsController.
               if (smoothSuppression > 0.0F) {
                  float pulse = 0.9F + 0.1F * Mth.sin(pulseTime * 0.46F + 0.8F);
                  float intensity = Math.min(0.55F, smoothSuppression * 0.40F * pulse);
                  renderVignette(guiGraphics, width, height, 40, 46, 58, intensity * 0.50F, 16);
               }

               float tinnitusStrength = suppressionFeedbackController.getTinnitusStrength();
               if (tinnitusStrength > 0.0F) {
                  float pulse = 0.84F + 0.16F * Mth.sin(pulseTime * 0.77F + 1.3F);
                  renderVignette(guiGraphics, width, height, 210, 214, 224, Math.min(0.38F, tinnitusStrength * 0.22F), 16);
               }

               if (damageModel.getUnconsciousTicks() > 0) {
                  // Light dim only — continuous blindness was removed; keep surroundings readable.
                  guiGraphics.fill(0, 0, width, height, color(78, 0, 0, 0));
                  renderVignette(guiGraphics, width, height, 0, 0, 0, 0.42F, 20);
                  if (deathDanger > 0.0F) {
                     renderDeathDangerOverlay(guiGraphics, width, height, deathDanger, pulseTime);
                  }

                  float partialTick = deltaTracker.getGameTimeDeltaTicks();
                  Component title = Component.translatable(
                     playerDamageModel != null
                        ? playerDamageModel.getUnconsciousReasonKey()
                        : (damageModel.isCriticalConditionActive() ? "firstaid.gui.critical_condition" : "firstaid.gui.unconscious")
                  );
                  Component timer = playerDamageModel != null && playerDamageModel.canGiveUp()
                     ? Component.translatable(
                        "firstaid.gui.death_countdown_seconds", new Object[]{formatPreciseSeconds(damageModel.getUnconsciousTicks(), partialTick)}
                     )
                     : Component.translatable(
                        "firstaid.gui.unconscious_left", new Object[]{StringUtil.formatTickDuration(damageModel.getUnconsciousTicks(), 20.0F)}
                     );
                  int centerX = width / 2;
                  int centerY = height / 2;
                  guiGraphics.centeredText(minecraft.font, title, centerX, centerY - 26, opaque(16773617));
                  guiGraphics.centeredText(minecraft.font, timer, centerX, centerY - 10, opaque(13619151));
                  if (playerDamageModel != null && playerDamageModel.canGiveUp()) {
                     int lineY = centerY + 2;
                     guiGraphics.centeredText(
                        minecraft.font, Component.translatable("firstaid.gui.waiting_for_rescue"), centerX, lineY, opaque(15260121)
                     );
                     lineY += 12;
                     lineY = drawCenteredLines(
                        guiGraphics,
                        minecraft,
                        new Component[]{
                           Component.translatable("firstaid.gui.rescue_help.line1"),
                           Component.translatable("firstaid.gui.rescue_help.line2")
                        },
                        centerX,
                        lineY,
                        opaque(14207690),
                        11
                     );
                     lineY += 4;
                     if (ClientEventHandler.isSelfDefibInteractionPrompt()) {
                        lineY = renderSelfDefibProgress(guiGraphics, minecraft, centerX, lineY, partialTick);
                        lineY += 6;
                     }
                     guiGraphics.centeredText(
                        minecraft.font,
                        Component.translatable("firstaid.gui.give_up_hint", new Object[]{ClientHooks.GIVE_UP.getTranslatedKeyMessage()}),
                        centerX,
                        lineY,
                        opaque(16757683)
                     );
                     renderGiveUpProgress(guiGraphics, minecraft, centerX, lineY + 14, partialTick);
                  }
               } else if (ClientEventHandler.hasInteractionPrompt()) {
                  renderRescuePrompt(guiGraphics, minecraft, width / 2, height / 2 + 24, deltaTracker.getGameTimeDeltaTicks());
               }

            }
         }
      }
   }

   private void tickStrengths(float targetPain, float targetSuppression) {
      this.lastPainStrength = this.painStrength;
      this.lastSuppressionStrength = this.suppressionStrength;
      this.painStrength = approachStrength(this.painStrength, targetPain, 0.045F, 0.015F);
      this.suppressionStrength = approachStrength(this.suppressionStrength, targetSuppression, 0.18F, 0.012F);
   }

   private static float approachStrength(float current, float target, float gain, float decay) {
      return target > current ? Math.min(target, current + gain) : Math.max(target, current - decay);
   }

   private static void renderVignette(GuiGraphicsExtractor guiGraphics, int width, int height, int red, int green, int blue, float intensity, int baseThickness) {
      if (!(intensity <= 0.0F)) {
         int layers = 7;

         for (int layer = 0; layer < layers; layer++) {
            float progress = (float)(layer + 1) / layers;
            float falloff = 1.0F - progress;
            int thickness = Math.max(4, Math.round(baseThickness * (0.35F + progress * (1.15F + intensity * 0.95F))));
            int alpha = Math.round((8.0F + 76.0F * intensity) * falloff * falloff);
            if (alpha > 0) {
               fillEdge(guiGraphics, width, height, color(alpha, red, green, blue), thickness);
            }
         }
      }
   }

   private static void fillEdge(GuiGraphicsExtractor guiGraphics, int width, int height, int color, int thickness) {
      guiGraphics.fill(0, 0, width, thickness, color);
      guiGraphics.fill(0, height - thickness, width, height, color);
      guiGraphics.fill(0, thickness, thickness, height - thickness, color);
      guiGraphics.fill(width - thickness, thickness, width, height - thickness, color);
   }

   private static void renderDeathDangerOverlay(GuiGraphicsExtractor guiGraphics, int width, int height, float deathDanger, float pulseTime) {
      float pulse = 0.72F + (0.2F + deathDanger * 0.24F) * Mth.sin(pulseTime * (0.07F + deathDanger * 0.03F));
      float intensity = Mth.clamp(deathDanger * pulse, 0.0F, 1.0F);
      int redCoverAlpha = Math.round(16.0F + 132.0F * deathDanger);
      guiGraphics.fill(0, 0, width, height, color(redCoverAlpha, 90, 0, 0));
      renderVignette(guiGraphics, width, height, 160, 10, 10, 0.18F + intensity * 0.82F, 28);
   }

      private static int renderSelfDefibProgress(GuiGraphicsExtractor guiGraphics, Minecraft minecraft, int centerX, int top, float partialTick) {
      guiGraphics.centeredText(
         minecraft.font,
         ClientEventHandler.getInteractionPromptTitle(),
         centerX,
         top,
         opaque(0x55E0FF)
      );
      guiGraphics.centeredText(
         minecraft.font,
         ClientEventHandler.getInteractionPromptDetail(),
         centerX,
         top + 11,
         opaque(0x8FEFFF)
      );
      int barTop = top + 24;
      int left = centerX - 72;
      int right = left + 144;
      int bottom = barTop + 8;
      float progress = ClientEventHandler.getInteractionHoldProgress(partialTick);
      int fillWidth = Math.round(142.0F * progress);
      guiGraphics.fill(left, barTop, right, bottom, color(180, 6, 28, 36));
      guiGraphics.fill(left + 1, barTop + 1, right - 1, bottom - 1, color(180, 10, 48, 58));
      if (fillWidth > 0) {
         guiGraphics.fill(left + 1, barTop + 1, left + 1 + fillWidth, bottom - 1, color(230, 64, 220, 255));
      }
      guiGraphics.centeredText(
         minecraft.font,
         ClientEventHandler.getInteractionPromptProgressText(partialTick),
         centerX,
         barTop + 12,
         opaque(0x9CF6FF)
      );
      return barTop + 24;
   }

   private static int drawCenteredLines(
      GuiGraphicsExtractor guiGraphics, Minecraft minecraft, Component[] lines, int centerX, int startY, int color, int lineHeight
   ) {
      int y = startY;
      for (Component line : lines) {
         guiGraphics.centeredText(minecraft.font, line, centerX, y, color);
         y += lineHeight;
      }
      return y;
   }

   private static void renderGiveUpProgress(GuiGraphicsExtractor guiGraphics, Minecraft minecraft, int centerX, int top, float partialTick) {
      int left = centerX - 72;
      int right = left + 144;
      int bottom = top + 8;
      float progress = ClientEventHandler.getGiveUpHoldProgress(partialTick);
      int fillWidth = Math.round(142.0F * progress);
      guiGraphics.fill(left, top, right, bottom, color(180, 24, 6, 6));
      guiGraphics.fill(left + 1, top + 1, right - 1, bottom - 1, color(180, 50, 12, 12));
      if (fillWidth > 0) {
         guiGraphics.fill(left + 1, top + 1, left + 1 + fillWidth, bottom - 1, color(220, 186, 32, 32));
      }

      guiGraphics.centeredText(
         minecraft.font,
         Component.translatable(
            "firstaid.gui.give_up_progress",
            new Object[]{
               formatSingleDecimal(ClientEventHandler.getGiveUpHoldSeconds(partialTick)),
               formatSingleDecimal(ClientEventHandler.getGiveUpHoldDurationSeconds())
            }
         ),
         centerX,
         top + 12,
         opaque(16757683)
      );
   }

   private static void renderRescuePrompt(GuiGraphicsExtractor guiGraphics, Minecraft minecraft, int centerX, int centerY, float partialTick) {
      boolean healingPrompt = ClientEventHandler.isHealingInteractionPrompt();
      boolean executionPrompt = ClientEventHandler.isExecutionInteractionPrompt();
      guiGraphics.centeredText(
         minecraft.font,
         ClientEventHandler.getInteractionPromptTitle(),
         centerX,
         centerY - 26,
         healingPrompt ? opaque(7657471) : (executionPrompt ? opaque(16767436) : opaque(15333346))
      );
      guiGraphics.centeredText(
         minecraft.font,
         ClientEventHandler.getInteractionPromptDetail(),
         centerX,
         centerY - 12,
         healingPrompt ? opaque(10395294) : (executionPrompt ? opaque(15717458) : opaque(13624517))
      );
      if (ClientEventHandler.getInteractionHoldDurationSeconds() <= 0.0F) {
         return;
      }

      int left = centerX - 72;
      int right = left + 144;
      int top = centerY + 2;
      int bottom = top + 8;
      float progress = ClientEventHandler.getInteractionHoldProgress(partialTick);
      int fillWidth = Math.round(142.0F * progress);
      guiGraphics.fill(left, top, right, bottom, healingPrompt ? color(180, 8, 28, 36) : (executionPrompt ? color(180, 48, 8, 8) : color(180, 10, 38, 14)));
      guiGraphics.fill(
         left + 1,
         top + 1,
         right - 1,
         bottom - 1,
         healingPrompt ? color(180, 12, 54, 66) : (executionPrompt ? color(180, 82, 18, 18) : color(180, 24, 74, 28))
      );
      if (fillWidth > 0) {
         guiGraphics.fill(
            left + 1,
            top + 1,
            left + 1 + fillWidth,
            bottom - 1,
            healingPrompt ? color(220, 88, 224, 210) : (executionPrompt ? color(220, 232, 70, 70) : color(220, 126, 214, 110))
         );
      }

      guiGraphics.centeredText(
         minecraft.font,
         ClientEventHandler.getInteractionPromptProgressText(partialTick),
         centerX,
         top + 12,
         healingPrompt ? opaque(11460492) : (executionPrompt ? opaque(16760992) : opaque(14217424))
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
      return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
   }

   private static int opaque(int rgb) {
      return 0xFF000000 | rgb;
   }

   private static void renderStatusSummary(
      GuiGraphicsExtractor guiGraphics, Minecraft minecraft, AbstractPlayerDamageModel damageModel, @Nullable PlayerDamageModel playerDamageModel
   ) {
      int lineY = 8;
            if (damageModel.getPainLevel() > 0) {
         boolean painSuppressed = minecraft.player.hasEffect(RegistryObjects.PAINKILLER_EFFECT)
            || minecraft.player.hasEffect(RegistryObjects.MORPHINE_EFFECT);
         Component painText = painSuppressed
            ? Component.translatable("firstaid.gui.status.pain_suppressed")
            : Component.translatable("firstaid.gui.status.pain", new Object[]{Component.translatable(getPainSeverityKey(damageModel.getPainLevel()))});
         guiGraphics.text(minecraft.font, painText, 8, lineY, painSuppressed ? 9425919 : 16747146);
         lineY += 10;
      }

      if (playerDamageModel != null) {
         int pulse = playerDamageModel.getAddictionPulseType();
         if (pulse == PlayerDamageModel.PULSE_INCREASE) {
            guiGraphics.text(minecraft.font, Component.translatable("firstaid.gui.status.addiction_increase"), 8, lineY, 0xE8A0A0);
            lineY += 10;
         } else if (pulse == PlayerDamageModel.PULSE_ULTRA_INCREASE) {
            guiGraphics.text(minecraft.font, Component.translatable("firstaid.gui.status.addiction_ultra_increase"), 8, lineY, 0xFF6A6A);
            lineY += 10;
         } else if (pulse == PlayerDamageModel.PULSE_DECREASE) {
            guiGraphics.text(minecraft.font, Component.translatable("firstaid.gui.status.addiction_decrease"), 8, lineY, 0x90C090);
            lineY += 10;
         }
         if (playerDamageModel.getWithdrawalEpisodeTicksLeft() > 0) {
            int flags = playerDamageModel.getWithdrawalEpisodeType();
            guiGraphics.text(minecraft.font, Component.translatable("firstaid.gui.status.withdrawal_pain"), 8, lineY, 0xC8A2C8);
            lineY += 10;
            if ((flags & PlayerDamageModel.EPISODE_DARKNESS) != 0) {
               guiGraphics.text(minecraft.font, Component.translatable("firstaid.gui.status.withdrawal_darkness"), 8, lineY, 0xC8A2C8);
               lineY += 10;
            }
            if ((flags & PlayerDamageModel.EPISODE_NAUSEA) != 0) {
               guiGraphics.text(minecraft.font, Component.translatable("firstaid.gui.status.withdrawal_nausea"), 8, lineY, 0xC8A2C8);
               lineY += 10;
            }
            if ((flags & PlayerDamageModel.EPISODE_WEAKNESS) != 0) {
               guiGraphics.text(minecraft.font, Component.translatable("firstaid.gui.status.withdrawal_weakness"), 8, lineY, 0xC8A2C8);
               lineY += 10;
            }
            if ((flags & PlayerDamageModel.EPISODE_SLOWNESS) != 0) {
               guiGraphics.text(minecraft.font, Component.translatable("firstaid.gui.status.withdrawal_slowness"), 8, lineY, 0xC8A2C8);
               lineY += 10;
            }
         }
      }

      if (damageModel.getAdrenalineLevel() > 0) {
         int suppressionLevel = playerDamageModel != null ? playerDamageModel.getSuppressionLevel() : damageModel.getAdrenalineLevel();
         guiGraphics.text(
            minecraft.font,
            Component.translatable("firstaid.gui.status.suppression", new Object[]{Component.translatable(getSuppressionSeverityKey(suppressionLevel))}),
            8,
            lineY,
            12637930
         );
         lineY += 10;
      }

      if (damageModel.getUnconsciousTicks() > 0) {
         guiGraphics.text(
            minecraft.font,
            Component.translatable(
               playerDamageModel != null
                  ? playerDamageModel.getUnconsciousReasonKey()
                  : (damageModel.isCriticalConditionActive() ? "firstaid.gui.critical_condition" : "firstaid.gui.unconscious")
            ),
            8,
            lineY,
            16766421
         );
         lineY += 10;
      }

      for (MedicineStatusDisplay display : MedicineStatusClientHelper.collect(minecraft.player)) {
         lineY = MedicineStatusClientHelper.drawStatusLine(guiGraphics, minecraft.font, display, 8, lineY);
      }
   }

   private static String getPainSeverityKey(int painLevel) {
      return switch (painLevel) {
         case 1 -> "firstaid.gui.pain.mild";
         case 2 -> "firstaid.gui.pain.moderate";
         case 3 -> "firstaid.gui.pain.severe";
         case 4 -> "firstaid.gui.pain.extreme";
         default -> "firstaid.gui.pain.critical";
      };
   }

   private static String getSuppressionSeverityKey(int suppressionLevel) {
      return switch (suppressionLevel) {
         case 1 -> "firstaid.gui.suppression.low";
         case 2 -> "firstaid.gui.suppression.medium";
         default -> "firstaid.gui.suppression.high";
      };
   }
}

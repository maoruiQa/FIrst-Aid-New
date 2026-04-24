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

package ichttt.mods.firstaid.client.util;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

import java.util.Random;

public final class PlayerModelRenderer {
    private static final Random RANDOM = new Random();

    private static int angle;
    private static boolean otherWay;
    private static int cooldown;

    private PlayerModelRenderer() {
    }

    public static void renderPlayerHealth(int xOffset, int yOffset, AbstractPlayerDamageModel damageModel, boolean fourColors, GuiGraphicsExtractor guiGraphics, boolean flashState, float alpha, float partialTicks) {
        int renderX = xOffset + 8;
        int renderY = yOffset + 8;
        int opacity = Math.max(64, Math.min(255, 255 - Math.round(alpha)));
        int borderColor = ARGB.color(opacity, 0, 0, 0);
        int deadColor = ARGB.color(opacity, 60, 60, 60);

        if (FirstAidConfig.CLIENT.enableEasterEggs.get() && (EventCalendar.isAFDay() || EventCalendar.isHalloween())) {
            float renderAngle = angle;
            if (cooldown == 0) {
                renderAngle += (otherWay ? -partialTicks : partialTicks) * 2F;
            }
            if (FirstAidConfig.CLIENT.pos.get() == FirstAidConfig.Client.Position.BOTTOM_LEFT
                    || FirstAidConfig.CLIENT.pos.get() == FirstAidConfig.Client.Position.TOP_LEFT) {
                renderX += (int) (renderAngle * 1.5F);
            } else {
                renderX += (int) (renderAngle * 0.5F);
            }
        }

        drawPart(guiGraphics, damageModel.HEAD, renderX + 8, renderY, 16, 16, borderColor, deadColor, fourColors, flashState, opacity);
        drawPart(guiGraphics, damageModel.BODY, renderX + 8, renderY + 16, 16, 24, borderColor, deadColor, fourColors, flashState, opacity);
        drawPart(guiGraphics, damageModel.LEFT_ARM, renderX, renderY + 16, 8, 24, borderColor, deadColor, fourColors, flashState, opacity);
        drawPart(guiGraphics, damageModel.RIGHT_ARM, renderX + 24, renderY + 16, 8, 24, borderColor, deadColor, fourColors, flashState, opacity);
        drawPart(guiGraphics, damageModel.LEFT_LEG, renderX + 8, renderY + 40, 8, 16, borderColor, deadColor, fourColors, flashState, opacity);
        drawPart(guiGraphics, damageModel.RIGHT_LEG, renderX + 16, renderY + 40, 8, 16, borderColor, deadColor, fourColors, flashState, opacity);
        drawPart(guiGraphics, damageModel.LEFT_FOOT, renderX + 8, renderY + 56, 8, 8, borderColor, deadColor, fourColors, flashState, opacity);
        drawPart(guiGraphics, damageModel.RIGHT_FOOT, renderX + 16, renderY + 56, 8, 8, borderColor, deadColor, fourColors, flashState, opacity);
    }

    private static void drawPart(GuiGraphicsExtractor guiGraphics, AbstractDamageablePart part, int x, int y, int width, int height, int borderColor, int deadColor, boolean fourColors, boolean flashState, int opacity) {
        int fillColor = getColor(part, fourColors, opacity);
        if (part.currentHealth <= 0.001F) {
            fillColor = deadColor;
        }

        if (flashState && part.currentHealth > 0.001F) {
            fillColor = brighten(fillColor, 1.2F);
        }

        guiGraphics.fill(x, y, x + width, y + height, borderColor);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fillColor);
    }

    private static int getColor(AbstractDamageablePart part, boolean fourColors, int opacity) {
        if (part.currentHealth <= 0.001F) {
            return ARGB.color(opacity, 60, 60, 60);
        }

        float percent = CommonUtils.getVisibleHealthRatio(part);

        if (percent > 0.85F) {
            return ARGB.color(opacity, 60, 220, 60);
        }
        if (percent > 0.65F) {
            return ARGB.color(opacity, 180, 235, 60);
        }
        if (percent > 0.4F) {
            return ARGB.color(opacity, 245, 140, 60);
        }
        return ARGB.color(opacity, 235, 70, 60);
    }

    private static int brighten(int color, float factor) {
        int alpha = ARGB.alpha(color);
        int red = Math.min(255, Math.round(ARGB.red(color) * factor));
        int green = Math.min(255, Math.round(ARGB.green(color) * factor));
        int blue = Math.min(255, Math.round(ARGB.blue(color) * factor));
        return ARGB.color(alpha, red, green, blue);
    }

    public static void tickFun() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        angle += otherWay ? -2 : 2;
        if (angle >= 90 || angle <= 0) {
            otherWay = !otherWay;
            if (!otherWay) {
                int multiplier = EventCalendar.isHalloween() ? 10 : 1;
                cooldown = (200 + RANDOM.nextInt(400)) * multiplier;
            } else {
                int multiplier = EventCalendar.isHalloween() ? 2 : 1;
                cooldown = (30 + RANDOM.nextInt(60)) * multiplier;
            }
        }
    }
}

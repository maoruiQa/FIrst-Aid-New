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

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.util.Random;

/**
 * Renders the body-part overlay from {@code simple_health.png} so resource packs can retexture it.
 */
public final class PlayerModelRenderer {
    private static final Identifier HEALTH_RENDER_LOCATION =
            Identifier.fromNamespaceAndPath(FirstAid.MODID, "textures/gui/simple_health.png");
    private static final int TEXTURE_SIZE = 256;
    private static final int STATE_WIDTH = 32;
    private static final Random RANDOM = new Random();

    private static int angle;
    private static boolean otherWay;
    private static int cooldown;

    private PlayerModelRenderer() {
    }

    public static void renderPlayerHealth(int xOffset, int yOffset, AbstractPlayerDamageModel damageModel, boolean fourColors, GuiGraphicsExtractor guiGraphics, boolean flashState, float alpha, float partialTicks) {
        int renderX = xOffset + 8;
        int renderY = yOffset + 8;
        int flashYOffset = flashState ? 64 : 0;
        int opacity = Math.max(64, Math.min(255, 255 - Math.round(alpha)));
        int color = ARGB.color(opacity, 255, 255, 255);

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

        drawPart(guiGraphics, fourColors, damageModel.HEAD, renderX + 8, renderY, 8, flashYOffset, 16, 16, color);
        drawPart(guiGraphics, fourColors, damageModel.BODY, renderX + 8, renderY + 16, 8, flashYOffset + 16, 16, 24, color);
        drawPart(guiGraphics, fourColors, damageModel.LEFT_ARM, renderX, renderY + 16, 0, flashYOffset + 16, 8, 24, color);
        drawPart(guiGraphics, fourColors, damageModel.RIGHT_ARM, renderX + 24, renderY + 16, 24, flashYOffset + 16, 8, 24, color);
        drawPart(guiGraphics, fourColors, damageModel.LEFT_LEG, renderX + 8, renderY + 40, 8, flashYOffset + 40, 8, 16, color);
        drawPart(guiGraphics, fourColors, damageModel.RIGHT_LEG, renderX + 16, renderY + 40, 16, flashYOffset + 40, 8, 16, color);
        drawPart(guiGraphics, fourColors, damageModel.LEFT_FOOT, renderX + 8, renderY + 56, 8, flashYOffset + 56, 8, 8, color);
        drawPart(guiGraphics, fourColors, damageModel.RIGHT_FOOT, renderX + 16, renderY + 56, 16, flashYOffset + 56, 8, 8, color);
    }

    private static void drawPart(GuiGraphicsExtractor guiGraphics, boolean fourColors, AbstractDamageablePart part,
                                 int screenX, int screenY, int texX, int texY, int width, int height, int color) {
        int stateTexX = texX + STATE_WIDTH * getState(part, fourColors);
        HealthRenderUtils.blit(guiGraphics, HEALTH_RENDER_LOCATION, TEXTURE_SIZE, TEXTURE_SIZE,
                screenX, screenY, stateTexX, texY, width, height, color);
    }

    private static int getState(AbstractDamageablePart part, boolean fourColors) {
        if (part.currentHealth <= 0.001F) {
            return 5;
        }
        int maxHealth = part.getMaxHealth();
        float visualHealth = CommonUtils.getVisualHealth(part);
        if (Math.abs(visualHealth - maxHealth) < 0.001F) {
            return 0;
        }
        float healthPercentage = visualHealth / maxHealth;
        if (healthPercentage >= 1 || healthPercentage <= 0) {
            FirstAid.LOGGER.error("Calculated invalid health for part {} with current health {} and max health {}. Got value {}",
                    part.part, part.currentHealth, maxHealth, healthPercentage);
        }
        if (!fourColors && healthPercentage > 0.75F) {
            return 1;
        }
        if (healthPercentage > 0.5F) {
            return 2;
        }
        if (!fourColors && healthPercentage > 0.25F) {
            return 3;
        }
        return 4;
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

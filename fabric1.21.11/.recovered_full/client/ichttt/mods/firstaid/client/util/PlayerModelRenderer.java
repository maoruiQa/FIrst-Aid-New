/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ichttt.mods.firstaid.FirstAidConfig
 *  ichttt.mods.firstaid.FirstAidConfig$Client$Position
 *  ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart
 *  ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.util.ARGB
 */
package ichttt.mods.firstaid.client.util;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.util.EventCalendar;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.ARGB;

public final class PlayerModelRenderer {
    private static final Random RANDOM = new Random();
    private static int angle;
    private static boolean otherWay;
    private static int cooldown;

    private PlayerModelRenderer() {
    }

    public static void renderPlayerHealth(int xOffset, int yOffset, AbstractPlayerDamageModel damageModel, boolean fourColors, GuiGraphics guiGraphics, boolean flashState, float alpha, float partialTicks) {
        int renderX = xOffset + 8;
        int renderY = yOffset + 8;
        int opacity = Math.max(64, Math.min(255, 255 - Math.round(alpha)));
        int borderColor = ARGB.color((int)opacity, (int)0, (int)0, (int)0);
        int deadColor = ARGB.color((int)opacity, (int)60, (int)60, (int)60);
        if (((Boolean)FirstAidConfig.CLIENT.enableEasterEggs.get()).booleanValue() && (EventCalendar.isAFDay() || EventCalendar.isHalloween())) {
            float renderAngle = angle;
            if (cooldown == 0) {
                renderAngle += (otherWay ? -partialTicks : partialTicks) * 2.0f;
            }
            renderX = FirstAidConfig.CLIENT.pos.get() == FirstAidConfig.Client.Position.BOTTOM_LEFT || FirstAidConfig.CLIENT.pos.get() == FirstAidConfig.Client.Position.TOP_LEFT ? (renderX += (int)(renderAngle * 1.5f)) : (renderX += (int)(renderAngle * 0.5f));
        }
        PlayerModelRenderer.drawPart(guiGraphics, damageModel.HEAD, renderX + 8, renderY, 16, 16, borderColor, deadColor, fourColors, flashState, opacity);
        PlayerModelRenderer.drawPart(guiGraphics, damageModel.BODY, renderX + 8, renderY + 16, 16, 24, borderColor, deadColor, fourColors, flashState, opacity);
        PlayerModelRenderer.drawPart(guiGraphics, damageModel.LEFT_ARM, renderX, renderY + 16, 8, 24, borderColor, deadColor, fourColors, flashState, opacity);
        PlayerModelRenderer.drawPart(guiGraphics, damageModel.RIGHT_ARM, renderX + 24, renderY + 16, 8, 24, borderColor, deadColor, fourColors, flashState, opacity);
        PlayerModelRenderer.drawPart(guiGraphics, damageModel.LEFT_LEG, renderX + 8, renderY + 40, 8, 16, borderColor, deadColor, fourColors, flashState, opacity);
        PlayerModelRenderer.drawPart(guiGraphics, damageModel.RIGHT_LEG, renderX + 16, renderY + 40, 8, 16, borderColor, deadColor, fourColors, flashState, opacity);
        PlayerModelRenderer.drawPart(guiGraphics, damageModel.LEFT_FOOT, renderX + 8, renderY + 56, 8, 8, borderColor, deadColor, fourColors, flashState, opacity);
        PlayerModelRenderer.drawPart(guiGraphics, damageModel.RIGHT_FOOT, renderX + 16, renderY + 56, 8, 8, borderColor, deadColor, fourColors, flashState, opacity);
    }

    private static void drawPart(GuiGraphics guiGraphics, AbstractDamageablePart part, int x, int y, int width, int height, int borderColor, int deadColor, boolean fourColors, boolean flashState, int opacity) {
        int fillColor = PlayerModelRenderer.getColor(part, fourColors, opacity);
        if (part.currentHealth <= 0.001f) {
            fillColor = deadColor;
        }
        if (flashState && part.currentHealth > 0.001f) {
            fillColor = PlayerModelRenderer.brighten(fillColor, 1.2f);
        }
        guiGraphics.fill(x, y, x + width, y + height, borderColor);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fillColor);
    }

    private static int getColor(AbstractDamageablePart part, boolean fourColors, int opacity) {
        float percent;
        if (part.currentHealth <= 0.001f) {
            return ARGB.color((int)opacity, (int)60, (int)60, (int)60);
        }
        float maxHealth = part.getMaxHealth();
        float f = percent = maxHealth <= 0.0f ? 0.0f : part.currentHealth / maxHealth;
        if (fourColors) {
            if (percent > 0.75f) {
                return ARGB.color((int)opacity, (int)60, (int)220, (int)60);
            }
            if (percent > 0.5f) {
                return ARGB.color((int)opacity, (int)180, (int)235, (int)60);
            }
            if (percent > 0.25f) {
                return ARGB.color((int)opacity, (int)245, (int)200, (int)40);
            }
            return ARGB.color((int)opacity, (int)235, (int)70, (int)60);
        }
        if (percent > 0.5f) {
            return ARGB.color((int)opacity, (int)60, (int)220, (int)60);
        }
        if (percent > 0.25f) {
            return ARGB.color((int)opacity, (int)245, (int)200, (int)40);
        }
        return ARGB.color((int)opacity, (int)235, (int)70, (int)60);
    }

    private static int brighten(int color, float factor) {
        int alpha = ARGB.alpha((int)color);
        int red = Math.min(255, Math.round((float)ARGB.red((int)color) * factor));
        int green = Math.min(255, Math.round((float)ARGB.green((int)color) * factor));
        int blue = Math.min(255, Math.round((float)ARGB.blue((int)color) * factor));
        return ARGB.color((int)alpha, (int)red, (int)green, (int)blue);
    }

    public static void tickFun() {
        if (cooldown > 0) {
            --cooldown;
            return;
        }
        if ((angle += otherWay ? -2 : 2) >= 90 || angle <= 0) {
            boolean bl = otherWay = !otherWay;
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


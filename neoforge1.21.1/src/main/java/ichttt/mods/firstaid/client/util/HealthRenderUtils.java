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
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.client.gui.FlashStateManager;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.util.CommonUtils;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.effect.MobEffects;

import java.text.DecimalFormat;
import java.util.EnumMap;
import java.util.Objects;

public final class HealthRenderUtils {
    public static final ResourceLocation SHOW_WOUNDS_LOCATION = ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "textures/gui/show_wounds.png");
    public static final DecimalFormat TEXT_FORMAT = new DecimalFormat("0.0");
    private static final ResourceLocation HEART_CONTAINER_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation HEART_CONTAINER_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/container_blinking");
    private static final ResourceLocation HEART_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HEART_FULL_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/full_blinking");
    private static final ResourceLocation HEART_HALF_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final ResourceLocation HEART_HALF_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/half_blinking");
    private static final ResourceLocation HEART_ABSORBING_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full");
    private static final ResourceLocation HEART_ABSORBING_FULL_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full_blinking");
    private static final ResourceLocation HEART_ABSORBING_HALF_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_half");
    private static final ResourceLocation HEART_ABSORBING_HALF_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_half_blinking");

    private static final Object2IntOpenHashMap<EnumPlayerPart> PREV_HEALTH = new Object2IntOpenHashMap<>();
    private static final EnumMap<EnumPlayerPart, FlashStateManager> FLASH_STATES = new EnumMap<>(EnumPlayerPart.class);

    static {
        for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
            FLASH_STATES.put(part, new FlashStateManager());
        }
    }

    private HealthRenderUtils() {
    }

    public static void blit(GuiGraphics guiGraphics, ResourceLocation texture, int textureWidth, int textureHeight, int x, int y, int u, int v, int width, int height) {
        guiGraphics.blit(texture, x, y, 0, (float) u, (float) v, width, height, textureWidth, textureHeight);
    }

    public static void blit(GuiGraphics guiGraphics, ResourceLocation texture, int textureWidth, int textureHeight, int x, int y, int u, int v, int width, int height, int color) {
        float alpha = ((color >> 24) & 255) / 255.0F;
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        guiGraphics.setColor(red, green, blue, alpha);
        guiGraphics.blit(texture, x, y, 0, (float) u, (float) v, width, height, textureWidth, textureHeight);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawHealthString(GuiGraphics guiGraphics, Font font, AbstractDamageablePart damageablePart, int xTranslation, int yTranslation, boolean allowSecondLine) {
        float absorption = damageablePart.getAbsorption();
        int healthColor = getHealthColor(damageablePart);
        String text = TEXT_FORMAT.format(CommonUtils.getVisualHealth(damageablePart)) + "/" + damageablePart.getMaxHealth();
        if (absorption > 0F) {
            String line2 = "+ " + TEXT_FORMAT.format(absorption);
            if (allowSecondLine) {
                guiGraphics.drawString(font, line2, xTranslation, yTranslation + 5, 0xFFFFFF);
                yTranslation -= 5;
            } else {
                text += " " + line2;
            }
        }
        guiGraphics.drawString(font, text, xTranslation, yTranslation, healthColor);
    }

    public static boolean healthChanged(AbstractDamageablePart damageablePart, boolean playerDead) {
        int current = (int) Math.ceil(damageablePart.currentHealth);
        FlashStateManager activeFlashState = Objects.requireNonNull(FLASH_STATES.get(damageablePart.part));
        if (PREV_HEALTH.containsKey(damageablePart.part)) {
            int prev = PREV_HEALTH.getInt(damageablePart.part);
            updatePrev(damageablePart.part, current, playerDead);
            if (prev != current) {
                activeFlashState.setActive(Util.getMillis());
                return true;
            }
            return false;
        }
        activeFlashState.setActive(Util.getMillis());
        updatePrev(damageablePart.part, current, playerDead);
        return true;
    }

    private static void updatePrev(EnumPlayerPart part, int current, boolean playerDead) {
        if (playerDead) {
            PREV_HEALTH.clear();
        } else {
            PREV_HEALTH.put(part, current);
        }
    }

    public static boolean drawAsString(AbstractDamageablePart damageablePart, boolean allowSecondLine) {
        int maxHealth = getMaxHearts(damageablePart.getMaxHealth());
        int maxExtraHealth = getMaxHearts(damageablePart.getAbsorption());
        return (maxHealth + maxExtraHealth > 8 && allowSecondLine) || (maxHealth + maxExtraHealth > 12);
    }

    public static void drawHealth(GuiGraphics guiGraphics, Font font, AbstractDamageablePart damageablePart, int xTranslation, int yTranslation, boolean allowSecondLine) {
        int maxHealth = getMaxHearts(damageablePart.getMaxHealth());
        int maxExtraHealth = getMaxHearts(damageablePart.getAbsorption());
        int current = (int) Math.ceil(CommonUtils.getVisualHealth(damageablePart));
        int absorption = (int) Math.ceil(damageablePart.getAbsorption());
        int healthColor = getHealthColor(damageablePart);

        if (drawAsString(damageablePart, allowSecondLine)) {
            drawHealthString(guiGraphics, font, damageablePart, xTranslation, yTranslation, allowSecondLine);
            return;
        }

        FlashStateManager activeFlashState = Objects.requireNonNull(FLASH_STATES.get(damageablePart.part));
        boolean highlight = activeFlashState.update(Util.getMillis());
        boolean low = current + absorption < 1.25F;
        Minecraft minecraft = Minecraft.getInstance();
        int regen = -1;
        if (minecraft.player != null && FirstAidConfig.SERVER.allowOtherHealingItems.get() && minecraft.player.hasEffect(MobEffects.REGENERATION)) {
            regen = (minecraft.gui.getGuiTicks() / 2) % 15;
        }

        PoseStack stack = guiGraphics.pose();
        stack.pushPose();
        stack.translate(xTranslation, yTranslation, 0.0F);

        boolean drawSecondLine = allowSecondLine && (maxHealth + maxExtraHealth > 4);
        if (drawSecondLine) {
            int maxHealth2 = Math.max(0, maxHealth - 4);
            int maxExtraHealth2 = Math.max(0, maxExtraHealth - (4 - Math.min(4, maxHealth)));
            int current2 = Math.max(0, current - 8);
            int absorption2 = Math.max(0, absorption - maxExtraHealth * 2);

            maxHealth = Math.min(4, maxHealth);
            maxExtraHealth -= maxExtraHealth2;
            current = Math.min(8, current);
            absorption -= absorption2;

            stack.pushPose();
            stack.translate(0F, 5F, 0.0F);
            renderLine(stack, regen, low, maxHealth2, maxExtraHealth2, current2, absorption2, guiGraphics, highlight, healthColor);
            stack.popPose();
        }

        renderLine(stack, regen, low, maxHealth, maxExtraHealth, current, absorption, guiGraphics, highlight, healthColor);
        stack.popPose();
    }

    private static void renderLine(PoseStack stack, int regen, boolean low, int maxHealth, int maxExtraHearts, int current, int absorption, GuiGraphics guiGraphics, boolean highlight, int healthColor) {
        int[] lowOffsets = new int[maxHealth + maxExtraHearts];
        if (low) {
            for (int i = 0; i < lowOffsets.length; i++) {
                lowOffsets[i] = EventHandler.RAND.nextInt(2);
            }
        }

        stack.pushPose();
        renderMax(regen, lowOffsets, maxHealth, guiGraphics, highlight);
        if (maxExtraHearts > 0) {
            if (maxHealth != 0) {
                stack.translate(2 + 9 * maxHealth, 0, 0.0F);
            }
            renderMax(regen - maxHealth, lowOffsets, maxExtraHearts, guiGraphics, false);
        }
        stack.popPose();

        renderCurrentHealth(regen, lowOffsets, current, guiGraphics, highlight, healthColor);
        if (absorption > 0) {
            stack.pushPose();
            stack.translate(maxHealth * 9 + (maxHealth == 0 ? 0 : 2), 0, 0.0F);
            renderAbsorption(regen - maxHealth, lowOffsets, absorption, guiGraphics, highlight);
            stack.popPose();
        }
    }

    public static int getMaxHearts(float value) {
        int maxCurrentHearts = Mth.ceil(value);
        if (maxCurrentHearts % 2 != 0) {
            maxCurrentHearts++;
        }
        return maxCurrentHearts >> 1;
    }

    private static void renderMax(int regen, int[] lowOffsets, int max, GuiGraphics guiGraphics, boolean highlight) {
        renderHeartSprites(regen, lowOffsets, max, false, guiGraphics, highlight ? HEART_CONTAINER_BLINKING_SPRITE : HEART_CONTAINER_SPRITE, highlight ? HEART_CONTAINER_BLINKING_SPRITE : HEART_CONTAINER_SPRITE);
    }

    private static void renderCurrentHealth(int regen, int[] lowOffsets, int current, GuiGraphics guiGraphics, boolean highlight, int healthColor) {
        boolean renderLastHalf = current % 2 != 0;
        int render = current / 2 + (renderLastHalf ? 1 : 0);
        applyColor(guiGraphics, healthColor);
        renderHeartSprites(regen, lowOffsets, render, renderLastHalf, guiGraphics, highlight ? HEART_HALF_BLINKING_SPRITE : HEART_HALF_SPRITE, highlight ? HEART_FULL_BLINKING_SPRITE : HEART_FULL_SPRITE);
        resetColor(guiGraphics);
    }

    private static void renderAbsorption(int regen, int[] lowOffsets, int absorption, GuiGraphics guiGraphics, boolean highlight) {
        boolean renderLastHalf = absorption % 2 != 0;
        int render = absorption / 2 + (renderLastHalf ? 1 : 0);
        if (render > 0) {
            renderHeartSprites(regen, lowOffsets, render, renderLastHalf, guiGraphics,
                    highlight ? HEART_ABSORBING_HALF_BLINKING_SPRITE : HEART_ABSORBING_HALF_SPRITE,
                    highlight ? HEART_ABSORBING_FULL_BLINKING_SPRITE : HEART_ABSORBING_FULL_SPRITE);
        }
    }

    private static void renderHeartSprites(int regen, int[] lowOffsets, int toDraw, boolean lastOneHalf, GuiGraphics guiGraphics, ResourceLocation halfSprite, ResourceLocation fullSprite) {
        if (toDraw <= 0) {
            return;
        }
        for (int i = 0; i < toDraw; i++) {
            boolean renderHalf = lastOneHalf && i + 1 == toDraw;
            guiGraphics.blitSprite(renderHalf ? halfSprite : fullSprite, (int) (9F * i), (i == regen ? -2 : 0) - lowOffsets[i], 9, 9);
        }
    }

    public static int getHealthColor(AbstractDamageablePart damageablePart) {
        float healthRatio = CommonUtils.getVisibleHealthRatio(damageablePart);
        if (healthRatio > 0.85F) {
            return 0x63D56D;
        }
        if (healthRatio > 0.65F) {
            return 0xE2D142;
        }
        if (healthRatio > 0.40F) {
            return 0xE68F39;
        }
        return 0xD95145;
    }

    private static void applyColor(GuiGraphics guiGraphics, int color) {
        float red = ((color >> 16) & 255) / 255.0F;
        float green = ((color >> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        guiGraphics.setColor(red, green, blue, 1.0F);
    }

    private static void resetColor(GuiGraphics guiGraphics) {
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}


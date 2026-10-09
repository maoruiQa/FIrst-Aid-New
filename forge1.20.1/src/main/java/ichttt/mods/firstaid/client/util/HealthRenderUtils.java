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

import com.mojang.blaze3d.vertex.PoseStack;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.client.gui.FlashStateManager;
import ichttt.mods.firstaid.common.EventHandler;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;

import java.text.DecimalFormat;
import java.util.EnumMap;
import java.util.Objects;

public class HealthRenderUtils {
    public static final ResourceLocation SHOW_WOUNDS_LOCATION = new ResourceLocation(FirstAid.MODID, "textures/gui/show_wounds.png");
    public static final ResourceLocation GUI_ICONS_LOCATION = new ResourceLocation("textures/gui/icons.png");
    public static final DecimalFormat TEXT_FORMAT = new DecimalFormat("0.0");
    private static final Object2IntOpenHashMap<EnumPlayerPart> prevHealth = new Object2IntOpenHashMap<>();
    private static final EnumMap<EnumPlayerPart, FlashStateManager> flashStates = new EnumMap<>(EnumPlayerPart.class);

    static {
        for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
            flashStates.put(part, new FlashStateManager());
        }
    }

    public static void drawHealthString(GuiGraphics guiGraphics, Font font, AbstractDamageablePart damageablePart, int xTranslation, int yTranslation, boolean allowSecondLine) {
        String text = TEXT_FORMAT.format(damageablePart.currentHealth) + "/" + damageablePart.getMaxHealth();
        guiGraphics.drawString(font, text, xTranslation, yTranslation, 0xFFFFFF);
    }

    private static void updatePrev(EnumPlayerPart part, int current, boolean playerDead) {
        if (!playerDead)
            prevHealth.put(part, current);
        else
            prevHealth.clear();
    }

    public static boolean healthChanged(AbstractDamageablePart damageablePart, boolean playerDead) {
        int current = (int) Math.ceil(damageablePart.currentHealth);
        FlashStateManager activeFlashState = Objects.requireNonNull(flashStates.get(damageablePart.part));
        if (prevHealth.containsKey(damageablePart.part)) {
            int prev = prevHealth.getInt(damageablePart.part);
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

    public static boolean drawAsString(AbstractDamageablePart damageablePart, boolean allowSecondLine) {
        int maxHealth = getMaxHearts(damageablePart.getMaxHealth());
        return (maxHealth > 8 && allowSecondLine) || maxHealth > 12;
    }

    public static void drawHealth(GuiGraphics guiGraphics, Font font, AbstractDamageablePart damageablePart, int xTranslation, int yTranslation, boolean allowSecondLine) {
        if (drawAsString(damageablePart, allowSecondLine)) {
            drawHealthString(guiGraphics, font, damageablePart, xTranslation, yTranslation, allowSecondLine);
            return;
        }
        int maxHealth = getMaxHearts(damageablePart.getMaxHealth());
        int current = (int) Math.ceil(damageablePart.currentHealth);
        int yTexture = damageablePart.canCauseDeath ? 45 : 0;
        boolean highlight = Objects.requireNonNull(flashStates.get(damageablePart.part)).update(Util.getMillis());
        Minecraft mc = Minecraft.getInstance();
        int regen = FirstAidConfig.SERVER.allowOtherHealingItems.get() && mc.player.hasEffect(MobEffects.REGENERATION)
                ? (mc.gui.getGuiTicks() / 2) % 15 : -1;
        PoseStack stack = guiGraphics.pose();
        stack.pushPose();
        stack.translate(xTranslation, yTranslation, 0);
        if (allowSecondLine && maxHealth > 4) {
            stack.pushPose();
            stack.translate(0F, 5F, 0F);
            renderLine(stack, regen, current < 1.25F, yTexture, maxHealth - 4, Math.max(0, current - 8), guiGraphics, highlight);
            regen -= maxHealth - 4;
            stack.popPose();
            stack.translate(0F, -5F, 0F);
            maxHealth = 4;
            current = Math.min(current, 8);
        }
        renderLine(stack, regen, current < 1.25F, yTexture, maxHealth, current, guiGraphics, highlight);
        stack.popPose();
    }

    private static void renderLine(PoseStack stack, int regen, boolean low, int yTexture, int maxHealth, int current, GuiGraphics guiGraphics, boolean highlight) {
        int[] lowOffsets = new int[maxHealth];
        if (low) {
            for (int i = 0; i < lowOffsets.length; i++) {
                lowOffsets[i] = EventHandler.RAND.nextInt(2);
            }
        }
        renderMax(regen, lowOffsets, maxHealth, yTexture, guiGraphics, highlight);
        renderCurrentHealth(regen, lowOffsets, current, yTexture, guiGraphics);
    }

    public static int getMaxHearts(float value) {
        int maxCurrentHearts = (int) Math.ceil(value);
        if (maxCurrentHearts % 2 != 0)
            maxCurrentHearts++;
        return maxCurrentHearts >> 1;
    }

    private static void renderMax(int regen, int[] lowOffsets, int max, int yTexture, GuiGraphics guiGraphics, boolean highlight) {
        final int BACKGROUND = (highlight ? 25 : 16);
        renderTexturedModalRects(regen, lowOffsets, max, false, BACKGROUND, BACKGROUND, yTexture, guiGraphics);
    }

    private static void renderCurrentHealth(int regen, int[] lowOffsets, int current, int yTexture, GuiGraphics guiGraphics) {
        boolean renderLastHalf;
        int render;

        renderLastHalf = false;
        render = current >> 1;
        if (current % 2 != 0) {
            renderLastHalf = true;
            render++;
        }
        renderTexturedModalRects(regen, lowOffsets, render, renderLastHalf, 61, 52, yTexture, guiGraphics);
    }

    private static void renderTexturedModalRects(int regen, int[] lowOffsets, int toDraw, boolean lastOneHalf, int halfTextureX, int textureX, int textureY, GuiGraphics guiGraphics) {
        if (toDraw == 0)
            return;
        if (toDraw < 0) throw new IllegalArgumentException("Cannot draw negative amount of icons " + toDraw);
        for (int i = 0; i < toDraw; i++) {
            boolean renderHalf = lastOneHalf && i + 1 == toDraw;
            guiGraphics.blit(GUI_ICONS_LOCATION, (int) (9F * i), (i == regen ? -2 : 0) - lowOffsets[i], renderHalf ? halfTextureX : textureX, textureY, 9, 9);
        }
    }
}

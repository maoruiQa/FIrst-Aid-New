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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffects;

import java.text.DecimalFormat;
import java.util.EnumMap;
import java.util.Objects;

public final class HealthRenderUtils {
    public static final Identifier SHOW_WOUNDS_LOCATION = Identifier.fromNamespaceAndPath(FirstAid.MODID, "textures/gui/show_wounds.png");
    public static final DecimalFormat TEXT_FORMAT = new DecimalFormat("0.0");
    private static final int ABSORPTION_TEXT_COLOR = 0xE2D142;
    private static final Identifier HEART_CONTAINER_SPRITE = Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier HEART_CONTAINER_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/container_blinking");
    private static final Identifier HEART_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier HEART_FULL_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/full_blinking");
    private static final Identifier HEART_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/half");
    private static final Identifier HEART_HALF_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/half_blinking");

    private static final Object2IntOpenHashMap<EnumPlayerPart> PREV_HEALTH = new Object2IntOpenHashMap<>();
    private static final EnumMap<EnumPlayerPart, FlashStateManager> FLASH_STATES = new EnumMap<>(EnumPlayerPart.class);

    static {
        for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
            FLASH_STATES.put(part, new FlashStateManager());
        }
    }

    private HealthRenderUtils() {
    }

    public static void blit(GuiGraphics guiGraphics, Identifier texture, int textureWidth, int textureHeight, int x, int y, int u, int v, int width, int height) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (float) u, (float) v, width, height, textureWidth, textureHeight);
    }

    public static void blit(GuiGraphics guiGraphics, Identifier texture, int textureWidth, int textureHeight, int x, int y, int u, int v, int width, int height, int color) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (float) u, (float) v, width, height, textureWidth, textureHeight, color);
    }

    public static void drawHealthString(GuiGraphics guiGraphics, Font font, AbstractDamageablePart damageablePart, int xTranslation, int yTranslation, boolean allowSecondLine) {
        float absorption = damageablePart.getAbsorption();
        int healthColor = getHealthColor(damageablePart);
        String text = TEXT_FORMAT.format(CommonUtils.getVisualHealth(damageablePart)) + "/" + damageablePart.getMaxHealth();
        if (absorption > 0F) {
            String line2 = "+ " + TEXT_FORMAT.format(absorption);
            if (allowSecondLine) {
                guiGraphics.drawString(font, line2, xTranslation, yTranslation + 5, ABSORPTION_TEXT_COLOR);
                yTranslation -= 5;
            } else {
                guiGraphics.drawString(font, text, xTranslation, yTranslation, healthColor);
                guiGraphics.drawString(font, " " + line2, xTranslation + font.width(text), yTranslation, ABSORPTION_TEXT_COLOR);
                return;
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
        int totalHearts = getRenderedHeartSlots(damageablePart);
        return (allowSecondLine && totalHearts > 8) || (totalHearts > 12);
    }

    public static int getHeartRenderWidth(AbstractDamageablePart damageablePart, boolean allowSecondLine) {
        int totalHearts = getRenderedHeartSlots(damageablePart);
        return getHeartsPerRow(totalHearts, allowSecondLine) * 9;
    }

    public static void drawHealth(GuiGraphics guiGraphics, Font font, AbstractDamageablePart damageablePart, int xTranslation, int yTranslation, boolean allowSecondLine) {
        int maxHealth = getMaxHearts(damageablePart.getMaxHealth());
        int current = (int) Math.ceil(CommonUtils.getVisualHealth(damageablePart));

        if (drawAsString(damageablePart, allowSecondLine)) {
            drawHealthString(guiGraphics, font, damageablePart, xTranslation, yTranslation, allowSecondLine);
            return;
        }

        FlashStateManager activeFlashState = Objects.requireNonNull(FLASH_STATES.get(damageablePart.part));
        boolean highlight = activeFlashState.update(Util.getMillis());
        boolean low = current < 1.25F;
        Minecraft minecraft = Minecraft.getInstance();
        int regen = -1;
        if (minecraft.player != null && FirstAidConfig.SERVER.allowOtherHealingItems.get() && minecraft.player.hasEffect(MobEffects.REGENERATION)) {
            regen = (minecraft.gui.getGuiTicks() / 2) % 15;
        }

        int currentHearts = getFilledHeartSlots(current);
        int totalHearts = Math.max(maxHealth, currentHearts);
        int heartsPerRow = getHeartsPerRow(totalHearts, allowSecondLine);
        int[] lowOffsets = new int[totalHearts];
        if (low) {
            for (int i = 0; i < lowOffsets.length; i++) {
                lowOffsets[i] = EventHandler.RAND.nextInt(2);
            }
        }

        renderMax(regen, lowOffsets, 0, maxHealth, heartsPerRow, xTranslation, yTranslation, guiGraphics, highlight);
        if (totalHearts > maxHealth) {
            renderMax(regen, lowOffsets, maxHealth, totalHearts - maxHealth, heartsPerRow, xTranslation, yTranslation, guiGraphics, false);
        }
        renderCurrentHealth(regen, lowOffsets, current, heartsPerRow, xTranslation, yTranslation, guiGraphics, highlight);
    }

    private static int getRenderedHeartSlots(AbstractDamageablePart damageablePart) {
        int maxHealth = getMaxHearts(damageablePart.getMaxHealth());
        int current = (int) Math.ceil(CommonUtils.getVisualHealth(damageablePart));
        return Math.max(maxHealth, getFilledHeartSlots(current));
    }

    private static int getFilledHeartSlots(int health) {
        return health / 2 + (health % 2 != 0 ? 1 : 0);
    }

    private static int getHeartsPerRow(int totalHearts, boolean allowSecondLine) {
        if (allowSecondLine && totalHearts > 4) {
            return 4;
        }
        return Math.max(totalHearts, 1);
    }

    public static int getMaxHearts(float value) {
        int maxCurrentHearts = Mth.ceil(value);
        if (maxCurrentHearts % 2 != 0) {
            maxCurrentHearts++;
        }
        return maxCurrentHearts >> 1;
    }

    private static void renderMax(int regen, int[] lowOffsets, int startSlot, int max, int heartsPerRow, int baseX, int baseY, GuiGraphics guiGraphics, boolean highlight) {
        renderHeartSprites(regen, lowOffsets, startSlot, max, false, heartsPerRow, baseX, baseY, guiGraphics, highlight ? HEART_CONTAINER_BLINKING_SPRITE : HEART_CONTAINER_SPRITE, highlight ? HEART_CONTAINER_BLINKING_SPRITE : HEART_CONTAINER_SPRITE);
    }

    private static void renderCurrentHealth(int regen, int[] lowOffsets, int current, int heartsPerRow, int baseX, int baseY, GuiGraphics guiGraphics, boolean highlight) {
        boolean renderLastHalf = current % 2 != 0;
        int render = getFilledHeartSlots(current);
        renderHeartSprites(regen, lowOffsets, 0, render, renderLastHalf, heartsPerRow, baseX, baseY, guiGraphics, highlight ? HEART_HALF_BLINKING_SPRITE : HEART_HALF_SPRITE, highlight ? HEART_FULL_BLINKING_SPRITE : HEART_FULL_SPRITE);
    }

    private static void renderHeartSprites(int regen, int[] lowOffsets, int startSlot, int toDraw, boolean lastOneHalf, int heartsPerRow, int baseX, int baseY, GuiGraphics guiGraphics, Identifier halfSprite, Identifier fullSprite) {
        if (toDraw <= 0) {
            return;
        }
        for (int i = 0; i < toDraw; i++) {
            boolean renderHalf = lastOneHalf && i + 1 == toDraw;
            int slot = startSlot + i;
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, renderHalf ? halfSprite : fullSprite, baseX + 9 * (slot % heartsPerRow), baseY + 5 * (slot / heartsPerRow) + (slot == regen ? -2 : 0) - lowOffsets[slot], 9, 9);
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
}

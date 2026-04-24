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

package ichttt.mods.firstaid.client.gui;

import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public final class FirstaidIngameGui {
    private static final Identifier HEART_CONTAINER_SPRITE = Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier HEART_CONTAINER_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/container_blinking");
    private static final Identifier HEART_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier HEART_FULL_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/full_blinking");
    private static final Identifier HEART_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/half");
    private static final Identifier HEART_HALF_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/half_blinking");
    private static final Identifier HEART_POISONED_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/poisoned_full");
    private static final Identifier HEART_POISONED_FULL_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/poisoned_full_blinking");
    private static final Identifier HEART_POISONED_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/poisoned_half");
    private static final Identifier HEART_POISONED_HALF_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/poisoned_half_blinking");
    private static final Identifier HEART_WITHERED_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/withered_full");
    private static final Identifier HEART_WITHERED_FULL_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/withered_full_blinking");
    private static final Identifier HEART_WITHERED_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/withered_half");
    private static final Identifier HEART_WITHERED_HALF_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/withered_half_blinking");
    private static final Identifier HEART_ABSORBING_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/absorbing_full");
    private static final Identifier HEART_ABSORBING_FULL_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/absorbing_full_blinking");
    private static final Identifier HEART_ABSORBING_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/absorbing_half");
    private static final Identifier HEART_ABSORBING_HALF_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/absorbing_half_blinking");

    private FirstaidIngameGui() {
    }

    public static void renderHealth(Gui gui, int width, int height, GuiGraphicsExtractor guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }

        reserveHealthBarSpace(gui, player);

        AbstractPlayerDamageModel damageModel = CommonUtils.getOptionalDamageModel(minecraft.player).orElse(null);
        int criticalHalfHearts = 0;
        if (damageModel != null) {
            float criticalHealth = Float.MAX_VALUE;
            for (AbstractDamageablePart part : damageModel) {
                if (part.canCauseDeath) {
                    criticalHealth = Math.min(criticalHealth, part.currentHealth);
                }
            }
            criticalHealth = (criticalHealth / (float) damageModel.getCurrentMaxHealth()) * minecraft.player.getMaxHealth();
            criticalHalfHearts = Mth.ceil(criticalHealth);
        }

        int health = Mth.ceil(player.getHealth());
        AttributeInstance attrMaxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        float healthMax = Math.max((float) attrMaxHealth.getValue(), health);
        int absorption = Mth.ceil(player.getAbsorptionAmount());

        int left = width / 2 - 91;
        int rowHeight = getRowHeight(player);
        int top = height - gui.leftHeight + getReservedOffset(player);

        boolean poisoned = player.hasEffect(MobEffects.POISON);
        boolean withered = !poisoned && player.hasEffect(MobEffects.WITHER);

        float absorptionRemaining = absorption;
        for (int i = Mth.ceil((healthMax + absorption) / 2.0F) - 1; i >= 0; --i) {
            boolean criticalHalf = (i * 2) + 1 == criticalHalfHearts;
            boolean criticalBlink = i * 2 < criticalHalfHearts && !criticalHalf;
            int row = Mth.ceil((float) (i + 1) / 10.0F) - 1;
            int x = left + i % 10 * 8;
            int y = top - row * rowHeight;

            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, criticalBlink ? HEART_CONTAINER_BLINKING_SPRITE : HEART_CONTAINER_SPRITE, x, y, 9, 9);

            if (absorptionRemaining > 0.0F) {
                boolean halfAbsorption = absorptionRemaining == absorption && absorption % 2 == 1;
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        halfAbsorption
                                ? (criticalBlink ? HEART_ABSORBING_HALF_BLINKING_SPRITE : HEART_ABSORBING_HALF_SPRITE)
                                : (criticalBlink ? HEART_ABSORBING_FULL_BLINKING_SPRITE : HEART_ABSORBING_FULL_SPRITE),
                        x, y, 9, 9);
                absorptionRemaining -= absorptionRemaining == absorption && absorption % 2 == 1 ? 1.0F : 2.0F;
                continue;
            }

            if (criticalHalf) {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, getHeartSprite(poisoned, withered, true, true), x, y, 9, 9);
            }
            if (i * 2 + 1 < health) {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, getHeartSprite(poisoned, withered, false, criticalBlink), x, y, 9, 9);
            } else if (i * 2 + 1 == health && !criticalHalf) {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, getHeartSprite(poisoned, withered, true, criticalBlink), x, y, 9, 9);
            }
        }
    }

    public static void reserveHealthBarSpace(Gui gui, Player player) {
        int healthRows = getHealthRows(player);
        int rowHeight = getRowHeight(player);
        gui.leftHeight += healthRows * rowHeight;
        if (rowHeight != 10) {
            gui.leftHeight += 10 - rowHeight;
        }
    }

    private static int getReservedOffset(Player player) {
        return getHealthRows(player) * getRowHeight(player) + Math.max(0, 10 - getRowHeight(player));
    }

    private static int getHealthRows(Player player) {
        int health = Mth.ceil(player.getHealth());
        AttributeInstance attrMaxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        float healthMax = Math.max((float) attrMaxHealth.getValue(), health);
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        return Mth.ceil((healthMax + absorption) / 2.0F / 10.0F);
    }

    private static int getRowHeight(Player player) {
        return Math.max(10 - (getHealthRows(player) - 2), 3);
    }

    private static Identifier getHeartSprite(boolean poisoned, boolean withered, boolean halfHeart, boolean blinking) {
        if (poisoned) {
            return halfHeart
                    ? (blinking ? HEART_POISONED_HALF_BLINKING_SPRITE : HEART_POISONED_HALF_SPRITE)
                    : (blinking ? HEART_POISONED_FULL_BLINKING_SPRITE : HEART_POISONED_FULL_SPRITE);
        }
        if (withered) {
            return halfHeart
                    ? (blinking ? HEART_WITHERED_HALF_BLINKING_SPRITE : HEART_WITHERED_HALF_SPRITE)
                    : (blinking ? HEART_WITHERED_FULL_BLINKING_SPRITE : HEART_WITHERED_FULL_SPRITE);
        }
        return halfHeart
                ? (blinking ? HEART_HALF_BLINKING_SPRITE : HEART_HALF_SPRITE)
                : (blinking ? HEART_FULL_BLINKING_SPRITE : HEART_FULL_SPRITE);
    }
}

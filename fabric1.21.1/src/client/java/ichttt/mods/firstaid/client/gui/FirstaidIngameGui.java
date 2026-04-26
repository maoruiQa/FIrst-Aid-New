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

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public final class FirstaidIngameGui {
    private static final ResourceLocation HEART_CONTAINER_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation HEART_CONTAINER_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/container_blinking");
    private static final ResourceLocation HEART_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HEART_FULL_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/full_blinking");
    private static final ResourceLocation HEART_HALF_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final ResourceLocation HEART_HALF_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/half_blinking");
    private static final ResourceLocation HEART_POISONED_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/poisoned_full");
    private static final ResourceLocation HEART_POISONED_FULL_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/poisoned_full_blinking");
    private static final ResourceLocation HEART_POISONED_HALF_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/poisoned_half");
    private static final ResourceLocation HEART_POISONED_HALF_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/poisoned_half_blinking");
    private static final ResourceLocation HEART_WITHERED_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/withered_full");
    private static final ResourceLocation HEART_WITHERED_FULL_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/withered_full_blinking");
    private static final ResourceLocation HEART_WITHERED_HALF_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/withered_half");
    private static final ResourceLocation HEART_WITHERED_HALF_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/withered_half_blinking");
    private static final ResourceLocation HEART_ABSORBING_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full");
    private static final ResourceLocation HEART_ABSORBING_FULL_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full_blinking");
    private static final ResourceLocation HEART_ABSORBING_HALF_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_half");
    private static final ResourceLocation HEART_ABSORBING_HALF_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_half_blinking");

    private FirstaidIngameGui() {
    }

    public static void renderHealth(Gui gui, int width, int height, GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }

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

        int health = Mth.ceil(getModelDisplayHealth(player, damageModel));
        AttributeInstance attrMaxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        float healthMax = Math.max((float) attrMaxHealth.getValue(), health);
        int absorption = Mth.ceil(player.getAbsorptionAmount());

        int healthRows = Mth.ceil((healthMax + absorption) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (healthRows - 2), 3);
        int left = width / 2 - 91;
        int top = height - 39;

        boolean poisoned = player.hasEffect(MobEffects.POISON);
        boolean withered = !poisoned && player.hasEffect(MobEffects.WITHER);

        float absorptionRemaining = absorption;
        for (int i = Mth.ceil((healthMax + absorption) / 2.0F) - 1; i >= 0; --i) {
            boolean criticalHalf = (i * 2) + 1 == criticalHalfHearts;
            boolean criticalBlink = i * 2 < criticalHalfHearts && !criticalHalf;
            int row = Mth.ceil((float) (i + 1) / 10.0F) - 1;
            int x = left + i % 10 * 8;
            int y = top - row * rowHeight;

            guiGraphics.blitSprite(criticalBlink ? HEART_CONTAINER_BLINKING_SPRITE : HEART_CONTAINER_SPRITE, x, y, 9, 9);

            if (absorptionRemaining > 0.0F) {
                boolean halfAbsorption = absorptionRemaining == absorption && absorption % 2 == 1;
                guiGraphics.blitSprite(
                        halfAbsorption
                                ? (criticalBlink ? HEART_ABSORBING_HALF_BLINKING_SPRITE : HEART_ABSORBING_HALF_SPRITE)
                                : (criticalBlink ? HEART_ABSORBING_FULL_BLINKING_SPRITE : HEART_ABSORBING_FULL_SPRITE),
                        x, y, 9, 9);
                absorptionRemaining -= absorptionRemaining == absorption && absorption % 2 == 1 ? 1.0F : 2.0F;
                continue;
            }

            if (criticalHalf) {
                guiGraphics.blitSprite(getHeartSprite(poisoned, withered, true, true), x, y, 9, 9);
            }
            if (i * 2 + 1 < health) {
                guiGraphics.blitSprite(getHeartSprite(poisoned, withered, false, criticalBlink), x, y, 9, 9);
            } else if (i * 2 + 1 == health && !criticalHalf) {
                guiGraphics.blitSprite(getHeartSprite(poisoned, withered, true, criticalBlink), x, y, 9, 9);
            }
        }
    }

    private static float getModelDisplayHealth(Player player, AbstractPlayerDamageModel damageModel) {
        if (damageModel == null) {
            return player.getHealth();
        }

        float currentHealth = 0.0F;
        FirstAidConfig.Server.VanillaHealthCalculationMode mode = FirstAidConfig.SERVER.vanillaHealthCalculation.get();
        if (damageModel.hasNoCritical()) {
            mode = FirstAidConfig.Server.VanillaHealthCalculationMode.AVERAGE_ALL;
        }

        float ratio = switch (mode) {
            case AVERAGE_CRITICAL -> {
                int maxHealth = 0;

                for (AbstractDamageablePart part : damageModel) {
                    if (part.canCauseDeath) {
                        currentHealth += part.currentHealth;
                        maxHealth += part.getMaxHealth();
                    }
                }

                yield maxHealth <= 0 ? 0.0F : currentHealth / maxHealth;
            }
            case MIN_CRITICAL -> {
                AbstractDamageablePart minimal = null;
                float lowest = Float.MAX_VALUE;

                for (AbstractDamageablePart part : damageModel) {
                    if (part.canCauseDeath && part.currentHealth < lowest) {
                        minimal = part;
                        lowest = part.currentHealth;
                    }
                }

                yield minimal == null || minimal.getMaxHealth() <= 0 ? 0.0F : minimal.currentHealth / minimal.getMaxHealth();
            }
            case AVERAGE_ALL -> {
                for (AbstractDamageablePart part : damageModel) {
                    currentHealth += part.currentHealth;
                }

                int maxHealth = damageModel.getCurrentMaxHealth();
                yield maxHealth <= 0 ? 0.0F : currentHealth / maxHealth;
            }
            case CRITICAL_50_PERCENT_OTHER_50_PERCENT -> {
                float currentNormal = 0.0F;
                int maxNormal = 0;
                float currentCritical = 0.0F;
                int maxCritical = 0;

                for (AbstractDamageablePart part : damageModel) {
                    if (part.canCauseDeath) {
                        currentCritical += part.currentHealth;
                        maxCritical += part.getMaxHealth();
                    } else {
                        currentNormal += part.currentHealth;
                        maxNormal += part.getMaxHealth();
                    }
                }

                float avgNormal = maxNormal <= 0 ? 0.0F : currentNormal / maxNormal;
                float avgCritical = maxCritical <= 0 ? 0.0F : currentCritical / maxCritical;
                yield (avgCritical + avgNormal) / 2.0F;
            }
        };

        float displayHealth = ratio * player.getMaxHealth();
        return displayHealth <= 0.0F && player.isAlive() && !damageModel.isDead(player) ? 1.0F : displayHealth;
    }

    private static ResourceLocation getHeartSprite(boolean poisoned, boolean withered, boolean halfHeart, boolean blinking) {
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


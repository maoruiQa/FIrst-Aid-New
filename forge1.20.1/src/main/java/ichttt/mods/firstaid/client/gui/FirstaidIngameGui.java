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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;

public final class FirstaidIngameGui {
    private FirstaidIngameGui() {
    }

    public static void renderHealth(ForgeGui gui, int width, int height, GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }

        reserveHealthBarSpace(gui, player);

        AbstractPlayerDamageModel damageModel = CommonUtils.getOptionalDamageModel(player).resolve().orElse(null);
        int criticalHalfHearts = 0;
        if (damageModel != null) {
            float criticalHealth = Float.MAX_VALUE;
            for (AbstractDamageablePart part : damageModel) {
                if (part.canCauseDeath) {
                    criticalHealth = Math.min(criticalHealth, part.currentHealth);
                }
            }
            criticalHealth = (criticalHealth / (float) damageModel.getCurrentMaxHealth()) * player.getMaxHealth();
            criticalHalfHearts = Mth.ceil(criticalHealth);
        }

        minecraft.getProfiler().push("health");

        int health = Mth.ceil(getModelDisplayHealth(player, damageModel));
        boolean highlight = gui.healthBlinkTime > (long) gui.tickCount && (gui.healthBlinkTime - (long) gui.tickCount) / 3L % 2L == 1L;

        if (health < gui.lastHealth && player.invulnerableTime > 0) {
            gui.lastHealthTime = Util.getMillis();
            gui.healthBlinkTime = (long) (gui.tickCount + 20);
        } else if (health > gui.lastHealth && player.invulnerableTime > 0) {
            gui.lastHealthTime = Util.getMillis();
            gui.healthBlinkTime = (long) (gui.tickCount + 10);
        }

        if (Util.getMillis() - gui.lastHealthTime > 1000L) {
            gui.lastHealth = health;
            gui.displayHealth = health;
            gui.lastHealthTime = Util.getMillis();
        }

        gui.lastHealth = health;
        int healthLast = gui.displayHealth;

        AttributeInstance attrMaxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        float healthMax = Math.max((float) attrMaxHealth.getValue(), Math.max(healthLast, health));
        int absorption = Mth.ceil(player.getAbsorptionAmount());

        int left = width / 2 - 91;
        int rowHeight = getRowHeight(player);
        int top = height - gui.leftHeight + getReservedOffset(player);

        int regen = -1;
        if (player.hasEffect(MobEffects.REGENERATION)) {
            regen = gui.tickCount % Mth.ceil(healthMax + 5.0F);
        }

        gui.random.setSeed((long) (gui.tickCount * 312871));

        final int BACKGROUND = (highlight ? 25 : 16);
        int MARGIN = 16;
        if (player.hasEffect(MobEffects.POISON)) {
            MARGIN += 36;
        } else if (player.hasEffect(MobEffects.WITHER)) {
            MARGIN += 72;
        }
        float absorptionRemaining = absorption;

        RenderSystem.enableBlend();
        for (int i = Mth.ceil((healthMax + absorption) / 2.0F) - 1; i >= 0; --i) {
            boolean thisHalfCritical = (i * 2) + 1 == criticalHalfHearts;
            final int TOP = 9 * (i * 2 < criticalHalfHearts && !thisHalfCritical ? 5 : 0);
            int row = Mth.ceil((float) (i + 1) / 10.0F) - 1;
            int x = left + i % 10 * 8;
            int y = top - row * rowHeight;

            if (health <= 4) {
                y += gui.random.nextInt(2);
            }
            if (i == regen) {
                y -= 2;
            }

            guiGraphics.blit(HealthRenderUtils.GUI_ICONS_LOCATION, x, y, BACKGROUND, TOP, 9, 9);

            if (highlight) {
                PoseStack stack = guiGraphics.pose();
                if (thisHalfCritical) {
                    stack.pushPose();
                    stack.translate(0.0F, 0.0F, 1000.0F);
                    guiGraphics.blit(HealthRenderUtils.GUI_ICONS_LOCATION, x, y, MARGIN + 63, 9 * 5, 9, 9);
                    stack.popPose();
                }
                if (i * 2 + 1 < healthLast) {
                    guiGraphics.blit(HealthRenderUtils.GUI_ICONS_LOCATION, x + (thisHalfCritical ? 5 : 0), y, MARGIN + 54 + (thisHalfCritical ? 5 : 0), TOP, 9 - (thisHalfCritical ? 5 : 0), 9);
                } else if (i * 2 + 1 == healthLast) {
                    guiGraphics.blit(HealthRenderUtils.GUI_ICONS_LOCATION, x, y, MARGIN + 63, TOP, 9, 9);
                }
            }

            if (absorptionRemaining > 0.0F) {
                if (absorptionRemaining == absorption && absorption % 2 == 1) {
                    guiGraphics.blit(HealthRenderUtils.GUI_ICONS_LOCATION, x, y, MARGIN + 153, TOP, 9, 9);
                    absorptionRemaining -= 1.0F;
                } else {
                    guiGraphics.blit(HealthRenderUtils.GUI_ICONS_LOCATION, x, y, MARGIN + 144, TOP, 9, 9);
                    absorptionRemaining -= 2.0F;
                }
            } else {
                PoseStack stack = guiGraphics.pose();
                if (thisHalfCritical) {
                    stack.pushPose();
                    stack.translate(0.0F, 0.0F, 10.0F);
                    guiGraphics.blit(HealthRenderUtils.GUI_ICONS_LOCATION, x, y, MARGIN + 45, 9 * 5, 9, 9);
                    stack.popPose();
                }
                if (i * 2 + 1 < health) {
                    guiGraphics.blit(HealthRenderUtils.GUI_ICONS_LOCATION, x + (thisHalfCritical ? 5 : 0), y, MARGIN + 36 + (thisHalfCritical ? 5 : 0), TOP, 9 - (thisHalfCritical ? 5 : 0), 9);
                } else if (i * 2 + 1 == health && !thisHalfCritical) {
                    guiGraphics.blit(HealthRenderUtils.GUI_ICONS_LOCATION, x, y, MARGIN + 45, TOP, 9, 9);
                }
            }
        }
        RenderSystem.disableBlend();
        minecraft.getProfiler().pop();
    }

    public static void reserveHealthBarSpace(ForgeGui gui, Player player) {
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
}

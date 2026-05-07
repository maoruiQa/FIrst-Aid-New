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

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.medicine.MedicineStatusDisplay;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

public final class StatusSummaryRenderer {
    private StatusSummaryRenderer() {
    }

    public static int renderStatusSummary(
            GuiGraphics guiGraphics,
            Font font,
            Player player,
            AbstractPlayerDamageModel damageModel,
            @Nullable PlayerDamageModel playerDamageModel,
            int baseX,
            int baseY
    ) {
        int lineY = baseY;
        int painLevel = damageModel.getPainLevel();
        if (painLevel <= 0) {
            painLevel = calculateLocalPainLevel(damageModel);
        }

        if (painLevel > 0) {
            boolean painSuppressed = player.hasEffect(RegistryObjects.MORPHINE_EFFECT)
                    || player.hasEffect(RegistryObjects.PAINKILLER_EFFECT);
            Component painText = painSuppressed
                    ? Component.translatable("firstaid.gui.status.pain_suppressed")
                    : Component.translatable("firstaid.gui.status.pain", Component.translatable(getPainSeverityKey(painLevel)));
            guiGraphics.drawString(font, painText, baseX, lineY, opaque(painSuppressed ? 9425919 : 16747146));
            lineY += 10;
        }

        int adrenalineLevel = damageModel.getAdrenalineLevel();
        if (adrenalineLevel > 0) {
            int suppressionLevel = playerDamageModel != null ? playerDamageModel.getSuppressionLevel() : adrenalineLevel;
            guiGraphics.drawString(
                    font,
                    Component.translatable("firstaid.gui.status.suppression", Component.translatable(getSuppressionSeverityKey(suppressionLevel))),
                    baseX,
                    lineY,
                    opaque(12637930)
            );
            lineY += 10;
        }

        int unconsciousTicks = playerDamageModel != null ? playerDamageModel.getUnconsciousTicks() : damageModel.getUnconsciousTicks();
        if (unconsciousTicks > 0) {
            guiGraphics.drawString(
                    font,
                    Component.translatable(
                            playerDamageModel != null
                                    ? playerDamageModel.getUnconsciousReasonKey()
                                    : (damageModel.isCriticalConditionActive() ? "firstaid.gui.critical_condition" : "firstaid.gui.unconscious")
                    ),
                    baseX,
                    lineY,
                    opaque(16766421)
            );
            lineY += 10;
            guiGraphics.drawString(
                    font,
                    playerDamageModel != null && playerDamageModel.canGiveUp()
                            ? Component.translatable("firstaid.gui.death_countdown_seconds", playerDamageModel.getUnconsciousSecondsLeft())
                            : Component.translatable("firstaid.gui.unconscious_left", StringUtil.formatTickDuration(unconsciousTicks, 20.0F)),
                    baseX,
                    lineY,
                    opaque(16766421)
            );
            lineY += 10;
            if (playerDamageModel != null && playerDamageModel.canGiveUp()) {
                guiGraphics.drawString(font, Component.translatable("firstaid.gui.waiting_for_rescue"), baseX, lineY, opaque(16766421));
                lineY += 10;
                guiGraphics.drawString(font, Component.translatable("firstaid.gui.rescue_help"), baseX, lineY, opaque(16766421));
                lineY += 10;
                guiGraphics.drawString(font, Component.translatable("firstaid.gui.give_up_hint", ClientHooks.GIVE_UP.getTranslatedKeyMessage()), baseX, lineY, opaque(16757683));
                lineY += 10;
            }
        }

        for (MedicineStatusDisplay display : MedicineStatusClientHelper.collect(player)) {
            lineY = MedicineStatusClientHelper.drawStatusLine(guiGraphics, font, display, baseX, lineY);
        }

        return lineY;
    }

    public static int countVisibleLines(AbstractPlayerDamageModel damageModel, Player player) {
        int count = 0;
        int painLevel = damageModel.getPainLevel();
        if (painLevel <= 0) {
            painLevel = calculateLocalPainLevel(damageModel);
        }
        if (painLevel > 0) {
            count++;
        }
        int adrenalineLevel = damageModel.getAdrenalineLevel();
        if (adrenalineLevel > 0) {
            count++;
        }
        if (damageModel instanceof PlayerDamageModel playerDamageModel) {
            if (playerDamageModel.getUnconsciousTicks() > 0) {
                count += 2;
                if (playerDamageModel.canGiveUp()) {
                    count += 3;
                }
            }
        } else if (damageModel.getUnconsciousTicks() > 0) {
            count += 2;
        }
        for (MedicineStatusDisplay display : MedicineStatusClientHelper.collect(player)) {
            count++;
        }
        return count;
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
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

    private static int calculateLocalPainLevel(AbstractPlayerDamageModel model) {
        boolean hasInjury = false;
        int fullyLostParts = 0;
        float maxSeverity = 0.0F;
        float weightedSeverity = 0.0F;
        float totalWeight = 0.0F;
        for (AbstractDamageablePart part : model) {
            float missingHealth = Math.max(0.0F, part.getMaxHealth() - part.currentHealth);
            if (missingHealth <= 0F) {
                continue;
            }
            hasInjury = true;
            float injuryRatio = missingHealth / part.getMaxHealth();
            if (part.currentHealth <= 0F) {
                fullyLostParts++;
                injuryRatio = part.canCauseDeath ? 1.0F : 0.85F;
            }
            if (part.canCauseDeath && injuryRatio >= 0.55F) {
                injuryRatio = Math.min(1.0F, injuryRatio + 0.15F);
            }
            float weight = part.canCauseDeath ? 1.35F : 1.0F;
            maxSeverity = Math.max(maxSeverity, injuryRatio);
            weightedSeverity += injuryRatio * weight;
            totalWeight += weight;
        }
        if (!hasInjury) {
            return 0;
        }
        if (!FirstAid.dynamicPainEnabled) {
            return Math.max(1, Math.min(5, FirstAid.mildPainLevel));
        }
        float averageSeverity = totalWeight <= 0.0F ? 0.0F : weightedSeverity / totalWeight;
        float combinedSeverity = Math.min(1.0F, maxSeverity * 0.65F + averageSeverity * 0.35F);
        int maxPainLevel = 5;
        int painLevel = Math.max(1, Math.min(maxPainLevel, 1 + (int) Math.floor(combinedSeverity * (maxPainLevel - 0.0001F))));
        if (fullyLostParts < 3 && painLevel >= maxPainLevel) {
            return maxPainLevel - 1;
        }
        return painLevel;
    }
}

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

        if (damageModel.getPainLevel() > 0) {
            boolean painSuppressed = player.hasEffect(RegistryObjects.MORPHINE_EFFECT)
                    || player.hasEffect(RegistryObjects.PAINKILLER_EFFECT);
            Component painText = painSuppressed
                    ? Component.translatable("firstaid.gui.status.pain_suppressed")
                    : Component.translatable("firstaid.gui.status.pain", Component.translatable(getPainSeverityKey(damageModel.getPainLevel())));
            guiGraphics.drawString(font, painText, baseX, lineY, painSuppressed ? 9425919 : 16747146);
            lineY += 10;
        }

        if (damageModel.getAdrenalineLevel() > 0) {
            int suppressionLevel = playerDamageModel != null ? playerDamageModel.getSuppressionLevel() : damageModel.getAdrenalineLevel();
            guiGraphics.drawString(
                    font,
                    Component.translatable("firstaid.gui.status.suppression", Component.translatable(getSuppressionSeverityKey(suppressionLevel))),
                    baseX,
                    lineY,
                    12637930
            );
            lineY += 10;
        }

        if (damageModel.getUnconsciousTicks() > 0) {
            guiGraphics.drawString(
                    font,
                    Component.translatable(
                            playerDamageModel != null
                                    ? playerDamageModel.getUnconsciousReasonKey()
                                    : (damageModel.isCriticalConditionActive() ? "firstaid.gui.critical_condition" : "firstaid.gui.unconscious")
                    ),
                    baseX,
                    lineY,
                    16766421
            );
            lineY += 10;
            guiGraphics.drawString(
                    font,
                    playerDamageModel != null && playerDamageModel.canGiveUp()
                            ? Component.translatable("firstaid.gui.death_countdown_seconds", playerDamageModel.getUnconsciousSecondsLeft())
                            : Component.translatable("firstaid.gui.unconscious_left", StringUtil.formatTickDuration(damageModel.getUnconsciousTicks(), 20.0F)),
                    baseX,
                    lineY,
                    16766421
            );
            lineY += 10;
            if (playerDamageModel != null && playerDamageModel.canGiveUp()) {
                guiGraphics.drawString(font, Component.translatable("firstaid.gui.waiting_for_rescue"), baseX, lineY, 16766421);
                lineY += 10;
                guiGraphics.drawString(font, Component.translatable("firstaid.gui.rescue_help"), baseX, lineY, 16766421);
                lineY += 10;
                guiGraphics.drawString(font, Component.translatable("firstaid.gui.give_up_hint", ClientHooks.GIVE_UP.getTranslatedKeyMessage()), baseX, lineY, 16757683);
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
        if (damageModel.getPainLevel() > 0) count++;
        if (damageModel.getAdrenalineLevel() > 0) count++;
        if (damageModel.getUnconsciousTicks() > 0) {
            count++;
            count++;
            if (damageModel instanceof PlayerDamageModel pm && pm.canGiveUp()) {
                count += 3;
            }
        }
        for (MedicineStatusDisplay display : MedicineStatusClientHelper.collect(player)) {
            count++;
        }
        return count;
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
}

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

package ichttt.mods.firstaid.common;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.common.damagesystem.DamageablePart;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Decides whether a collapsed critical part downs the player or kills them, based on {@link FirstAid#knockdownMode}.
 */
public final class KnockdownRules {
    private static final double[] SAMPLE_OVERFLOW_RATIOS = {0.0D, 0.5D, 1.0D, 2.0D, 3.0D, 5.0D};
    @Nullable
    private static Float forcedRoll;

    private KnockdownRules() {
    }

    /**
     * @param overflowRatio damage beyond the critical part's remaining health, divided by that part's max health
     * @return the chance in [0, 1] that the player goes down instead of dying
     */
    public static double chanceForOverflowRatio(double overflowRatio, double coefficient) {
        if (!(overflowRatio > 0.0D) || !(coefficient > 0.0D)) {
            return 1.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, Math.exp(-coefficient * overflowRatio)));
    }

    public static double criticalOverflowRatio(PlayerDamageModel damageModel) {
        double ratio = 0.0D;
        for (AbstractDamageablePart part : damageModel) {
            if (part.canCauseDeath && part.currentHealth <= 0.0F && part instanceof DamageablePart damageablePart) {
                ratio = Math.max(ratio, damageablePart.getKnockdownOverflow() / Math.max(1.0D, damageablePart.getMaxHealth()));
            }
        }
        return ratio;
    }

    public static boolean shouldKnockDown(PlayerDamageModel damageModel, Player player) {
        return switch (FirstAid.knockdownMode) {
            case ALWAYS -> true;
            case NEVER -> false;
            case CHANCE -> {
                double ratio = criticalOverflowRatio(damageModel);
                double chance = chanceForOverflowRatio(ratio, FirstAid.knockdownOverflowCoefficient);
                float roll = forcedRoll != null ? forcedRoll : player.getRandom().nextFloat();
                boolean knockDown = roll < chance;
                if (FirstAidConfig.GENERAL.debug.get()) {
                    FirstAid.LOGGER.info("Knockdown roll for {}: overflow ratio {}, chance {}, roll {}, downed {}", player.getName().getString(), ratio, chance, roll, knockDown);
                }
                yield knockDown;
            }
        };
    }

    public static String fingerprint() {
        return fingerprint(FirstAid.knockdownMode, FirstAid.knockdownOverflowCoefficient);
    }

    /**
     * Identifies the knockdown settings players have been notified about. The coefficient only matters in CHANCE mode.
     */
    public static String fingerprint(FirstAid.KnockdownMode mode, double coefficient) {
        return mode == FirstAid.KnockdownMode.CHANCE ? mode.name() + ";k=" + formatCoefficient(coefficient) : mode.name();
    }

    public static String formatCoefficient(double coefficient) {
        return String.format(Locale.ROOT, "%.2f", coefficient);
    }

    public static String formatChance(double chance) {
        return Math.round(chance * 100.0D) + "%";
    }

    public static String formatRatio(double ratio) {
        return String.format(Locale.ROOT, "%.1f", ratio);
    }

    public static double[] sampleOverflowRatios() {
        return SAMPLE_OVERFLOW_RATIOS.clone();
    }

    /** Compact "overflow ratio -> chance" list such as {@code 0.5x 84% | 1.0x 70%}, used in command feedback. */
    public static String sampleSummary(double coefficient) {
        StringBuilder builder = new StringBuilder();
        for (double ratio : SAMPLE_OVERFLOW_RATIOS) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(formatRatio(ratio)).append("x ").append(formatChance(chanceForOverflowRatio(ratio, coefficient)));
        }
        return builder.toString();
    }

    /** Test hook: forces the CHANCE roll, {@code null} restores the player's random source. */
    public static void setForcedRollForTesting(@Nullable Float roll) {
        forcedRoll = roll;
    }
}

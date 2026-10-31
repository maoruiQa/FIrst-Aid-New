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

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * {@code /firstaid knockdown [always|chance|never]} and {@code /firstaid knockdown coefficient [value]}.
 */
public final class KnockdownCommands {

    private KnockdownCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> knockdown = Commands.literal("knockdown")
                .executes(context -> query(context.getSource()));
        for (FirstAid.KnockdownMode mode : FirstAid.KnockdownMode.values()) {
            knockdown.then(Commands.literal(mode.name().toLowerCase(Locale.ROOT))
                    .executes(context -> setMode(context.getSource(), mode)));
        }
        knockdown.then(Commands.literal("coefficient")
                .executes(context -> query(context.getSource()))
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, FirstAid.MAX_KNOCKDOWN_OVERFLOW_COEFFICIENT))
                        .executes(context -> setCoefficient(context.getSource(), DoubleArgumentType.getDouble(context, "value")))));
        dispatcher.register(Commands.literal("firstaid")
                .requires(source -> source.hasPermission(2))
                .then(knockdown));
    }

    public static Component modeName(FirstAid.KnockdownMode mode) {
        return Component.translatable("firstaid.knockdown.mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private static int query(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("firstaid.command.knockdown.query",
                modeName(FirstAid.knockdownMode), KnockdownRules.formatCoefficient(FirstAid.knockdownOverflowCoefficient)), false);
        if (FirstAid.knockdownMode == FirstAid.KnockdownMode.CHANCE) {
            source.sendSuccess(() -> Component.translatable("firstaid.command.knockdown.samples",
                    KnockdownRules.sampleSummary(FirstAid.knockdownOverflowCoefficient)), false);
        }
        return 1;
    }

    private static int setMode(CommandSourceStack source, FirstAid.KnockdownMode mode) {
        FirstAid.knockdownMode = mode;
        FirstAidConfig.persistCommandSettings();
        KnockdownNotices.broadcastIfChanged(source.getServer());
        source.sendSuccess(() -> Component.translatable("firstaid.command.knockdown.mode.set", modeName(mode)), true);
        return 1;
    }

    private static int setCoefficient(CommandSourceStack source, double value) {
        FirstAid.knockdownOverflowCoefficient = value;
        FirstAidConfig.persistCommandSettings();
        KnockdownNotices.broadcastIfChanged(source.getServer());
        source.sendSuccess(() -> Component.translatable("firstaid.command.knockdown.coefficient.set",
                KnockdownRules.formatCoefficient(value), KnockdownRules.sampleSummary(value)), true);
        if (FirstAid.knockdownMode != FirstAid.KnockdownMode.CHANCE) {
            source.sendSuccess(() -> Component.translatable("firstaid.command.knockdown.coefficient.inactive",
                    modeName(FirstAid.knockdownMode)), false);
        }
        return 1;
    }
}

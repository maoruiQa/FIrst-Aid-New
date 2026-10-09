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

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import ichttt.mods.firstaid.FirstAidConfig;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class TinnitusCommands {
    private TinnitusCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("firstaid")
                .then(Commands.literal("tinnitus")
                        .executes(context -> show(context.getSource()))
                        .then(Commands.literal("on").executes(context -> enabled(context.getSource(), true)))
                        .then(Commands.literal("off").executes(context -> enabled(context.getSource(), false)))
                        .then(Commands.literal("duration")
                                .then(Commands.argument("percent", IntegerArgumentType.integer(1, 400))
                                        .executes(context -> {
                                            FirstAidConfig.CLIENT.tinnitusDurationPercent.set(IntegerArgumentType.getInteger(context, "percent"));
                                            FirstAidConfig.saveClient();
                                            return show(context.getSource());
                                        })))));
    }

    private static int enabled(CommandSourceStack source, boolean enabled) {
        FirstAidConfig.CLIENT.tinnitusEnabled.set(enabled);
        if (!enabled) TinnitusSoundController.clear();
        FirstAidConfig.saveClient();
        return show(source);
    }

    private static int show(CommandSourceStack source) {
        Component state = Component.translatable(FirstAidConfig.CLIENT.tinnitusEnabled.get()
                ? "firstaid.command.tinnitus.on" : "firstaid.command.tinnitus.off");
        Component message = Component.translatable("firstaid.command.tinnitus.settings", state,
                FirstAidConfig.CLIENT.tinnitusDurationPercent.get());
        source.sendSuccess(() -> message, false);
        return 1;
    }
}

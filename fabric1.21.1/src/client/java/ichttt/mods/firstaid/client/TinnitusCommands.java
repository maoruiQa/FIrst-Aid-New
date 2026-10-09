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
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import ichttt.mods.firstaid.FirstAidConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class TinnitusCommands {
    private TinnitusCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        CommandNode<FabricClientCommandSource> root = ClientCommandManager.literal("firstaid")
                .then(ClientCommandManager.literal("tinnitus")
                        .executes(context -> show(context.getSource()))
                        .then(ClientCommandManager.literal("on").executes(context -> enabled(context.getSource(), true)))
                        .then(ClientCommandManager.literal("off").executes(context -> enabled(context.getSource(), false)))
                        .then(ClientCommandManager.literal("duration")
                                .then(ClientCommandManager.argument("percent", IntegerArgumentType.integer(1, 400))
                                        .executes(context -> {
                                            FirstAidConfig.CLIENT.tinnitusDurationPercent.set(IntegerArgumentType.getInteger(context, "percent"));
                                            FirstAidConfig.saveClient();
                                            return show(context.getSource());
                                        })))).build();
        dispatcher.getRoot().addChild(new TinnitusRoot(root));
    }

    /** Fabric's client dispatcher must yield to the server for the other /firstaid branches. */
    private static final class TinnitusRoot extends LiteralCommandNode<FabricClientCommandSource> {
        private TinnitusRoot(CommandNode<FabricClientCommandSource> source) {
            super(source.getName(), source.getCommand(), source.getRequirement(), source.getRedirect(),
                    source.getRedirectModifier(), source.isFork());
            source.getChildren().forEach(this::addChild);
        }

        @Override
        public void parse(StringReader reader, CommandContextBuilder<FabricClientCommandSource> context)
                throws CommandSyntaxException {
            int start = reader.getCursor();
            super.parse(reader, context);
            String remaining = reader.getRemaining();
            if (!remaining.isEmpty() && !remaining.startsWith(" tinnitus") && !remaining.startsWith("\ttinnitus")) {
                reader.setCursor(start);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(reader);
            }
        }
    }

    private static int enabled(FabricClientCommandSource source, boolean enabled) {
        FirstAidConfig.CLIENT.tinnitusEnabled.set(enabled);
        if (!enabled) TinnitusSoundController.clear();
        FirstAidConfig.saveClient();
        return show(source);
    }

    private static int show(FabricClientCommandSource source) {
        Component state = Component.translatable(FirstAidConfig.CLIENT.tinnitusEnabled.get()
                ? "firstaid.command.tinnitus.on" : "firstaid.command.tinnitus.off");
        Component message = Component.translatable("firstaid.command.tinnitus.settings", state,
                FirstAidConfig.CLIENT.tinnitusDurationPercent.get());
        source.sendFeedback(message);
        return 1;
    }
}

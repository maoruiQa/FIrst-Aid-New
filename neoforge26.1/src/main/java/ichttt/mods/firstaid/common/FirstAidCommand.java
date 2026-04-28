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
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.network.MessageSyncCommandSettings;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class FirstAidCommand {

    private FirstAidCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("firstaid")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("pain")
                        .then(Commands.literal("dynamic")
                                .executes(context -> setDynamicPain(context.getSource(), true)))
                        .then(Commands.literal("mild")
                                .executes(context -> setDynamicPain(context.getSource(), false))))
                .then(Commands.literal("suppression")
                        .then(Commands.literal("dynamic")
                                .executes(context -> setLowSuppression(context.getSource(), false)))
                        .then(Commands.literal("mild")
                                .executes(context -> setLowSuppression(context.getSource(), true)))
                        .then(Commands.literal("blacklist")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("entity", StringArgumentType.greedyString())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder))
                                                .executes(context -> addSuppressionBlacklistEntry(context.getSource(), StringArgumentType.getString(context, "entity")))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("entity", StringArgumentType.greedyString())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(FirstAid.suppressionEntityBlacklist, builder))
                                                .executes(context -> removeSuppressionBlacklistEntry(context.getSource(), StringArgumentType.getString(context, "entity")))))))
                .then(Commands.literal("revivewakeup")
                        .then(Commands.literal("on")
                                .executes(context -> setRescueWakeUp(context.getSource(), true))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                        .executes(context -> setRescueWakeUpDelay(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))))
                        .then(Commands.literal("off")
                                .executes(context -> setRescueWakeUp(context.getSource(), false))))
                .then(Commands.literal("naturalregen")
                        .then(Commands.literal("off")
                                .executes(context -> setNaturalRegenMode(context.getSource(), FirstAid.NaturalRegenMode.OFF)))
                        .then(buildNaturalRegenBranch("limited", FirstAid.NaturalRegenMode.LIMITED))
                        .then(buildNaturalRegenBranch("limited2", FirstAid.NaturalRegenMode.LIMITED2))
                        .then(buildNaturalRegenBranch("full", FirstAid.NaturalRegenMode.FULL)))
                .then(Commands.literal("medicineeffect")
                        .then(Commands.literal("realistic")
                                .executes(context -> setMedicineEffectMode(context.getSource(), FirstAid.MedicineEffectMode.REALISTIC)))
                        .then(Commands.literal("assisted")
                                .executes(context -> setMedicineEffectMode(context.getSource(), FirstAid.MedicineEffectMode.ASSISTED)))
                        .then(Commands.literal("casual")
                                .executes(context -> setMedicineEffectMode(context.getSource(), FirstAid.MedicineEffectMode.CASUAL))))
                .then(Commands.literal("injurydebuff")
                        .then(Commands.literal("normal")
                                .executes(context -> setInjuryDebuffMode(context.getSource(), FirstAid.InjuryDebuffMode.NORMAL)))
                        .then(Commands.literal("low")
                                .executes(context -> setInjuryDebuffMode(context.getSource(), FirstAid.InjuryDebuffMode.LOW)))
                        .then(Commands.literal("off")
                                .executes(context -> setInjuryDebuffMode(context.getSource(), FirstAid.InjuryDebuffMode.OFF)))
                        .then(Commands.argument("effect", StringArgumentType.word())
                                .then(Commands.literal("normal")
                                        .executes(context -> setInjuryDebuffModeForEffect(context.getSource(), StringArgumentType.getString(context, "effect"), FirstAid.InjuryDebuffMode.NORMAL)))
                                .then(Commands.literal("low")
                                        .executes(context -> setInjuryDebuffModeForEffect(context.getSource(), StringArgumentType.getString(context, "effect"), FirstAid.InjuryDebuffMode.LOW)))
                                .then(Commands.literal("off")
                                        .executes(context -> setInjuryDebuffModeForEffect(context.getSource(), StringArgumentType.getString(context, "effect"), FirstAid.InjuryDebuffMode.OFF))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildNaturalRegenBranch(String literal, FirstAid.NaturalRegenMode mode) {
        return Commands.literal(literal)
                .executes(context -> setNaturalRegenMode(context.getSource(), mode))
                .then(Commands.literal("critical")
                        .executes(context -> setNaturalRegenModeAndStrategy(context.getSource(), mode, FirstAid.NaturalRegenStrategy.CRITICAL)))
                .then(Commands.literal("random")
                        .executes(context -> setNaturalRegenModeAndStrategy(context.getSource(), mode, FirstAid.NaturalRegenStrategy.RANDOM)));
    }

    private static int setDynamicPain(CommandSourceStack source, boolean enabled) {
        FirstAid.dynamicPainEnabled = enabled;
        if (source.getServer() != null) {
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
                if (damageModel instanceof PlayerDamageModel playerDamageModel) {
                    playerDamageModel.refreshPainState(player);
                    CommonUtils.syncDamageModel(player);
                }
            }
        }
        source.sendSuccess(() -> Component.translatable(enabled
                ? "firstaid.command.pain.dynamic"
                : "firstaid.command.pain.mild"), true);
        FirstAidConfig.persistCommandSettings();
        return 1;
    }

    private static int setLowSuppression(CommandSourceStack source, boolean enabled) {
        FirstAid.lowSuppressionEnabled = enabled;
        source.sendSuccess(() -> Component.translatable(enabled
                ? "firstaid.command.suppression.mild"
                : "firstaid.command.suppression.dynamic"), true);
        FirstAidConfig.persistCommandSettings();
        return 1;
    }

    private static int addSuppressionBlacklistEntry(CommandSourceStack source, String entityInput) {
        Identifier entityId = parseEntityId(source, entityInput);
        if (entityId == null) {
            return 0;
        }
        if (BuiltInRegistries.ENTITY_TYPE.getValue(entityId) == null) {
            source.sendFailure(Component.translatable("firstaid.command.suppression.blacklist.unknown", entityId.toString()));
            return 0;
        }
        FirstAid.suppressionEntityBlacklist.add(entityId);
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable("firstaid.command.suppression.blacklist.add", entityId.toString()), true);
        return 1;
    }

    private static int removeSuppressionBlacklistEntry(CommandSourceStack source, String entityInput) {
        Identifier entityId = parseEntityId(source, entityInput);
        if (entityId == null) {
            return 0;
        }
        if (BuiltInRegistries.ENTITY_TYPE.getValue(entityId) == null) {
            source.sendFailure(Component.translatable("firstaid.command.suppression.blacklist.unknown", entityId.toString()));
            return 0;
        }
        FirstAid.suppressionEntityBlacklist.remove(entityId);
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable("firstaid.command.suppression.blacklist.remove", entityId.toString()), true);
        return 1;
    }

    private static void syncCommandSettings(CommandSourceStack source) {
        if (source.getServer() == null) {
            return;
        }
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            FirstAid.NETWORKING.sendCommandSettingsSync(player, MessageSyncCommandSettings.current());
        }
    }

    private static int setRescueWakeUp(CommandSourceStack source, boolean enabled) {
        FirstAid.rescueWakeUpEnabled = enabled;
        FirstAidConfig.persistCommandSettings();
        refreshRescueWakeUpState(source);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "firstaid.command.revivewakeup.on"
                : "firstaid.command.revivewakeup.off"), true);
        return 1;
    }

    private static int setRescueWakeUpDelay(CommandSourceStack source, int seconds) {
        FirstAid.rescueWakeUpEnabled = true;
        FirstAid.rescueWakeUpDelaySeconds = seconds;
        FirstAidConfig.persistCommandSettings();
        refreshRescueWakeUpState(source);
        source.sendSuccess(() -> Component.translatable("firstaid.command.revivewakeup.time", seconds), true);
        return 1;
    }

    private static int setNaturalRegenMode(CommandSourceStack source, FirstAid.NaturalRegenMode mode) {
        FirstAid.naturalRegenMode = mode;
        FirstAidConfig.persistCommandSettings();
        refreshNaturalRegenState(source);
        source.sendSuccess(() -> Component.translatable("firstaid.command.naturalregen.mode", Component.translatable(getNaturalRegenModeKey(mode))), true);
        return 1;
    }

    private static int setNaturalRegenModeAndStrategy(CommandSourceStack source, FirstAid.NaturalRegenMode mode, FirstAid.NaturalRegenStrategy strategy) {
        FirstAid.naturalRegenMode = mode;
        FirstAid.naturalRegenStrategy = strategy;
        FirstAidConfig.persistCommandSettings();
        refreshNaturalRegenState(source);
        source.sendSuccess(() -> Component.translatable(
                "firstaid.command.naturalregen.mode_strategy",
                Component.translatable(getNaturalRegenModeKey(mode)),
                Component.translatable(getNaturalRegenStrategyKey(strategy))), true);
        return 1;
    }

    private static void refreshRescueWakeUpState(CommandSourceStack source) {
        if (source.getServer() == null) {
            return;
        }

        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (CommonUtils.getDamageModel(player) instanceof PlayerDamageModel playerDamageModel && playerDamageModel.refreshRescueWakeUpState(player)) {
                CommonUtils.syncDamageModel(player);
            }
        }
    }

    private static void refreshNaturalRegenState(CommandSourceStack source) {
        if (source.getServer() == null) {
            return;
        }
        boolean enabled = FirstAid.naturalRegenMode != FirstAid.NaturalRegenMode.OFF;
        source.getServer().getAllLevels().forEach(level -> level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.NATURAL_HEALTH_REGENERATION, enabled, source.getServer()));
    }

    private static String getNaturalRegenModeKey(FirstAid.NaturalRegenMode mode) {
        return switch (mode) {
            case OFF -> "firstaid.command.naturalregen.mode_value.off";
            case FULL -> "firstaid.command.naturalregen.mode_value.full";
            case LIMITED -> "firstaid.command.naturalregen.mode_value.limited";
            case LIMITED2 -> "firstaid.command.naturalregen.mode_value.limited2";
        };
    }

    private static String getNaturalRegenStrategyKey(FirstAid.NaturalRegenStrategy strategy) {
        return switch (strategy) {
            case RANDOM -> "firstaid.command.naturalregen.strategy_value.random";
            case CRITICAL -> "firstaid.command.naturalregen.strategy_value.critical";
        };
    }

    private static int setMedicineEffectMode(CommandSourceStack source, FirstAid.MedicineEffectMode mode) {
        FirstAid.medicineEffectMode = mode;
        FirstAid.medicineTimingMultiplier = mode.getTimingMultiplier();
        String key = switch (mode) {
            case ASSISTED -> "firstaid.command.medicineeffect.assisted";
            case CASUAL -> "firstaid.command.medicineeffect.casual";
            default -> "firstaid.command.medicineeffect.realistic";
        };
        source.sendSuccess(() -> Component.translatable(key), true);
        FirstAidConfig.persistCommandSettings();
        return 1;
    }

    private static int setInjuryDebuffMode(CommandSourceStack source, FirstAid.InjuryDebuffMode mode) {
        FirstAid.injuryDebuffMode = mode;
        String key = switch (mode) {
            case LOW -> "firstaid.command.injurydebuff.low";
            case OFF -> "firstaid.command.injurydebuff.off";
            default -> "firstaid.command.injurydebuff.normal";
        };
        source.sendSuccess(() -> Component.translatable(key), true);
        FirstAidConfig.persistCommandSettings();
        return 1;
    }

    private static int setInjuryDebuffModeForEffect(CommandSourceStack source, String effectInput, FirstAid.InjuryDebuffMode mode) {
        Identifier effectId = parseEffectId(source, effectInput);
        if (effectId == null) {
            return 0;
        }
        if (BuiltInRegistries.MOB_EFFECT.getValue(effectId) == null) {
            source.sendFailure(Component.translatable("firstaid.command.injurydebuff.effect.unknown", effectId.toString()));
            return 0;
        }
        FirstAid.setInjuryDebuffOverride(effectId, mode);
        String key = switch (mode) {
            case LOW -> "firstaid.command.injurydebuff.effect.low";
            case OFF -> "firstaid.command.injurydebuff.effect.off";
            default -> "firstaid.command.injurydebuff.effect.normal";
        };
        source.sendSuccess(() -> Component.translatable(key, effectId.toString()), true);
        FirstAidConfig.persistCommandSettings();
        return 1;
    }

    private static Identifier parseEffectId(CommandSourceStack source, String input) {
        Identifier effectId = Identifier.tryParse(input);
        if (effectId == null) {
            effectId = Identifier.tryParse("minecraft:" + input);
        }
        if (effectId == null) {
            source.sendFailure(Component.translatable("firstaid.command.injurydebuff.effect.invalid", input));
        }
        return effectId;
    }

    private static Identifier parseEntityId(CommandSourceStack source, String input) {
        Identifier entityId = Identifier.tryParse(input);
        if (entityId == null) {
            entityId = Identifier.tryParse("minecraft:" + input);
        }
        if (entityId == null) {
            source.sendFailure(Component.translatable("firstaid.command.suppression.blacklist.invalid", input));
        }
        return entityId;
    }
}


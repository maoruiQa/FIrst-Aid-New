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
import com.mojang.brigadier.arguments.FloatArgumentType;
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
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class FirstAidCommand {

    private FirstAidCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        MedicalSettingCommands.register(dispatcher);
        KnockdownCommands.register(dispatcher);
        dispatcher.register(Commands.literal("firstaid")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("pain")
                        .then(Commands.literal("dynamic")
                                .executes(context -> setDynamicPain(context.getSource(), true)))
                        .then(Commands.literal("mild")
                                .executes(context -> setDynamicPain(context.getSource(), false)))
                        .then(Commands.literal("display")
                                .then(Commands.literal("vignette")
                                        .then(Commands.literal("on")
                                                .executes(context -> setPainVignette(context.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(context -> setPainVignette(context.getSource(), false))))
                                .then(Commands.literal("hitpulse")
                                        .then(Commands.literal("on")
                                                .executes(context -> setPainVignette(context.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(context -> setPainVignette(context.getSource(), false))))
                                .then(Commands.literal("blur")
                                        .then(Commands.literal("on")
                                                .executes(context -> setPainBlur(context.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(context -> setPainBlur(context.getSource(), false))))
                                .then(Commands.literal("fov")
                                        .then(Commands.literal("on")
                                                .executes(context -> setPainFovCompression(context.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(context -> setPainFovCompression(context.getSource(), false))))
                                .then(Commands.literal("audio")
                                        .then(Commands.literal("on")
                                                .executes(context -> setPainAudioEffects(context.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(context -> setPainAudioEffects(context.getSource(), false))))))
                .then(buildAdrenalineBranch("suppression"))
            .then(buildAdrenalineBranch("adrenaline"))
                .then(Commands.literal("naturalregen")
                        .then(Commands.literal("off")
                                .executes(context -> setNaturalRegenMode(context.getSource(), FirstAid.NaturalRegenMode.OFF)))
                        .then(buildNaturalRegenBranch("limited", FirstAid.NaturalRegenMode.LIMITED))
                        .then(buildNaturalRegenBranch("limited2", FirstAid.NaturalRegenMode.LIMITED2))
                        .then(buildNaturalRegenBranch("full", FirstAid.NaturalRegenMode.FULL)))
                .then(Commands.literal("randomdamage")
                        .then(Commands.literal("friendly")
                                .executes(context -> setFriendlyRandomDistribution(context.getSource(), true))
                                .then(Commands.literal("chance")
                                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0D, 100.0D))
                                                .executes(context -> setFriendlyRandomDistributionChance(context.getSource(), DoubleArgumentType.getDouble(context, "percent"))))))
                        .then(Commands.literal("normal")
                                .executes(context -> setFriendlyRandomDistribution(context.getSource(), false))))
                .then(Commands.literal("medicineeffect")
                        .then(Commands.literal("realistic")
                                .executes(context -> setMedicineEffectMode(context.getSource(), FirstAid.MedicineEffectMode.REALISTIC)))
                        .then(Commands.literal("assisted")
                                .executes(context -> setMedicineEffectMode(context.getSource(), FirstAid.MedicineEffectMode.ASSISTED)))
                        .then(Commands.literal("casual")
                                .executes(context -> setMedicineEffectMode(context.getSource(), FirstAid.MedicineEffectMode.CASUAL)))
                        .then(Commands.literal("multiplier")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.01D, 20D))
                                        .executes(context -> setMedicineTimingMultiplier(context.getSource(), DoubleArgumentType.getDouble(context, "value"))))))
                .then(Commands.literal("injurydebuff")
                        .then(Commands.literal("normal")
                                .executes(context -> setInjuryDebuffMode(context.getSource(), FirstAid.InjuryDebuffMode.NORMAL)))
                        .then(Commands.literal("low")
                                .executes(context -> setInjuryDebuffMode(context.getSource(), FirstAid.InjuryDebuffMode.LOW)))
                        .then(Commands.literal("off")
                                .executes(context -> setInjuryDebuffMode(context.getSource(), FirstAid.InjuryDebuffMode.OFF))))
                .then(Commands.literal("revivewakeup")
                        .then(Commands.literal("on")
                                .executes(context -> setRescueWakeUp(context.getSource(), true))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                        .executes(context -> setRescueWakeUpDelay(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))))
                        .then(Commands.literal("off")
                                .executes(context -> setRescueWakeUp(context.getSource(), false)))));
        dispatcher.register(Commands.literal("firstaid")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("commandtips")
                        .then(Commands.literal("on")
                                .executes(context -> setCommandTips(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setCommandTips(context.getSource(), false)))));
        dispatcher.register(Commands.literal("firstaid")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("addiction")
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> queryAddiction(context.getSource(), EntityArgument.getPlayer(context, "player"))))
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 100.0F))
                                                .executes(context -> setAddiction(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        FloatArgumentType.getFloat(context, "value"))))))));
    }

    private static int setMedicineEffectMode(CommandSourceStack source, FirstAid.MedicineEffectMode mode) {
        FirstAid.medicineEffectMode = mode;
        FirstAid.medicineTimingMultiplier = mode.getTimingMultiplier();
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable("firstaid.command.medicineeffect." + mode.name().toLowerCase(java.util.Locale.ROOT)), true);
        return 1;
    }

    private static int setMedicineTimingMultiplier(CommandSourceStack source, double multiplier) {
        if (!Double.isFinite(multiplier)) return 0;
        FirstAid.medicineTimingMultiplier = (float) multiplier;
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable("firstaid.command.medicineeffect.multiplier", multiplier), true);
        return 1;
    }

    private static int setInjuryDebuffMode(CommandSourceStack source, FirstAid.InjuryDebuffMode mode) {
        FirstAid.injuryDebuffMode = mode;
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable("firstaid.command.injurydebuff." + mode.name().toLowerCase(java.util.Locale.ROOT)), true);
        return 1;
    }

    private static int queryAddiction(CommandSourceStack source, ServerPlayer target) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(target);
        if (!(damageModel instanceof PlayerDamageModel playerDamageModel)) {
            source.sendFailure(Component.translatable("firstaid.command.addiction.unavailable"));
            return 0;
        }
        float value = playerDamageModel.getAddictionValue();
        source.sendSuccess(() -> Component.translatable(
                "firstaid.command.addiction.query",
                target.getDisplayName(),
                String.format(java.util.Locale.ROOT, "%.1f", value)
        ), false);
        return 1;
    }

    private static int setAddiction(CommandSourceStack source, ServerPlayer target, float value) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(target);
        if (!(damageModel instanceof PlayerDamageModel playerDamageModel)) {
            source.sendFailure(Component.translatable("firstaid.command.addiction.unavailable"));
            return 0;
        }
        playerDamageModel.setAddictionValue(value);
        CommonUtils.syncDamageModel(target);
        source.sendSuccess(() -> Component.translatable(
                "firstaid.command.addiction.set",
                target.getDisplayName(),
                String.format(java.util.Locale.ROOT, "%.1f", playerDamageModel.getAddictionValue())
        ), true);
        return 1;
    }

    private static int setCommandTips(CommandSourceStack source, boolean enabled) {
        FirstAidConfig.setServerValue(FirstAidConfig.SERVER.commandTipsEnabled, enabled);
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.translatable(enabled
                ? "firstaid.command.commandtips.on"
                : "firstaid.command.commandtips.off"), true);
        return 1;
    }

    private static int setDynamicPain(CommandSourceStack source, boolean enabled) {
        FirstAid.dynamicPainEnabled = enabled;
        source.sendSuccess(() -> Component.translatable(enabled
                ? "firstaid.command.pain.dynamic"
                : "firstaid.command.pain.mild"), true);
        FirstAidConfig.persistCommandSettings();
        return 1;
    }

    private static int setPainVignette(CommandSourceStack source, boolean enabled) {
        FirstAid.enablePainVignette = enabled;
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable("firstaid.command.pain.display.vignette." + (enabled ? "on" : "off")), true);
        return 1;
    }
    private static int setPainBlur(CommandSourceStack source, boolean enabled) {
        FirstAid.enablePainBlur = enabled;
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable("firstaid.command.pain.display.blur." + (enabled ? "on" : "off")), true);
        return 1;
    }


    private static int setPainFovCompression(CommandSourceStack source, boolean enabled) {
        FirstAid.enablePainFovCompression = enabled;
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable("firstaid.command.pain.display.fov." + (enabled ? "on" : "off")), true);
        return 1;
    }

    private static int setPainAudioEffects(CommandSourceStack source, boolean enabled) {
        FirstAid.enablePainAudioEffects = enabled;
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable("firstaid.command.pain.display.audio." + (enabled ? "on" : "off")), true);
        return 1;
    }

    private static int setAdrenalineRelief(CommandSourceStack source, double multiplier) {
        FirstAid.adrenalineReliefMultiplier = (float) multiplier;
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable("firstaid.command.adrenaline.relief", multiplier), true);
        return 1;
    }

    private static int setSuppressionGain(CommandSourceStack source, double coefficient) {
        FirstAid.suppressionGainMultiplier = (float) coefficient;
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.literal("Adrenaline gain coefficient set to " + coefficient), true);
        return 1;
    }

    private static int setLowSuppression(CommandSourceStack source, boolean enabled) {
        FirstAid.projectileSuppressionEnabled = true;
        FirstAid.lowSuppressionEnabled = enabled;
        source.sendSuccess(() -> Component.translatable(enabled
                ? "firstaid.command.suppression.mild"
                : "firstaid.command.suppression.dynamic"), true);
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        return 1;
    }

    private static int setProjectileSuppression(CommandSourceStack source, boolean enabled) {
        FirstAid.projectileSuppressionEnabled = enabled;
        source.sendSuccess(() -> Component.translatable("firstaid.command.suppression.off"), true);
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        return 1;
    }

    private static int addSuppressionBlacklistEntry(CommandSourceStack source, String entityInput) {
        ResourceLocation entityId = parseEntityId(source, entityInput);
        if (entityId == null) {
            return 0;
        }
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) {
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
        ResourceLocation entityId = parseEntityId(source, entityInput);
        if (entityId == null) {
            return 0;
        }
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) {
            source.sendFailure(Component.translatable("firstaid.command.suppression.blacklist.unknown", entityId.toString()));
            return 0;
        }
        FirstAid.suppressionEntityBlacklist.remove(entityId);
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable("firstaid.command.suppression.blacklist.remove", entityId.toString()), true);
        return 1;
    }

    static void syncCommandSettings(CommandSourceStack source) {
        if (source.getServer() == null) {
            return;
        }
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            FirstAid.NETWORKING.send(PacketDistributor.PLAYER.with(() -> player), MessageSyncCommandSettings.current());
        }
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildAdrenalineFatigueBranch() {
        return Commands.literal("fatigue")
            .then(Commands.literal("on").executes(context -> setAdrenalineFatigueEnabled(context.getSource(), true)))
            .then(Commands.literal("off").executes(context -> setAdrenalineFatigueEnabled(context.getSource(), false)))
            .then(Commands.literal("threshold")
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                    .executes(context -> setAdrenalineFatigueThreshold(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))))
            .then(Commands.literal("ratio")
                .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0D, 100.0D))
                    .executes(context -> setAdrenalineFatigueRatio(context.getSource(), DoubleArgumentType.getDouble(context, "percent")))));
    }

    private static int setAdrenalineFatigueEnabled(CommandSourceStack source, boolean enabled) {
        FirstAidConfig.setServerValue(FirstAidConfig.SERVER.adrenalineFatigueEnabled, enabled);
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.translatable(enabled ? "firstaid.command.adrenaline.fatigue.on" : "firstaid.command.adrenaline.fatigue.off"), true);
        return 1;
    }

    private static int setAdrenalineFatigueThreshold(CommandSourceStack source, int seconds) {
        FirstAidConfig.setServerValue(FirstAidConfig.SERVER.adrenalineFatigueThresholdSeconds, seconds);
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.translatable("firstaid.command.adrenaline.fatigue.threshold", seconds), true);
        return 1;
    }

    private static int setAdrenalineFatigueRatio(CommandSourceStack source, double percent) {
        FirstAidConfig.setServerValue(FirstAidConfig.SERVER.adrenalineFatigueDurationRatio, percent / 100.0D);
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.translatable("firstaid.command.adrenaline.fatigue.ratio", percent), true);
        return 1;
    }

    private static int setAdrenalineMaxLevel(CommandSourceStack source, int level) {
        FirstAidConfig.setServerValue(FirstAidConfig.SERVER.adrenalineMaxLevel, level);
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.translatable("firstaid.command.adrenaline.maxlevel", level), true);
        return 1;
    }

    private static int setAdrenalineAttackGain(CommandSourceStack source, double multiplier) {
        FirstAidConfig.setServerValue(FirstAidConfig.SERVER.adrenalineAttackGainMultiplier, multiplier);
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.translatable("firstaid.command.adrenaline.attackgain", multiplier), true);
        return 1;
    }

    private static int setAdrenalineAttackCooldown(CommandSourceStack source, int ticks) {
        FirstAidConfig.setServerValue(FirstAidConfig.SERVER.adrenalineAttackCooldownTicks, ticks);
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.translatable("firstaid.command.adrenaline.attackcooldown", ticks), true);
        return 1;
    }

    private static int setAdrenalineAttackHold(CommandSourceStack source, int ticks) {
        FirstAidConfig.setServerValue(FirstAidConfig.SERVER.adrenalineAttackHoldTicks, ticks);
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.translatable("firstaid.command.adrenaline.attackhold", ticks), true);
        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildAdrenalineBranch(String literalName) {
        return Commands.literal(literalName)
                  .then(buildAdrenalineFatigueBranch())
                  .then(Commands.literal("relief")
                     .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.0D, 2.0D))
                        .executes(context -> setAdrenalineRelief(context.getSource(), DoubleArgumentType.getDouble(context, "multiplier")))))
                  .then(Commands.literal("gain")
                     .then(Commands.argument("coefficient", DoubleArgumentType.doubleArg(0.01D, 1.0D))
                        .executes(context -> setSuppressionGain(context.getSource(), DoubleArgumentType.getDouble(context, "coefficient")))))
                  .then(Commands.literal("maxlevel")
                     .then(Commands.argument("level", IntegerArgumentType.integer(0, 5))
                        .executes(context -> setAdrenalineMaxLevel(context.getSource(), IntegerArgumentType.getInteger(context, "level")))))
                  .then(Commands.literal("attackgain")
                     .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.0D, 2.0D))
                        .executes(context -> setAdrenalineAttackGain(context.getSource(), DoubleArgumentType.getDouble(context, "multiplier")))))
                  .then(Commands.literal("attackcooldown")
                     .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 200))
                        .executes(context -> setAdrenalineAttackCooldown(context.getSource(), IntegerArgumentType.getInteger(context, "ticks")))))
                  .then(Commands.literal("attackhold")
                     .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 200))
                        .executes(context -> setAdrenalineAttackHold(context.getSource(), IntegerArgumentType.getInteger(context, "ticks")))))
                        .then(Commands.literal("dynamic")
                                .executes(context -> setLowSuppression(context.getSource(), false)))
                        .then(Commands.literal("mild")
                                .executes(context -> setLowSuppression(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setProjectileSuppression(context.getSource(), false)))
                        .then(Commands.literal("blacklist")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("entity", StringArgumentType.greedyString())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder))
                                                .executes(context -> addSuppressionBlacklistEntry(context.getSource(), StringArgumentType.getString(context, "entity")))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("entity", StringArgumentType.greedyString())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(FirstAid.suppressionEntityBlacklist, builder))
                                                .executes(context -> removeSuppressionBlacklistEntry(context.getSource(), StringArgumentType.getString(context, "entity"))))));
    }


    private static LiteralArgumentBuilder<CommandSourceStack> buildNaturalRegenBranch(String literal, FirstAid.NaturalRegenMode mode) {
        return Commands.literal(literal)
                .executes(context -> setNaturalRegenMode(context.getSource(), mode))
                .then(Commands.literal("critical")
                        .executes(context -> setNaturalRegenModeAndStrategy(context.getSource(), mode, FirstAid.NaturalRegenStrategy.CRITICAL)))
                .then(Commands.literal("random")
                        .executes(context -> setNaturalRegenModeAndStrategy(context.getSource(), mode, FirstAid.NaturalRegenStrategy.RANDOM)));
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

    static void refreshNaturalRegenState(CommandSourceStack source) {
        if (source.getServer() == null) {
            return;
        }
        boolean enabled = FirstAid.naturalRegenMode != FirstAid.NaturalRegenMode.OFF;
        source.getServer().getAllLevels().forEach(level ->
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION).set(enabled, source.getServer()));
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

    private static int setFriendlyRandomDistribution(CommandSourceStack source, boolean enabled) {
        FirstAid.useFriendlyRandomDistribution = enabled;
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.translatable(enabled
                ? "firstaid.command.randomdamage.friendly"
                : "firstaid.command.randomdamage.normal"), true);
        return 1;
    }

    private static int setFriendlyRandomDistributionChance(CommandSourceStack source, double percent) {
        FirstAid.friendlyRandomDistributionChance = FirstAid.clampFriendlyRandomDistributionChance((float) (percent / 100.0D));
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.translatable("firstaid.command.randomdamage.chance", Math.round(FirstAid.friendlyRandomDistributionChance * 1000.0F) / 10.0F), true);
        return 1;
    }

    private static ResourceLocation parseEntityId(CommandSourceStack source, String input) {
        ResourceLocation entityId = ResourceLocation.tryParse(input);
        if (entityId == null) {
            entityId = ResourceLocation.tryParse("minecraft:" + input);
        }
        if (entityId == null) {
            source.sendFailure(Component.translatable("firstaid.command.suppression.blacklist.invalid", input));
        }
        return entityId;
    }
}

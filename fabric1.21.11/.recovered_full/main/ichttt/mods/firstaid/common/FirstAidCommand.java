/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  com.mojang.brigadier.arguments.ArgumentType
 *  com.mojang.brigadier.arguments.StringArgumentType
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  com.mojang.brigadier.builder.RequiredArgumentBuilder
 *  com.mojang.brigadier.context.CommandContext
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.commands.Commands
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.Identifier
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.server.permissions.Permissions
 *  net.minecraft.world.entity.player.Player
 */
package ichttt.mods.firstaid.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;

public final class FirstAidCommand {
    private FirstAidCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"firstaid").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))).then(((LiteralArgumentBuilder)Commands.literal((String)"pain").then(Commands.literal((String)"dynamic").executes(context -> FirstAidCommand.setDynamicPain((CommandSourceStack)context.getSource(), true)))).then(Commands.literal((String)"mild").executes(context -> FirstAidCommand.setDynamicPain((CommandSourceStack)context.getSource(), false))))).then(((LiteralArgumentBuilder)Commands.literal((String)"suppression").then(Commands.literal((String)"dynamic").executes(context -> FirstAidCommand.setLowSuppression((CommandSourceStack)context.getSource(), false)))).then(Commands.literal((String)"mild").executes(context -> FirstAidCommand.setLowSuppression((CommandSourceStack)context.getSource(), true))))).then(((LiteralArgumentBuilder)Commands.literal((String)"revivewakeup").then(Commands.literal((String)"on").executes(context -> FirstAidCommand.setRescueWakeUp((CommandSourceStack)context.getSource(), true)))).then(Commands.literal((String)"off").executes(context -> FirstAidCommand.setRescueWakeUp((CommandSourceStack)context.getSource(), false))))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"medicineeffect").then(Commands.literal((String)"realistic").executes(context -> FirstAidCommand.setMedicineEffectMode((CommandSourceStack)context.getSource(), FirstAid.MedicineEffectMode.REALISTIC)))).then(Commands.literal((String)"assisted").executes(context -> FirstAidCommand.setMedicineEffectMode((CommandSourceStack)context.getSource(), FirstAid.MedicineEffectMode.ASSISTED)))).then(Commands.literal((String)"casual").executes(context -> FirstAidCommand.setMedicineEffectMode((CommandSourceStack)context.getSource(), FirstAid.MedicineEffectMode.CASUAL))))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"injurydebuff").then(Commands.literal((String)"normal").executes(context -> FirstAidCommand.setInjuryDebuffMode((CommandSourceStack)context.getSource(), FirstAid.InjuryDebuffMode.NORMAL)))).then(Commands.literal((String)"low").executes(context -> FirstAidCommand.setInjuryDebuffMode((CommandSourceStack)context.getSource(), FirstAid.InjuryDebuffMode.LOW)))).then(Commands.literal((String)"off").executes(context -> FirstAidCommand.setInjuryDebuffMode((CommandSourceStack)context.getSource(), FirstAid.InjuryDebuffMode.OFF)))).then(((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument((String)"effect", (ArgumentType)StringArgumentType.word()).then(Commands.literal((String)"normal").executes(context -> FirstAidCommand.setInjuryDebuffModeForEffect((CommandSourceStack)context.getSource(), StringArgumentType.getString((CommandContext)context, (String)"effect"), FirstAid.InjuryDebuffMode.NORMAL)))).then(Commands.literal((String)"low").executes(context -> FirstAidCommand.setInjuryDebuffModeForEffect((CommandSourceStack)context.getSource(), StringArgumentType.getString((CommandContext)context, (String)"effect"), FirstAid.InjuryDebuffMode.LOW)))).then(Commands.literal((String)"off").executes(context -> FirstAidCommand.setInjuryDebuffModeForEffect((CommandSourceStack)context.getSource(), StringArgumentType.getString((CommandContext)context, (String)"effect"), FirstAid.InjuryDebuffMode.OFF))))));
    }

    private static int setDynamicPain(CommandSourceStack source, boolean enabled) {
        FirstAid.dynamicPainEnabled = enabled;
        if (source.getServer() != null) {
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel((Player)player);
                if (!(damageModel instanceof PlayerDamageModel)) continue;
                PlayerDamageModel playerDamageModel = (PlayerDamageModel)damageModel;
                playerDamageModel.refreshPainState((Player)player);
                FirstAidNetworking.sendDamageModelSync(player, playerDamageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
            }
        }
        source.sendSuccess(() -> Component.translatable((String)(enabled ? "firstaid.command.pain.dynamic" : "firstaid.command.pain.mild")), true);
        FirstAidConfig.persistCommandSettings();
        return 1;
    }

    private static int setLowSuppression(CommandSourceStack source, boolean enabled) {
        FirstAid.lowSuppressionEnabled = enabled;
        source.sendSuccess(() -> Component.translatable((String)(enabled ? "firstaid.command.suppression.mild" : "firstaid.command.suppression.dynamic")), true);
        FirstAidConfig.persistCommandSettings();
        return 1;
    }

    private static int setRescueWakeUp(CommandSourceStack source, boolean enabled) {
        FirstAid.rescueWakeUpEnabled = enabled;
        source.sendSuccess(() -> Component.translatable((String)(enabled ? "firstaid.command.revivewakeup.on" : "firstaid.command.revivewakeup.off")), true);
        FirstAidConfig.persistCommandSettings();
        return 1;
    }

    private static int setMedicineEffectMode(CommandSourceStack source, FirstAid.MedicineEffectMode mode) {
        FirstAid.medicineEffectMode = mode;
        String key = switch (mode) {
            case FirstAid.MedicineEffectMode.ASSISTED -> "firstaid.command.medicineeffect.assisted";
            case FirstAid.MedicineEffectMode.CASUAL -> "firstaid.command.medicineeffect.casual";
            default -> "firstaid.command.medicineeffect.realistic";
        };
        source.sendSuccess(() -> Component.translatable((String)key), true);
        FirstAidConfig.persistCommandSettings();
        return 1;
    }

    private static int setInjuryDebuffMode(CommandSourceStack source, FirstAid.InjuryDebuffMode mode) {
        FirstAid.injuryDebuffMode = mode;
        String key = switch (mode) {
            case FirstAid.InjuryDebuffMode.LOW -> "firstaid.command.injurydebuff.low";
            case FirstAid.InjuryDebuffMode.OFF -> "firstaid.command.injurydebuff.off";
            default -> "firstaid.command.injurydebuff.normal";
        };
        source.sendSuccess(() -> Component.translatable((String)key), true);
        FirstAidConfig.persistCommandSettings();
        return 1;
    }

    private static int setInjuryDebuffModeForEffect(CommandSourceStack source, String effectInput, FirstAid.InjuryDebuffMode mode) {
        Identifier effectId = FirstAidCommand.parseEffectId(source, effectInput);
        if (effectId == null) {
            return 0;
        }
        if (BuiltInRegistries.MOB_EFFECT.getValue(effectId) == null) {
            source.sendFailure((Component)Component.translatable((String)"firstaid.command.injurydebuff.effect.unknown", (Object[])new Object[]{effectId.toString()}));
            return 0;
        }
        FirstAid.setInjuryDebuffOverride(effectId, mode);
        String key = switch (mode) {
            case FirstAid.InjuryDebuffMode.LOW -> "firstaid.command.injurydebuff.effect.low";
            case FirstAid.InjuryDebuffMode.OFF -> "firstaid.command.injurydebuff.effect.off";
            default -> "firstaid.command.injurydebuff.effect.normal";
        };
        source.sendSuccess(() -> Component.translatable((String)key, (Object[])new Object[]{effectId.toString()}), true);
        FirstAidConfig.persistCommandSettings();
        return 1;
    }

    private static Identifier parseEffectId(CommandSourceStack source, String input) {
        Identifier effectId = Identifier.tryParse((String)input);
        if (effectId == null) {
            effectId = Identifier.tryParse((String)("minecraft:" + input));
        }
        if (effectId == null) {
            source.sendFailure((Component)Component.translatable((String)"firstaid.command.injurydebuff.effect.invalid", (Object[])new Object[]{input}));
        }
        return effectId;
    }
}


/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  com.mojang.brigadier.Message
 *  com.mojang.brigadier.arguments.ArgumentType
 *  com.mojang.brigadier.arguments.FloatArgumentType
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  com.mojang.brigadier.builder.RequiredArgumentBuilder
 *  com.mojang.brigadier.context.CommandContext
 *  com.mojang.brigadier.exceptions.CommandSyntaxException
 *  com.mojang.brigadier.exceptions.SimpleCommandExceptionType
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.commands.Commands
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.server.permissions.Permissions
 *  net.minecraft.world.entity.player.Player
 */
package ichttt.mods.firstaid.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.damagesystem.distribution.DamageDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.DirectDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;

public class DebugDamageCommand {
    private static final SimpleCommandExceptionType TYPE = new SimpleCommandExceptionType((Message)Component.literal((String)"0 is invalid as damage"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder builder = (LiteralArgumentBuilder)Commands.literal((String)"damagePart").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
        ArrayList<EnumPlayerPart> allowedValues = new ArrayList<EnumPlayerPart>(Arrays.asList(EnumPlayerPart.VALUES));
        allowedValues.add(null);
        for (EnumPlayerPart part : allowedValues) {
            builder.then(Commands.literal((String)(part == null ? "ALL" : part.name())).then(((RequiredArgumentBuilder)Commands.argument((String)"damage", (ArgumentType)FloatArgumentType.floatArg()).executes(context -> DebugDamageCommand.handleCommand(part, FloatArgumentType.getFloat((CommandContext)context, (String)"damage"), true, ((CommandSourceStack)context.getSource()).getPlayerOrException()))).then(Commands.literal((String)"nodebuff").executes(context -> DebugDamageCommand.handleCommand(part, FloatArgumentType.getFloat((CommandContext)context, (String)"damage"), false, ((CommandSourceStack)context.getSource()).getPlayerOrException())))));
        }
        dispatcher.register(builder);
    }

    private static int handleCommand(EnumPlayerPart part, float damage, boolean debuff, ServerPlayer player) throws CommandSyntaxException {
        if (damage == 0.0f) {
            throw TYPE.create();
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel((Player)player);
        if (damageModel == null) {
            return 0;
        }
        if (part == null) {
            for (EnumPlayerPart aPart : EnumPlayerPart.VALUES) {
                DebugDamageCommand.doDamage(aPart, damage, debuff, player, damageModel);
            }
        } else {
            DebugDamageCommand.doDamage(part, damage, debuff, player, damageModel);
        }
        if (damageModel.isDead((Player)player)) {
            player.sendSystemMessage((Component)Component.translatable((String)"death.attack.generic", (Object[])new Object[]{player.getDisplayName()}));
            CommonUtils.killPlayer(damageModel, (Player)player, null);
        }
        FirstAidNetworking.sendDamageModelSync(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
        return 1;
    }

    private static void doDamage(EnumPlayerPart part, float damage, boolean debuff, ServerPlayer player, AbstractPlayerDamageModel damageModel) {
        if (damage > 0.0f) {
            DamageDistribution.handleDamageTaken(new DirectDamageDistributionAlgorithm(part, debuff), damageModel, damage, (Player)player, player.damageSources().fellOutOfWorld(), false, false);
        } else {
            damageModel.getFromEnum(part).heal(-damage, (Player)player, debuff);
        }
    }
}


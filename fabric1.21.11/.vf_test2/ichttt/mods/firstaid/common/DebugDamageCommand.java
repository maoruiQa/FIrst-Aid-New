package ichttt.mods.firstaid.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public class DebugDamageCommand {
   private static final SimpleCommandExceptionType TYPE = new SimpleCommandExceptionType(Component.literal("0 is invalid as damage"));

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      LiteralArgumentBuilder<CommandSourceStack> builder = (LiteralArgumentBuilder<CommandSourceStack>)Commands.literal("damagePart")
         .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
      List<EnumPlayerPart> allowedValues = new ArrayList<>(Arrays.asList(EnumPlayerPart.VALUES));
      allowedValues.add(null);

      for (EnumPlayerPart part : allowedValues) {
         builder.then(
            Commands.literal(part == null ? "ALL" : part.name())
               .then(
                  ((RequiredArgumentBuilder)Commands.argument("damage", FloatArgumentType.floatArg())
                        .executes(
                           context -> handleCommand(
                              part, FloatArgumentType.getFloat(context, "damage"), true, ((CommandSourceStack)context.getSource()).getPlayerOrException()
                           )
                        ))
                     .then(
                        Commands.literal("nodebuff")
                           .executes(
                              context -> handleCommand(
                                 part, FloatArgumentType.getFloat(context, "damage"), false, ((CommandSourceStack)context.getSource()).getPlayerOrException()
                              )
                           )
                     )
               )
         );
      }

      dispatcher.register(builder);
   }

   private static int handleCommand(EnumPlayerPart part, float damage, boolean debuff, ServerPlayer player) throws CommandSyntaxException {
      if (damage == 0.0F) {
         throw TYPE.create();
      } else {
         AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
         if (damageModel == null) {
            return 0;
         } else {
            if (part == null) {
               for (EnumPlayerPart aPart : EnumPlayerPart.VALUES) {
                  doDamage(aPart, damage, debuff, player, damageModel);
               }
            } else {
               doDamage(part, damage, debuff, player, damageModel);
            }

            if (damageModel.isDead(player)) {
               player.sendSystemMessage(Component.translatable("death.attack.generic", new Object[]{player.getDisplayName()}));
               CommonUtils.killPlayer(damageModel, player, null);
            }

            FirstAidNetworking.sendDamageModelSync(player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
            return 1;
         }
      }
   }

   private static void doDamage(EnumPlayerPart part, float damage, boolean debuff, ServerPlayer player, AbstractPlayerDamageModel damageModel) {
      if (damage > 0.0F) {
         DamageDistribution.handleDamageTaken(
            new DirectDamageDistributionAlgorithm(part, debuff), damageModel, damage, player, player.damageSources().fellOutOfWorld(), false, false
         );
      } else {
         damageModel.getFromEnum(part).heal(-damage, player, debuff);
      }
   }
}

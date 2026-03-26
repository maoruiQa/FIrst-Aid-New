package ichttt.mods.firstaid.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
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

public final class FirstAidCommand {
   private FirstAidCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                              "firstaid"
                           )
                           .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)))
                        .then(
                           ((LiteralArgumentBuilder)Commands.literal("pain")
                                 .then(Commands.literal("dynamic").executes(context -> setDynamicPain((CommandSourceStack)context.getSource(), true))))
                              .then(Commands.literal("mild").executes(context -> setDynamicPain((CommandSourceStack)context.getSource(), false)))
                        ))
                     .then(
                        ((LiteralArgumentBuilder)Commands.literal("suppression")
                              .then(Commands.literal("dynamic").executes(context -> setLowSuppression((CommandSourceStack)context.getSource(), false))))
                           .then(Commands.literal("mild").executes(context -> setLowSuppression((CommandSourceStack)context.getSource(), true)))
                     ))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("revivewakeup")
                           .then(
                              ((LiteralArgumentBuilder)Commands.literal("on")
                                    .executes(context -> setRescueWakeUp((CommandSourceStack)context.getSource(), true)))
                                 .then(
                                    Commands.argument("seconds", IntegerArgumentType.integer(0))
                                       .executes(
                                          context -> setRescueWakeUpDelay(
                                                (CommandSourceStack)context.getSource(), IntegerArgumentType.getInteger(context, "seconds")
                                             )
                                       )
                                 )
                           ))
                        .then(Commands.literal("off").executes(context -> setRescueWakeUp((CommandSourceStack)context.getSource(), false)))
                  ))
               .then(
                  ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("medicineeffect")
                           .then(
                              Commands.literal("realistic")
                                 .executes(context -> setMedicineEffectMode((CommandSourceStack)context.getSource(), FirstAid.MedicineEffectMode.REALISTIC))
                           ))
                        .then(
                           Commands.literal("assisted")
                              .executes(context -> setMedicineEffectMode((CommandSourceStack)context.getSource(), FirstAid.MedicineEffectMode.ASSISTED))
                        ))
                     .then(
                        Commands.literal("casual")
                           .executes(context -> setMedicineEffectMode((CommandSourceStack)context.getSource(), FirstAid.MedicineEffectMode.CASUAL))
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("injurydebuff")
                           .then(
                              Commands.literal("normal")
                                 .executes(context -> setInjuryDebuffMode((CommandSourceStack)context.getSource(), FirstAid.InjuryDebuffMode.NORMAL))
                           ))
                        .then(
                           Commands.literal("low")
                              .executes(context -> setInjuryDebuffMode((CommandSourceStack)context.getSource(), FirstAid.InjuryDebuffMode.LOW))
                        ))
                     .then(
                        Commands.literal("off")
                           .executes(context -> setInjuryDebuffMode((CommandSourceStack)context.getSource(), FirstAid.InjuryDebuffMode.OFF))
                     ))
                  .then(
                     ((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("effect", StringArgumentType.word())
                              .then(
                                 Commands.literal("normal")
                                    .executes(
                                       context -> setInjuryDebuffModeForEffect(
                                          (CommandSourceStack)context.getSource(),
                                          StringArgumentType.getString(context, "effect"),
                                          FirstAid.InjuryDebuffMode.NORMAL
                                       )
                                    )
                              ))
                           .then(
                              Commands.literal("low")
                                 .executes(
                                    context -> setInjuryDebuffModeForEffect(
                                       (CommandSourceStack)context.getSource(), StringArgumentType.getString(context, "effect"), FirstAid.InjuryDebuffMode.LOW
                                    )
                                 )
                           ))
                        .then(
                           Commands.literal("off")
                              .executes(
                                 context -> setInjuryDebuffModeForEffect(
                                    (CommandSourceStack)context.getSource(), StringArgumentType.getString(context, "effect"), FirstAid.InjuryDebuffMode.OFF
                                 )
                              )
                        )
                  )
            )
      );
   }

   private static int setDynamicPain(CommandSourceStack source, boolean enabled) {
      FirstAid.dynamicPainEnabled = enabled;
      if (source.getServer() != null) {
         for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (CommonUtils.getDamageModel(player) instanceof PlayerDamageModel playerDamageModel) {
               playerDamageModel.refreshPainState(player);
               FirstAidNetworking.sendDamageModelSync(player, playerDamageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
            }
         }
      }

      source.sendSuccess(() -> Component.translatable(enabled ? "firstaid.command.pain.dynamic" : "firstaid.command.pain.mild"), true);
      FirstAidConfig.persistCommandSettings();
      return 1;
   }

   private static int setLowSuppression(CommandSourceStack source, boolean enabled) {
      FirstAid.lowSuppressionEnabled = enabled;
      source.sendSuccess(() -> Component.translatable(enabled ? "firstaid.command.suppression.mild" : "firstaid.command.suppression.dynamic"), true);
      FirstAidConfig.persistCommandSettings();
      return 1;
   }

   private static int setRescueWakeUp(CommandSourceStack source, boolean enabled) {
      FirstAid.rescueWakeUpEnabled = enabled;
      FirstAidConfig.persistCommandSettings();
      refreshRescueWakeUpState(source);
      source.sendSuccess(() -> Component.translatable(enabled ? "firstaid.command.revivewakeup.on" : "firstaid.command.revivewakeup.off"), true);
      return 1;
   }

   private static int setRescueWakeUpDelay(CommandSourceStack source, int seconds) {
      FirstAid.rescueWakeUpEnabled = true;
      FirstAid.rescueWakeUpDelaySeconds = seconds;
      FirstAidConfig.persistCommandSettings();
      refreshRescueWakeUpState(source);
      source.sendSuccess(() -> Component.translatable("firstaid.command.revivewakeup.time", new Object[]{seconds}), true);
      return 1;
   }

   private static void refreshRescueWakeUpState(CommandSourceStack source) {
      if (source.getServer() == null) {
         return;
      }

      for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
         FirstAidNetworking.sendServerConfig(player);
         if (CommonUtils.getDamageModel(player) instanceof PlayerDamageModel playerDamageModel && playerDamageModel.refreshRescueWakeUpState(player)) {
            FirstAidNetworking.sendDamageModelSync(player, playerDamageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
         }
      }
   }

   private static int setMedicineEffectMode(CommandSourceStack source, FirstAid.MedicineEffectMode mode) {
      FirstAid.medicineEffectMode = mode;

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
      } else if (BuiltInRegistries.MOB_EFFECT.getValue(effectId) == null) {
         source.sendFailure(Component.translatable("firstaid.command.injurydebuff.effect.unknown", new Object[]{effectId.toString()}));
         return 0;
      } else {
         FirstAid.setInjuryDebuffOverride(effectId, mode);

         String key = switch (mode) {
            case LOW -> "firstaid.command.injurydebuff.effect.low";
            case OFF -> "firstaid.command.injurydebuff.effect.off";
            default -> "firstaid.command.injurydebuff.effect.normal";
         };
         source.sendSuccess(() -> Component.translatable(key, new Object[]{effectId.toString()}), true);
         FirstAidConfig.persistCommandSettings();
         return 1;
      }
   }

   private static Identifier parseEffectId(CommandSourceStack source, String input) {
      Identifier effectId = Identifier.tryParse(input);
      if (effectId == null) {
         effectId = Identifier.tryParse("minecraft:" + input);
      }

      if (effectId == null) {
         source.sendFailure(Component.translatable("firstaid.command.injurydebuff.effect.invalid", new Object[]{input}));
      }

      return effectId;
   }
}

package ichttt.mods.firstaid.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public final class MedicalSettingCommands {
   private static boolean reloadPending;

   private MedicalSettingCommands() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      var health = Commands.literal("health");
      for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
         health.then(Commands.literal(part.getSerializedName())
            .executes(context -> queryHealth(context.getSource(), part))
            .then(Commands.argument("value", IntegerArgumentType.integer(2, 12))
               .executes(context -> setHealth(context.getSource(), part, IntegerArgumentType.getInteger(context, "value")))));
      }
      var gain = Commands.literal("gain");
      addDouble(gain, "morphine", () -> FirstAidConfig.SERVER.morphineAddictionGain.get(), FirstAidConfig.SERVER.morphineAddictionGain::set);
      addDouble(gain, "morphine_injector", () -> FirstAidConfig.SERVER.morphineInjectorAddictionGain.get(), FirstAidConfig.SERVER.morphineInjectorAddictionGain::set);
      var yields = Commands.literal("yield");
      addInt(yields, "bandage", "yield", () -> FirstAidConfig.SERVER.bandageCraftYield.get(), FirstAidConfig.SERVER.bandageCraftYield::set, 16);
      addInt(yields, "plaster", "yield", () -> FirstAidConfig.SERVER.plasterCraftYield.get(), FirstAidConfig.SERVER.plasterCraftYield::set, 16);
      addInt(yields, "painkillers", "yield", () -> FirstAidConfig.SERVER.painkillersCraftYield.get(), FirstAidConfig.SERVER.painkillersCraftYield::set, 16);
      addInt(yields, "morphine", "yield", () -> FirstAidConfig.SERVER.morphineCraftYield.get(), FirstAidConfig.SERVER.morphineCraftYield::set, 16);
      var durability = Commands.literal("durability");
      addInt(durability, "morphine_injector", "durability", () -> FirstAidConfig.SERVER.morphineInjectorCraftUses.get(), FirstAidConfig.SERVER.morphineInjectorCraftUses::set, 1000);
      addInt(durability, "adrenaline_injector", "durability", () -> FirstAidConfig.SERVER.adrenalineInjectorCraftUses.get(), FirstAidConfig.SERVER.adrenalineInjectorCraftUses::set, 1000);
      addInt(durability, "defibrillator", "durability", () -> FirstAidConfig.SERVER.defibrillatorCraftUses.get(), FirstAidConfig.SERVER.defibrillatorCraftUses::set, 1000);
      dispatcher.register(Commands.literal("firstaid")
         .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
         .then(health)
         .then(Commands.literal("reset").executes(context -> resetSettings(context.getSource())))
         .then(Commands.literal("addiction").then(gain))
         .then(Commands.literal("crafting").then(yields).then(durability)));
   }

   private static int resetSettings(CommandSourceStack source) {
      if (reloadPending) {
         source.sendFailure(Component.translatable("firstaid.command.setting.busy"));
         return 0;
      }
      try {
         FirstAidConfig.resetServerSettings();
      } catch (RuntimeException error) {
         FirstAid.LOGGER.error("Failed to reset First Aid server settings", error);
         source.sendFailure(Component.translatable("firstaid.command.reset.failed"));
         return 0;
      }
      FirstAidCommand.refreshNaturalRegenState(source);
      FirstAidCommand.syncCommandSettings(source);
      for (var player : source.getServer().getPlayerList().getPlayers()) {
         if (CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model) {
            model.refreshMaxHealth(player);
            model.refreshRescueWakeUpState(player);
            model.refreshPainState(player);
            CommonUtils.syncDamageModel(player);
         }
      }
      reloadPending = true;
      var server = source.getServer();
      try {
         server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored, error) -> server.execute(() -> {
            reloadPending = false;
            if (error != null) {
               FirstAid.LOGGER.error("Recipe reload after First Aid reset failed", error);
               source.sendFailure(Component.translatable("firstaid.command.reset.reload_failed"));
            } else {
               source.sendSuccess(() -> Component.translatable("firstaid.command.reset.done"), true);
            }
         }));
      } catch (RuntimeException error) {
         reloadPending = false;
         FirstAid.LOGGER.error("Recipe reload after First Aid reset failed", error);
         source.sendFailure(Component.translatable("firstaid.command.reset.reload_failed"));
         return 0;
      }
      return 1;
   }

   private static int queryHealth(CommandSourceStack source, EnumPlayerPart part) {
      source.sendSuccess(() -> Component.translatable("firstaid.command.health.query",
         Component.translatable("firstaid.gui." + part.getSerializedName()), FirstAidConfig.getPartMaxHealth(part).get()), false);
      return 1;
   }

   private static int setHealth(CommandSourceStack source, EnumPlayerPart part, int value) {
      FirstAidConfig.getPartMaxHealth(part).set(value);
      FirstAidConfig.persistCommandSettings();
      for (var player : source.getServer().getPlayerList().getPlayers()) {
         if (CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model) {
            model.refreshMaxHealth(player);
            CommonUtils.syncDamageModel(player);
         }
      }
      source.sendSuccess(() -> Component.translatable("firstaid.command.health.set",
         Component.translatable("firstaid.gui." + part.getSerializedName()), value), true);
      return 1;
   }

   private static void addDouble(LiteralArgumentBuilder<CommandSourceStack> parent, String item, DoubleSupplier get, DoubleConsumer set) {
      parent.then(Commands.literal(item)
         .executes(context -> query(context.getSource(), "gain", item, format(get.getAsDouble())))
         .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 100.0D))
            .executes(context -> {
               double value = DoubleArgumentType.getDouble(context, "value");
               set.accept(value);
               FirstAidConfig.persistCommandSettings();
               success(context.getSource(), "gain", item, format(value));
               return 1;
            })));
   }

   private static void addInt(LiteralArgumentBuilder<CommandSourceStack> parent, String item, String kind,
                              IntSupplier get, IntConsumer set, int max) {
      parent.then(Commands.literal(item)
         .executes(context -> query(context.getSource(), kind, item, Integer.toString(get.getAsInt())))
         .then(Commands.argument("value", IntegerArgumentType.integer(1, max))
            .executes(context -> {
               var source = context.getSource();
               if (reloadPending) {
                  source.sendFailure(Component.translatable("firstaid.command.setting.busy"));
                  return 0;
               }
               int before = get.getAsInt();
               int value = IntegerArgumentType.getInteger(context, "value");
               set.accept(value);
               FirstAidConfig.persistCommandSettings();
               reloadPending = true;
               var server = source.getServer();
               try {
                  server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored, error) -> server.execute(() -> {
                     reloadPending = false;
                     if (error != null) {
                        set.accept(before);
                        FirstAidConfig.persistCommandSettings();
                        source.sendFailure(Component.translatable("firstaid.command.setting.reload_failed"));
                     } else {
                        success(source, kind, item, Integer.toString(value));
                     }
                  }));
               } catch (RuntimeException error) {
                  reloadPending = false;
                  set.accept(before);
                  FirstAidConfig.persistCommandSettings();
                  source.sendFailure(Component.translatable("firstaid.command.setting.reload_failed"));
                  return 0;
               }
               return 1;
            })));
   }

   private static int query(CommandSourceStack source, String kind, String item, String value) {
      source.sendSuccess(() -> Component.translatable("firstaid.command.setting.query", label(kind), label(item), value), false);
      return 1;
   }

   private static void success(CommandSourceStack source, String kind, String item, String value) {
      source.sendSuccess(() -> Component.translatable("firstaid.command.setting.set", label(kind), label(item), value), true);
   }

   private static Component label(String key) {
      return Component.translatable(key.equals("gain") || key.equals("yield") || key.equals("durability")
         ? "firstaid.command.setting." + key : "item.firstaid." + key);
   }

   private static String format(double value) {
      return String.format(Locale.ROOT, "%.1f", value);
   }
}

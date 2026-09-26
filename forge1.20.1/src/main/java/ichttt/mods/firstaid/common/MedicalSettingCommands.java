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
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import ichttt.mods.firstaid.FirstAidConfig;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class MedicalSettingCommands {
   private static boolean reloadPending;

   private MedicalSettingCommands() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
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
         .requires(source -> source.hasPermission(2))
         .then(Commands.literal("addiction").then(gain))
         .then(Commands.literal("crafting").then(yields).then(durability)));
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

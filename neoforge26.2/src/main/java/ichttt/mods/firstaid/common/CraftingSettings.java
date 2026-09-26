package ichttt.mods.firstaid.common;

import ichttt.mods.firstaid.FirstAidConfig;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class CraftingSettings {
   private CraftingSettings() {
   }

   public interface ResultAccess {
      ItemStackTemplate firstaid$getResult();

      void firstaid$setResult(ItemStackTemplate result);
   }

   private static int configured(ModConfigSpec.IntValue value) {
      try {
         return value.get();
      } catch (IllegalStateException notLoadedYet) {
         return value.getDefault();
      }
   }

   public static void adjust(String namespace, String path, Recipe<?> recipe) {
      if (!"firstaid".equals(namespace) || !(recipe instanceof ResultAccess access)) {
         return;
      }
      ItemStackTemplate result = access.firstaid$getResult();
      if (!result.item().is(net.minecraft.resources.Identifier.fromNamespaceAndPath("firstaid", path))) {
         return;
      }
      int count = switch (path) {
         case "bandage" -> configured(FirstAidConfig.SERVER.bandageCraftYield);
         case "plaster" -> configured(FirstAidConfig.SERVER.plasterCraftYield);
         case "painkillers" -> configured(FirstAidConfig.SERVER.painkillersCraftYield);
         case "morphine" -> configured(FirstAidConfig.SERVER.morphineCraftYield);
         default -> 0;
      };
      int uses = switch (path) {
         case "morphine_injector" -> configured(FirstAidConfig.SERVER.morphineInjectorCraftUses);
         case "adrenaline_injector" -> configured(FirstAidConfig.SERVER.adrenalineInjectorCraftUses);
         case "defibrillator" -> configured(FirstAidConfig.SERVER.defibrillatorCraftUses);
         default -> 0;
      };
      if (count > 0) {
         access.firstaid$setResult(result.withCount(count));
      } else if (uses > 0) {
         var previous = result.components().split();
         var patch = DataComponentPatch.builder().set(previous.added());
         previous.removed().forEach(patch::remove);
         patch.set(DataComponents.MAX_DAMAGE, uses).set(DataComponents.DAMAGE, 0);
         access.firstaid$setResult(new ItemStackTemplate(result.item(), 1, patch.build()));
      }
   }
}

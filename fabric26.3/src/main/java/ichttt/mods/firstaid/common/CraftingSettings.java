package ichttt.mods.firstaid.common;

import ichttt.mods.firstaid.FirstAidConfig;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Recipe;

public final class CraftingSettings {
   private CraftingSettings() {
   }

   public interface ResultAccess {
      ItemStackTemplate firstaid$getResult();

      void firstaid$setResult(ItemStackTemplate result);
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
         case "bandage" -> FirstAidConfig.SERVER.bandageCraftYield.get();
         case "plaster" -> FirstAidConfig.SERVER.plasterCraftYield.get();
         case "painkillers" -> FirstAidConfig.SERVER.painkillersCraftYield.get();
         case "morphine" -> FirstAidConfig.SERVER.morphineCraftYield.get();
         default -> 0;
      };
      int uses = switch (path) {
         case "morphine_injector" -> FirstAidConfig.SERVER.morphineInjectorCraftUses.get();
         case "adrenaline_injector" -> FirstAidConfig.SERVER.adrenalineInjectorCraftUses.get();
         case "defibrillator" -> FirstAidConfig.SERVER.defibrillatorCraftUses.get();
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

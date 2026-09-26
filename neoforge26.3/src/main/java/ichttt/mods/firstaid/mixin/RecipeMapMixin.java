package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.common.CraftingSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeMap.class)
public abstract class RecipeMapMixin {
   @Inject(method = "create", at = @At("HEAD"))
   private static void firstaid$adjust(HolderLookup<Recipe<?>> recipes, CallbackInfoReturnable<RecipeMap> cir) {
      for (Holder.Reference<Recipe<?>> holder : recipes.listElements().toList()) {
         var id = holder.key().identifier();
         CraftingSettings.adjust(id.getNamespace(), id.getPath(), holder.value());
      }
   }
}

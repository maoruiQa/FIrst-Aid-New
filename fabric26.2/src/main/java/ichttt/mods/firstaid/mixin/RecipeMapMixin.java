package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.common.CraftingSettings;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeMap.class)
public abstract class RecipeMapMixin {
   @Inject(method = "create", at = @At("HEAD"))
   private static void firstaid$adjust(Iterable<RecipeHolder<?>> recipes, CallbackInfoReturnable<RecipeMap> cir) {
      for (RecipeHolder<?> holder : recipes) {
         var id = holder.id().identifier();
         CraftingSettings.adjust(id.getNamespace(), id.getPath(), holder.value());
      }
   }
}

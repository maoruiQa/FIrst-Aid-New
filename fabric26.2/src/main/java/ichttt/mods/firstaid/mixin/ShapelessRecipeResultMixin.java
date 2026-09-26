package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.common.CraftingSettings;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ShapelessRecipe.class)
public abstract class ShapelessRecipeResultMixin implements CraftingSettings.ResultAccess {
   @Shadow @Final @Mutable private ItemStackTemplate result;

   public ItemStackTemplate firstaid$getResult() {
      return result;
   }

   public void firstaid$setResult(ItemStackTemplate value) {
      result = value;
   }
}

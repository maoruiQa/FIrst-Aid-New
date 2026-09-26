package ichttt.mods.firstaid.common;

import ichttt.mods.firstaid.FirstAidConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

public final class CraftingSettings {
    private CraftingSettings() {
    }

    public static void adjust(String namespace, String path, Recipe<?> recipe) {
        if (!"firstaid".equals(namespace) || !(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)) {
            return;
        }
        ItemStack stack = recipe.getResultItem(null);
        if (!stack.is(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("firstaid", path)))) {
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
            stack.setCount(Math.min(count, stack.getMaxStackSize()));
        } else if (uses > 0) {
            stack.set(DataComponents.MAX_DAMAGE, uses);
            stack.setDamageValue(0);
        }
    }
}

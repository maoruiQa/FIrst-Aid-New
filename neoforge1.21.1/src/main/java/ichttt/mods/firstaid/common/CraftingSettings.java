package ichttt.mods.firstaid.common;

import ichttt.mods.firstaid.FirstAidConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class CraftingSettings {
    private CraftingSettings() {
    }

    private static int configured(ModConfigSpec.IntValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return value.getDefault();
        }
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
            stack.setCount(Math.min(count, stack.getMaxStackSize()));
        } else if (uses > 0) {
            stack.set(DataComponents.MAX_DAMAGE, uses);
            stack.setDamageValue(0);
        }
    }
}

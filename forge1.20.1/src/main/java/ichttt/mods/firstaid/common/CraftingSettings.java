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

import ichttt.mods.firstaid.FirstAidConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

public final class CraftingSettings {
    public static final String CRAFT_USES_TAG = "firstaidCraftUses";

    private CraftingSettings() {
    }

    public static void adjust(String path, Recipe<?> recipe) {
        if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)) {
            return;
        }
        ItemStack stack = recipe.getResultItem(RegistryAccess.EMPTY);
        if (!stack.is(BuiltInRegistries.ITEM.get(new ResourceLocation("firstaid", path)))) {
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
            stack.getOrCreateTag().putInt(CRAFT_USES_TAG, uses);
            stack.setDamageValue(0);
        }
    }

    public static int craftedMaxUses(ItemStack stack) {
        if (stack.getTag() == null || !stack.getTag().contains(CRAFT_USES_TAG)) {
            return 0;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!"firstaid".equals(id.getNamespace())) {
            return 0;
        }
        return switch (id.getPath()) {
            case "morphine_injector", "adrenaline_injector", "defibrillator" ->
                Math.max(1, Math.min(1000, stack.getTag().getInt(CRAFT_USES_TAG)));
            default -> 0;
        };
    }
}

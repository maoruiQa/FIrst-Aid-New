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

package ichttt.mods.firstaid.mixin;

import com.google.gson.JsonElement;
import ichttt.mods.firstaid.common.CraftingSettings;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Inject(method = "apply", at = @At("TAIL"))
    private void firstaid$adjustResults(Map<ResourceLocation, JsonElement> jsons,
                                        ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        RecipeManager manager = (RecipeManager) (Object) this;
        for (String path : new String[]{"bandage", "plaster", "painkillers", "morphine", "morphine_injector", "adrenaline_injector", "defibrillator"}) {
            manager.byKey(new ResourceLocation("firstaid", path)).ifPresent(recipe -> CraftingSettings.adjust(path, recipe));
        }
    }
}

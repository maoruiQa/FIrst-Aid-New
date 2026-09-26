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
import com.google.gson.JsonParser;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.common.CraftingSettings;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    private static final FileToIdConverter FIRSTAID_RECIPE_LISTER = FileToIdConverter.json("recipe");

    @Inject(method = "apply", at = @At("TAIL"))
    private void firstaid$adjustResults(Map<ResourceLocation, JsonElement> jsons,
                                        ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        for (var holder : ((RecipeManager) (Object) this).getRecipes()) {
            CraftingSettings.adjust(holder.id().getNamespace(), holder.id().getPath(), holder.value());
        }
    }

    @Inject(method = "apply", at = @At("HEAD"))
    private void firstaid$loadCustomRecipes(Map<ResourceLocation, JsonElement> jsons,
                                            ResourceManager resourceManager,
                                            ProfilerFiller profiler,
                                            CallbackInfo ci) {
        Map<ResourceLocation, JsonElement> extraRecipes = new HashMap<>();
        loadFirstAidRecipes(resourceManager, extraRecipes);
        if (extraRecipes.isEmpty()) {
            return;
        }
        extraRecipes.forEach((id, json) -> {
            if (jsons.containsKey(id)) {
                FirstAid.LOGGER.warn("Overriding recipe {} from data/{}/recipe", id, id.getNamespace());
            }
            jsons.put(id, json);
        });
    }

    private static void loadFirstAidRecipes(ResourceManager resourceManager,
                                            Map<ResourceLocation, JsonElement> output) {
        for (Map.Entry<ResourceLocation, Resource> entry : FIRSTAID_RECIPE_LISTER.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation resourceId = entry.getKey();
            if (!FirstAid.MODID.equals(resourceId.getNamespace())) {
                continue;
            }
            ResourceLocation id = FIRSTAID_RECIPE_LISTER.fileToId(resourceId);
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement jsonElement = JsonParser.parseReader(reader);
                output.put(id, jsonElement);
            } catch (IllegalArgumentException | IOException ex) {
                FirstAid.LOGGER.error("Couldn't parse data file '{}' from '{}'", id, resourceId, ex);
            }
        }
    }
}

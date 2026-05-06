/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonElement
 *  com.mojang.serialization.DynamicOps
 *  com.mojang.serialization.JsonOps
 *  net.minecraft.core.HolderLookup$Provider
 *  net.minecraft.resources.FileToIdConverter
 *  net.minecraft.resources.Identifier
 *  net.minecraft.resources.RegistryOps
 *  net.minecraft.server.packs.resources.Resource
 *  net.minecraft.server.packs.resources.ResourceManager
 *  net.minecraft.util.StrictJsonParser
 *  net.minecraft.util.profiling.ProfilerFiller
 *  net.minecraft.world.item.crafting.Recipe
 *  net.minecraft.world.item.crafting.RecipeManager
 *  net.minecraft.world.item.crafting.RecipeMap
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.At$Shift
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 *  org.spongepowered.asm.mixin.injection.callback.LocalCapture
 */
package ichttt.mods.firstaid.mixin;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import ichttt.mods.firstaid.FirstAid;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value={RecipeManager.class})
public abstract class RecipeManagerMixin {
    private static final FileToIdConverter FIRSTAID_RECIPE_LISTER = FileToIdConverter.json((String)"recipe");
    @Shadow
    @Final
    private HolderLookup.Provider registries;

    @Inject(method={"prepare"}, at={@At(value="INVOKE", target="Lnet/minecraft/server/packs/resources/SimpleJsonResourceReloadListener;scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V", shift=At.Shift.AFTER)}, locals=LocalCapture.CAPTURE_FAILHARD)
    private void firstaid$loadCustomRecipes(ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfoReturnable<RecipeMap> cir, SortedMap<Identifier, Recipe<?>> sortedMap) {
        HashMap extraRecipes = new HashMap();
        RegistryOps registryOps = this.registries.createSerializationContext((DynamicOps)JsonOps.INSTANCE);
        RecipeManagerMixin.loadFirstAidRecipes(resourceManager, (RegistryOps<JsonElement>)registryOps, extraRecipes);
        if (extraRecipes.isEmpty()) {
            return;
        }
        extraRecipes.forEach((id, recipe) -> {
            if (sortedMap.containsKey(id)) {
                FirstAid.LOGGER.warn("Overriding recipe {} from data/{}/recipe", id, (Object)id.getNamespace());
            }
            sortedMap.put((Identifier)id, (Recipe<?>)recipe);
        });
    }

    private static void loadFirstAidRecipes(ResourceManager resourceManager, RegistryOps<JsonElement> registryOps, Map<Identifier, Recipe<?>> output) {
        for (Map.Entry entry : FIRSTAID_RECIPE_LISTER.listMatchingResources(resourceManager).entrySet()) {
            Identifier resourceId = (Identifier)entry.getKey();
            if (!"firstaid".equals(resourceId.getNamespace())) continue;
            Identifier id = FIRSTAID_RECIPE_LISTER.fileToId(resourceId);
            try {
                BufferedReader reader = ((Resource)entry.getValue()).openAsReader();
                try {
                    JsonElement jsonElement = StrictJsonParser.parse((Reader)reader);
                    Recipe.CODEC.parse(registryOps, (Object)jsonElement).ifSuccess(recipe -> output.put(id, (Recipe<?>)recipe)).ifError(error -> FirstAid.LOGGER.error("Couldn't parse data file '{}' from '{}': {}", (Object)id, (Object)resourceId, error));
                }
                finally {
                    if (reader == null) continue;
                    ((Reader)reader).close();
                }
            }
            catch (IOException | IllegalArgumentException ex) {
                FirstAid.LOGGER.error("Couldn't parse data file '{}' from '{}'", (Object)id, (Object)resourceId, (Object)ex);
            }
        }
    }
}


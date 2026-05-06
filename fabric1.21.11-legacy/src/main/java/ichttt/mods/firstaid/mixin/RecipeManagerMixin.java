package ichttt.mods.firstaid.mixin;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import ichttt.mods.firstaid.FirstAid;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
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
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
   private static final FileToIdConverter FIRSTAID_RECIPE_LISTER = FileToIdConverter.json("recipe");
   @Shadow
   @Final
   private Provider registries;

   @Inject(
      method = "prepare",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/server/packs/resources/SimpleJsonResourceReloadListener;scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V",
         shift = Shift.AFTER
      ),
      locals = LocalCapture.CAPTURE_FAILHARD
   )
   private void firstaid$loadCustomRecipes(
      ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfoReturnable<RecipeMap> cir, SortedMap<Identifier, Recipe<?>> sortedMap
   ) {
      Map<Identifier, Recipe<?>> extraRecipes = new HashMap<>();
      RegistryOps<JsonElement> registryOps = this.registries.createSerializationContext(JsonOps.INSTANCE);
      loadFirstAidRecipes(resourceManager, registryOps, extraRecipes);
      if (!extraRecipes.isEmpty()) {
         extraRecipes.forEach((id, recipe) -> {
            if (sortedMap.containsKey(id)) {
               FirstAid.LOGGER.warn("Overriding recipe {} from data/{}/recipe", id, id.getNamespace());
            }

            sortedMap.put(id, (Recipe<?>)recipe);
         });
      }
   }

   private static void loadFirstAidRecipes(ResourceManager resourceManager, RegistryOps<JsonElement> registryOps, Map<Identifier, Recipe<?>> output) {
      for (Entry<Identifier, Resource> entry : FIRSTAID_RECIPE_LISTER.listMatchingResources(resourceManager).entrySet()) {
         Identifier resourceId = entry.getKey();
         if ("firstaid".equals(resourceId.getNamespace())) {
            Identifier id = FIRSTAID_RECIPE_LISTER.fileToId(resourceId);

            try (Reader reader = entry.getValue().openAsReader()) {
               JsonElement jsonElement = StrictJsonParser.parse(reader);
               Recipe.CODEC
                  .parse(registryOps, jsonElement)
                  .ifSuccess(recipe -> output.put(id, recipe))
                  .ifError(error -> FirstAid.LOGGER.error("Couldn't parse data file '{}' from '{}': {}", id, resourceId, error));
            } catch (IOException | IllegalArgumentException var12) {
               FirstAid.LOGGER.error("Couldn't parse data file '{}' from '{}'", id, resourceId, var12);
            }
         }
      }
   }
}

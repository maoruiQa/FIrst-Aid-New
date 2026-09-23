package ichttt.mods.firstaid.common.registries;

import com.mojang.serialization.Codec;
import ichttt.mods.firstaid.api.debuff.IDebuffBuilder;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionTarget;
import java.util.Map;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class FirstAidDataReloadListener
   extends SimplePreparableReloadListener<FirstAidDataReloadListener.Data>
   implements IdentifiableResourceReloadListener {
   private static final FileToIdConverter DAMAGE_LISTER = FileToIdConverter.json("firstaid/damage_distributions");
   private static final FileToIdConverter DEBUFF_LISTER = FileToIdConverter.json("firstaid/debuffs");
   private static final Identifier RELOAD_ID = Identifier.fromNamespaceAndPath("firstaid", "data_reload");

   protected FirstAidDataReloadListener.Data prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
      Map<Identifier, IDamageDistributionTarget> damageTargets = load(
         resourceManager, profiler, DAMAGE_LISTER, FirstAidBaseCodecs.DAMAGE_DISTRIBUTION_TARGETS_DIRECT_CODEC
      );
      Map<Identifier, IDebuffBuilder> debuffBuilders = load(
         resourceManager, profiler, DEBUFF_LISTER, FirstAidBaseCodecs.DEBUFF_BUILDERS_DIRECT_CODEC
      );
      return new FirstAidDataReloadListener.Data(damageTargets, debuffBuilders);
   }

   private static <T> Map<Identifier, T> load(ResourceManager resourceManager, ProfilerFiller profiler, FileToIdConverter lister, Codec<T> codec) {
      final class Loader extends SimpleJsonResourceReloadListener<T> {
         private Loader() {
            super(codec, lister);
         }

         private Map<Identifier, T> read(ResourceManager manager, ProfilerFiller profilerFiller) {
            return prepare(manager, profilerFiller);
         }

         protected void apply(Map<Identifier, T> data, ResourceManager manager, ProfilerFiller profilerFiller) {
         }
      }

      return new Loader().read(resourceManager, profiler);
   }

   protected void apply(FirstAidDataReloadListener.Data data, ResourceManager resourceManager, ProfilerFiller profiler) {
      FirstAidRegistryLookups.updateData(data.damageTargets, data.debuffBuilders);
   }

   public Identifier getFabricId() {
      return RELOAD_ID;
   }

   record Data(Map<Identifier, IDamageDistributionTarget> damageTargets, Map<Identifier, IDebuffBuilder> debuffBuilders) {
   }
}

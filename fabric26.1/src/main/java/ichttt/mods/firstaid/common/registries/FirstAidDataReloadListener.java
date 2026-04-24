package ichttt.mods.firstaid.common.registries;

import com.mojang.serialization.JsonOps;
import ichttt.mods.firstaid.api.debuff.IDebuffBuilder;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionTarget;
import java.util.HashMap;
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
      Map<Identifier, IDamageDistributionTarget> damageTargets = new HashMap<>();
      Map<Identifier, IDebuffBuilder> debuffBuilders = new HashMap<>();
      SimpleJsonResourceReloadListener.scanDirectory(
         resourceManager, DAMAGE_LISTER, JsonOps.INSTANCE, FirstAidBaseCodecs.DAMAGE_DISTRIBUTION_TARGETS_DIRECT_CODEC, damageTargets
      );
      SimpleJsonResourceReloadListener.scanDirectory(
         resourceManager, DEBUFF_LISTER, JsonOps.INSTANCE, FirstAidBaseCodecs.DEBUFF_BUILDERS_DIRECT_CODEC, debuffBuilders
      );
      return new FirstAidDataReloadListener.Data(damageTargets, debuffBuilders);
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

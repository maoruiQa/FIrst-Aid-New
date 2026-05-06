package ichttt.mods.firstaid.common.apiimpl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionTarget;
import ichttt.mods.firstaid.common.registries.FirstAidBaseCodecs;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

public class StaticDamageDistributionTarget implements IDamageDistributionTarget {
   public static final MapCodec<StaticDamageDistributionTarget> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            FirstAidBaseCodecs.DAMAGE_DISTRIBUTION_ALGORITHMS_DIRECT_CODEC.fieldOf("algorithm").forGetter(o -> o.algorithm),
            Identifier.CODEC.listOf().fieldOf("damageTypes").forGetter(o -> o.damageTypes)
         )
         .apply(instance, StaticDamageDistributionTarget::new)
   );
   private final IDamageDistributionAlgorithm algorithm;
   private final List<Identifier> damageTypes;

   public StaticDamageDistributionTarget(IDamageDistributionAlgorithm algorithm, List<Identifier> damageTypes) {
      this.algorithm = algorithm;
      this.damageTypes = damageTypes;
   }

   @Override
   public IDamageDistributionAlgorithm getAlgorithm() {
      return this.algorithm;
   }

   @Override
   public List<DamageType> buildApplyList(RegistryLookup<DamageType> allDamageTypes) {
      Builder<DamageType> builder = ImmutableList.builder();
      List<Identifier> localDamageTypes = new ArrayList<>(this.damageTypes);

      allDamageTypes.listElements().forEach(holder -> {
         ResourceKey<DamageType> key = holder.key();
         Identifier location = key.identifier();
         if (localDamageTypes.remove(location)) {
            builder.add((DamageType)holder.value());
         }
      });

      if (!localDamageTypes.isEmpty()) {
         FirstAid.LOGGER
            .warn(LoggingMarkers.REGISTRY, "Some damage types in {} failed to map: {}", StaticDamageDistributionTarget.class.getSimpleName(), localDamageTypes);
      }

      return builder.build();
   }

   @Override
   public boolean isDynamic() {
      return false;
   }

   @Override
   public MapCodec<? extends IDamageDistributionTarget> codec() {
      return CODEC;
   }
}

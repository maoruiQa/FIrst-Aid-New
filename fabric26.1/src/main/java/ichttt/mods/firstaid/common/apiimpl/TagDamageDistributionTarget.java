package ichttt.mods.firstaid.common.apiimpl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionTarget;
import ichttt.mods.firstaid.common.registries.FirstAidBaseCodecs;
import java.util.List;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

public class TagDamageDistributionTarget implements IDamageDistributionTarget {
   public static final MapCodec<TagDamageDistributionTarget> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            FirstAidBaseCodecs.DAMAGE_DISTRIBUTION_ALGORITHMS_DIRECT_CODEC.fieldOf("algorithm").forGetter(o -> o.algorithm),
            TagKey.codec(Registries.DAMAGE_TYPE).fieldOf("tag").forGetter(o -> o.tag)
         )
         .apply(instance, TagDamageDistributionTarget::new)
   );
   private final IDamageDistributionAlgorithm algorithm;
   private final TagKey<DamageType> tag;

   public TagDamageDistributionTarget(IDamageDistributionAlgorithm algorithm, TagKey<DamageType> tag) {
      this.algorithm = algorithm;
      this.tag = tag;
   }

   @Override
   public IDamageDistributionAlgorithm getAlgorithm() {
      return this.algorithm;
   }

   @Override
   public List<DamageType> buildApplyList(RegistryLookup<DamageType> allDamageTypes) {
      Builder<DamageType> builder = ImmutableList.builder();

      allDamageTypes.listElements().forEach(holder -> {
         if (holder.is(this.tag)) {
            builder.add((DamageType)holder.value());
         }
      });

      return builder.build();
   }

   @Override
   public boolean isDynamic() {
      return true;
   }

   @Override
   public MapCodec<? extends IDamageDistributionTarget> codec() {
      return CODEC;
   }
}

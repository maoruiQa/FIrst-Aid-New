package ichttt.mods.firstaid.common.registries;

import com.mojang.serialization.MapCodec;
import ichttt.mods.firstaid.api.debuff.IDebuffBuilder;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionTarget;
import ichttt.mods.firstaid.common.apiimpl.StaticDamageDistributionTarget;
import ichttt.mods.firstaid.common.apiimpl.TagDamageDistributionTarget;
import ichttt.mods.firstaid.common.damagesystem.debuff.builder.ConstantDebuffBuilder;
import ichttt.mods.firstaid.common.damagesystem.debuff.builder.OnHitDebuffBuilder;
import ichttt.mods.firstaid.common.damagesystem.distribution.DirectDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.damagesystem.distribution.EqualDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.damagesystem.distribution.RandomDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.damagesystem.distribution.StandardDamageDistributionAlgorithm;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public final class FirstAidRegistries {
   public static final Registry<MapCodec<? extends IDamageDistributionAlgorithm>> DAMAGE_DISTRIBUTION_ALGORITHMS = FabricRegistryBuilder.createSimple(
         FirstAidRegistries.Keys.DAMAGE_DISTRIBUTION_ALGORITHMS
      )
      .attribute(RegistryAttribute.SYNCED)
      .buildAndRegister();
   public static final Registry<MapCodec<? extends IDamageDistributionTarget>> DAMAGE_DISTRIBUTION_TARGETS = FabricRegistryBuilder.createSimple(
         FirstAidRegistries.Keys.DAMAGE_DISTRIBUTION_TARGETS
      )
      .attribute(RegistryAttribute.SYNCED)
      .buildAndRegister();
   public static final Registry<MapCodec<? extends IDebuffBuilder>> DEBUFF_BUILDERS = FabricRegistryBuilder.createSimple(
         FirstAidRegistries.Keys.DEBUFF_BUILDERS
      )
      .attribute(RegistryAttribute.SYNCED)
      .buildAndRegister();

   private FirstAidRegistries() {
   }

   public static void bootstrap() {
   }

   public static void clearDataRegistries() {
   }

   private static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("firstaid", path);
   }

   static {
      Registry.register(DAMAGE_DISTRIBUTION_ALGORITHMS, id("direct"), DirectDamageDistributionAlgorithm.CODEC);
      Registry.register(DAMAGE_DISTRIBUTION_ALGORITHMS, id("equal"), EqualDamageDistributionAlgorithm.CODEC);
      Registry.register(DAMAGE_DISTRIBUTION_ALGORITHMS, id("random"), RandomDamageDistributionAlgorithm.CODEC);
      Registry.register(DAMAGE_DISTRIBUTION_ALGORITHMS, id("standard"), StandardDamageDistributionAlgorithm.CODEC);
      Registry.register(DAMAGE_DISTRIBUTION_TARGETS, id("static"), StaticDamageDistributionTarget.CODEC);
      Registry.register(DAMAGE_DISTRIBUTION_TARGETS, id("tag"), TagDamageDistributionTarget.CODEC);
      Registry.register(DEBUFF_BUILDERS, id("constant"), ConstantDebuffBuilder.CODEC);
      Registry.register(DEBUFF_BUILDERS, id("on_hit"), OnHitDebuffBuilder.CODEC);
   }

   public static final class Keys {
      public static final ResourceKey<Registry<MapCodec<? extends IDamageDistributionAlgorithm>>> DAMAGE_DISTRIBUTION_ALGORITHMS = key(
         "damage_distribution_algorithms"
      );
      public static final ResourceKey<Registry<MapCodec<? extends IDamageDistributionTarget>>> DAMAGE_DISTRIBUTION_TARGETS = key("damage_distribution_targets");
      public static final ResourceKey<Registry<MapCodec<? extends IDebuffBuilder>>> DEBUFF_BUILDERS = key("debuff_builders");

      private static <T> ResourceKey<Registry<T>> key(String name) {
         return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("firstaid", name));
      }

      private Keys() {
      }
   }
}

package ichttt.mods.firstaid.common.registries;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.api.debuff.IDebuffBuilder;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionTarget;
import ichttt.mods.firstaid.api.enums.EnumDebuffSlot;
import ichttt.mods.firstaid.common.damagesystem.debuff.SharedDebuff;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageType;

public class FirstAidRegistryLookups {
   private static final Object LOCK = new Object();
   private static final Collection<LookupReloadListener> LISTENERS = Collections.newSetFromMap(new WeakHashMap<>());
   private static Map<DamageType, IDamageDistributionAlgorithm> DAMAGE_DISTRIBUTIONS;
   private static Map<EnumDebuffSlot, List<IDebuffBuilder>> DEBUFF_BUILDERS;
   private static volatile Map<Identifier, IDamageDistributionTarget> DATA_DAMAGE_TARGETS = Collections.emptyMap();
   private static volatile Map<Identifier, IDebuffBuilder> DATA_DEBUFF_BUILDERS = Collections.emptyMap();

   public static IDamageDistributionAlgorithm getDamageDistributions(DamageType damageType) {
      return DAMAGE_DISTRIBUTIONS.get(damageType);
   }

   public static IDebuff[] getDebuffs(EnumDebuffSlot slot) {
      List<IDebuff> list = new ArrayList<>();

      for (IDebuffBuilder iDebuffBuilder : DEBUFF_BUILDERS.getOrDefault(slot, Collections.emptyList())) {
         IDebuff build = iDebuffBuilder.build();
         if (slot.playerParts.length > 1) {
            build = new SharedDebuff(build, slot);
         }

         list.add(build);
      }

      return list.toArray(new IDebuff[0]);
   }

   public static void init(Provider lookupProvider, boolean isRemote) {
      if (isRemote && FirstAid.isSynced) {
         throw new IllegalStateException("Synced before registry lookups have been loaded!");
      } else {
         synchronized (LOCK) {
            DAMAGE_DISTRIBUTIONS = buildDamageDistributions(lookupProvider);
            DEBUFF_BUILDERS = buildDebuffs(lookupProvider);

            for (LookupReloadListener listener : LISTENERS) {
               listener.onLookupsReloaded();
            }
         }

         FirstAid.LOGGER.info(LoggingMarkers.REGISTRY, "Built {} FirstAid registry lookups", isRemote ? "remote" : "local");
      }
   }

   private static Map<DamageType, IDamageDistributionAlgorithm> buildDamageDistributions(Provider lookupProvider) {
      RegistryLookup<DamageType> damageTypeRegistry = lookupProvider.lookupOrThrow(Registries.DAMAGE_TYPE);
      Map<DamageType, IDamageDistributionAlgorithm> staticAlgorithms = new HashMap<>();
      Map<DamageType, IDamageDistributionAlgorithm> dynamicAlgorithms = new HashMap<>();

      for (Entry<Identifier, IDamageDistributionTarget> entry : DATA_DAMAGE_TARGETS.entrySet()) {
         Identifier key = entry.getKey();
         IDamageDistributionTarget distributionTarget = entry.getValue();
         IDamageDistributionAlgorithm algorithm = distributionTarget.getAlgorithm();
         List<DamageType> damageTypes = distributionTarget.buildApplyList(damageTypeRegistry);
         Map<DamageType, IDamageDistributionAlgorithm> mapToUse = distributionTarget.isDynamic() ? dynamicAlgorithms : staticAlgorithms;

         for (DamageType damageType : damageTypes) {
            IDamageDistributionAlgorithm oldVal = mapToUse.put(damageType, algorithm);
            if (oldVal != null) {
               FirstAid.LOGGER
                  .warn(
                     LoggingMarkers.REGISTRY,
                     "Damage distribution {} overwrites previously registered distribution for damage type {}",
                     key,
                     damageType.msgId()
                  );
            }
         }
      }

      Builder<DamageType, IDamageDistributionAlgorithm> allDamageDistributions = ImmutableMap.builder();
      allDamageDistributions.putAll(staticAlgorithms);

      for (Entry<DamageType, IDamageDistributionAlgorithm> dynamicEntry : dynamicAlgorithms.entrySet()) {
         if (!staticAlgorithms.containsKey(dynamicEntry.getKey())) {
            allDamageDistributions.put(dynamicEntry);
         }
      }

      return allDamageDistributions.build();
   }

   private static Map<EnumDebuffSlot, List<IDebuffBuilder>> buildDebuffs(Provider lookupProvider) {
      EnumMap<EnumDebuffSlot, List<IDebuffBuilder>> debuffMap = new EnumMap<>(EnumDebuffSlot.class);

      for (IDebuffBuilder debuffBuilder : DATA_DEBUFF_BUILDERS.values()) {
         debuffMap.computeIfAbsent(debuffBuilder.affectedSlot(), slot -> new ArrayList<>()).add(debuffBuilder);
      }

      return debuffMap;
   }

   public static void reset() {
      DAMAGE_DISTRIBUTIONS = null;
      DEBUFF_BUILDERS = null;
      DATA_DAMAGE_TARGETS = Collections.emptyMap();
      DATA_DEBUFF_BUILDERS = Collections.emptyMap();
      LISTENERS.clear();
   }

   public static void updateData(Map<Identifier, IDamageDistributionTarget> damageTargets, Map<Identifier, IDebuffBuilder> debuffBuilders) {
      DATA_DAMAGE_TARGETS = Map.copyOf(damageTargets);
      DATA_DEBUFF_BUILDERS = Map.copyOf(debuffBuilders);
   }

   public static void registerReloadListener(LookupReloadListener reloadListener) {
      Objects.requireNonNull(reloadListener);
      synchronized (LOCK) {
         LISTENERS.add(reloadListener);
         if (DAMAGE_DISTRIBUTIONS != null) {
            reloadListener.onLookupsReloaded();
         }
      }
   }
}

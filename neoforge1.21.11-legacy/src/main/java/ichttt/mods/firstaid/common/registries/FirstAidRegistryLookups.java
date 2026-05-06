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

package ichttt.mods.firstaid.common.registries;

import com.google.common.collect.ImmutableMap;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.api.debuff.IDebuffBuilder;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionTarget;
import ichttt.mods.firstaid.api.enums.EnumDebuffSlot;
import ichttt.mods.firstaid.common.damagesystem.debuff.SharedDebuff;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.damagesource.DamageType;

import java.util.*;

public class FirstAidRegistryLookups {
    private static final Object LOCK = new Object();
    private static final Collection<LookupReloadListener> LISTENERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static Map<DamageType, IDamageDistributionAlgorithm> DAMAGE_DISTRIBUTIONS;
    private static Map<EnumDebuffSlot, List<IDebuffBuilder>> DEBUFF_BUILDERS;

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

    public static void init(HolderLookup.Provider lookupProvider, boolean isRemote) {
        if (isRemote && FirstAid.isSynced) {
            throw new IllegalStateException("Synced before registry lookups have been loaded!");
        }

        synchronized (LOCK) {
            DAMAGE_DISTRIBUTIONS = buildDamageDistributions(lookupProvider);
            DEBUFF_BUILDERS = buildDebuffs(lookupProvider);
            for (LookupReloadListener listener : LISTENERS) {
                listener.onLookupsReloaded();
            }
        }
        FirstAid.LOGGER.info(LoggingMarkers.REGISTRY, "Built {} FirstAid registry lookups", isRemote ? "remote" : "local");
    }

    private static Map<DamageType, IDamageDistributionAlgorithm> buildDamageDistributions(HolderLookup.Provider lookupProvider) {
        HolderLookup.RegistryLookup<IDamageDistributionTarget> damageDistributionRegistry = lookupProvider.lookupOrThrow(FirstAidRegistries.Keys.DAMAGE_DISTRIBUTIONS);
        HolderLookup.RegistryLookup<DamageType> damageTypeRegistry = lookupProvider.lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);

        Map<DamageType, IDamageDistributionAlgorithm> staticAlgorithms = new HashMap<>();
        Map<DamageType, IDamageDistributionAlgorithm> dynamicAlgorithms = new HashMap<>();

        for (var holder : (Iterable<net.minecraft.core.Holder.Reference<IDamageDistributionTarget>>) damageDistributionRegistry.listElements()::iterator) {
            var key = holder.key();
            IDamageDistributionTarget distributionTarget = holder.value();

            IDamageDistributionAlgorithm algorithm = distributionTarget.getAlgorithm();
            List<DamageType> damageTypes = distributionTarget.buildApplyList(damageTypeRegistry);
            Map<DamageType, IDamageDistributionAlgorithm> mapToUse = distributionTarget.isDynamic() ? dynamicAlgorithms : staticAlgorithms;
            for (DamageType damageType : damageTypes) {
                IDamageDistributionAlgorithm oldVal = mapToUse.put(damageType, algorithm);
                if (oldVal != null) {
                    FirstAid.LOGGER.warn(LoggingMarkers.REGISTRY, "Damage distribution {} overwrites previously registered distribution for damage type {}", key, damageType.msgId());
                }
            }
        }
        ImmutableMap.Builder<DamageType, IDamageDistributionAlgorithm> allDamageDistributions = ImmutableMap.builder();
        allDamageDistributions.putAll(staticAlgorithms);
        for (Map.Entry<DamageType, IDamageDistributionAlgorithm> dynamicEntry : dynamicAlgorithms.entrySet()) {
            if (!staticAlgorithms.containsKey(dynamicEntry.getKey())) {
                allDamageDistributions.put(dynamicEntry);
            }
        }
        return allDamageDistributions.build();
    }

    private static Map<EnumDebuffSlot, List<IDebuffBuilder>> buildDebuffs(HolderLookup.Provider lookupProvider) {
        HolderLookup.RegistryLookup<IDebuffBuilder> debuffBuilderRegistry = lookupProvider.lookupOrThrow(FirstAidRegistries.Keys.DEBUFFS);

        EnumMap<EnumDebuffSlot, List<IDebuffBuilder>> debuffMap = new EnumMap<>(EnumDebuffSlot.class);
        for (var holder : (Iterable<net.minecraft.core.Holder.Reference<IDebuffBuilder>>) debuffBuilderRegistry.listElements()::iterator) {
            IDebuffBuilder debuffBuilder = holder.value();
            debuffMap.computeIfAbsent(debuffBuilder.affectedSlot(), slot -> new ArrayList<>()).add(debuffBuilder);
        }
        return debuffMap;
    }

    public static void reset() {
        DAMAGE_DISTRIBUTIONS = null;
        DEBUFF_BUILDERS = null;
        LISTENERS.clear();
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

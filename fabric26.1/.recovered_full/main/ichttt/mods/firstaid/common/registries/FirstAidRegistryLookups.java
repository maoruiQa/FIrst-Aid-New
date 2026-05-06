/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableMap
 *  com.google.common.collect.ImmutableMap$Builder
 *  net.minecraft.core.HolderLookup$Provider
 *  net.minecraft.core.HolderLookup$RegistryLookup
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.resources.Identifier
 *  net.minecraft.world.damagesource.DamageType
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
import ichttt.mods.firstaid.common.registries.LookupReloadListener;
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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageType;

public class FirstAidRegistryLookups {
    private static final Object LOCK = new Object();
    private static final Collection<LookupReloadListener> LISTENERS = Collections.newSetFromMap(new WeakHashMap());
    private static Map<DamageType, IDamageDistributionAlgorithm> DAMAGE_DISTRIBUTIONS;
    private static Map<EnumDebuffSlot, List<IDebuffBuilder>> DEBUFF_BUILDERS;
    private static volatile Map<Identifier, IDamageDistributionTarget> DATA_DAMAGE_TARGETS;
    private static volatile Map<Identifier, IDebuffBuilder> DATA_DEBUFF_BUILDERS;

    public static IDamageDistributionAlgorithm getDamageDistributions(DamageType damageType) {
        return DAMAGE_DISTRIBUTIONS.get(damageType);
    }

    public static IDebuff[] getDebuffs(EnumDebuffSlot slot) {
        ArrayList<IDebuff> list = new ArrayList<IDebuff>();
        for (IDebuffBuilder iDebuffBuilder : DEBUFF_BUILDERS.getOrDefault((Object)slot, Collections.emptyList())) {
            IDebuff build = iDebuffBuilder.build();
            if (slot.playerParts.length > 1) {
                build = new SharedDebuff(build, slot);
            }
            list.add(build);
        }
        return list.toArray(new IDebuff[0]);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void init(HolderLookup.Provider lookupProvider, boolean isRemote) {
        if (isRemote && FirstAid.isSynced) {
            throw new IllegalStateException("Synced before registry lookups have been loaded!");
        }
        Object object = LOCK;
        synchronized (object) {
            DAMAGE_DISTRIBUTIONS = FirstAidRegistryLookups.buildDamageDistributions(lookupProvider);
            DEBUFF_BUILDERS = FirstAidRegistryLookups.buildDebuffs(lookupProvider);
            for (LookupReloadListener listener : LISTENERS) {
                listener.onLookupsReloaded();
            }
        }
        FirstAid.LOGGER.info(LoggingMarkers.REGISTRY, "Built {} FirstAid registry lookups", (Object)(isRemote ? "remote" : "local"));
    }

    private static Map<DamageType, IDamageDistributionAlgorithm> buildDamageDistributions(HolderLookup.Provider lookupProvider) {
        HolderLookup.RegistryLookup damageTypeRegistry = lookupProvider.lookupOrThrow(Registries.DAMAGE_TYPE);
        HashMap staticAlgorithms = new HashMap();
        HashMap<DamageType, IDamageDistributionAlgorithm> dynamicAlgorithms = new HashMap<DamageType, IDamageDistributionAlgorithm>();
        for (Map.Entry<Identifier, IDamageDistributionTarget> entry : DATA_DAMAGE_TARGETS.entrySet()) {
            Identifier key = entry.getKey();
            IDamageDistributionTarget distributionTarget = entry.getValue();
            IDamageDistributionAlgorithm algorithm = distributionTarget.getAlgorithm();
            List<DamageType> damageTypes = distributionTarget.buildApplyList((HolderLookup.RegistryLookup<DamageType>)damageTypeRegistry);
            HashMap<DamageType, IDamageDistributionAlgorithm> mapToUse = distributionTarget.isDynamic() ? dynamicAlgorithms : staticAlgorithms;
            for (DamageType damageType : damageTypes) {
                IDamageDistributionAlgorithm oldVal = mapToUse.put(damageType, algorithm);
                if (oldVal == null) continue;
                FirstAid.LOGGER.warn(LoggingMarkers.REGISTRY, "Damage distribution {} overwrites previously registered distribution for damage type {}", (Object)key, (Object)damageType.msgId());
            }
        }
        ImmutableMap.Builder allDamageDistributions = ImmutableMap.builder();
        allDamageDistributions.putAll(staticAlgorithms);
        for (Map.Entry dynamicEntry : dynamicAlgorithms.entrySet()) {
            if (staticAlgorithms.containsKey(dynamicEntry.getKey())) continue;
            allDamageDistributions.put(dynamicEntry);
        }
        return allDamageDistributions.build();
    }

    private static Map<EnumDebuffSlot, List<IDebuffBuilder>> buildDebuffs(HolderLookup.Provider lookupProvider) {
        EnumMap<EnumDebuffSlot, List<IDebuffBuilder>> debuffMap = new EnumMap<EnumDebuffSlot, List<IDebuffBuilder>>(EnumDebuffSlot.class);
        for (IDebuffBuilder debuffBuilder : DATA_DEBUFF_BUILDERS.values()) {
            debuffMap.computeIfAbsent(debuffBuilder.affectedSlot(), slot -> new ArrayList()).add(debuffBuilder);
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

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void registerReloadListener(LookupReloadListener reloadListener) {
        Objects.requireNonNull(reloadListener);
        Object object = LOCK;
        synchronized (object) {
            LISTENERS.add(reloadListener);
            if (DAMAGE_DISTRIBUTIONS != null) {
                reloadListener.onLookupsReloaded();
            }
        }
    }

    static {
        DATA_DAMAGE_TARGETS = Collections.emptyMap();
        DATA_DEBUFF_BUILDERS = Collections.emptyMap();
    }
}


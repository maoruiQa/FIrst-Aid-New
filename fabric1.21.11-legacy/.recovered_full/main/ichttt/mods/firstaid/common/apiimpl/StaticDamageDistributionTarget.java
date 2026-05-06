/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableList
 *  com.google.common.collect.ImmutableList$Builder
 *  com.mojang.datafixers.kinds.App
 *  com.mojang.datafixers.kinds.Applicative
 *  com.mojang.serialization.MapCodec
 *  com.mojang.serialization.codecs.RecordCodecBuilder
 *  net.minecraft.core.Holder$Reference
 *  net.minecraft.core.HolderLookup$RegistryLookup
 *  net.minecraft.resources.Identifier
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.world.damagesource.DamageType
 */
package ichttt.mods.firstaid.common.apiimpl;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionTarget;
import ichttt.mods.firstaid.common.registries.FirstAidBaseCodecs;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

public class StaticDamageDistributionTarget
implements IDamageDistributionTarget {
    public static final MapCodec<StaticDamageDistributionTarget> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group((App)FirstAidBaseCodecs.DAMAGE_DISTRIBUTION_ALGORITHMS_DIRECT_CODEC.fieldOf("algorithm").forGetter(o -> o.algorithm), (App)Identifier.CODEC.listOf().fieldOf("damageTypes").forGetter(o -> o.damageTypes)).apply((Applicative)instance, StaticDamageDistributionTarget::new));
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
    public List<DamageType> buildApplyList(HolderLookup.RegistryLookup<DamageType> allDamageTypes) {
        ImmutableList.Builder builder = ImmutableList.builder();
        ArrayList<Identifier> localDamageTypes = new ArrayList<Identifier>(this.damageTypes);
        for (Holder.Reference holder : allDamageTypes.listElements()::iterator) {
            ResourceKey key = holder.key();
            Identifier location = key.identifier();
            if (!localDamageTypes.remove(location)) continue;
            builder.add((Object)((DamageType)holder.value()));
        }
        if (!localDamageTypes.isEmpty()) {
            FirstAid.LOGGER.warn(LoggingMarkers.REGISTRY, "Some damage types in {} failed to map: {}", (Object)StaticDamageDistributionTarget.class.getSimpleName(), localDamageTypes);
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


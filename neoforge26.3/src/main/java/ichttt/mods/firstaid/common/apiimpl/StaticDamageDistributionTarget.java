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

package ichttt.mods.firstaid.common.apiimpl;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionTarget;
import ichttt.mods.firstaid.common.registries.FirstAidBaseCodecs;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StaticDamageDistributionTarget implements IDamageDistributionTarget {
    public static final MapCodec<StaticDamageDistributionTarget> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    FirstAidBaseCodecs.DAMAGE_DISTRIBUTION_ALGORITHMS_DIRECT_CODEC.fieldOf("algorithm").forGetter(o -> o.algorithm),
                    Identifier.CODEC.listOf().fieldOf("damageTypes").forGetter(o -> o.damageTypes)
            ).apply(instance, StaticDamageDistributionTarget::new)
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
    public List<DamageType> buildApplyList(HolderLookup.RegistryLookup<DamageType> allDamageTypes) {
        ImmutableList.Builder<DamageType> builder = ImmutableList.builder();
        List<Identifier> localDamageTypes = new ArrayList<>(damageTypes);
        for (var holder : (Iterable<net.minecraft.core.Holder.Reference<DamageType>>) allDamageTypes.listElements()::iterator) {
            ResourceKey<DamageType> key = holder.key();
            Identifier location = key.identifier();
            if (localDamageTypes.remove(location)) {
                builder.add(holder.value());
            }
        }
        if (!localDamageTypes.isEmpty()) {
            FirstAid.LOGGER.warn(LoggingMarkers.REGISTRY, "Some damage types in {} failed to map: {}", StaticDamageDistributionTarget.class.getSimpleName(), localDamageTypes);
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


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
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.tags.TagKey
 *  net.minecraft.world.damagesource.DamageType
 */
package ichttt.mods.firstaid.common.apiimpl;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionTarget;
import ichttt.mods.firstaid.common.registries.FirstAidBaseCodecs;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

public class TagDamageDistributionTarget
implements IDamageDistributionTarget {
    public static final MapCodec<TagDamageDistributionTarget> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group((App)FirstAidBaseCodecs.DAMAGE_DISTRIBUTION_ALGORITHMS_DIRECT_CODEC.fieldOf("algorithm").forGetter(o -> o.algorithm), (App)TagKey.codec((ResourceKey)Registries.DAMAGE_TYPE).fieldOf("tag").forGetter(o -> o.tag)).apply((Applicative)instance, TagDamageDistributionTarget::new));
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
    public List<DamageType> buildApplyList(HolderLookup.RegistryLookup<DamageType> allDamageTypes) {
        ImmutableList.Builder builder = ImmutableList.builder();
        for (Holder.Reference holder : allDamageTypes.listElements()::iterator) {
            if (!holder.is(this.tag)) continue;
            builder.add((Object)((DamageType)holder.value()));
        }
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


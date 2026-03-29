/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.serialization.MapCodec
 *  net.minecraft.core.HolderLookup$RegistryLookup
 *  net.minecraft.world.damagesource.DamageType
 */
package ichttt.mods.firstaid.api.distribution;

import com.mojang.serialization.MapCodec;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.damagesource.DamageType;

public interface IDamageDistributionTarget {
    public IDamageDistributionAlgorithm getAlgorithm();

    public List<DamageType> buildApplyList(HolderLookup.RegistryLookup<DamageType> var1);

    public boolean isDynamic();

    public MapCodec<? extends IDamageDistributionTarget> codec();
}


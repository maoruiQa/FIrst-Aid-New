/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.serialization.MapCodec
 *  javax.annotation.Nonnull
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.entity.player.Player
 */
package ichttt.mods.firstaid.api.distribution;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nonnull;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

public interface IDamageDistributionAlgorithm {
    public float distributeDamage(float var1, @Nonnull Player var2, @Nonnull DamageSource var3, boolean var4);

    default public boolean skipGlobalPotionModifiers() {
        return false;
    }

    public MapCodec<? extends IDamageDistributionAlgorithm> codec();
}


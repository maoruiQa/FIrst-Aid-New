/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.datafixers.kinds.App
 *  com.mojang.datafixers.kinds.Applicative
 *  com.mojang.serialization.Codec
 *  com.mojang.serialization.codecs.RecordCodecBuilder
 *  net.minecraft.util.ExtraCodecs
 */
package ichttt.mods.firstaid.common.damagesystem.debuff;

import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

public record ConstantDebuffEntry(float healthFractionThreshold, int effectAmplifier) {
    public static final Codec<ConstantDebuffEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group((App)Codec.FLOAT.fieldOf("healthFractionThreshold").forGetter(o -> Float.valueOf(o.healthFractionThreshold)), (App)ExtraCodecs.intRange((int)0, (int)127).fieldOf("effectAmplifier").forGetter(o -> o.effectAmplifier)).apply((Applicative)instance, ConstantDebuffEntry::new));
}


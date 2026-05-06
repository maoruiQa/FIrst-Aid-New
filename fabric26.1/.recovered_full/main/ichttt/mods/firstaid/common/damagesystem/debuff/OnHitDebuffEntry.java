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

public record OnHitDebuffEntry(float damageTakenThreshold, int effectDuration) {
    public static final Codec<OnHitDebuffEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group((App)Codec.FLOAT.fieldOf("damageTakenThreshold").forGetter(o -> Float.valueOf(o.damageTakenThreshold)), (App)ExtraCodecs.intRange((int)1, (int)Short.MAX_VALUE).fieldOf("effectDuration").forGetter(o -> o.effectDuration)).apply((Applicative)instance, OnHitDebuffEntry::new));
}


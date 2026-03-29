/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.datafixers.kinds.App
 *  com.mojang.datafixers.kinds.Applicative
 *  com.mojang.serialization.Codec
 *  com.mojang.serialization.MapCodec
 *  com.mojang.serialization.codecs.RecordCodecBuilder
 *  javax.annotation.Nonnull
 *  net.minecraft.util.StringRepresentable
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.entity.player.Player
 */
package ichttt.mods.firstaid.common.damagesystem.distribution;

import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.util.CommonUtils;
import javax.annotation.Nonnull;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

public class DirectDamageDistributionAlgorithm
implements IDamageDistributionAlgorithm {
    public static final MapCodec<DirectDamageDistributionAlgorithm> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group((App)StringRepresentable.fromEnum(() -> EnumPlayerPart.VALUES).fieldOf("part").forGetter(o -> o.part), (App)Codec.BOOL.fieldOf("debuff").forGetter(o -> o.debuff)).apply((Applicative)instance, DirectDamageDistributionAlgorithm::new));
    private final EnumPlayerPart part;
    private final boolean debuff;

    public DirectDamageDistributionAlgorithm(EnumPlayerPart part, boolean debuff) {
        this.part = part;
        this.debuff = debuff;
    }

    @Override
    public float distributeDamage(float damage, @Nonnull Player player, @Nonnull DamageSource source, boolean addStat) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) {
            return 0.0f;
        }
        return damageModel.getFromEnum(this.part).damage(damage, player, this.debuff);
    }

    public MapCodec<DirectDamageDistributionAlgorithm> codec() {
        return CODEC;
    }
}


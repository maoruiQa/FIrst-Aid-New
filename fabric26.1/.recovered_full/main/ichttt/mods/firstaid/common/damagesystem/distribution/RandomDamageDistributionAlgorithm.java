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
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.player.Player
 *  org.apache.commons.lang3.tuple.Pair
 */
package ichttt.mods.firstaid.common.damagesystem.distribution;

import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.damagesystem.distribution.DamageDistribution;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.annotation.Nonnull;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.tuple.Pair;

public class RandomDamageDistributionAlgorithm
extends DamageDistribution {
    public static final MapCodec<RandomDamageDistributionAlgorithm> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group((App)Codec.BOOL.fieldOf("nearestFirst").forGetter(o -> o.nearestFirst), (App)Codec.BOOL.fieldOf("tryNoKill").forGetter(o -> o.tryNoKill)).apply((Applicative)instance, RandomDamageDistributionAlgorithm::pick));
    public static final RandomDamageDistributionAlgorithm NEAREST_NOKILL = new RandomDamageDistributionAlgorithm(true, true);
    public static final RandomDamageDistributionAlgorithm NEAREST_KILL = new RandomDamageDistributionAlgorithm(true, false);
    public static final RandomDamageDistributionAlgorithm ANY_NOKILL = new RandomDamageDistributionAlgorithm(false, true);
    public static final RandomDamageDistributionAlgorithm ANY_KILL = new RandomDamageDistributionAlgorithm(false, false);
    private static final Random RANDOM = new Random();
    private final boolean nearestFirst;
    private final boolean tryNoKill;

    public static RandomDamageDistributionAlgorithm getDefault() {
        return FirstAidConfig.SERVER.useFriendlyRandomDistribution.get() != false ? NEAREST_NOKILL : NEAREST_KILL;
    }

    public static RandomDamageDistributionAlgorithm pick(boolean nearestFirst, boolean tryNoKill) {
        if (nearestFirst) {
            return tryNoKill ? NEAREST_NOKILL : NEAREST_KILL;
        }
        return tryNoKill ? ANY_NOKILL : ANY_KILL;
    }

    protected RandomDamageDistributionAlgorithm(boolean nearestFirst, boolean tryNoKill) {
        this.nearestFirst = nearestFirst;
        this.tryNoKill = tryNoKill;
    }

    @Override
    protected float minHealth(@Nonnull Player player, @Nonnull AbstractDamageablePart playerPart) {
        if (this.tryNoKill && playerPart.canCauseDeath) {
            return 1.0f;
        }
        return 0.0f;
    }

    @Override
    @Nonnull
    protected List<Pair<EquipmentSlot, EnumPlayerPart[]>> getPartList() {
        if (this.nearestFirst) {
            int startValue = RANDOM.nextInt(4);
            return RandomDamageDistributionAlgorithm.addAllRandom(startValue, RANDOM.nextBoolean());
        }
        ArrayList<Pair<EquipmentSlot, EnumPlayerPart[]>> partList = new ArrayList<Pair<EquipmentSlot, EnumPlayerPart[]>>();
        List<EquipmentSlot> slots = Arrays.asList(EquipmentSlot.values());
        Collections.shuffle(slots, RANDOM);
        for (EquipmentSlot slot : slots) {
            List<EnumPlayerPart> parts;
            if (!CommonUtils.isValidArmorSlot(slot) || (parts = CommonUtils.getPartListForSlot(slot)).isEmpty()) continue;
            Collections.shuffle(parts);
            partList.add((Pair<EquipmentSlot, EnumPlayerPart[]>)Pair.of((Object)slot, (Object)parts.toArray(new EnumPlayerPart[0])));
        }
        return partList;
    }

    public static List<Pair<EquipmentSlot, EnumPlayerPart[]>> addAllRandom(int startValue, boolean up) {
        ArrayList<Pair<EquipmentSlot, EnumPlayerPart[]>> partList = new ArrayList<Pair<EquipmentSlot, EnumPlayerPart[]>>();
        for (int i = 0; i < CommonUtils.ARMOR_SLOTS.length; ++i) {
            EquipmentSlot slot;
            List<EnumPlayerPart> parts;
            int posInArray = Math.abs(i - (up ? 0 : 3)) + startValue;
            if (posInArray > 3) {
                posInArray -= 4;
            }
            if ((parts = CommonUtils.getPartListForSlot(slot = CommonUtils.ARMOR_SLOTS[posInArray])).isEmpty()) continue;
            Collections.shuffle(parts);
            partList.add((Pair<EquipmentSlot, EnumPlayerPart[]>)Pair.of((Object)slot, (Object)parts.toArray(new EnumPlayerPart[0])));
        }
        return partList;
    }

    public MapCodec<RandomDamageDistributionAlgorithm> codec() {
        return CODEC;
    }
}


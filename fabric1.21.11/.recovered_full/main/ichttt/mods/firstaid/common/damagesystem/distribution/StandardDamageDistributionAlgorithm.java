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
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.damagesystem.distribution.DamageDistribution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.tuple.Pair;

public class StandardDamageDistributionAlgorithm
extends DamageDistribution {
    public static final MapCodec<StandardDamageDistributionAlgorithm> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group((App)Codec.unboundedMap((Codec)EquipmentSlot.CODEC, (Codec)StringRepresentable.fromEnum(() -> EnumPlayerPart.VALUES).listOf()).fieldOf("partMap").forGetter(o -> o.builtList.stream().collect(Collectors.toMap(Pair::getLeft, pair -> Arrays.asList((EnumPlayerPart[])pair.getRight())))), (App)Codec.BOOL.optionalFieldOf("shuffle", (Object)false).forGetter(o -> o.shuffle), (App)Codec.BOOL.optionalFieldOf("doNeighbours", (Object)true).forGetter(o -> o.doNeighbours)).apply((Applicative)instance, StandardDamageDistributionAlgorithm::new));
    private final boolean shuffle;
    private final boolean doNeighbours;
    private final EnumSet<EnumPlayerPart> blockedParts;
    private final List<Pair<EquipmentSlot, EnumPlayerPart[]>> builtList;

    public StandardDamageDistributionAlgorithm(Map<EquipmentSlot, List<EnumPlayerPart>> partList, boolean shuffle, boolean doNeighbours) {
        this.builtList = new ArrayList<Pair<EquipmentSlot, EnumPlayerPart[]>>(partList.size());
        for (Map.Entry<EquipmentSlot, List<EnumPlayerPart>> entry : partList.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            List<EnumPlayerPart> parts = entry.getValue();
            for (EnumPlayerPart part : parts) {
                if (part.slot == slot) continue;
                throw new RuntimeException(String.valueOf((Object)part) + " is not a member of " + String.valueOf(slot));
            }
            this.builtList.add((Pair<EquipmentSlot, EnumPlayerPart[]>)Pair.of((Object)slot, (Object)parts.toArray(new EnumPlayerPart[0])));
        }
        this.shuffle = shuffle;
        this.doNeighbours = doNeighbours;
        this.blockedParts = EnumSet.noneOf(EnumPlayerPart.class);
    }

    private StandardDamageDistributionAlgorithm(List<Pair<EquipmentSlot, EnumPlayerPart[]>> partList, boolean shuffle, boolean doNeighbours, EnumSet<EnumPlayerPart> blockedParts) {
        this.builtList = partList;
        this.shuffle = shuffle;
        this.doNeighbours = doNeighbours;
        this.blockedParts = blockedParts;
    }

    @Override
    @Nonnull
    protected List<Pair<EquipmentSlot, EnumPlayerPart[]>> getPartList() {
        if (this.shuffle) {
            Collections.shuffle(this.builtList);
        }
        return this.builtList;
    }

    @Override
    public float distributeDamage(float damage, @Nonnull Player player, @Nonnull DamageSource source, boolean addStat) {
        float rest = super.distributeDamage(damage, player, source, addStat);
        if (rest > 0.0f && this.doNeighbours) {
            EnumSet<EnumPlayerPart> neighboursSet = EnumSet.noneOf(EnumPlayerPart.class);
            EnumSet<EnumPlayerPart> blockedParts = EnumSet.copyOf(this.blockedParts);
            for (Pair<EquipmentSlot, EnumPlayerPart[]> pair : this.builtList) {
                blockedParts.addAll(Arrays.asList((EnumPlayerPart[])pair.getRight()));
            }
            for (int i = this.builtList.size() - 1; i >= 0; --i) {
                EnumPlayerPart[] parts;
                for (EnumPlayerPart enumPlayerPart : parts = (EnumPlayerPart[])this.builtList.get(i).getRight()) {
                    neighboursSet.addAll((Collection<EnumPlayerPart>)enumPlayerPart.getNeighbours());
                }
                neighboursSet.removeIf(blockedParts::contains);
                if (neighboursSet.isEmpty()) continue;
                ArrayList<EnumPlayerPart> neighbours = new ArrayList<EnumPlayerPart>(neighboursSet);
                Collections.shuffle(neighbours);
                LinkedHashMap<EquipmentSlot, List> neighbourMapping = new LinkedHashMap<EquipmentSlot, List>();
                for (EnumPlayerPart enumPlayerPart : neighbours) {
                    neighbourMapping.computeIfAbsent(enumPlayerPart.slot, type -> new ArrayList(3)).add(enumPlayerPart);
                }
                ArrayList<Pair<EquipmentSlot, EnumPlayerPart[]>> neighbourDistributions = new ArrayList<Pair<EquipmentSlot, EnumPlayerPart[]>>();
                for (Map.Entry entry : neighbourMapping.entrySet()) {
                    neighbourDistributions.add((Pair<EquipmentSlot, EnumPlayerPart[]>)Pair.of((Object)((EquipmentSlot)entry.getKey()), (Object)((List)entry.getValue()).toArray(new EnumPlayerPart[0])));
                }
                StandardDamageDistributionAlgorithm standardDamageDistributionAlgorithm = new StandardDamageDistributionAlgorithm(neighbourDistributions, false, true, blockedParts);
                if ((rest = standardDamageDistributionAlgorithm.distributeDamage(rest, player, source, addStat)) <= 0.0f) break;
                blockedParts = standardDamageDistributionAlgorithm.blockedParts;
                neighboursSet.clear();
            }
        }
        return rest;
    }

    public MapCodec<StandardDamageDistributionAlgorithm> codec() {
        return CODEC;
    }
}


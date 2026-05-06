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
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.stats.Stats
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 */
package ichttt.mods.firstaid.common.damagesystem.distribution;

import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import ichttt.mods.firstaid.common.util.ArmorUtils;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import javax.annotation.Nonnull;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class EqualDamageDistributionAlgorithm
implements IDamageDistributionAlgorithm {
    public static final MapCodec<EqualDamageDistributionAlgorithm> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group((App)Codec.BOOL.fieldOf("tryNoKill").forGetter(o -> o.tryNoKill), (App)Codec.FLOAT.fieldOf("reductionMultiplier").forGetter(o -> Float.valueOf(o.reductionMultiplier))).apply((Applicative)instance, EqualDamageDistributionAlgorithm::new));
    private final boolean tryNoKill;
    private final float reductionMultiplier;

    public EqualDamageDistributionAlgorithm(boolean tryNoKill, float reductionMultiplier) {
        this.tryNoKill = tryNoKill;
        this.reductionMultiplier = reductionMultiplier;
    }

    private float reduceDamage(float originalDamage, Player player, DamageSource source) {
        float damage = originalDamage;
        for (EquipmentSlot slot : CommonUtils.ARMOR_SLOTS) {
            ItemStack armor = player.getItemBySlot(slot);
            damage = ArmorUtils.applyArmor(player, armor, source, damage, slot);
            if (!(damage <= 0.0f)) continue;
            return 0.0f;
        }
        if ((damage = ArmorUtils.applyGlobalPotionModifiers(player, source, damage)) <= 0.0f) {
            return 0.0f;
        }
        float reduction = originalDamage - damage;
        if (reduction > 0.0f) {
            reduction *= this.reductionMultiplier;
        }
        if ((damage = originalDamage - reduction) <= 0.0f) {
            return 0.0f;
        }
        return damage;
    }

    private float distributeOnParts(float damage, AbstractPlayerDamageModel damageModel, Player player, boolean tryNoKillThisRound) {
        float prevDamageLeft;
        int iterationCounter = 0;
        int divCount = EnumPlayerPart.VALUES.length;
        float damageLeft = damage;
        do {
            prevDamageLeft = damageLeft;
            float toDamage = damageLeft / (float)divCount;
            divCount = 0;
            damageLeft = 0.0f;
            for (AbstractDamageablePart part : damageModel) {
                if (!(part.currentHealth > 0.0f)) continue;
                damageLeft += part.damage(toDamage, player, !player.hasEffect(RegistryObjects.MORPHINE_EFFECT), tryNoKillThisRound ? 1.0f : 0.0f);
                ++divCount;
            }
            if (iterationCounter >= 50) {
                FirstAid.LOGGER.warn(LoggingMarkers.DAMAGE_DISTRIBUTION, "Not done distribution equally after 50 rounds, diff {}. Dropping!", (Object)Float.valueOf(Math.abs(prevDamageLeft - damageLeft)));
                break;
            }
            ++iterationCounter;
        } while (prevDamageLeft != damageLeft);
        return damageLeft;
    }

    @Override
    public float distributeDamage(float damage, @Nonnull Player player, @Nonnull DamageSource source, boolean addStat) {
        float effectiveDmg;
        if ((damage = this.reduceDamage(damage, player, source)) <= 0.0f) {
            return 0.0f;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) {
            return 0.0f;
        }
        float damageLeft = this.distributeOnParts(damage, damageModel, player, this.tryNoKill);
        if (damageLeft > 0.0f && this.tryNoKill) {
            damageLeft = this.distributeOnParts(damage, damageModel, player, false);
        }
        if (player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer)player;
            FirstAidNetworking.sendDamageModelSync(serverPlayer, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
        }
        if ((effectiveDmg = damage - damageLeft) < 3.4028235E37f) {
            player.awardStat(Stats.DAMAGE_TAKEN, Math.round(effectiveDmg * 10.0f));
        }
        return damageLeft;
    }

    @Override
    public boolean skipGlobalPotionModifiers() {
        return true;
    }

    public MapCodec<EqualDamageDistributionAlgorithm> codec() {
        return CODEC;
    }
}


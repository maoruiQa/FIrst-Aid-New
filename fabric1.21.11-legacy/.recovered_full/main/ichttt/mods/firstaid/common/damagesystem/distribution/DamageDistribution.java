/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.stats.Stats
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.player.Player
 *  org.apache.commons.lang3.tuple.Pair
 */
package ichttt.mods.firstaid.common.damagesystem.distribution;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.event.FirstAidLivingDamageEvent;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.distribution.RandomDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import ichttt.mods.firstaid.common.util.ArmorUtils;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.tuple.Pair;

public abstract class DamageDistribution
implements IDamageDistributionAlgorithm {
    public static float handleDamageTaken(IDamageDistributionAlgorithm damageDistribution, AbstractPlayerDamageModel damageModel, float damage, @Nonnull Player player, @Nonnull DamageSource source, boolean addStat, boolean redistributeIfLeft) {
        boolean hasTriedNoKill;
        float left;
        if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
            FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "--- Damaging {} using {} for dmg source {}, redistribute {}, addStat {} ---", (Object)Float.valueOf(damage), (Object)damageDistribution.toString(), (Object)source.type().msgId(), (Object)redistributeIfLeft, (Object)addStat);
        }
        CompoundTag beforeCache = (CompoundTag)damageModel.serializeNBT();
        if (!damageDistribution.skipGlobalPotionModifiers()) {
            damage = ArmorUtils.applyGlobalPotionModifiers(player, source, damage);
        }
        if (damage != 0.0f) {
            player.causeFoodExhaustion(source.getFoodExhaustion());
            player.getCombatTracker().recordDamage(source, damage);
        }
        if ((left = damageDistribution.distributeDamage(damage, player, source, addStat)) > 0.0f && redistributeIfLeft && (left = (damageDistribution = (hasTriedNoKill = damageDistribution == RandomDamageDistributionAlgorithm.NEAREST_NOKILL || damageDistribution == RandomDamageDistributionAlgorithm.ANY_NOKILL) ? RandomDamageDistributionAlgorithm.NEAREST_KILL : RandomDamageDistributionAlgorithm.getDefault()).distributeDamage(left, player, source, addStat)) > 0.0f && !hasTriedNoKill) {
            damageDistribution = RandomDamageDistributionAlgorithm.NEAREST_KILL;
            left = damageDistribution.distributeDamage(left, player, source, addStat);
        }
        PlayerDamageModel before = new PlayerDamageModel();
        before.deserializeNBT(beforeCache);
        FirstAidLivingDamageEvent event = new FirstAidLivingDamageEvent(player, damageModel, before, source, left);
        ((FirstAidLivingDamageEvent.Callback)FirstAidLivingDamageEvent.EVENT.invoker()).onDamage(event);
        if (event.isCanceled()) {
            damageModel.deserializeNBT(beforeCache);
            if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
                FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "--- DONE! Event got canceled ---");
            }
            return 0.0f;
        }
        if (damageModel instanceof PlayerDamageModel) {
            PlayerDamageModel playerDamageModel = (PlayerDamageModel)damageModel;
            playerDamageModel.handlePostDamage(player);
        }
        if (damageModel.isDead(player)) {
            CommonUtils.killPlayer(damageModel, player, source);
        }
        if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
            FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "--- DONE! {} still left ---", (Object)Float.valueOf(left));
        }
        return left;
    }

    protected float minHealth(@Nonnull Player player, @Nonnull AbstractDamageablePart part) {
        return 0.0f;
    }

    protected float distributeDamageOnParts(float damage, @Nonnull AbstractPlayerDamageModel damageModel, @Nonnull EnumPlayerPart[] enumParts, @Nonnull Player player, boolean addStat) {
        ArrayList<AbstractDamageablePart> damageableParts = new ArrayList<AbstractDamageablePart>(enumParts.length);
        for (EnumPlayerPart part : enumParts) {
            damageableParts.add(damageModel.getFromEnum(part));
        }
        Collections.shuffle(damageableParts);
        for (AbstractDamageablePart part : damageableParts) {
            float minHealth = this.minHealth(player, part);
            float dmgDone = damage - part.damage(damage, player, !player.hasEffect(RegistryObjects.MORPHINE_EFFECT), minHealth);
            if (player instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer)player;
                FirstAidNetworking.sendDamageModelSync(serverPlayer, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
            }
            if (addStat) {
                player.awardStat(Stats.DAMAGE_TAKEN, Math.round(dmgDone * 10.0f));
            }
            if ((damage -= dmgDone) == 0.0f) break;
            if (!(damage < 0.0f)) continue;
            FirstAid.LOGGER.error(LoggingMarkers.DAMAGE_DISTRIBUTION, "Got negative damage {} left? Logic error? ", (Object)Float.valueOf(damage));
            break;
        }
        return damage;
    }

    @Nonnull
    protected abstract List<Pair<EquipmentSlot, EnumPlayerPart[]>> getPartList();

    @Override
    public float distributeDamage(float damage, @Nonnull Player player, @Nonnull DamageSource source, boolean addStat) {
        if (damage <= 0.0f) {
            return 0.0f;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) {
            return 0.0f;
        }
        if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
            FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "Starting distribution of {} damage...", (Object)Float.valueOf(damage));
        }
        for (Pair<EquipmentSlot, EnumPlayerPart[]> pair : this.getPartList()) {
            EquipmentSlot slot = (EquipmentSlot)pair.getLeft();
            EnumPlayerPart[] parts = (EnumPlayerPart[])pair.getRight();
            if (Arrays.stream(parts).map(damageModel::getFromEnum).anyMatch(part -> part.currentHealth > this.minHealth(player, (AbstractDamageablePart)part))) {
                float originalDamage = damage;
                damage = ArmorUtils.applyArmor(player, player.getItemBySlot(slot), source, damage, slot);
                if (damage <= 0.0f) {
                    return 0.0f;
                }
                if ((damage = ArmorUtils.applyEnchantmentModifiers(player, slot, source, damage)) <= 0.0f) {
                    return 0.0f;
                }
                float dmgAfterReduce = damage;
                if ((damage = this.distributeDamageOnParts(damage, damageModel, parts, player, addStat)) == 0.0f) break;
                float absorbFactor = originalDamage / dmgAfterReduce;
                float damageDistributed = dmgAfterReduce - damage;
                damage = originalDamage - damageDistributed * absorbFactor;
                if (!FirstAidConfig.GENERAL.debug.get().booleanValue()) continue;
                FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "Distribution round: Not done yet, going to next round. Needed to distribute {} damage (reduced to {}) to {}, but only distributed {}. New damage to be distributed is {}, based on absorb factor {}", (Object)Float.valueOf(originalDamage), (Object)Float.valueOf(dmgAfterReduce), (Object)slot, (Object)Float.valueOf(damageDistributed), (Object)Float.valueOf(damage), (Object)Float.valueOf(absorbFactor));
                continue;
            }
            if (!FirstAidConfig.GENERAL.debug.get().booleanValue()) continue;
            FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "Skipping {}, no health > min in parts!", (Object)slot);
        }
        return damage;
    }
}


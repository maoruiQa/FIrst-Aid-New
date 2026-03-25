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
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import ichttt.mods.firstaid.common.network.MessageUpdatePart;
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

public abstract class DamageDistribution implements IDamageDistributionAlgorithm {
   public static float handleDamageTaken(
      IDamageDistributionAlgorithm damageDistribution,
      AbstractPlayerDamageModel damageModel,
      float damage,
      @Nonnull Player player,
      @Nonnull DamageSource source,
      boolean addStat,
      boolean redistributeIfLeft
   ) {
      if (FirstAidConfig.GENERAL.debug.get()) {
         FirstAid.LOGGER
            .info(
               LoggingMarkers.DAMAGE_DISTRIBUTION,
               "--- Damaging {} using {} for dmg source {}, redistribute {}, addStat {} ---",
               damage,
               damageDistribution.toString(),
               source.type().msgId(),
               redistributeIfLeft,
               addStat
            );
      }

      CompoundTag beforeCache = damageModel.serializeNBT();
      if (!damageDistribution.skipGlobalPotionModifiers()) {
         damage = ArmorUtils.applyGlobalPotionModifiers(player, source, damage);
      }

      if (damage != 0.0F) {
         player.causeFoodExhaustion(source.getFoodExhaustion());
         player.getCombatTracker().recordDamage(source, damage);
      }

      float left = damageDistribution.distributeDamage(damage, player, source, addStat);
      if (left > 0.0F && redistributeIfLeft) {
         boolean hasTriedNoKill = damageDistribution == RandomDamageDistributionAlgorithm.NEAREST_NOKILL
            || damageDistribution == RandomDamageDistributionAlgorithm.ANY_NOKILL;
         damageDistribution = hasTriedNoKill ? RandomDamageDistributionAlgorithm.NEAREST_KILL : RandomDamageDistributionAlgorithm.getDefault();
         left = damageDistribution.distributeDamage(left, player, source, addStat);
         if (left > 0.0F && !hasTriedNoKill) {
            damageDistribution = RandomDamageDistributionAlgorithm.NEAREST_KILL;
            left = damageDistribution.distributeDamage(left, player, source, addStat);
         }
      }

      PlayerDamageModel before = new PlayerDamageModel();
      before.deserializeNBT(beforeCache);
      FirstAidLivingDamageEvent event = new FirstAidLivingDamageEvent(player, damageModel, before, source, left);
      ((FirstAidLivingDamageEvent.Callback)FirstAidLivingDamageEvent.EVENT.invoker()).onDamage(event);
      if (event.isCanceled()) {
         damageModel.deserializeNBT(beforeCache);
         if (FirstAidConfig.GENERAL.debug.get()) {
            FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "--- DONE! Event got canceled ---");
         }

         return 0.0F;
      } else {
         if (damageModel instanceof PlayerDamageModel playerDamageModel) {
            playerDamageModel.handlePostDamage(player);
         }

         if (damageModel.isDead(player)) {
            CommonUtils.killPlayer(damageModel, player, source);
         }

         if (FirstAidConfig.GENERAL.debug.get()) {
            FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "--- DONE! {} still left ---", left);
         }

         return left;
      }
   }

   protected float minHealth(@Nonnull Player player, @Nonnull AbstractDamageablePart part) {
      return 0.0F;
   }

   protected float distributeDamageOnParts(
      float damage, @Nonnull AbstractPlayerDamageModel damageModel, @Nonnull EnumPlayerPart[] enumParts, @Nonnull Player player, boolean addStat
   ) {
      ArrayList<AbstractDamageablePart> damageableParts = new ArrayList<>(enumParts.length);

      for (EnumPlayerPart part : enumParts) {
         damageableParts.add(damageModel.getFromEnum(part));
      }

      Collections.shuffle(damageableParts);

      for (AbstractDamageablePart part : damageableParts) {
         float minHealth = this.minHealth(player, part);
         float dmgDone = damage - part.damage(damage, player, !player.hasEffect(RegistryObjects.MORPHINE_EFFECT), minHealth);
         if (player instanceof ServerPlayer serverPlayer) {
            FirstAidNetworking.sendPartUpdate(serverPlayer, new MessageUpdatePart(player.getId(), part));
         }

         if (addStat) {
            player.awardStat(Stats.DAMAGE_TAKEN, Math.round(dmgDone * 10.0F));
         }

         damage -= dmgDone;
         if (damage == 0.0F) {
            break;
         }

         if (damage < 0.0F) {
            FirstAid.LOGGER.error(LoggingMarkers.DAMAGE_DISTRIBUTION, "Got negative damage {} left? Logic error? ", damage);
            break;
         }
      }

      return damage;
   }

   @Nonnull
   protected abstract List<Pair<EquipmentSlot, EnumPlayerPart[]>> getPartList();

   @Override
   public float distributeDamage(float damage, @Nonnull Player player, @Nonnull DamageSource source, boolean addStat) {
      if (damage <= 0.0F) {
         return 0.0F;
      } else {
         AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
         if (damageModel == null) {
            return 0.0F;
         } else {
            if (FirstAidConfig.GENERAL.debug.get()) {
               FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "Starting distribution of {} damage...", damage);
            }

            for (Pair<EquipmentSlot, EnumPlayerPart[]> pair : this.getPartList()) {
               EquipmentSlot slot = (EquipmentSlot)pair.getLeft();
               EnumPlayerPart[] parts = (EnumPlayerPart[])pair.getRight();
               if (!Arrays.stream(parts).map(damageModel::getFromEnum).anyMatch(part -> part.currentHealth > this.minHealth(player, part))) {
                  if (FirstAidConfig.GENERAL.debug.get()) {
                     FirstAid.LOGGER.info(LoggingMarkers.DAMAGE_DISTRIBUTION, "Skipping {}, no health > min in parts!", slot);
                  }
               } else {
                  float originalDamage = damage;
                  damage = ArmorUtils.applyArmor(player, player.getItemBySlot(slot), source, damage, slot);
                  if (damage <= 0.0F) {
                     return 0.0F;
                  }

                  damage = ArmorUtils.applyEnchantmentModifiers(player, slot, source, damage);
                  if (damage <= 0.0F) {
                     return 0.0F;
                  }

                  float damageAfterReduce = damage;
                  damage = this.distributeDamageOnParts(damage, damageModel, parts, player, addStat);
                  if (damage == 0.0F) {
                     break;
                  }

                  float absorbFactor = originalDamage / damageAfterReduce;
                  float damageDistributed = damageAfterReduce - damage;
                  damage = originalDamage - damageDistributed * absorbFactor;
                  if (FirstAidConfig.GENERAL.debug.get()) {
                     FirstAid.LOGGER
                        .info(
                           LoggingMarkers.DAMAGE_DISTRIBUTION,
                           "Distribution round: Not done yet, going to next round. Needed to distribute {} damage (reduced to {}) to {}, but only distributed {}. New damage to be distributed is {}, based on absorb factor {}",
                           originalDamage,
                           damageAfterReduce,
                           slot,
                           damageDistributed,
                           damage,
                           absorbFactor
                        );
                  }
               }
            }

            return damage;
         }
      }
   }
}

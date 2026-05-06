package ichttt.mods.firstaid.common.damagesystem.distribution;

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

public class EqualDamageDistributionAlgorithm implements IDamageDistributionAlgorithm {
   public static final MapCodec<EqualDamageDistributionAlgorithm> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            Codec.BOOL.fieldOf("tryNoKill").forGetter(o -> o.tryNoKill), Codec.FLOAT.fieldOf("reductionMultiplier").forGetter(o -> o.reductionMultiplier)
         )
         .apply(instance, EqualDamageDistributionAlgorithm::new)
   );
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
         if (damage <= 0.0F) {
            return 0.0F;
         }
      }

      damage = ArmorUtils.applyGlobalPotionModifiers(player, source, damage);
      if (damage <= 0.0F) {
         return 0.0F;
      } else {
         float reduction = originalDamage - damage;
         if (reduction > 0.0F) {
            reduction *= this.reductionMultiplier;
         }

         damage = originalDamage - reduction;
         return damage <= 0.0F ? 0.0F : damage;
      }
   }

   private float distributeOnParts(float damage, AbstractPlayerDamageModel damageModel, Player player, boolean tryNoKillThisRound) {
      int iterationCounter = 0;
      int divCount = EnumPlayerPart.VALUES.length;
      float damageLeft = damage;

      float prevDamageLeft;
      do {
         prevDamageLeft = damageLeft;
         float toDamage = damageLeft / divCount;
         divCount = 0;
         damageLeft = 0.0F;

         for (AbstractDamageablePart part : damageModel) {
            if (part.currentHealth > 0.0F) {
               damageLeft += part.damage(toDamage, player, !player.hasEffect(RegistryObjects.MORPHINE_EFFECT), tryNoKillThisRound ? 1.0F : 0.0F);
               divCount++;
            }
         }

         if (iterationCounter >= 50) {
            FirstAid.LOGGER
               .warn(
                  LoggingMarkers.DAMAGE_DISTRIBUTION,
                  "Not done distribution equally after 50 rounds, diff {}. Dropping!",
                  Math.abs(prevDamageLeft - damageLeft)
               );
            break;
         }

         iterationCounter++;
      } while (prevDamageLeft != damageLeft);

      return damageLeft;
   }

   @Override
   public float distributeDamage(float damage, @Nonnull Player player, @Nonnull DamageSource source, boolean addStat) {
      damage = this.reduceDamage(damage, player, source);
      if (damage <= 0.0F) {
         return 0.0F;
      } else {
         AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
         if (damageModel == null) {
            return 0.0F;
         } else {
            float damageLeft = this.distributeOnParts(damage, damageModel, player, this.tryNoKill);
            if (damageLeft > 0.0F && this.tryNoKill) {
               damageLeft = this.distributeOnParts(damage, damageModel, player, false);
            }

            if (player instanceof ServerPlayer serverPlayer) {
               FirstAidNetworking.sendDamageModelSync(serverPlayer, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
            }

            float effectiveDmg = damage - damageLeft;
            if (effectiveDmg < 3.4028235E37F) {
               player.awardStat(Stats.DAMAGE_TAKEN, Math.round(effectiveDmg * 10.0F));
            }

            return damageLeft;
         }
      }
   }

   @Override
   public boolean skipGlobalPotionModifiers() {
      return true;
   }

   @Override
   public MapCodec<EqualDamageDistributionAlgorithm> codec() {
      return CODEC;
   }
}

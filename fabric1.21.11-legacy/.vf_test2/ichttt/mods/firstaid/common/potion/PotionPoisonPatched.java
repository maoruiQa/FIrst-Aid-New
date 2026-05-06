package ichttt.mods.firstaid.common.potion;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.damagesystem.distribution.DamageDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.RandomDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.util.CommonUtils;
import javax.annotation.Nonnull;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class PotionPoisonPatched extends MobEffect {
   private static final IDamageDistributionAlgorithm POISON_DISTRIBUTION = new PotionPoisonPatched.PoisonDamageDistributionAlgorithm();

   public PotionPoisonPatched(MobEffectCategory type, int liquidColorIn) {
      super(type, liquidColorIn);
   }

   public boolean applyEffectTick(@Nonnull ServerLevel level, @Nonnull LivingEntity entity, int amplifier) {
      if (entity instanceof Player && (FirstAidConfig.SERVER.causeDeathBody.get() || FirstAidConfig.SERVER.causeDeathHead.get())) {
         DamageSource magicDamage = entity.damageSources().magic();
         if (!level.isClientSide() && entity.isAlive() && !entity.isInvulnerableTo(level, magicDamage)) {
            if (entity.isSleeping()) {
               entity.stopSleeping();
            }

            Player player = (Player)entity;
            AbstractPlayerDamageModel playerDamageModel = CommonUtils.getDamageModel(player);
            if (playerDamageModel == null) {
               return false;
            } else {
               DamageDistribution.handleDamageTaken(POISON_DISTRIBUTION, playerDamageModel, 1.0F, player, magicDamage, true, false);
               return true;
            }
         } else {
            return false;
         }
      } else {
         return super.applyEffectTick(level, entity, amplifier);
      }
   }

   public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
      int interval = 25 >> amplifier;
      return interval <= 0 || duration % interval == 0;
   }

   private static final class PoisonDamageDistributionAlgorithm extends RandomDamageDistributionAlgorithm {
      private PoisonDamageDistributionAlgorithm() {
         super(false, true);
      }

      @Override
      protected float minHealth(@Nonnull Player player, @Nonnull AbstractDamageablePart playerPart) {
         return playerPart.getMaxHealth() * 0.3F;
      }
   }
}

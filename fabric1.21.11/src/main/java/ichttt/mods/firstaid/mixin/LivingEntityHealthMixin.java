package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.damagesystem.distribution.DamageDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.HealthDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.RandomDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityHealthMixin {
   @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
   private void firstaid$hurtServer(ServerLevel world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
      if ((Object)this instanceof Player) {
         CommonUtils.pushActiveDamageSource(source);
      }

      if ((Object)this instanceof Player player) {
         Boolean result = EventHandler.preHandleCustomPlayerDamage(player, source, amount);
         if (result != null) {
            cir.setReturnValue(result);
         }
      }
   }

   @Inject(method = "hurtServer", at = @At("RETURN"))
   private void firstaid$hurtServerReturn(ServerLevel world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
      if ((Object)this instanceof Player) {
         CommonUtils.popActiveDamageSource();
      }
   }

   @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
   private void firstaid$actuallyHurt(ServerLevel world, DamageSource source, float amount, CallbackInfo ci) {
      if ((Object)this instanceof Player player && EventHandler.handleCustomPlayerDamage(player, source, amount)) {
         ci.cancel();
      }
   }

   @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
   private void firstaid$setHealth(float health, CallbackInfo ci) {
      LivingEntity entity = (LivingEntity)(Object)this;
      if (entity instanceof Player player && !entity.level().isClientSide()) {
         AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
         if (damageModel != null) {
            if (health > player.getMaxHealth()) {
               damageModel.forEach(damageablePart -> damageablePart.currentHealth = damageablePart.getMaxHealth());
            } else if (shouldInterceptSetHealth(player, health)) {
               float original = player.getHealth();
               if (original > 0.0F && !Float.isNaN(original) && !Float.isInfinite(original)) {
                  if (FirstAidConfig.SERVER.scaleMaxHealth.get()) {
                     original = Math.min(original, (float)player.getAttribute(Attributes.MAX_HEALTH).getValue());
                  }

                  float healed = health - original;
                  if (Math.abs(healed) > 0.001F) {
                     if (healed < 0.0F) {
                        if (FirstAidConfig.GENERAL.debug.get()) {
                           CommonUtils.debugLogStacktrace("DAMAGING: " + -healed);
                        }

                        DamageSource activeSource = CommonUtils.getActiveDamageSource();
                        DamageSource damageSource = activeSource != null ? activeSource : player.damageSources().magic();
                        IDamageDistributionAlgorithm damageDistribution = activeSource != null
                           ? EventHandler.getForcedDamageDistribution(activeSource)
                           : null;
                        if (damageDistribution == null) {
                           damageDistribution = RandomDamageDistributionAlgorithm.getDefault();
                        }

                        DamageDistribution.handleDamageTaken(
                           damageDistribution,
                           damageModel,
                           -healed,
                           player,
                           damageSource,
                           true,
                           activeSource == null || !CommonUtils.isFootOnlyDamageSource(activeSource)
                        );
                     } else {
                        if (FirstAidConfig.GENERAL.debug.get()) {
                           CommonUtils.debugLogStacktrace("HEALING: " + healed);
                        }

                        HealthDistribution.addRandomHealth(healed, player, true);
                     }
                  }

                  ci.cancel();
               }
            }
         }
      }
   }

   private static boolean shouldInterceptSetHealth(Player player, float health) {
      if (CommonUtils.isSetHealthInterceptionSuppressed()) {
         return false;
      }

      if (!Float.isNaN(health) && !Float.isInfinite(health)) {
         return player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null ? !isInternalFirstAidCall() : false;
      } else {
         return false;
      }
   }

   private static boolean isInternalFirstAidCall() {
      for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
         String name = element.getClassName();
         if (name.startsWith("ichttt.mods.firstaid.common.damagesystem.")) {
            return true;
         }

         if (name.startsWith("ichttt.mods.firstaid.common.util.")) {
            return true;
         }
      }

      return false;
   }
}

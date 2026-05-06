/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.ai.attributes.Attributes
 *  net.minecraft.world.entity.player.Player
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.distribution.DamageDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.HealthDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.RandomDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={LivingEntity.class})
public abstract class LivingEntityHealthMixin {
    @Inject(method={"getAbsorptionAmount"}, at={@At(value="HEAD")}, cancellable=true)
    private void firstaid$getAbsorptionAmount(CallbackInfoReturnable<Float> cir) {
        Float absorption;
        LivingEntity entity = (LivingEntity)this;
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel != null && (absorption = damageModel.getAbsorption()) != null) {
            cir.setReturnValue((Object)absorption);
        }
    }

    @Inject(method={"setAbsorptionAmount"}, at={@At(value="HEAD")})
    private void firstaid$setAbsorptionAmount(float absorptionAmount, CallbackInfo ci) {
        Player player;
        block7: {
            block6: {
                LivingEntity entity = (LivingEntity)this;
                if (!(entity instanceof Player)) break block6;
                player = (Player)entity;
                if (!entity.level().isClientSide()) break block7;
            }
            return;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) {
            return;
        }
        damageModel.setAbsorption(absorptionAmount);
        if (player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer)player;
            if (serverPlayer.connection != null) {
                FirstAidNetworking.sendDamageModelSync(serverPlayer, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
            }
        }
    }

    @Inject(method={"setHealth"}, at={@At(value="HEAD")}, cancellable=true)
    private void firstaid$setHealth(float health, CallbackInfo ci) {
        Player player;
        block14: {
            block13: {
                LivingEntity entity = (LivingEntity)this;
                if (!(entity instanceof Player)) break block13;
                player = (Player)entity;
                if (!entity.level().isClientSide()) break block14;
            }
            return;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) {
            return;
        }
        if (health > player.getMaxHealth()) {
            damageModel.forEach(damageablePart -> {
                damageablePart.currentHealth = damageablePart.getMaxHealth();
            });
            return;
        }
        if (!LivingEntityHealthMixin.shouldInterceptSetHealth(player, health)) {
            return;
        }
        float original = player.getHealth();
        if (original > 0.0f && !Float.isNaN(original) && !Float.isInfinite(original)) {
            float healed;
            if (FirstAidConfig.SERVER.scaleMaxHealth.get().booleanValue()) {
                original = Math.min(original, (float)player.getAttribute(Attributes.MAX_HEALTH).getValue());
            }
            if (Math.abs(healed = health - original) > 0.001f) {
                if (healed < 0.0f) {
                    if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
                        CommonUtils.debugLogStacktrace("DAMAGING: " + -healed);
                    }
                    DamageDistribution.handleDamageTaken(RandomDamageDistributionAlgorithm.getDefault(), damageModel, -healed, player, player.damageSources().magic(), true, true);
                } else {
                    if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
                        CommonUtils.debugLogStacktrace("HEALING: " + healed);
                    }
                    HealthDistribution.addRandomHealth(healed, player, true);
                }
            }
        }
        ci.cancel();
    }

    private static boolean shouldInterceptSetHealth(Player player, float health) {
        block5: {
            block4: {
                if (health <= 0.0f || Float.isNaN(health) || Float.isInfinite(health)) {
                    return false;
                }
                if (!(player instanceof ServerPlayer)) break block4;
                ServerPlayer serverPlayer = (ServerPlayer)player;
                if (serverPlayer.connection != null) break block5;
            }
            return false;
        }
        return !LivingEntityHealthMixin.isInternalFirstAidCall();
    }

    private static boolean isInternalFirstAidCall() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String name = element.getClassName();
            if (name.startsWith("ichttt.mods.firstaid.common.damagesystem.")) {
                return true;
            }
            if (!name.startsWith("ichttt.mods.firstaid.common.util.")) continue;
            return true;
        }
        return false;
    }
}


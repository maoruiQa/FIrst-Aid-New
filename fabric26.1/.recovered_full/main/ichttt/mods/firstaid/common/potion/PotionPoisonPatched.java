/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.effect.MobEffect
 *  net.minecraft.world.effect.MobEffectCategory
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 */
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

public class PotionPoisonPatched
extends MobEffect {
    private static final IDamageDistributionAlgorithm POISON_DISTRIBUTION = new PoisonDamageDistributionAlgorithm();

    public PotionPoisonPatched(MobEffectCategory type, int liquidColorIn) {
        super(type, liquidColorIn);
    }

    public boolean applyEffectTick(@Nonnull ServerLevel level, @Nonnull LivingEntity entity, int amplifier) {
        if (entity instanceof Player && (FirstAidConfig.SERVER.causeDeathBody.get().booleanValue() || FirstAidConfig.SERVER.causeDeathHead.get().booleanValue())) {
            Player player;
            AbstractPlayerDamageModel playerDamageModel;
            DamageSource magicDamage = entity.damageSources().magic();
            if (level.isClientSide() || !entity.isAlive() || entity.isInvulnerableTo(level, magicDamage)) {
                return false;
            }
            if (entity.isSleeping()) {
                entity.stopSleeping();
            }
            if ((playerDamageModel = CommonUtils.getDamageModel(player = (Player)entity)) == null) {
                return false;
            }
            DamageDistribution.handleDamageTaken(POISON_DISTRIBUTION, playerDamageModel, 1.0f, player, magicDamage, true, false);
            return true;
        }
        return super.applyEffectTick(level, entity, amplifier);
    }

    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = 25 >> amplifier;
        return interval <= 0 || duration % interval == 0;
    }

    private static final class PoisonDamageDistributionAlgorithm
    extends RandomDamageDistributionAlgorithm {
        private PoisonDamageDistributionAlgorithm() {
            super(false, true);
        }

        @Override
        protected float minHealth(@Nonnull Player player, @Nonnull AbstractDamageablePart playerPart) {
            return (float)playerPart.getMaxHealth() * 0.3f;
        }
    }
}


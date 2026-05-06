/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.common.potion;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.damagesystem.distribution.DamageDistribution;
import ichttt.mods.firstaid.common.damagesystem.distribution.RandomDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayer;

import javax.annotation.Nonnull;

@SuppressWarnings("unused")
public class PotionPoisonPatched extends MobEffect {
    private static final IDamageDistributionAlgorithm POISON_DISTRIBUTION = new PoisonDamageDistributionAlgorithm();

    public PotionPoisonPatched(MobEffectCategory type, int liquidColorIn) {
        super(type, liquidColorIn);
    }

    @Override
    public boolean applyEffectTick(@Nonnull ServerLevel level, @Nonnull LivingEntity entity, int amplifier) {
        if (entity instanceof Player && !(entity instanceof FakePlayer) && (FirstAidConfig.SERVER.causeDeathBody.get() || FirstAidConfig.SERVER.causeDeathHead.get())) {
            DamageSource magicDamage = entity.damageSources().magic();
            if (level.isClientSide() || !entity.isAlive() || entity.isInvulnerableTo(level, magicDamage))
                return false;
            if (entity.isSleeping())
                entity.stopSleeping();
            Player player = (Player) entity;
            AbstractPlayerDamageModel playerDamageModel = CommonUtils.getDamageModel(player);
            if (playerDamageModel == null) {
                return false;
            }
            DamageDistribution.handleDamageTaken(POISON_DISTRIBUTION, playerDamageModel, 1.0F, player, magicDamage, true, false);
            return true;
        }
        return super.applyEffectTick(level, entity, amplifier);
    }

    @Override
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


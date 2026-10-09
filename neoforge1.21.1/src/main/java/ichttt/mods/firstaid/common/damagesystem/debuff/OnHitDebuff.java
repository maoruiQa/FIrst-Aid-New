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

package ichttt.mods.firstaid.common.damagesystem.debuff;

import ichttt.mods.firstaid.FirstAid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public class OnHitDebuff extends AbstractDebuff {
    @Nonnull
    private final List<OnHitDebuffEntry> timeBoundaries;
    @Nullable
    private final SoundEvent sound;
    private final int cooldownTicks;
    private int lastTriggerTick = -1;

    public OnHitDebuff(@Nonnull ResourceLocation potionName, @Nonnull List<OnHitDebuffEntry> timeBoundaries, @Nullable ResourceLocation soundEvent) {
        this(potionName, timeBoundaries, soundEvent, 0);
    }

    public OnHitDebuff(@Nonnull ResourceLocation potionName, @Nonnull List<OnHitDebuffEntry> timeBoundaries, @Nullable ResourceLocation soundEvent, int cooldownTicks) {
        super(potionName);
        this.timeBoundaries = timeBoundaries;
        this.sound = soundEvent == null ? null : Objects.requireNonNull(BuiltInRegistries.SOUND_EVENT.get(soundEvent));
        this.cooldownTicks = Math.max(0, cooldownTicks);
    }

    @Override
    public void handleDamageTaken(float damage, float healthFraction, ServerPlayer player) {
        if (player == null || !Float.isFinite(damage) || damage <= 0.0F) return;
        FirstAid.InjuryDebuffMode mode = getDebuffMode();
        if (mode == FirstAid.InjuryDebuffMode.OFF) return;
        int value = -1;
        for (OnHitDebuffEntry entry : timeBoundaries) {
            if (damage >= entry.damageTakenThreshold() || (entry.healthFractionThreshold() > 0.0F
                    && healthFraction >= 0.0F && healthFraction <= entry.healthFractionThreshold())) {
                value = Math.max(value, entry.effectDuration());
            }
        }
        if (value <= 0 || (cooldownTicks > 0 && lastTriggerTick >= 0 && player.tickCount >= lastTriggerTick
                && (long) player.tickCount - lastTriggerTick < cooldownTicks)) return;
        int duration = (mode == FirstAid.InjuryDebuffMode.LOW ? scaleDurationForMode(value) : value);
        this.applyInjuryEffect(player, new MobEffectInstance(effect, duration, 0, false, false));
        lastTriggerTick = player.tickCount;
    }

    @Override
    public void handleHealing(float healingDone, float healthFraction, ServerPlayer player) {

    }
}




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
import ichttt.mods.firstaid.api.debuff.IDebuff;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

import javax.annotation.Nonnull;
import java.util.Objects;

public abstract class AbstractDebuff implements IDebuff {

    @Nonnull
    public final Holder<MobEffect> effect;
    @Nonnull
    private final Identifier effectId;

    public AbstractDebuff(@Nonnull Identifier potionName) {
        this.effectId = Objects.requireNonNull(potionName);
        this.effect = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(Objects.requireNonNull(BuiltInRegistries.MOB_EFFECT.getValue(potionName)));
    }

    protected final FirstAid.InjuryDebuffMode getDebuffMode() {
        return FirstAid.getInjuryDebuffMode(this.effectId);
    }

    protected final int scaleAmplifierForMode(int amplifier) {
        if (amplifier <= 0) {
            return amplifier;
        }
        return Math.max(0, Math.round(amplifier * FirstAid.lowInjuryDebuffAmplifierScale));
    }

    protected final int scaleDurationForMode(int duration) {
        if (duration <= 1) {
            return duration;
        }
        return Math.max(1, Math.round(duration * FirstAid.lowInjuryDebuffDurationScale));
    }
}



/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 *  net.minecraft.core.Holder
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.resources.Identifier
 *  net.minecraft.world.effect.MobEffect
 */
package ichttt.mods.firstaid.common.damagesystem.debuff;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.debuff.IDebuff;
import java.util.Objects;
import javax.annotation.Nonnull;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

public abstract class AbstractDebuff
implements IDebuff {
    protected static final float LOW_DEBUFF_AMPLIFIER_SCALE = 0.5f;
    protected static final float LOW_DEBUFF_DURATION_SCALE = 0.5f;
    @Nonnull
    public final Holder<MobEffect> effect;
    @Nonnull
    private final Identifier effectId;

    public AbstractDebuff(@Nonnull Identifier potionName) {
        this.effectId = Objects.requireNonNull(potionName);
        this.effect = BuiltInRegistries.MOB_EFFECT.wrapAsHolder((Object)Objects.requireNonNull((MobEffect)BuiltInRegistries.MOB_EFFECT.getValue(potionName)));
    }

    protected final FirstAid.InjuryDebuffMode getDebuffMode() {
        return FirstAid.getInjuryDebuffMode(this.effectId);
    }

    protected final int scaleAmplifierForMode(int amplifier) {
        if (amplifier <= 0) {
            return amplifier;
        }
        return Math.max(0, Math.round((float)amplifier * 0.5f));
    }

    protected final int scaleDurationForMode(int duration) {
        if (duration <= 1) {
            return duration;
        }
        return Math.max(1, Math.round((float)duration * 0.5f));
    }
}


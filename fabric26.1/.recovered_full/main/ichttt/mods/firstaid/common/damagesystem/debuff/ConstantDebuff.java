/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 *  net.minecraft.resources.Identifier
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.effect.MobEffectInstance
 *  net.minecraft.world.entity.player.Player
 */
package ichttt.mods.firstaid.common.damagesystem.debuff;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.common.damagesystem.debuff.AbstractDebuff;
import ichttt.mods.firstaid.common.damagesystem.debuff.ConstantDebuffEntry;
import java.util.List;
import javax.annotation.Nonnull;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

public class ConstantDebuff
extends AbstractDebuff {
    private int ticks = 0;
    private int activeMultiplier = 0;
    private final List<ConstantDebuffEntry> amplifierBoundaries;

    public ConstantDebuff(@Nonnull Identifier potionName, @Nonnull List<ConstantDebuffEntry> amplifierBoundaries) {
        super(potionName);
        this.amplifierBoundaries = amplifierBoundaries;
    }

    private void syncMultiplier(float healthPerMax) {
        boolean found = false;
        for (ConstantDebuffEntry entry : this.amplifierBoundaries) {
            if (!(healthPerMax < entry.healthFractionThreshold())) continue;
            this.ticks = 0;
            this.activeMultiplier = entry.effectAmplifier();
            found = true;
            break;
        }
        if (!found) {
            this.activeMultiplier = 0;
        }
    }

    @Override
    public void handleDamageTaken(float damage, float healthFraction, ServerPlayer player) {
        this.syncMultiplier(healthFraction);
    }

    @Override
    public void handleHealing(float healingDone, float healthFraction, ServerPlayer player) {
        this.syncMultiplier(healthFraction);
    }

    @Override
    public void update(Player player, float healthFraction) {
        FirstAid.InjuryDebuffMode mode = this.getDebuffMode();
        if (mode == FirstAid.InjuryDebuffMode.OFF) {
            this.ticks = 0;
            this.activeMultiplier = 0;
            return;
        }
        if (this.activeMultiplier == 0) {
            this.ticks = 0;
            return;
        }
        if (this.ticks == 0) {
            if (healthFraction != -1.0f) {
                this.syncMultiplier(healthFraction);
            }
            if (this.activeMultiplier != 0) {
                int amplifier = this.activeMultiplier - 1;
                if (mode == FirstAid.InjuryDebuffMode.LOW) {
                    amplifier = this.scaleAmplifierForMode(amplifier);
                }
                player.addEffect(new MobEffectInstance(this.effect, 169, amplifier, false, false));
            }
        }
        ++this.ticks;
        if (this.ticks >= 79) {
            this.ticks = 0;
        }
    }
}


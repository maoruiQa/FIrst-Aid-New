/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.ItemStack
 */
package ichttt.mods.firstaid.common.damagesystem;

import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import java.util.function.IntSupplier;
import net.minecraft.world.item.ItemStack;

public class PartHealer
extends AbstractPartHealer {
    private int ticksPassed = 0;
    private int heals = 0;

    public PartHealer(IntSupplier ticksPerHeal, IntSupplier maxHeal, ItemStack stack) {
        super(maxHeal, stack, ticksPerHeal);
    }

    @Override
    public AbstractPartHealer loadNBT(int ticksPassed, int heals) {
        this.ticksPassed = ticksPassed;
        this.heals = heals;
        return this;
    }

    @Override
    public boolean hasFinished() {
        return this.heals >= this.maxHeal.getAsInt();
    }

    @Override
    public boolean tick() {
        boolean nextHeal;
        if (this.hasFinished()) {
            return false;
        }
        ++this.ticksPassed;
        boolean bl = nextHeal = this.ticksPassed >= this.ticksPerHeal.getAsInt();
        if (nextHeal) {
            this.ticksPassed = 0;
            ++this.heals;
        }
        return nextHeal;
    }

    @Override
    public int getTicksPassed() {
        return this.ticksPassed;
    }

    @Override
    public int getHealsDone() {
        return this.heals;
    }
}


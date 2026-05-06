/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.entity.player.Player
 */
package ichttt.mods.firstaid.common.compat.playerrevive;

import ichttt.mods.firstaid.common.compat.playerrevive.IPRCompatHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

public class NoopPRCompatHandler
implements IPRCompatHandler {
    @Override
    public boolean tryKnockOutPlayer(Player player, DamageSource source) {
        return false;
    }

    @Override
    public boolean isBleeding(Player player) {
        return false;
    }
}


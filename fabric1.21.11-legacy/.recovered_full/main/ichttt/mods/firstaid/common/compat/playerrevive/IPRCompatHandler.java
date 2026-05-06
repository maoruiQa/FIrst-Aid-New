/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.entity.player.Player
 */
package ichttt.mods.firstaid.common.compat.playerrevive;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

public interface IPRCompatHandler {
    public boolean tryKnockOutPlayer(Player var1, DamageSource var2);

    public boolean isBleeding(Player var1);
}


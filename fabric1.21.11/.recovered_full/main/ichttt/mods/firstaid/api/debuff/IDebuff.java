/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.player.Player
 */
package ichttt.mods.firstaid.api.debuff;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public interface IDebuff {
    public void handleDamageTaken(float var1, float var2, ServerPlayer var3);

    public void handleHealing(float var1, float var2, ServerPlayer var3);

    default public void update(Player player, float healthFraction) {
    }
}


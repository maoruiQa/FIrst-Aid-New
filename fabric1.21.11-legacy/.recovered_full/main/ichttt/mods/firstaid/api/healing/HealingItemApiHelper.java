/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.Level
 */
package ichttt.mods.firstaid.api.healing;

import ichttt.mods.firstaid.api.healing.ItemHealing;
import javax.annotation.Nonnull;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public abstract class HealingItemApiHelper {
    static HealingItemApiHelper INSTANCE;

    public static void setImpl(HealingItemApiHelper impl) {
        INSTANCE = impl;
    }

    @Nonnull
    public abstract InteractionResult onItemRightClick(ItemHealing var1, Level var2, Player var3, InteractionHand var4);
}


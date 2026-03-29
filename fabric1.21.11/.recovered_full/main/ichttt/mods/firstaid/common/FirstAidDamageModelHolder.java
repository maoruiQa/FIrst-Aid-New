/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nullable
 */
package ichttt.mods.firstaid.common;

import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import javax.annotation.Nullable;

public interface FirstAidDamageModelHolder {
    public PlayerDamageModel firstaid$getDamageModel();

    @Nullable
    public PlayerDamageModel firstaid$getDamageModelNullable();

    public void firstaid$setDamageModel(PlayerDamageModel var1);
}


/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.serialization.MapCodec
 */
package ichttt.mods.firstaid.api.debuff;

import com.mojang.serialization.MapCodec;
import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.api.enums.EnumDebuffSlot;

public interface IDebuffBuilder {
    public MapCodec<? extends IDebuffBuilder> codec();

    public EnumDebuffSlot affectedSlot();

    public IDebuff build();
}


package ichttt.mods.firstaid.api.debuff;

import com.mojang.serialization.MapCodec;
import ichttt.mods.firstaid.api.enums.EnumDebuffSlot;

public interface IDebuffBuilder {
   MapCodec<? extends IDebuffBuilder> codec();

   EnumDebuffSlot affectedSlot();

   IDebuff build();
}

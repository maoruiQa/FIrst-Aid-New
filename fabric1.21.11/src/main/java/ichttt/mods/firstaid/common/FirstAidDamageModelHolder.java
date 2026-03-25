package ichttt.mods.firstaid.common;

import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import javax.annotation.Nullable;

public interface FirstAidDamageModelHolder {
   PlayerDamageModel firstaid$getDamageModel();

   @Nullable
   PlayerDamageModel firstaid$getDamageModelNullable();

   void firstaid$setDamageModel(PlayerDamageModel var1);
}

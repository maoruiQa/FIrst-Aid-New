package ichttt.mods.firstaid.api.util;

public interface INBTSerializable<T> {
   T serializeNBT();

   void deserializeNBT(T var1);
}

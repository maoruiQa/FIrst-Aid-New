package net.minecraftforge.common.util;

public interface INBTSerializable<T> {

    T serializeNBT();

    void deserializeNBT(T nbt);
}

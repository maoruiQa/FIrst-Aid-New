/*
 * Decompiled with CFR 0.152.
 */
package ichttt.mods.firstaid.api.util;

public interface INBTSerializable<T> {
    public T serializeNBT();

    public void deserializeNBT(T var1);
}


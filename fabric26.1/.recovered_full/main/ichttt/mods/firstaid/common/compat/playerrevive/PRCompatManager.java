/*
 * Decompiled with CFR 0.152.
 */
package ichttt.mods.firstaid.common.compat.playerrevive;

import ichttt.mods.firstaid.common.compat.playerrevive.IPRCompatHandler;
import ichttt.mods.firstaid.common.compat.playerrevive.NoopPRCompatHandler;

public class PRCompatManager {
    private static IPRCompatHandler handler = new NoopPRCompatHandler();

    public static IPRCompatHandler getHandler() {
        return handler;
    }

    public static void init() {
        handler = new NoopPRCompatHandler();
    }
}


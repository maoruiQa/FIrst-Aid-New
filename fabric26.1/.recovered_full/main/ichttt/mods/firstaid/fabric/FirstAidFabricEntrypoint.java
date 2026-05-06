/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.ModInitializer
 */
package ichttt.mods.firstaid.fabric;

import ichttt.mods.firstaid.FirstAid;
import net.fabricmc.api.ModInitializer;

public class FirstAidFabricEntrypoint
implements ModInitializer {
    public void onInitialize() {
        FirstAid.initCommon();
    }
}


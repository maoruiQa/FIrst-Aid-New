/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ichttt.mods.firstaid.FirstAidConfig
 *  ichttt.mods.firstaid.common.ClientAccess
 *  ichttt.mods.firstaid.common.ClientAccess$ClientActions
 *  net.minecraft.client.Minecraft
 *  net.minecraft.world.InteractionHand
 */
package ichttt.mods.firstaid;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.client.ClientHooks;
import ichttt.mods.firstaid.client.network.FirstAidClientNetworking;
import ichttt.mods.firstaid.common.ClientAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

public final class FirstAidClient {
    private FirstAidClient() {
    }

    public static void initClient() {
        FirstAidConfig.loadClient();
        ClientHooks.setup();
        FirstAidClientNetworking.registerClient();
        ClientAccess.install((ClientAccess.ClientActions)new ClientAccess.ClientActions(){

            public void showApplyHealth(InteractionHand hand) {
                ClientHooks.showGuiApplyHealth(hand);
            }

            public int getTextWidth(String text) {
                return Minecraft.getInstance().font.width(text);
            }
        });
    }
}


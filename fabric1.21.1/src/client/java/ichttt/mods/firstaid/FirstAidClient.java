/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid;

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
        ClientAccess.install(new ClientAccess.ClientActions() {
            @Override
            public boolean showApplyHealth(InteractionHand hand) {
                return ClientHooks.showGuiApplyHealth(hand);
            }

            @Override
            public boolean beginApplyHealthUse(InteractionHand hand) {
                return ClientHooks.beginApplyHealthUse(hand);
            }

            @Override
            public int getTextWidth(String text) {
                return Minecraft.getInstance().font.width(text);
            }
        });
    }
}

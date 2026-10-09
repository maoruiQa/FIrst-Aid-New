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

package ichttt.mods.firstaid.client.gui;

import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;

public final class FirstaidIngameGui {
    private FirstaidIngameGui() {
    }

    public static void renderHealth(ForgeGui gui, int width, int height, GuiGraphics guiGraphics) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int left = width / 2 - 91;
        int top = height - gui.leftHeight;
        reserveHealthBarSpace(gui, player);
        float amount = player.getAbsorptionAmount();
        if (!Float.isFinite(amount) || amount <= 0.0F) {
            return;
        }
        int absorption = Mth.ceil(amount);
        int hearts = Mth.ceil(absorption / 2.0F);
        int rows = Mth.ceil(hearts / 10.0F);
        int rowHeight = Math.max(10 - (rows - 2), 3);
        for (int i = 0; i < hearts; i++) {
            int x = left + i % 10 * 8;
            int y = top - i / 10 * rowHeight;
            boolean half = i == hearts - 1 && absorption % 2 == 1;
            int textureY = player.level().getLevelData().isHardcore() ? 45 : 0;
            guiGraphics.blit(HealthRenderUtils.GUI_ICONS_LOCATION, x, y, 16, textureY, 9, 9);
            guiGraphics.blit(HealthRenderUtils.GUI_ICONS_LOCATION, x, y, half ? 169 : 160, textureY, 9, 9);
        }
    }

    public static void reserveHealthBarSpace(ForgeGui gui, Player player) {
        int healthRows = getHealthRows(player);
        int rowHeight = getRowHeight(player);
        gui.leftHeight += healthRows * rowHeight;
        if (rowHeight != 10) {
            gui.leftHeight += 10 - rowHeight;
        }
    }

    private static int getHealthRows(Player player) {
        int health = Mth.ceil(player.getHealth());
        AttributeInstance attrMaxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        float healthMax = Math.max((float) attrMaxHealth.getValue(), health);
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        return Mth.ceil((healthMax + absorption) / 2.0F / 10.0F);
    }

    private static int getRowHeight(Player player) {
        return Math.max(10 - (getHealthRows(player) - 2), 3);
    }
}

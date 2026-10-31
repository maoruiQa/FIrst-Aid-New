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

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.client.gui.KnockdownNoticeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import javax.annotation.Nullable;

/**
 * Holds a pending knockdown notice until the client can show it, so it is not lost behind loading or death screens.
 */
public final class KnockdownNoticeClient {
    @Nullable
    private static KnockdownNoticeScreen.Notice pending;

    private KnockdownNoticeClient() {
    }

    public static void show(FirstAid.KnockdownMode mode, double coefficient) {
        pending = new KnockdownNoticeScreen.Notice(mode, coefficient);
        tick(Minecraft.getInstance());
    }

    /** Re-queues a notice whose screen was replaced (for example by the death screen) before it was confirmed. */
    public static void requeue(KnockdownNoticeScreen.Notice notice) {
        if (pending == null) {
            pending = notice;
        }
    }

    public static void tick(Minecraft mc) {
        KnockdownNoticeScreen.Notice notice = pending;
        if (notice == null || mc.player == null || mc.level == null || mc.gui.overlay() != null) {
            return;
        }
        Screen current = mc.gui.screen();
        Screen parent = current;
        if (current instanceof KnockdownNoticeScreen shown) {
            shown.supersede();
            parent = shown.parent();
        } else if (current != null && !current.shouldCloseOnEsc()) {
            // loading, death and other forced screens: wait until they are gone
            return;
        }
        if (parent instanceof AbstractContainerScreen<?>) {
            // the container closes when the notice opens, so there is nothing to return to
            parent = null;
        }
        pending = null;
        mc.gui.setScreen(new KnockdownNoticeScreen(notice, parent));
    }

    public static boolean hasPending() {
        return pending != null;
    }

    public static void reset() {
        pending = null;
    }
}

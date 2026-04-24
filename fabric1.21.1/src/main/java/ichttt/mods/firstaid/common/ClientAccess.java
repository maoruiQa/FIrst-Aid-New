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

package ichttt.mods.firstaid.common;

import net.minecraft.world.InteractionHand;

import java.util.Objects;

public final class ClientAccess {
    private static ClientActions actions = new NoopClientActions();

    private ClientAccess() {
    }

    public static void install(ClientActions clientActions) {
        actions = Objects.requireNonNull(clientActions, "clientActions");
    }

    public static boolean showApplyHealth(InteractionHand hand) {
        return actions.showApplyHealth(hand);
    }

    public static boolean beginApplyHealthUse(InteractionHand hand) {
        return actions.beginApplyHealthUse(hand);
    }

    public static int getTextWidth(String text) {
        return actions.getTextWidth(text);
    }

    public interface ClientActions {
        boolean showApplyHealth(InteractionHand hand);

        boolean beginApplyHealthUse(InteractionHand hand);

        int getTextWidth(String text);
    }

    private static final class NoopClientActions implements ClientActions {
        @Override
        public boolean showApplyHealth(InteractionHand hand) {
            return false;
        }

        @Override
        public boolean beginApplyHealthUse(InteractionHand hand) {
            return false;
        }

        @Override
        public int getTextWidth(String text) {
            return text == null ? 0 : text.length() * 6;
        }
    }
}

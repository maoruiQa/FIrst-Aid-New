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

package ichttt.mods.firstaid.common.compat.playerrevive;

import ichttt.mods.firstaid.FirstAid;
import net.neoforged.fml.ModList;

public class PRCompatManager {
    private static IPRCompatHandler handler = new NoopPRCompatHandler();

    public static IPRCompatHandler getHandler() {
        return handler;
    }

    public static void init() {
        handler = new NoopPRCompatHandler();
        if (!ModList.get().isLoaded("playerrevive")) {
            return;
        }

        if (PRPresentCompatHandler.canUse()) {
            handler = new PRPresentCompatHandler();
            FirstAid.LOGGER.info("Enabled PlayerRevive compatibility");
        } else {
            FirstAid.LOGGER.warn("PlayerRevive detected, but FirstAid could not resolve its runtime API. Falling back to vanilla death handling.");
        }
    }
}


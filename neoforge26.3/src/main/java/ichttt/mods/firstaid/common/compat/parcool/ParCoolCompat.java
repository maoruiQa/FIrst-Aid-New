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

package ichttt.mods.firstaid.common.compat.parcool;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Soft-compat for ParCool: cancel parkour actions while the player is unconscious.
 * Uses reflection so ParCool is not a compile-time dependency.
 */
public final class ParCoolCompat {
    private static final String[] TRY_START_CLASSES = {
            "com.alrex.parcool.api.unstable.action.ParCoolActionEvent$TryToStart",
            "com.alrex.parcool.api.unstable.action.ParCoolActionEvent$TryToStartEvent",
            "com.alrex.parcool.api.action.ParCoolActionEvent$TryToStart",
            "com.alrex.parcool.api.action.ParCoolActionEvent$TryToStartEvent"
    };
    private static final String[] TRY_CONTINUE_CLASSES = {
            "com.alrex.parcool.api.unstable.action.ParCoolActionEvent$TryToContinue",
            "com.alrex.parcool.api.unstable.action.ParCoolActionEvent$TryToContinueEvent",
            "com.alrex.parcool.api.action.ParCoolActionEvent$TryToContinue",
            "com.alrex.parcool.api.action.ParCoolActionEvent$TryToContinueEvent"
    };

    private ParCoolCompat() {
    }

    public static void init() {
        if (!ModList.get().isLoaded("parcool")) {
            return;
        }

        int registered = 0;
        registered += registerCancelListeners(TRY_START_CLASSES);
        registered += registerCancelListeners(TRY_CONTINUE_CLASSES);
        if (registered > 0) {
            FirstAid.LOGGER.info("Enabled ParCool compatibility ({} action cancel listener(s))", registered);
        } else {
            FirstAid.LOGGER.warn("ParCool detected, but FirstAid could not resolve its action cancel events");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int registerCancelListeners(String[] classNames) {
        int count = 0;
        for (String className : classNames) {
            try {
                Class<?> eventClass = Class.forName(className);
                NeoForge.EVENT_BUS.addListener(
                        EventPriority.HIGHEST,
                        false,
                        (Class) eventClass,
                        (Consumer) ParCoolCompat::cancelIfUnconscious
                );
                count++;
            } catch (ClassNotFoundException ignored) {
                // Try next package/name variant.
            } catch (Throwable t) {
                FirstAid.LOGGER.debug("Failed to register ParCool listener for {}", className, t);
            }
        }
        return count;
    }

    private static void cancelIfUnconscious(Object event) {
        Player player = extractPlayer(event);
        if (player == null || !isUnconscious(player)) {
            return;
        }
        if (event instanceof ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
            return;
        }
        try {
            Method setCanceled = event.getClass().getMethod("setCanceled", boolean.class);
            setCanceled.invoke(event, true);
        } catch (ReflectiveOperationException ignored) {
            // Event type is not cancellable.
        }
    }

    private static Player extractPlayer(Object event) {
        try {
            Object player = event.getClass().getMethod("getPlayer").invoke(event);
            return player instanceof Player cast ? cast : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static boolean isUnconscious(Player player) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getExistingDamageModel(player);
        return damageModel instanceof PlayerDamageModel playerDamageModel && playerDamageModel.isUnconscious();
    }
}

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

import com.mojang.blaze3d.platform.InputConstants;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.gui.GuiHealthScreen;
import ichttt.mods.firstaid.client.util.EventCalendar;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

public class ClientHooks {
    public static final KeyMapping SHOW_WOUNDS = new KeyMapping("keybinds.show_wounds", KeyConflictContext.UNIVERSAL, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_H), "First Aid");
    public static final KeyMapping GIVE_UP = new KeyMapping("keybinds.give_up", KeyConflictContext.UNIVERSAL, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G), "First Aid");

    public static void setup() {
        FirstAid.LOGGER.debug("Loading ClientHooks");
        MinecraftForge.EVENT_BUS.register(ClientEventHandler.class);
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(ClientHooks::registerKeybindEvent);
        modEventBus.addListener(ClientHooks::registerOverlayEvent);
        modEventBus.addListener(ClientHooks::registerReloadListenerEvent);
        EventCalendar.checkDate();
    }

    public static boolean showGuiApplyHealth(InteractionHand activeHand) {
        Minecraft mc = Minecraft.getInstance();
        if (ClientEventHandler.hasValidPendingHealingSelection(activeHand)) {
            return false;
        }
        if (!ClientEventHandler.canOpenHealingScreen(activeHand)) {
            return false;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
        if (damageModel == null) {
            return false;
        }
        FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.Type.REQUEST_REFRESH));
        GuiHealthScreen.INSTANCE = new GuiHealthScreen(damageModel, activeHand);
        mc.setScreen(GuiHealthScreen.INSTANCE);
        return true;
    }

    public static boolean beginApplyHealthUse(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientEventHandler.hasValidPendingHealingSelection(hand)) {
            return false;
        }
        mc.player.startUsingItem(hand);
        return true;
    }

    public static void registerKeybindEvent(RegisterKeyMappingsEvent event) {
        event.register(SHOW_WOUNDS);
        event.register(GIVE_UP);
    }

    public static void registerOverlayEvent(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("status_overlay", StatusEffectLayer.INSTANCE);
        event.registerAboveAll("hud_overlay", HUDHandler.INSTANCE);
    }

    public static void registerReloadListenerEvent(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(HUDHandler.INSTANCE);
    }
}

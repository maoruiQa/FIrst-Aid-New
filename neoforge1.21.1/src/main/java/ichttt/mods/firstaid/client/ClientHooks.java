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
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import org.lwjgl.glfw.GLFW;


public class ClientHooks {
    private static final String CATEGORY = "key.categories.firstaid";
    public static final KeyMapping SHOW_WOUNDS = new KeyMapping("keybinds.show_wounds", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping GIVE_UP = new KeyMapping("keybinds.give_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    public static void setup(IEventBus modEventBus) {
        FirstAid.LOGGER.debug("Loading ClientHooks");
        NeoForge.EVENT_BUS.register(ClientEventHandler.class);
        modEventBus.addListener(ClientHooks::registerKeybindEvent);
        modEventBus.addListener(ClientHooks::registerOverlayEvent);
        modEventBus.addListener(ClientHooks::registerReloadListenerEvent);
        EventCalendar.checkDate();
    }

    public static void showGuiApplyHealth(InteractionHand activeHand) {
        Minecraft mc = Minecraft.getInstance();
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
        if (damageModel == null) return;
        FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.REQUEST_REFRESH));
        GuiHealthScreen.INSTANCE = new GuiHealthScreen(damageModel, activeHand);
        mc.setScreen(GuiHealthScreen.INSTANCE);
    }

    public static void registerKeybindEvent(RegisterKeyMappingsEvent event) {
        event.register(ClientHooks.SHOW_WOUNDS);
        event.register(ClientHooks.GIVE_UP);
    }

    public static void registerOverlayEvent(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "status_overlay"), StatusEffectLayer.INSTANCE);
    }

    public static void registerReloadListenerEvent(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(HUDHandler.INSTANCE);
    }
}



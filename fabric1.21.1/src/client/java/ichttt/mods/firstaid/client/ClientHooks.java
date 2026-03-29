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
import ichttt.mods.firstaid.client.network.FirstAidClientNetworking;
import ichttt.mods.firstaid.client.util.EventCalendar;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.items.ItemAdrenalineInjector;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

public final class ClientHooks {
    private static final String CATEGORY = "key.categories.firstaid";
    public static final KeyMapping SHOW_WOUNDS = new KeyMapping("keybinds.show_wounds", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping GIVE_UP = new KeyMapping("keybinds.give_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    private ClientHooks() {
    }

    public static void setup() {
        FirstAid.LOGGER.debug("Loading ClientHooks");
        KeyBindingHelper.registerKeyBinding(SHOW_WOUNDS);
        KeyBindingHelper.registerKeyBinding(GIVE_UP);
        HudRenderCallback.EVENT.register(StatusEffectLayer.INSTANCE);
        HudRenderCallback.EVENT.register(HUDHandler.INSTANCE);
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(HUDHandler.INSTANCE);
        ClientEventHandler.register();
        EventCalendar.checkDate();
        ItemAdrenalineInjector.clientStopSoundCallback = () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().stop(ichttt.mods.firstaid.common.RegistryObjects.ADRENALINE_INJECTOR_USE.value().getLocation(), SoundSource.PLAYERS);
        };
    }

    public static void showGuiApplyHealth(InteractionHand activeHand) {
        Minecraft mc = Minecraft.getInstance();
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
        if (damageModel == null) {
            return;
        }
        FirstAidClientNetworking.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.REQUEST_REFRESH));
        GuiHealthScreen.INSTANCE = new GuiHealthScreen(damageModel, activeHand);
        mc.setScreen(GuiHealthScreen.INSTANCE);
    }
}

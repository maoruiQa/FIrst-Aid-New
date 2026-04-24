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
import ichttt.mods.firstaid.common.network.MessageClientRequest.RequestType;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import org.lwjgl.glfw.GLFW;


public class ClientHooks {
    private static final Identifier HUD_RELOAD_LISTENER = Identifier.fromNamespaceAndPath(FirstAid.MODID, "hud");
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(FirstAid.MODID, "firstaid"));
    public static final KeyMapping SHOW_WOUNDS = new KeyMapping("keybinds.show_wounds", KeyConflictContext.UNIVERSAL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping GIVE_UP = new KeyMapping("keybinds.give_up", KeyConflictContext.UNIVERSAL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    public static void setup(IEventBus modEventBus) {
        FirstAid.LOGGER.debug("Loading ClientHooks");
        NeoForge.EVENT_BUS.register(ClientEventHandler.class);
        modEventBus.addListener(ClientHooks::registerKeybindEvent);
        modEventBus.addListener(ClientHooks::registerOverlayEvent);
        modEventBus.addListener(ClientHooks::registerReloadListenerEvent);
        modEventBus.addListener(ClientHooks::registerRenderStateExtensions);
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
        FirstAid.NETWORKING.sendToServer(new MessageClientRequest(RequestType.REQUEST_REFRESH));
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
        event.registerCategory(CATEGORY);
        event.register(ClientHooks.SHOW_WOUNDS);
        event.register(ClientHooks.GIVE_UP);
    }

    public static void registerOverlayEvent(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(FirstAid.MODID, "status_overlay"), StatusEffectLayer.INSTANCE);
        event.registerAboveAll(Identifier.fromNamespaceAndPath(FirstAid.MODID, "hud_overlay"), HUDHandler.INSTANCE);
    }

    public static void registerReloadListenerEvent(AddClientReloadListenersEvent event) {
        event.addListener(HUD_RELOAD_LISTENER, HUDHandler.INSTANCE);
    }

    @SuppressWarnings({"unchecked", "RedundantCast"})
    public static void registerRenderStateExtensions(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                (Class<? extends AvatarRenderer<AbstractClientPlayer>>) (Object) AvatarRenderer.class,
                (entity, state) -> {
                    state.setRenderData(RenderStateExtensions.PASSENGER, entity.isPassenger());
                    var damageModel = CommonUtils.getExistingDamageModel(entity);
                    float collapseProgress = 1.0F;
                    boolean unconscious = false;
                    if (damageModel instanceof ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel playerDamageModel) {
                        collapseProgress = playerDamageModel.getCollapseAnimationProgress(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
                        unconscious = playerDamageModel.isUnconscious();
                    }
                    if (unconscious) {
                        state.swimAmount = 0.0F;
                        state.isVisuallySwimming = false;
                    }
                    state.setRenderData(RenderStateExtensions.UNCONSCIOUS, unconscious);
                    state.setRenderData(RenderStateExtensions.COLLAPSE_PROGRESS, collapseProgress);
                }
        );
    }
}

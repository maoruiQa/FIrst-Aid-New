/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.InputConstants$Type
 *  ichttt.mods.firstaid.FirstAid
 *  ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel
 *  ichttt.mods.firstaid.common.network.MessageClientRequest
 *  ichttt.mods.firstaid.common.network.MessageClientRequest$RequestType
 *  ichttt.mods.firstaid.common.util.CommonUtils
 *  net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
 *  net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
 *  net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
 *  net.fabricmc.fabric.api.resource.ResourceManagerHelper
 *  net.minecraft.client.KeyMapping
 *  net.minecraft.client.KeyMapping$Category
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.resources.Identifier
 *  net.minecraft.server.packs.PackType
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.player.Player
 */
package ichttt.mods.firstaid.client;

import com.mojang.blaze3d.platform.InputConstants;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.ClientEventHandler;
import ichttt.mods.firstaid.client.HUDHandler;
import ichttt.mods.firstaid.client.StatusEffectLayer;
import ichttt.mods.firstaid.client.gui.GuiHealthScreen;
import ichttt.mods.firstaid.client.network.FirstAidClientNetworking;
import ichttt.mods.firstaid.client.util.EventCalendar;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

public final class ClientHooks {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register((Identifier)Identifier.fromNamespaceAndPath((String)"firstaid", (String)"firstaid"));
    public static final KeyMapping SHOW_WOUNDS = new KeyMapping("keybinds.show_wounds", InputConstants.Type.KEYSYM, 72, CATEGORY);
    public static final KeyMapping GIVE_UP = new KeyMapping("keybinds.give_up", InputConstants.Type.KEYSYM, 71, CATEGORY);

    private ClientHooks() {
    }

    public static void setup() {
        FirstAid.LOGGER.debug("Loading ClientHooks");
        KeyBindingHelper.registerKeyBinding((KeyMapping)SHOW_WOUNDS);
        KeyBindingHelper.registerKeyBinding((KeyMapping)GIVE_UP);
        HudRenderCallback.EVENT.register((Object)StatusEffectLayer.INSTANCE);
        HudRenderCallback.EVENT.register((Object)HUDHandler.INSTANCE);
        ResourceManagerHelper.get((PackType)PackType.CLIENT_RESOURCES).registerReloadListener((IdentifiableResourceReloadListener)HUDHandler.INSTANCE);
        ClientEventHandler.register();
        EventCalendar.checkDate();
    }

    public static void showGuiApplyHealth(InteractionHand activeHand) {
        Minecraft mc = Minecraft.getInstance();
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel((Player)mc.player);
        if (damageModel == null) {
            return;
        }
        FirstAidClientNetworking.sendToServer((CustomPacketPayload)new MessageClientRequest(MessageClientRequest.RequestType.REQUEST_REFRESH));
        GuiHealthScreen.INSTANCE = new GuiHealthScreen(damageModel, activeHand);
        mc.setScreen((Screen)GuiHealthScreen.INSTANCE);
    }
}


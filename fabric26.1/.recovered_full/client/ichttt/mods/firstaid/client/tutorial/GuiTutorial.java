/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel
 *  ichttt.mods.firstaid.common.network.MessageClientRequest
 *  ichttt.mods.firstaid.common.network.MessageClientRequest$RequestType
 *  ichttt.mods.firstaid.common.util.CommonUtils
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.resources.language.I18n
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.world.entity.player.Player
 */
package ichttt.mods.firstaid.client.tutorial;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.ClientHooks;
import ichttt.mods.firstaid.client.gui.GuiHealthScreen;
import ichttt.mods.firstaid.client.network.FirstAidClientNetworking;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public class GuiTutorial
extends Screen {
    public GuiTutorial() {
        super((Component)Component.translatable((String)"firstaid.tutorial"));
    }

    protected void init() {
        this.addRenderableWidget((GuiEventListener)Button.builder((Component)Component.translatable((String)"gui.done"), button -> this.finishTutorial()).bounds(this.width / 2 - 100, this.height - 40, 200, 20).build());
    }

    private void finishTutorial() {
        FirstAidClientNetworking.sendToServer((CustomPacketPayload)new MessageClientRequest(MessageClientRequest.RequestType.TUTORIAL_COMPLETE));
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel((Player)this.minecraft.player);
        if (damageModel != null) {
            this.minecraft.setScreen((Screen)new GuiHealthScreen(damageModel));
        } else {
            this.minecraft.setScreen(null);
        }
    }

    public void drawOffsetString(GuiGraphics guiGraphics, String text, int yOffset) {
        int guiLeft = (this.width - 256) / 2;
        int guiTop = (this.height - 110) / 2;
        guiGraphics.drawString(this.font, text, guiLeft + 30, guiTop + yOffset, 0xFFFFFF);
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, -2013265920);
        int guiLeft = (this.width - 256) / 2;
        int guiTop = (this.height - 110) / 2;
        guiGraphics.fill(guiLeft, guiTop, guiLeft + 256, guiTop + 110, -872415232);
        HealthRenderUtils.blit(guiGraphics, HealthRenderUtils.SHOW_WOUNDS_LOCATION, 256, 256, guiLeft, guiTop, 0, 139, 256, 28);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, guiTop + 8, 0xFFFFFF);
        guiGraphics.drawWordWrap(this.font, (FormattedText)Component.literal((String)I18n.get((String)"firstaid.tutorial.welcome", (Object[])new Object[0])), guiLeft + 12, guiTop + 36, 232, 0xFFFFFF);
        guiGraphics.drawWordWrap(this.font, (FormattedText)Component.literal((String)I18n.get((String)"firstaid.tutorial.line1", (Object[])new Object[0])), guiLeft + 12, guiTop + 50, 232, 0xFFFFFF);
        guiGraphics.drawWordWrap(this.font, (FormattedText)Component.literal((String)I18n.get((String)"firstaid.tutorial.line2", (Object[])new Object[0])), guiLeft + 12, guiTop + 64, 232, 0xFFFFFF);
        guiGraphics.drawWordWrap(this.font, (FormattedText)Component.literal((String)I18n.get((String)"firstaid.tutorial.line8", (Object[])new Object[]{ClientHooks.SHOW_WOUNDS.getTranslatedKeyMessage().getString()})), guiLeft + 12, guiTop + 78, 232, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    public void onClose() {
        GuiHealthScreen.isOpen = false;
        super.onClose();
    }
}


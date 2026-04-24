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

package ichttt.mods.firstaid.client.tutorial;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.ClientHooks;
import ichttt.mods.firstaid.client.gui.GuiHealthScreen;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

public class GuiTutorial extends Screen {
    public GuiTutorial() {
        super(Component.translatable("firstaid.tutorial"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> finishTutorial())
                .bounds(width / 2 - 100, height - 40, 200, 20)
                .build());
    }

    private void finishTutorial() {
        FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.TUTORIAL_COMPLETE));
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(minecraft.player);
        if (damageModel != null) {
            minecraft.setScreen(new GuiHealthScreen(damageModel));
        } else {
            minecraft.setScreen(null);
        }
    }

    public void drawOffsetString(GuiGraphicsExtractor guiGraphics, String text, int yOffset) {
        int guiLeft = (width - GuiHealthScreen.xSize) / 2;
        int guiTop = (height - 110) / 2;
        guiGraphics.text(font, text, guiLeft + 30, guiTop + yOffset, 0xFFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, width, height, 0x88000000);
        int guiLeft = (width - GuiHealthScreen.xSize) / 2;
        int guiTop = (height - 110) / 2;
        guiGraphics.fill(guiLeft, guiTop, guiLeft + GuiHealthScreen.xSize, guiTop + 110, 0xCC000000);
        HealthRenderUtils.blit(guiGraphics, HealthRenderUtils.SHOW_WOUNDS_LOCATION, 256, 256, guiLeft, guiTop, 0, 139, GuiHealthScreen.xSize, 28);
        guiGraphics.centeredText(font, title, width / 2, guiTop + 8, 0xFFFFFF);
        guiGraphics.textWithWordWrap(font, Component.literal(I18n.get("firstaid.tutorial.welcome")), guiLeft + 12, guiTop + 36, GuiHealthScreen.xSize - 24, 0xFFFFFF);
        guiGraphics.textWithWordWrap(font, Component.literal(I18n.get("firstaid.tutorial.line1")), guiLeft + 12, guiTop + 50, GuiHealthScreen.xSize - 24, 0xFFFFFF);
        guiGraphics.textWithWordWrap(font, Component.literal(I18n.get("firstaid.tutorial.line2")), guiLeft + 12, guiTop + 64, GuiHealthScreen.xSize - 24, 0xFFFFFF);
        guiGraphics.textWithWordWrap(font, Component.literal(I18n.get("firstaid.tutorial.line8", ClientHooks.SHOW_WOUNDS.getTranslatedKeyMessage().getString())), guiLeft + 12, guiTop + 78, GuiHealthScreen.xSize - 24, 0xFFFFFF);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        GuiHealthScreen.isOpen = false;
        super.onClose();
    }
}

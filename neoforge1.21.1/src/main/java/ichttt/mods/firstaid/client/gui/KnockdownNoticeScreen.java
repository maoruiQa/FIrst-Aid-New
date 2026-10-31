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

package ichttt.mods.firstaid.client.gui;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.client.KnockdownNoticeClient;
import ichttt.mods.firstaid.common.KnockdownRules;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen notice describing the server's knockdown rules. It can only be dismissed with the confirm button, which
 * acknowledges the current settings to the server.
 */
public class KnockdownNoticeScreen extends Screen {
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFB8B8B8;
    private static final int ACCENT_COLOR = 0xFFFFC857;
    private static final int BACKGROUND_COLOR = 0xF0101216;
    private static final int PANEL_COLOR = 0xC0000000;
    private static final int MAX_TEXT_WIDTH = 360;

    private final Notice notice;
    @Nullable
    private final Screen parent;
    private boolean acknowledged;
    private boolean superseded;

    public record Notice(FirstAid.KnockdownMode mode, double coefficient) {
    }

    public KnockdownNoticeScreen(Notice notice, @Nullable Screen parent) {
        super(Component.translatable("firstaid.knockdown.notice.title"));
        this.notice = notice;
        this.parent = parent;
    }

    @Nullable
    public Screen parent() {
        return parent;
    }

    public Notice notice() {
        return notice;
    }

    /** Marks this screen as replaced by a newer notice, so closing it does not re-queue the old one. */
    public void supersede() {
        superseded = true;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("firstaid.knockdown.notice.confirm"), button -> confirm())
                .bounds(width / 2 - 100, height - 32, 200, 20)
                .build());
    }

    private void confirm() {
        acknowledged = true;
        FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.KNOCKDOWN_NOTICE_ACK));
        minecraft.setScreen(parent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (!acknowledged && !superseded) {
            KnockdownNoticeClient.requeue(notice);
        }
        super.removed();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, width, height, BACKGROUND_COLOR);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int textWidth = Math.min(MAX_TEXT_WIDTH, width - 32);
        int left = (width - textWidth) / 2;
        List<Component> paragraphs = bodyParagraphs();
        int tableHeight = notice.mode() == FirstAid.KnockdownMode.CHANCE ? font.lineHeight * 2 + 10 : 0;
        int contentHeight = font.lineHeight * 2 + 12 + tableHeight;
        for (Component paragraph : paragraphs) {
            contentHeight += font.split(paragraph, textWidth).size() * font.lineHeight + 6;
        }
        int top = Math.max(8, (height - 40 - contentHeight) / 2);
        guiGraphics.fill(left - 10, top - 8, left + textWidth + 10, Math.min(height - 40, top + contentHeight + 4), PANEL_COLOR);

        guiGraphics.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), width / 2, top, ACCENT_COLOR);
        int y = top + font.lineHeight + 4;
        guiGraphics.drawCenteredString(font, Component.translatable("firstaid.knockdown.notice.current", modeName(notice.mode())), width / 2, y, TEXT_COLOR);
        y += font.lineHeight + 8;
        for (int i = 0; i < paragraphs.size(); i++) {
            int color = i == paragraphs.size() - 1 ? MUTED_COLOR : TEXT_COLOR;
            for (FormattedCharSequence line : font.split(paragraphs.get(i), textWidth)) {
                guiGraphics.drawString(font, line, left, y, color);
                y += font.lineHeight;
            }
            y += 6;
            if (i == 0 && tableHeight > 0) {
                y = drawChanceTable(guiGraphics, left, y, textWidth) + 4;
            }
        }
    }

    private int drawChanceTable(GuiGraphics guiGraphics, int left, int y, int textWidth) {
        double[] ratios = KnockdownRules.sampleOverflowRatios();
        int labelWidth = Math.min(textWidth / 3, Math.max(font.width(Component.translatable("firstaid.knockdown.notice.table.overflow")),
                font.width(Component.translatable("firstaid.knockdown.notice.table.chance"))) + 8);
        int columnWidth = (textWidth - labelWidth) / ratios.length;
        guiGraphics.drawString(font, Component.translatable("firstaid.knockdown.notice.table.overflow"), left, y, MUTED_COLOR);
        guiGraphics.drawString(font, Component.translatable("firstaid.knockdown.notice.table.chance"), left, y + font.lineHeight + 2, MUTED_COLOR);
        for (int i = 0; i < ratios.length; i++) {
            int x = left + labelWidth + columnWidth * i + columnWidth / 2;
            guiGraphics.drawCenteredString(font, KnockdownRules.formatRatio(ratios[i]) + "x", x, y, TEXT_COLOR);
            guiGraphics.drawCenteredString(font, KnockdownRules.formatChance(KnockdownRules.chanceForOverflowRatio(ratios[i], notice.coefficient())), x, y + font.lineHeight + 2, ACCENT_COLOR);
        }
        return y + font.lineHeight * 2 + 2;
    }

    private List<Component> bodyParagraphs() {
        String mode = notice.mode().name().toLowerCase(Locale.ROOT);
        List<Component> paragraphs = new ArrayList<>();
        paragraphs.add(Component.translatable("firstaid.knockdown.notice." + mode + ".detail", KnockdownRules.formatCoefficient(notice.coefficient())));
        paragraphs.add(Component.translatable("firstaid.knockdown.notice.shared"));
        paragraphs.add(Component.translatable("firstaid.knockdown.notice.footer"));
        return paragraphs;
    }

    private static Component modeName(FirstAid.KnockdownMode mode) {
        return Component.translatable("firstaid.knockdown.mode." + mode.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GOLD);
    }
}

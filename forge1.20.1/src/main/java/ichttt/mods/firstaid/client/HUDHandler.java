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

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.client.gui.FlashStateManager;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.client.util.PlayerModelRenderer;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public class HUDHandler implements ResourceManagerReloadListener, IGuiOverlay {
    public static final HUDHandler INSTANCE = new HUDHandler();
    private static final int FADE_TIME = 30;
    private static final int PLAYER_MODEL_PADDING = 12;

    private final Map<EnumPlayerPart, String> translationMap = new EnumMap<>(EnumPlayerPart.class);
    private final FlashStateManager flashStateManager = new FlashStateManager();
    private int maxLength;
    public int ticker = -1;

    @Override
    public void onResourceManagerReload(@Nonnull ResourceManager resourceManager) {
        buildTranslationTable();
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (FirstAidConfig.CLIENT.overlayMode.get() == FirstAidConfig.Client.OverlayMode.OFF) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.isAlive() || minecraft.gameMode == null || minecraft.options.hideGui) {
            return;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(minecraft.player);
        if (damageModel == null || !FirstAid.isSynced) {
            return;
        }

        if (translationMap.isEmpty()) {
            buildTranslationTable();
        }

        int visibleTicks = FirstAidConfig.CLIENT.visibleDurationTicks.get();
        if (visibleTicks != -1) {
            visibleTicks += FADE_TIME;
        }
        boolean playerDead = damageModel.isDead(minecraft.player);
        for (AbstractDamageablePart damageablePart : damageModel) {
            if (HealthRenderUtils.healthChanged(damageablePart, playerDead)) {
                if (visibleTicks != -1) {
                    ticker = Math.max(ticker, visibleTicks);
                }
                if (FirstAidConfig.CLIENT.flash.get()) {
                    flashStateManager.setActive(Util.getMillis());
                }
            }
        }

        if (visibleTicks != -1 && ticker < 0) {
            return;
        }

        FirstAidConfig.Client.OverlayMode overlayMode = FirstAidConfig.CLIENT.overlayMode.get();
        boolean playerModel = overlayMode.isPlayerModel();

        if (minecraft.screen instanceof ChatScreen && FirstAidConfig.CLIENT.pos.get() == FirstAidConfig.Client.Position.BOTTOM_LEFT) {
            return;
        }
        if (minecraft.options.renderDebug && FirstAidConfig.CLIENT.pos.get() == FirstAidConfig.Client.Position.TOP_LEFT) {
            return;
        }

        int xOffset = FirstAidConfig.CLIENT.xOffset.get();
        int yOffset = FirstAidConfig.CLIENT.yOffset.get();
        switch (FirstAidConfig.CLIENT.pos.get()) {
            case TOP_RIGHT -> xOffset = minecraft.getWindow().getGuiScaledWidth() - xOffset - (playerModel ? 34 : damageModel.getMaxRenderSize() + maxLength);
            case BOTTOM_LEFT -> yOffset = minecraft.getWindow().getGuiScaledHeight() - yOffset - (playerModel ? 66 : 80);
            case BOTTOM_RIGHT -> {
                xOffset = minecraft.getWindow().getGuiScaledWidth() - xOffset - (playerModel ? 34 : damageModel.getMaxRenderSize() + maxLength);
                yOffset = minecraft.getWindow().getGuiScaledHeight() - yOffset - (playerModel ? 66 : 80);
            }
            default -> {
            }
        }

        if (playerModel) {
            switch (FirstAidConfig.CLIENT.pos.get()) {
                case TOP_LEFT -> {
                    xOffset = Math.max(xOffset, PLAYER_MODEL_PADDING);
                    yOffset = Math.max(yOffset, PLAYER_MODEL_PADDING);
                }
                case TOP_RIGHT -> {
                    xOffset -= PLAYER_MODEL_PADDING;
                    yOffset = Math.max(yOffset, PLAYER_MODEL_PADDING);
                }
                case BOTTOM_LEFT -> {
                    xOffset = Math.max(xOffset, PLAYER_MODEL_PADDING);
                    yOffset -= PLAYER_MODEL_PADDING;
                }
                case BOTTOM_RIGHT -> {
                    xOffset -= PLAYER_MODEL_PADDING;
                    yOffset -= PLAYER_MODEL_PADDING;
                }
            }
        }

        boolean enableAlphaBlend = visibleTicks != -1 && ticker < FADE_TIME;
        int alpha = enableAlphaBlend
                ? Mth.clamp((int) ((FADE_TIME - ticker) * 255.0F / (float) FADE_TIME), FirstAidConfig.CLIENT.alpha.get(), 250)
                : FirstAidConfig.CLIENT.alpha.get();

        PoseStack stack = guiGraphics.pose();
        stack.pushPose();
        stack.translate(xOffset, yOffset, 0F);
        if (enableAlphaBlend) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        }

        if (playerModel) {
            boolean fourColors = overlayMode == FirstAidConfig.Client.OverlayMode.PLAYER_MODEL_4_COLORS;
            PlayerModelRenderer.renderPlayerHealth(stack, damageModel, fourColors, guiGraphics, flashStateManager.update(Util.getMillis()), alpha, partialTick);
        } else {
            int xTranslation = maxLength + 6;
            for (AbstractDamageablePart part : damageModel) {
                guiGraphics.drawString(minecraft.font, translationMap.get(part.part), 0, 0, 0xFFFFFF - (alpha << 24 & -0xFFFFFF));
                if (overlayMode == FirstAidConfig.Client.OverlayMode.NUMBERS) {
                    HealthRenderUtils.drawHealthString(guiGraphics, minecraft.font, part, xTranslation, 0, false);
                } else {
                    HealthRenderUtils.drawHealth(guiGraphics, minecraft.font, part, xTranslation, 0, false);
                }
                stack.translate(0, 10F, 0F);
            }
        }

        if (enableAlphaBlend) {
            RenderSystem.disableBlend();
        }
        stack.popPose();
    }

    private synchronized void buildTranslationTable() {
        translationMap.clear();
        maxLength = 0;
        Minecraft minecraft = Minecraft.getInstance();
        for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
            String translated = I18n.get("firstaid.gui." + part.toString().toLowerCase(Locale.ENGLISH));
            maxLength = Math.max(maxLength, minecraft.font.width(translated));
            translationMap.put(part, translated);
        }
    }

    public void resetDebugState() {
    }
}

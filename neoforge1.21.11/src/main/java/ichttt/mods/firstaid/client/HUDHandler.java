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

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.client.gui.FlashStateManager;
import ichttt.mods.firstaid.client.gui.GuiHealthScreen;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.client.util.PlayerModelRenderer;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.gui.GuiLayer;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public class HUDHandler implements ResourceManagerReloadListener, GuiLayer {
    public static final HUDHandler INSTANCE = new HUDHandler();
    private static final int FADE_TIME = 30;
    private static final int PLAYER_MODEL_PADDING = 12;

    private final Map<EnumPlayerPart, String> translationMap = new EnumMap<>(EnumPlayerPart.class);
    private final FlashStateManager flashStateManager = new FlashStateManager();
    private boolean loggedRenderEntry;
    private boolean loggedMissingDamageModel;
    private boolean loggedDamageModelAvailable;
    private boolean loggedRenderPlacement;
    private boolean loggedSkippedByTicker;
    private boolean loggedSkippedByChat;
    private boolean loggedSkippedByDebugOverlay;
    private int maxLength;
    public int ticker = -1;

    @Override
    public void onResourceManagerReload(@Nonnull ResourceManager resourceManager) {
        buildTranslationTable();
    }

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (FirstAidConfig.CLIENT.overlayMode.get() == FirstAidConfig.Client.OverlayMode.OFF) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.isAlive() || minecraft.gameMode == null || minecraft.options.hideGui) {
            return;
        }

        if (!loggedRenderEntry) {
            FirstAid.LOGGER.info(
                    "HUDHandler.render entered, overlayMode={}, overlayPosition={}, visibleDurationTicks={}, alpha={}",
                    FirstAidConfig.CLIENT.overlayMode.get(),
                    FirstAidConfig.CLIENT.pos.get(),
                    FirstAidConfig.CLIENT.visibleDurationTicks.get(),
                    FirstAidConfig.CLIENT.alpha.get()
            );
            loggedRenderEntry = true;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(minecraft.player);
        if (damageModel == null || !FirstAid.isSynced) {
            if (damageModel == null && !loggedMissingDamageModel) {
                FirstAid.LOGGER.warn("HUDHandler.render could not obtain a damage model. isSynced={}", FirstAid.isSynced);
                loggedMissingDamageModel = true;
            }
            return;
        }

        if (!loggedDamageModelAvailable) {
            FirstAid.LOGGER.info(
                    "HUDHandler.render received damage model={}, head={}/{}, body={}/{}, unconsciousTicks={}",
                    damageModel.getClass().getSimpleName(),
                    damageModel.HEAD.currentHealth,
                    damageModel.HEAD.getMaxHealth(),
                    damageModel.BODY.currentHealth,
                    damageModel.BODY.getMaxHealth(),
                    damageModel.getUnconsciousTicks()
            );
            loggedDamageModelAvailable = true;
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
            if (!loggedSkippedByTicker) {
                FirstAid.LOGGER.info("HUDHandler.render skipped because ticker < 0 while visibleDurationTicks={}", visibleTicks - FADE_TIME);
                loggedSkippedByTicker = true;
            }
            return;
        }
        if (minecraft.screen instanceof ChatScreen && FirstAidConfig.CLIENT.pos.get() == FirstAidConfig.Client.Position.BOTTOM_LEFT) {
            if (!loggedSkippedByChat) {
                FirstAid.LOGGER.info("HUDHandler.render skipped because chat screen overlaps the bottom-left overlay");
                loggedSkippedByChat = true;
            }
            return;
        }
        if (minecraft.getDebugOverlay().showDebugScreen() && FirstAidConfig.CLIENT.pos.get() == FirstAidConfig.Client.Position.TOP_LEFT) {
            return;
        }
        int xOffset = FirstAidConfig.CLIENT.xOffset.get();
        int yOffset = FirstAidConfig.CLIENT.yOffset.get();
        FirstAidConfig.Client.OverlayMode overlayMode = FirstAidConfig.CLIENT.overlayMode.get();
        boolean playerModel = overlayMode.isPlayerModel();
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

        if (!loggedRenderPlacement) {
            FirstAid.LOGGER.info(
                    "HUDHandler.render resolved placement x={}, y={}, playerModel={}, gui={}x{}",
                    xOffset,
                    yOffset,
                    playerModel,
                    minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight()
            );
            loggedRenderPlacement = true;
        }

        if (playerModel) {
            PlayerModelRenderer.renderPlayerHealth(xOffset, yOffset, damageModel, overlayMode == FirstAidConfig.Client.OverlayMode.PLAYER_MODEL_4_COLORS, guiGraphics, flashStateManager.update(Util.getMillis()), FirstAidConfig.CLIENT.alpha.get(), deltaTracker.getGameTimeDeltaPartialTick(false));
        } else {
            int valueOffset = maxLength + 6;
            int y = yOffset;
            for (AbstractDamageablePart part : damageModel) {
                guiGraphics.drawString(minecraft.font, translationMap.get(part.part), xOffset, y, 0xFFFFFF);
                if (overlayMode == FirstAidConfig.Client.OverlayMode.NUMBERS) {
                    HealthRenderUtils.drawHealthString(guiGraphics, minecraft.font, part, xOffset + valueOffset, y, false);
                } else {
                    HealthRenderUtils.drawHealth(guiGraphics, minecraft.font, part, xOffset + valueOffset, y, false);
                }
                y += 10;
            }
        }
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
        loggedRenderEntry = false;
        loggedMissingDamageModel = false;
        loggedDamageModelAvailable = false;
        loggedRenderPlacement = false;
        loggedSkippedByTicker = false;
        loggedSkippedByChat = false;
        loggedSkippedByDebugOverlay = false;
    }
}

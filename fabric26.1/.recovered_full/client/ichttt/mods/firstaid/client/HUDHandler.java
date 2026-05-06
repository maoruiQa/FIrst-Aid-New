/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ichttt.mods.firstaid.FirstAid
 *  ichttt.mods.firstaid.FirstAidConfig
 *  ichttt.mods.firstaid.FirstAidConfig$Client$OverlayMode
 *  ichttt.mods.firstaid.FirstAidConfig$Client$Position
 *  ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart
 *  ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel
 *  ichttt.mods.firstaid.api.enums.EnumPlayerPart
 *  ichttt.mods.firstaid.common.util.CommonUtils
 *  javax.annotation.Nonnull
 *  net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
 *  net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
 *  net.minecraft.client.DeltaTracker
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.screens.ChatScreen
 *  net.minecraft.client.resources.language.I18n
 *  net.minecraft.resources.Identifier
 *  net.minecraft.server.packs.resources.ResourceManager
 *  net.minecraft.server.packs.resources.ResourceManagerReloadListener
 *  net.minecraft.util.Util
 *  net.minecraft.world.entity.player.Player
 */
package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.client.gui.FlashStateManager;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.client.util.PlayerModelRenderer;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

public class HUDHandler
implements IdentifiableResourceReloadListener,
ResourceManagerReloadListener,
HudRenderCallback {
    public static final HUDHandler INSTANCE = new HUDHandler();
    private static final int FADE_TIME = 30;
    private static final int PLAYER_MODEL_PADDING = 12;
    private final Map<EnumPlayerPart, String> translationMap = new EnumMap<EnumPlayerPart, String>(EnumPlayerPart.class);
    private final FlashStateManager flashStateManager = new FlashStateManager();
    private int maxLength;
    public int ticker = -1;

    public void onResourceManagerReload(@Nonnull ResourceManager resourceManager) {
        this.buildTranslationTable();
    }

    public Identifier getFabricId() {
        return Identifier.fromNamespaceAndPath((String)"firstaid", (String)"hud_handler");
    }

    public void onHudRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        int visibleTicks;
        if (FirstAidConfig.CLIENT.overlayMode.get() == FirstAidConfig.Client.OverlayMode.OFF) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.isAlive() || minecraft.gameMode == null || minecraft.options.hideGui) {
            return;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel((Player)minecraft.player);
        if (damageModel == null || !FirstAid.isSynced) {
            return;
        }
        if (this.translationMap.isEmpty()) {
            this.buildTranslationTable();
        }
        if ((visibleTicks = ((Integer)FirstAidConfig.CLIENT.visibleDurationTicks.get()).intValue()) != -1) {
            visibleTicks += 30;
        }
        boolean playerDead = damageModel.isDead((Player)minecraft.player);
        for (AbstractDamageablePart damageablePart : damageModel) {
            if (!HealthRenderUtils.healthChanged(damageablePart, playerDead)) continue;
            if (visibleTicks != -1) {
                this.ticker = Math.max(this.ticker, visibleTicks);
            }
            if (!((Boolean)FirstAidConfig.CLIENT.flash.get()).booleanValue()) continue;
            this.flashStateManager.setActive(Util.getMillis());
        }
        if (visibleTicks != -1 && this.ticker < 0) {
            return;
        }
        if (minecraft.screen instanceof ChatScreen && FirstAidConfig.CLIENT.pos.get() == FirstAidConfig.Client.Position.BOTTOM_LEFT) {
            return;
        }
        if (minecraft.getDebugOverlay().showDebugScreen() && FirstAidConfig.CLIENT.pos.get() == FirstAidConfig.Client.Position.TOP_LEFT) {
            return;
        }
        int xOffset = (Integer)FirstAidConfig.CLIENT.xOffset.get();
        int yOffset = (Integer)FirstAidConfig.CLIENT.yOffset.get();
        FirstAidConfig.Client.OverlayMode overlayMode = (FirstAidConfig.Client.OverlayMode)FirstAidConfig.CLIENT.overlayMode.get();
        boolean playerModel = overlayMode.isPlayerModel();
        switch ((FirstAidConfig.Client.Position)FirstAidConfig.CLIENT.pos.get()) {
            case TOP_RIGHT: {
                xOffset = minecraft.getWindow().getGuiScaledWidth() - xOffset - (playerModel ? 34 : damageModel.getMaxRenderSize() + this.maxLength);
                break;
            }
            case BOTTOM_LEFT: {
                yOffset = minecraft.getWindow().getGuiScaledHeight() - yOffset - (playerModel ? 66 : 80);
                break;
            }
            case BOTTOM_RIGHT: {
                xOffset = minecraft.getWindow().getGuiScaledWidth() - xOffset - (playerModel ? 34 : damageModel.getMaxRenderSize() + this.maxLength);
                yOffset = minecraft.getWindow().getGuiScaledHeight() - yOffset - (playerModel ? 66 : 80);
                break;
            }
        }
        if (playerModel) {
            switch ((FirstAidConfig.Client.Position)FirstAidConfig.CLIENT.pos.get()) {
                case TOP_LEFT: {
                    xOffset = Math.max(xOffset, 12);
                    yOffset = Math.max(yOffset, 12);
                    break;
                }
                case TOP_RIGHT: {
                    xOffset -= 12;
                    yOffset = Math.max(yOffset, 12);
                    break;
                }
                case BOTTOM_LEFT: {
                    xOffset = Math.max(xOffset, 12);
                    yOffset -= 12;
                    break;
                }
                case BOTTOM_RIGHT: {
                    xOffset -= 12;
                    yOffset -= 12;
                }
            }
        }
        if (playerModel) {
            PlayerModelRenderer.renderPlayerHealth(xOffset, yOffset, damageModel, overlayMode == FirstAidConfig.Client.OverlayMode.PLAYER_MODEL_4_COLORS, guiGraphics, this.flashStateManager.update(Util.getMillis()), ((Integer)FirstAidConfig.CLIENT.alpha.get()).intValue(), deltaTracker.getGameTimeDeltaPartialTick(false));
        } else {
            int valueOffset = this.maxLength + 6;
            int y = yOffset;
            for (AbstractDamageablePart part : damageModel) {
                guiGraphics.drawString(minecraft.font, this.translationMap.get(part.part), xOffset, y, 0xFFFFFF);
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
        this.translationMap.clear();
        this.maxLength = 0;
        Minecraft minecraft = Minecraft.getInstance();
        for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
            String translated = I18n.get((String)("firstaid.gui." + part.toString().toLowerCase(Locale.ENGLISH)), (Object[])new Object[0]);
            this.maxLength = Math.max(this.maxLength, minecraft.font.width(translated));
            this.translationMap.put(part, translated);
        }
    }
}


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
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.client.gui.FirstaidIngameGui;
import ichttt.mods.firstaid.client.gui.GuiHealthScreen;
import ichttt.mods.firstaid.client.util.EventCalendar;
import ichttt.mods.firstaid.client.util.PlayerModelRenderer;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public class ClientEventHandler {
    private static final Identifier PLAYER_HEALTH_LAYER = VanillaGuiLayers.PLAYER_HEALTH;
    private static final int GIVE_UP_HOLD_TICKS = 20 * 3;
    private static final SuppressionFeedbackController SUPPRESSION_FEEDBACK_CONTROLLER = new SuppressionFeedbackController();
    private static final ProjectileNearMissDetector PROJECTILE_NEAR_MISS_DETECTOR = new ProjectileNearMissDetector(SUPPRESSION_FEEDBACK_CONTROLLER);
    private static boolean loggedRenderGuiWithoutPlayer;
    private static boolean loggedRenderGuiWithPlayer;
    private static boolean loggedPlayerHealthLayerIntercept;
    private static int id;
    private static boolean showedCriticalPrompt;
    private static int giveUpHoldTicks;
    private static boolean giveUpTriggered;

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.connection == null) {
            resetGiveUpHoldState();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        SUPPRESSION_FEEDBACK_CONTROLLER.tick(mc);
        HealingSoundController.tick(mc);
        PROJECTILE_NEAR_MISS_DETECTOR.tick(mc);
        if (EventCalendar.isGuiFun()) {
            GuiHealthScreen.BED_ITEMSTACK.setDamageValue(id);
            if (mc.level.getGameTime() % 3 == 0) {
                id++;
            }
            if (id > 15) {
                id = 0;
            }
            GuiHealthScreen.tickFun();
            PlayerModelRenderer.tickFun();
        }
        if (HUDHandler.INSTANCE.ticker >= 0) {
            HUDHandler.INSTANCE.ticker--;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
        if (damageModel instanceof PlayerDamageModel playerDamageModel) {
            updateGiveUpHoldState(mc, playerDamageModel);
            boolean shouldShowCriticalPrompt = playerDamageModel.canGiveUp();
            if (shouldShowCriticalPrompt && !showedCriticalPrompt) {
                mc.player.displayClientMessage(Component.translatable("firstaid.gui.waiting_for_rescue").withStyle(ChatFormatting.RED), true);
                mc.player.displayClientMessage(Component.translatable("firstaid.gui.give_up_hint", ClientHooks.GIVE_UP.getTranslatedKeyMessage()).withStyle(ChatFormatting.RED), false);
            }
            showedCriticalPrompt = shouldShowCriticalPrompt;
        } else {
            showedCriticalPrompt = false;
            resetGiveUpHoldState();
        }
        if (damageModel != null && damageModel.getUnconsciousTicks() > 0 && mc.screen instanceof GuiHealthScreen) {
            mc.setScreen(null);
        }
    }

    @SubscribeEvent
    public static void onKeyPress(InputEvent.Key event) {
        if (event.getAction() != InputConstants.PRESS) {
            return;
        }

        KeyEvent keyEvent = event.getKeyEvent();
        Minecraft mc = Minecraft.getInstance();
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
        if (!ClientHooks.SHOW_WOUNDS.matches(keyEvent)) {
            return;
        }
        if (damageModel == null) {
            return;
        }
        if (damageModel.getUnconsciousTicks() > 0) {
            mc.player.displayClientMessage(Component.translatable("firstaid.gui.unconscious_hint").withStyle(ChatFormatting.RED), true);
            return;
        }
        FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.REQUEST_REFRESH));
        mc.setScreen(new GuiHealthScreen(damageModel));
    }

    @SubscribeEvent
    public static void onPlayerTurn(CalculatePlayerTurnEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && isUnconscious(mc.player)) {
            event.setMouseSensitivity(0.0D);
        }
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && isUnconscious(mc.player)) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        SUPPRESSION_FEEDBACK_CONTROLLER.applyCameraAngles(event);
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        SUPPRESSION_FEEDBACK_CONTROLLER.applyFov(event);
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SUPPRESSION_FEEDBACK_CONTROLLER.onPlaySound(event);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            if (!loggedRenderGuiWithoutPlayer) {
                FirstAid.LOGGER.info("ClientEventHandler.onRenderGui reached before player was initialized");
                loggedRenderGuiWithoutPlayer = true;
            }
        } else if (!loggedRenderGuiWithPlayer) {
            FirstAid.LOGGER.info(
                    "ClientEventHandler.onRenderGui reached for player={}, hideGui={}, overlayMode={}, vanillaHealthbarMode={}",
                    mc.player.getName().getString(),
                    mc.options.hideGui,
                    FirstAidConfig.CLIENT.overlayMode.get(),
                    FirstAidConfig.CLIENT.vanillaHealthBarMode.get()
            );
            loggedRenderGuiWithPlayer = true;
        }
        StatusEffectLayer.INSTANCE.render(event.getGuiGraphics(), event.getPartialTick());
        HUDHandler.INSTANCE.render(event.getGuiGraphics(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void preRender(RenderGuiLayerEvent.Pre event) {
        if (!PLAYER_HEALTH_LAYER.equals(event.getName())) {
            return;
        }

        if (!loggedPlayerHealthLayerIntercept) {
            FirstAid.LOGGER.info(
                    "ClientEventHandler.preRender intercepted PLAYER_HEALTH, vanillaHealthbarMode={}",
                    FirstAidConfig.CLIENT.vanillaHealthBarMode.get()
            );
            loggedPlayerHealthLayerIntercept = true;
        }

        FirstAidConfig.Client.VanillaHealthbarMode vanillaHealthBarMode = FirstAidConfig.CLIENT.vanillaHealthBarMode.get();
        if (vanillaHealthBarMode == FirstAidConfig.Client.VanillaHealthbarMode.NORMAL) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Gui gui = mc.gui;
        if (mc.gameMode == null || !mc.gameMode.canHurtPlayer() || mc.options.hideGui || mc.player == null) {
            return;
        }

        event.setCanceled(true);
        if (vanillaHealthBarMode == FirstAidConfig.Client.VanillaHealthbarMode.HIDE) {
            FirstaidIngameGui.reserveHealthBarSpace(gui, mc.player);
            return;
        }

        if (FirstAidConfig.SERVER.vanillaHealthCalculation.get() == FirstAidConfig.Server.VanillaHealthCalculationMode.AVERAGE_ALL) {
            FirstaidIngameGui.renderHealth(gui, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), event.getGuiGraphics());
        }
    }

    @SubscribeEvent
    public static void tooltipItems(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.is(RegistryObjects.MORPHINE.get())) {
            event.getToolTip().add(Component.translatable("firstaid.tooltip.morphine",
                    StringUtil.formatTickDuration(PlayerDamageModel.getMorphineActivationDelay(), 20F),
                    "3:30-4:30").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (stack.is(RegistryObjects.PAINKILLERS.get())) {
            event.getToolTip().add(Component.translatable("firstaid.tooltip.painkillers",
                    StringUtil.formatTickDuration(PlayerDamageModel.getPainkillerActivationDelay(), 20F),
                    "2:00").withStyle(ChatFormatting.GRAY));
            return;
        }

        if (stack.getItem() instanceof ItemHealing itemHealing && event.getEntity() != null) {
            AbstractPartHealer healer = itemHealing.createNewHealer(stack);
            if (healer != null) {
                event.getToolTip().add(Component.translatable("firstaid.tooltip.healer",
                        healer.maxHeal.getAsInt() / 2,
                        StringUtil.formatTickDuration(healer.ticksPerHeal.getAsInt(), 20F)));
            }
        }
    }

    @SubscribeEvent
    public static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        FirstAid.isSynced = false;
        HUDHandler.INSTANCE.ticker = -1;
        showedCriticalPrompt = false;
        resetGiveUpHoldState();
        HealingSoundController.clear();
        SUPPRESSION_FEEDBACK_CONTROLLER.clear();
        PROJECTILE_NEAR_MISS_DETECTOR.clear();
        loggedRenderGuiWithoutPlayer = false;
        loggedRenderGuiWithPlayer = false;
        loggedPlayerHealthLayerIntercept = false;
        HUDHandler.INSTANCE.resetDebugState();
        StatusEffectLayer.INSTANCE.resetDebugState();
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        resetGiveUpHoldState();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        MutableComponent message = Component.empty()
                .append(Component.literal("✚ ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal("[First Aid] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("Press ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(ClientHooks.SHOW_WOUNDS.getTranslatedKeyMessage().getString()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.UNDERLINE))
                .append(Component.literal(" to open the health UI ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("and inspect or treat body parts.").withStyle(ChatFormatting.WHITE));
        mc.player.displayClientMessage(message, false);
    }

    private static boolean isUnconscious(net.minecraft.world.entity.player.Player player) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        return damageModel instanceof PlayerDamageModel playerDamageModel
                ? playerDamageModel.isUnconscious()
                : damageModel != null && damageModel.getUnconsciousTicks() > 0;
    }

    public static float getGiveUpHoldProgress(float partialTick) {
        return Math.min(1.0F, getDisplayedGiveUpHoldTicks(partialTick) / GIVE_UP_HOLD_TICKS);
    }

    public static float getGiveUpHoldSeconds(float partialTick) {
        return getDisplayedGiveUpHoldTicks(partialTick) / 20.0F;
    }

    public static float getGiveUpHoldDurationSeconds() {
        return GIVE_UP_HOLD_TICKS / 20.0F;
    }

    public static SuppressionFeedbackController getSuppressionFeedbackController() {
        return SUPPRESSION_FEEDBACK_CONTROLLER;
    }

    private static float getDisplayedGiveUpHoldTicks(float partialTick) {
        if (giveUpHoldTicks <= 0) {
            return 0.0F;
        }
        float extraTicks = isGiveUpKeyHeld() && !giveUpTriggered ? Math.max(0.0F, partialTick) : 0.0F;
        return Math.min(GIVE_UP_HOLD_TICKS, giveUpHoldTicks + extraTicks);
    }

    private static void updateGiveUpHoldState(Minecraft mc, PlayerDamageModel playerDamageModel) {
        if (!playerDamageModel.canGiveUp() || mc.screen != null) {
            resetGiveUpHoldState();
            return;
        }
        if (!isGiveUpKeyHeld()) {
            resetGiveUpHoldState();
            return;
        }
        if (giveUpTriggered) {
            return;
        }
        giveUpHoldTicks = Math.min(GIVE_UP_HOLD_TICKS, giveUpHoldTicks + 1);
        if (giveUpHoldTicks >= GIVE_UP_HOLD_TICKS) {
            giveUpTriggered = true;
            FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.GIVE_UP));
        }
    }

    private static boolean isGiveUpKeyHeld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && ClientHooks.GIVE_UP.isDown();
    }

    private static void resetGiveUpHoldState() {
        giveUpHoldTicks = 0;
        giveUpTriggered = false;
    }
}


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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
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
    private static final int RESCUE_HOLD_TICKS = PlayerDamageModel.getRescueDurationTicks();
    private static final int DEFIBRILLATOR_RESCUE_HOLD_TICKS = PlayerDamageModel.getDefibrillatorRescueDurationTicks();
    private static final int EXECUTION_HOLD_TICKS = PlayerDamageModel.getExecutionDurationTicks();
    private static final int RESCUE_SOUND_DELAY_TICKS = 10;
    private static final int SYNC_RETRY_TICKS = 20;
    private static final SuppressionFeedbackController SUPPRESSION_FEEDBACK_CONTROLLER = new SuppressionFeedbackController();
    private static final ProjectileNearMissDetector PROJECTILE_NEAR_MISS_DETECTOR = new ProjectileNearMissDetector(SUPPRESSION_FEEDBACK_CONTROLLER);
    private static final HeartbeatSoundController HEARTBEAT_SOUND_CONTROLLER = new HeartbeatSoundController();
    private static boolean loggedRenderGuiWithoutPlayer;
    private static boolean loggedRenderGuiWithPlayer;
    private static boolean loggedPlayerHealthLayerIntercept;
    private static int id;
    private static boolean showedCriticalPrompt;
    private static int syncRetryTicks;
    private static int giveUpHoldTicks;
    private static boolean giveUpTriggered;
    private static int interactionHoldTicks;
    private static boolean interactionTriggered;
    private static boolean interactionSoundTriggered;
    private static InteractionPrompt interactionPrompt;

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.connection == null) {
            syncRetryTicks = 0;
            resetGiveUpHoldState();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        retryDamageModelSync(mc);
        SUPPRESSION_FEEDBACK_CONTROLLER.tick(mc);
        HEARTBEAT_SOUND_CONTROLLER.tick(mc);
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
            updateInteractionPromptState(mc);
            boolean shouldShowCriticalPrompt = playerDamageModel.canGiveUp();
            if (shouldShowCriticalPrompt && !showedCriticalPrompt) {
                mc.player.displayClientMessage(Component.translatable("firstaid.gui.waiting_for_rescue").withStyle(ChatFormatting.RED), true);
                mc.player.displayClientMessage(Component.translatable("firstaid.gui.give_up_hint", ClientHooks.GIVE_UP.getTranslatedKeyMessage()).withStyle(ChatFormatting.RED), false);
            }
            showedCriticalPrompt = shouldShowCriticalPrompt;
        } else {
            showedCriticalPrompt = false;
            resetGiveUpHoldState();
            resetInteractionPromptState();
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
        if (mc.screen != null) {
            return;
        }
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
            FirstaidIngameGui.renderHealth(gui, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), event.getGuiGraphics());
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
        if (stack.is(RegistryObjects.ADRENALINE_INJECTOR.get())) {
            event.getToolTip().add(Component.translatable("firstaid.tooltip.adrenaline_injector",
                    StringUtil.formatTickDuration(40, 20F),
                    StringUtil.formatTickDuration(PlayerDamageModel.getAdrenalineDuration(), 20F)).withStyle(ChatFormatting.GRAY));
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
        syncRetryTicks = 0;
        showedCriticalPrompt = false;
        resetGiveUpHoldState();
        resetInteractionPromptState();
        HealingSoundController.clear();
        HEARTBEAT_SOUND_CONTROLLER.clear();
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
        FirstAid.isSynced = false;
        syncRetryTicks = SYNC_RETRY_TICKS;
        resetGiveUpHoldState();
        resetInteractionPromptState();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.REQUEST_REFRESH));
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

    public static boolean hasInteractionPrompt() {
        return interactionPrompt != null;
    }

    public static Component getInteractionPromptTitle() {
        if (interactionPrompt == null) {
            return Component.empty();
        }
        return switch (interactionPrompt.type()) {
            case RESCUE -> Component.translatable("firstaid.gui.rescue_prompt_title", interactionPrompt.targetName()).withStyle(ChatFormatting.GREEN);
            case EXECUTE -> Component.translatable("firstaid.gui.execute_prompt_title", interactionPrompt.targetName()).withStyle(ChatFormatting.RED);
            default -> Component.translatable(
                    "firstaid.gui.rescue_execute_prompt_title",
                    getStyledRescueAction(),
                    getStyledExecutionAction(),
                    interactionPrompt.targetName()
            );
        };
    }

    public static Component getInteractionPromptDetail() {
        if (interactionPrompt == null) {
            return Component.empty();
        }
        return switch (interactionPrompt.type()) {
            case RESCUE -> Component.translatable("firstaid.gui.rescue_prompt_crouch", formatSingleDecimal(getInteractionHoldDurationSeconds())).withStyle(ChatFormatting.GREEN);
            case EXECUTE -> Component.translatable("firstaid.gui.execute_prompt_crouch", formatSingleDecimal(getInteractionHoldDurationSeconds())).withStyle(ChatFormatting.RED);
            default -> Component.translatable("firstaid.gui.rescue_execute_prompt_item", getStyledRescueAction(), getStyledExecutionAction());
        };
    }

    public static float getInteractionHoldProgress(float partialTick) {
        int holdDurationTicks = getCurrentInteractionHoldDurationTicks();
        return holdDurationTicks <= 0 ? 0.0F : Math.min(1.0F, getDisplayedInteractionHoldTicks(partialTick) / holdDurationTicks);
    }

    public static Component getInteractionPromptProgressText(float partialTick) {
        if (interactionPrompt == null) {
            return Component.empty();
        }
        return switch (interactionPrompt.type()) {
            case RESCUE -> Component.translatable(
                    "firstaid.gui.rescue_progress",
                    formatSingleDecimal(getInteractionHoldSeconds(partialTick)),
                    formatSingleDecimal(getInteractionHoldDurationSeconds())
            ).withStyle(ChatFormatting.GREEN);
            case EXECUTE -> Component.translatable(
                    "firstaid.gui.execute_progress",
                    formatSingleDecimal(getInteractionHoldSeconds(partialTick)),
                    formatSingleDecimal(getInteractionHoldDurationSeconds())
            ).withStyle(ChatFormatting.RED);
            default -> Component.empty();
        };
    }

    public static boolean isRescueInteractionPrompt() {
        return interactionPrompt != null && interactionPrompt.type() == InteractionType.RESCUE;
    }

    public static boolean isExecutionInteractionPrompt() {
        return interactionPrompt != null && interactionPrompt.type() == InteractionType.EXECUTE;
    }

    public static float getInteractionHoldSeconds(float partialTick) {
        return getDisplayedInteractionHoldTicks(partialTick) / 20.0F;
    }

    public static float getInteractionHoldDurationSeconds() {
        return getCurrentInteractionHoldDurationTicks() / 20.0F;
    }

    public static SuppressionFeedbackController getSuppressionFeedbackController() {
        return SUPPRESSION_FEEDBACK_CONTROLLER;
    }

    private static void retryDamageModelSync(Minecraft mc) {
        if (mc.player == null || mc.player.connection == null) {
            syncRetryTicks = 0;
        } else if (FirstAid.isSynced) {
            syncRetryTicks = 0;
        } else if (syncRetryTicks <= 0) {
            FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.REQUEST_REFRESH));
            syncRetryTicks = SYNC_RETRY_TICKS;
        } else {
            syncRetryTicks--;
        }
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

    private static void updateInteractionPromptState(Minecraft mc) {
        InteractionPrompt nextPrompt = findInteractionPrompt(mc);
        if (interactionPrompt == null
                || nextPrompt == null
                || interactionPrompt.targetId() != nextPrompt.targetId()
                || interactionPrompt.type() != nextPrompt.type()
                || interactionPrompt.hand() != nextPrompt.hand()
                || interactionPrompt.holdDurationTicks() != nextPrompt.holdDurationTicks()) {
            interactionHoldTicks = 0;
            interactionTriggered = false;
            interactionSoundTriggered = false;
        }
        interactionPrompt = nextPrompt;
        if (interactionPrompt == null || mc.screen != null || interactionPrompt.type() == InteractionType.INVALID_ITEM || !interactionPrompt.isSneaking()) {
            interactionHoldTicks = 0;
            interactionTriggered = false;
            interactionSoundTriggered = false;
            return;
        }
        int holdDurationTicks = getCurrentInteractionHoldDurationTicks();
        interactionHoldTicks = Math.min(holdDurationTicks, interactionHoldTicks + 1);
        if (interactionPrompt.type() == InteractionType.RESCUE
                && interactionHoldTicks >= RESCUE_SOUND_DELAY_TICKS
                && !interactionSoundTriggered
                && mc.player != null) {
            HealingSoundController.playRescueInteractionSound(mc.player.getItemInHand(interactionPrompt.hand()));
            interactionSoundTriggered = true;
        }
        if (interactionHoldTicks >= holdDurationTicks && !interactionTriggered) {
            interactionTriggered = true;
            if (interactionPrompt.type() == InteractionType.RESCUE) {
                FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.ATTEMPT_RESCUE));
            } else if (interactionPrompt.type() == InteractionType.EXECUTE) {
                FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.ATTEMPT_EXECUTION));
            }
        }
    }

    private static InteractionPrompt findInteractionPrompt(Minecraft mc) {
        if (mc.player == null || mc.level == null || !mc.player.isAlive() || isUnconscious(mc.player)) {
            return null;
        }

        Player closestTarget = findClosestRescueTarget(mc);
        if (closestTarget == null) {
            return null;
        }

        InteractionSelection selection = getInteractionSelection(mc.player);
        InteractionType type = selection == null ? InteractionType.INVALID_ITEM : selection.type();
        InteractionHand hand = selection == null ? InteractionHand.MAIN_HAND : selection.hand();
        int holdDurationTicks = selection == null ? 0 : getInteractionHoldDurationTicks(mc.player.getItemInHand(hand), type);
        return new InteractionPrompt(closestTarget.getId(), closestTarget.getDisplayName().copy(), type, mc.player.isCrouching(), hand, holdDurationTicks);
    }

    private static Player findClosestRescueTarget(Minecraft mc) {
        double maxDistanceSqr = PlayerDamageModel.getRescueRange() * PlayerDamageModel.getRescueRange();
        Player closestTarget = null;
        double closestDistanceSqr = maxDistanceSqr;
        for (Player candidate : mc.level.players()) {
            if (candidate == mc.player || !candidate.isAlive()) {
                continue;
            }
            AbstractPlayerDamageModel damageModel = CommonUtils.getExistingDamageModel(candidate);
            if (!(damageModel instanceof PlayerDamageModel playerDamageModel) || !playerDamageModel.canBeRescued()) {
                continue;
            }
            double distanceSqr = mc.player.distanceToSqr(candidate);
            if (distanceSqr > closestDistanceSqr) {
                continue;
            }
            closestDistanceSqr = distanceSqr;
            closestTarget = candidate;
        }
        return closestTarget;
    }

    private static InteractionSelection getInteractionSelection(Player player) {
        if (isRescueItem(player.getMainHandItem())) {
            return new InteractionSelection(InteractionType.RESCUE, InteractionHand.MAIN_HAND);
        }
        if (CommonUtils.isExecutionItem(player.getMainHandItem())) {
            return new InteractionSelection(InteractionType.EXECUTE, InteractionHand.MAIN_HAND);
        }
        if (isRescueItem(player.getOffhandItem())) {
            return new InteractionSelection(InteractionType.RESCUE, InteractionHand.OFF_HAND);
        }
        if (CommonUtils.isExecutionItem(player.getOffhandItem())) {
            return new InteractionSelection(InteractionType.EXECUTE, InteractionHand.OFF_HAND);
        }
        return null;
    }

    private static boolean isRescueItem(ItemStack stack) {
        return stack.is(RegistryObjects.BANDAGE.get()) || stack.is(RegistryObjects.PLASTER.get()) || isDefibrillator(stack);
    }

    private static boolean isDefibrillator(ItemStack stack) {
        return stack.is(RegistryObjects.DEFIBRILLATOR.get());
    }

    private static int getCurrentInteractionHoldDurationTicks() {
        if (interactionPrompt == null) {
            return 0;
        }
        return interactionPrompt.holdDurationTicks();
    }

    private static int getInteractionHoldDurationTicks(ItemStack stack, InteractionType type) {
        return switch (type) {
            case RESCUE -> isDefibrillator(stack) ? DEFIBRILLATOR_RESCUE_HOLD_TICKS : RESCUE_HOLD_TICKS;
            case EXECUTE -> EXECUTION_HOLD_TICKS;
            default -> 0;
        };
    }

    private static float getDisplayedInteractionHoldTicks(float partialTick) {
        if (interactionHoldTicks <= 0) {
            return 0.0F;
        }
        int holdDurationTicks = getCurrentInteractionHoldDurationTicks();
        float extraTicks = interactionPrompt != null
                && interactionPrompt.type() != InteractionType.INVALID_ITEM
                && interactionPrompt.isSneaking()
                && Minecraft.getInstance().screen == null
                ? Math.max(0.0F, partialTick)
                : 0.0F;
        return Math.min(holdDurationTicks, interactionHoldTicks + extraTicks);
    }

    private static String formatSingleDecimal(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static Component getStyledRescueAction() {
        return Component.translatable("firstaid.gui.prompt_action_rescue").withStyle(ChatFormatting.GREEN);
    }

    private static Component getStyledExecutionAction() {
        return Component.translatable("firstaid.gui.prompt_action_execute").withStyle(ChatFormatting.RED);
    }

    private static boolean isGiveUpKeyHeld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && ClientHooks.GIVE_UP.isDown();
    }

    private static void resetGiveUpHoldState() {
        giveUpHoldTicks = 0;
        giveUpTriggered = false;
    }

    private static void resetInteractionPromptState() {
        interactionHoldTicks = 0;
        interactionTriggered = false;
        interactionSoundTriggered = false;
        interactionPrompt = null;
    }

    private record InteractionPrompt(int targetId, Component targetName, InteractionType type, boolean isSneaking, InteractionHand hand, int holdDurationTicks) {
    }

    private record InteractionSelection(InteractionType type, InteractionHand hand) {
    }

    private enum InteractionType {
        RESCUE,
        EXECUTE,
        INVALID_ITEM
    }
}


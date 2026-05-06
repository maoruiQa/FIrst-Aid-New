/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ichttt.mods.firstaid.FirstAid
 *  ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer
 *  ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel
 *  ichttt.mods.firstaid.api.healing.ItemHealing
 *  ichttt.mods.firstaid.common.RegistryObjects
 *  ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel
 *  ichttt.mods.firstaid.common.network.MessageClientRequest
 *  ichttt.mods.firstaid.common.network.MessageClientRequest$RequestType
 *  ichttt.mods.firstaid.common.util.CommonUtils
 *  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 *  net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
 *  net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
 *  net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback
 *  net.minecraft.ChatFormatting
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.util.StringUtil
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.Item$TooltipContext
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.TooltipFlag
 */
package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.client.ClientHooks;
import ichttt.mods.firstaid.client.HUDHandler;
import ichttt.mods.firstaid.client.HealingSoundController;
import ichttt.mods.firstaid.client.ProjectileNearMissDetector;
import ichttt.mods.firstaid.client.SuppressionFeedbackController;
import ichttt.mods.firstaid.client.gui.GuiHealthScreen;
import ichttt.mods.firstaid.client.network.FirstAidClientNetworking;
import ichttt.mods.firstaid.client.util.EventCalendar;
import ichttt.mods.firstaid.client.util.PlayerModelRenderer;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class ClientEventHandler {
    private static final int GIVE_UP_HOLD_TICKS = 60;
    private static final int RESCUE_HOLD_TICKS = PlayerDamageModel.getRescueDurationTicks();
    private static final SuppressionFeedbackController SUPPRESSION_FEEDBACK_CONTROLLER = new SuppressionFeedbackController();
    private static final ProjectileNearMissDetector PROJECTILE_NEAR_MISS_DETECTOR = new ProjectileNearMissDetector(SUPPRESSION_FEEDBACK_CONTROLLER);
    private static int id;
    private static boolean showedCriticalPrompt;
    private static int giveUpHoldTicks;
    private static boolean giveUpTriggered;
    private static int rescueHoldTicks;
    private static boolean rescueTriggered;
    private static RescuePrompt rescuePrompt;

    private ClientEventHandler() {
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(ClientEventHandler::clientTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientEventHandler.onLogin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientEventHandler.onDisconnect());
        ItemTooltipCallback.EVENT.register(ClientEventHandler::tooltipItems);
        ClientPreAttackCallback.EVENT.register(ClientEventHandler::onPreAttack);
    }

    private static void clientTick(Minecraft mc) {
        AbstractPlayerDamageModel damageModel;
        if (mc.level == null || mc.player == null || mc.player.connection == null) {
            ClientEventHandler.resetGiveUpHoldState();
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
            if (mc.level.getGameTime() % 3L == 0L) {
                ++id;
            }
            if (id > 15) {
                id = 0;
            }
            GuiHealthScreen.tickFun();
            PlayerModelRenderer.tickFun();
        }
        if (HUDHandler.INSTANCE.ticker >= 0) {
            --HUDHandler.INSTANCE.ticker;
        }
        if ((damageModel = CommonUtils.getDamageModel((Player)mc.player)) instanceof PlayerDamageModel) {
            PlayerDamageModel playerDamageModel = (PlayerDamageModel)damageModel;
            ClientEventHandler.updateGiveUpHoldState(mc, playerDamageModel);
            ClientEventHandler.updateRescuePromptState(mc);
            boolean shouldShowCriticalPrompt = playerDamageModel.canGiveUp();
            if (shouldShowCriticalPrompt && !showedCriticalPrompt) {
                mc.player.displayClientMessage((Component)Component.translatable((String)"firstaid.gui.waiting_for_rescue").withStyle(ChatFormatting.RED), true);
                mc.player.displayClientMessage((Component)Component.translatable((String)"firstaid.gui.give_up_hint", (Object[])new Object[]{ClientHooks.GIVE_UP.getTranslatedKeyMessage()}).withStyle(ChatFormatting.RED), false);
            }
            showedCriticalPrompt = shouldShowCriticalPrompt;
        } else {
            showedCriticalPrompt = false;
            ClientEventHandler.resetGiveUpHoldState();
            ClientEventHandler.resetRescuePromptState();
        }
        if (damageModel != null && damageModel.getUnconsciousTicks() > 0 && mc.screen instanceof GuiHealthScreen) {
            mc.setScreen(null);
        }
        if (ClientHooks.SHOW_WOUNDS.consumeClick()) {
            ClientEventHandler.onShowWoundsPressed(mc);
        }
    }

    private static void onShowWoundsPressed(Minecraft mc) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel((Player)mc.player);
        if (damageModel == null) {
            return;
        }
        if (damageModel.getUnconsciousTicks() > 0) {
            mc.player.displayClientMessage((Component)Component.translatable((String)"firstaid.gui.unconscious_hint").withStyle(ChatFormatting.RED), true);
            return;
        }
        FirstAidClientNetworking.sendToServer((CustomPacketPayload)new MessageClientRequest(MessageClientRequest.RequestType.REQUEST_REFRESH));
        mc.setScreen((Screen)new GuiHealthScreen(damageModel));
    }

    private static boolean onPreAttack(Minecraft minecraft, LocalPlayer player, int clickCount) {
        return ClientEventHandler.isUnconscious((Player)player);
    }

    private static void tooltipItems(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
        ItemHealing itemHealing;
        AbstractPartHealer healer;
        if (stack.is((Item)RegistryObjects.MORPHINE.get())) {
            lines.add((Component)Component.translatable((String)"firstaid.tooltip.morphine", (Object[])new Object[]{StringUtil.formatTickDuration((int)PlayerDamageModel.getMorphineActivationDelay(), (float)20.0f), "3:30-4:30"}).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (stack.is((Item)RegistryObjects.PAINKILLERS.get())) {
            lines.add((Component)Component.translatable((String)"firstaid.tooltip.painkillers", (Object[])new Object[]{StringUtil.formatTickDuration((int)PlayerDamageModel.getPainkillerActivationDelay(), (float)20.0f), "2:00"}).withStyle(ChatFormatting.GRAY));
            return;
        }
        Item item = stack.getItem();
        if (item instanceof ItemHealing && (healer = (itemHealing = (ItemHealing)item).createNewHealer(stack)) != null) {
            lines.add((Component)Component.translatable((String)"firstaid.tooltip.healer", (Object[])new Object[]{healer.maxHeal.getAsInt() / 2, StringUtil.formatTickDuration((int)healer.ticksPerHeal.getAsInt(), (float)20.0f)}));
        }
    }

    private static void onDisconnect() {
        FirstAid.isSynced = false;
        HUDHandler.INSTANCE.ticker = -1;
        showedCriticalPrompt = false;
        ClientEventHandler.resetGiveUpHoldState();
        ClientEventHandler.resetRescuePromptState();
        HealingSoundController.clear();
        SUPPRESSION_FEEDBACK_CONTROLLER.clear();
        PROJECTILE_NEAR_MISS_DETECTOR.clear();
    }

    private static void onLogin(Minecraft mc) {
        ClientEventHandler.resetGiveUpHoldState();
        ClientEventHandler.resetRescuePromptState();
        if (mc.player == null) {
            return;
        }
        MutableComponent message = Component.empty().append((Component)Component.literal((String)"! ").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})).append((Component)Component.literal((String)"[First Aid] ").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})).append((Component)Component.literal((String)"Press ").withStyle(ChatFormatting.YELLOW)).append((Component)Component.literal((String)ClientHooks.SHOW_WOUNDS.getTranslatedKeyMessage().getString()).withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.UNDERLINE})).append((Component)Component.literal((String)" to open the health UI ").withStyle(ChatFormatting.AQUA)).append((Component)Component.literal((String)"and inspect or treat body parts.").withStyle(ChatFormatting.WHITE));
        mc.player.displayClientMessage((Component)message, false);
    }

    public static boolean isUnconscious(Player player) {
        boolean bl;
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel((Player)player);
        if (damageModel instanceof PlayerDamageModel) {
            PlayerDamageModel playerDamageModel = (PlayerDamageModel)damageModel;
            bl = playerDamageModel.isUnconscious();
        } else {
            bl = damageModel != null && damageModel.getUnconsciousTicks() > 0;
        }
        return bl;
    }

    public static float getGiveUpHoldProgress(float partialTick) {
        return Math.min(1.0f, ClientEventHandler.getDisplayedGiveUpHoldTicks(partialTick) / 60.0f);
    }

    public static float getGiveUpHoldSeconds(float partialTick) {
        return ClientEventHandler.getDisplayedGiveUpHoldTicks(partialTick) / 20.0f;
    }

    public static float getGiveUpHoldDurationSeconds() {
        return 3.0f;
    }

    public static boolean hasRescuePrompt() {
        return rescuePrompt != null;
    }

    public static Component getRescuePromptTitle() {
        return rescuePrompt == null ? Component.empty() : Component.translatable((String)"firstaid.gui.rescue_prompt_title", (Object[])new Object[]{rescuePrompt.targetName()});
    }

    public static Component getRescuePromptDetail() {
        if (rescuePrompt == null) {
            return Component.empty();
        }
        if (!rescuePrompt.hasValidItem()) {
            return Component.translatable((String)"firstaid.gui.rescue_prompt_item");
        }
        return Component.translatable((String)"firstaid.gui.rescue_prompt_crouch", (Object[])new Object[]{ClientEventHandler.formatSingleDecimal(ClientEventHandler.getRescueHoldDurationSeconds())});
    }

    public static float getRescueHoldProgress(float partialTick) {
        return Math.min(1.0f, ClientEventHandler.getDisplayedRescueHoldTicks(partialTick) / (float)RESCUE_HOLD_TICKS);
    }

    public static float getRescueHoldSeconds(float partialTick) {
        return ClientEventHandler.getDisplayedRescueHoldTicks(partialTick) / 20.0f;
    }

    public static float getRescueHoldDurationSeconds() {
        return (float)RESCUE_HOLD_TICKS / 20.0f;
    }

    public static SuppressionFeedbackController getSuppressionFeedbackController() {
        return SUPPRESSION_FEEDBACK_CONTROLLER;
    }

    private static float getDisplayedGiveUpHoldTicks(float partialTick) {
        if (giveUpHoldTicks <= 0) {
            return 0.0f;
        }
        float extraTicks = ClientEventHandler.isGiveUpKeyHeld() && !giveUpTriggered ? Math.max(0.0f, partialTick) : 0.0f;
        return Math.min(60.0f, (float)giveUpHoldTicks + extraTicks);
    }

    private static void updateGiveUpHoldState(Minecraft mc, PlayerDamageModel playerDamageModel) {
        if (!playerDamageModel.canGiveUp() || mc.screen != null) {
            ClientEventHandler.resetGiveUpHoldState();
            return;
        }
        if (!ClientEventHandler.isGiveUpKeyHeld()) {
            ClientEventHandler.resetGiveUpHoldState();
            return;
        }
        if (giveUpTriggered) {
            return;
        }
        if ((giveUpHoldTicks = Math.min(60, giveUpHoldTicks + 1)) >= 60) {
            giveUpTriggered = true;
            FirstAidClientNetworking.sendToServer((CustomPacketPayload)new MessageClientRequest(MessageClientRequest.RequestType.GIVE_UP));
        }
    }

    private static void updateRescuePromptState(Minecraft mc) {
        RescuePrompt nextPrompt = ClientEventHandler.findRescuePrompt(mc);
        if (rescuePrompt == null || nextPrompt == null || rescuePrompt.targetId() != nextPrompt.targetId()) {
            rescueHoldTicks = 0;
            rescueTriggered = false;
        }
        if ((rescuePrompt = nextPrompt) == null || mc.screen != null || !rescuePrompt.hasValidItem() || !rescuePrompt.isSneaking()) {
            rescueHoldTicks = 0;
            rescueTriggered = false;
            return;
        }
        if ((rescueHoldTicks = Math.min(RESCUE_HOLD_TICKS, rescueHoldTicks + 1)) >= RESCUE_HOLD_TICKS && !rescueTriggered) {
            rescueTriggered = true;
            FirstAidClientNetworking.sendToServer((CustomPacketPayload)new MessageClientRequest(MessageClientRequest.RequestType.ATTEMPT_RESCUE));
        }
    }

    private static RescuePrompt findRescuePrompt(Minecraft mc) {
        if (mc.player == null || mc.level == null || !mc.player.isAlive() || ClientEventHandler.isUnconscious((Player)mc.player)) {
            return null;
        }
        double maxDistanceSqr = PlayerDamageModel.getRescueRange() * PlayerDamageModel.getRescueRange();
        Player closestTarget = null;
        double closestDistanceSqr = maxDistanceSqr;
        for (Player candidate : mc.level.players()) {
            double distanceSqr;
            PlayerDamageModel playerDamageModel;
            AbstractPlayerDamageModel damageModel;
            if (candidate == mc.player || !candidate.isAlive() || !((damageModel = CommonUtils.getExistingDamageModel((Player)candidate)) instanceof PlayerDamageModel) || !(playerDamageModel = (PlayerDamageModel)damageModel).canBeRescued() || (distanceSqr = mc.player.distanceToSqr((Entity)candidate)) > closestDistanceSqr) continue;
            closestDistanceSqr = distanceSqr;
            closestTarget = candidate;
        }
        if (closestTarget == null) {
            return null;
        }
        return new RescuePrompt(closestTarget.getId(), (Component)closestTarget.getDisplayName().copy(), ClientEventHandler.getRescueHand((Player)mc.player) != null, mc.player.isCrouching());
    }

    private static InteractionHand getRescueHand(Player player) {
        if (ClientEventHandler.isRescueItem(player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (ClientEventHandler.isRescueItem(player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private static boolean isRescueItem(ItemStack stack) {
        return stack.is((Item)RegistryObjects.BANDAGE.get()) || stack.is((Item)RegistryObjects.PLASTER.get());
    }

    private static float getDisplayedRescueHoldTicks(float partialTick) {
        if (rescueHoldTicks <= 0) {
            return 0.0f;
        }
        float extraTicks = rescuePrompt != null && rescuePrompt.hasValidItem() && rescuePrompt.isSneaking() && Minecraft.getInstance().screen == null ? Math.max(0.0f, partialTick) : 0.0f;
        return Math.min((float)RESCUE_HOLD_TICKS, (float)rescueHoldTicks + extraTicks);
    }

    private static String formatSingleDecimal(float value) {
        return String.format(Locale.ROOT, "%.1f", Float.valueOf(value));
    }

    private static boolean isGiveUpKeyHeld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && ClientHooks.GIVE_UP.isDown();
    }

    private static void resetGiveUpHoldState() {
        giveUpHoldTicks = 0;
        giveUpTriggered = false;
    }

    private static void resetRescuePromptState() {
        rescueHoldTicks = 0;
        rescueTriggered = false;
        rescuePrompt = null;
    }

    private record RescuePrompt(int targetId, Component targetName, boolean hasValidItem, boolean isSneaking) {
    }
}


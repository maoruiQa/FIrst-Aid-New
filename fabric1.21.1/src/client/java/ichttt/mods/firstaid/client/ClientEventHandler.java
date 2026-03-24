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
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.client.gui.GuiHealthScreen;
import ichttt.mods.firstaid.client.network.FirstAidClientNetworking;
import ichttt.mods.firstaid.client.util.EventCalendar;
import ichttt.mods.firstaid.client.util.PlayerModelRenderer;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ClientEventHandler {
    private static final int GIVE_UP_HOLD_TICKS = 20 * 3;
    private static final SuppressionFeedbackController SUPPRESSION_FEEDBACK_CONTROLLER = new SuppressionFeedbackController();
    private static final ProjectileNearMissDetector PROJECTILE_NEAR_MISS_DETECTOR = new ProjectileNearMissDetector(SUPPRESSION_FEEDBACK_CONTROLLER);
    private static int id;
    private static boolean showedCriticalPrompt;
    private static int giveUpHoldTicks;
    private static boolean giveUpTriggered;

    private ClientEventHandler() {
    }

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(ClientEventHandler::clientTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onLogin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
        ItemTooltipCallback.EVENT.register(ClientEventHandler::tooltipItems);
        ClientPreAttackCallback.EVENT.register(ClientEventHandler::onPreAttack);
    }

    private static void clientTick(Minecraft mc) {
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

        if (ClientHooks.SHOW_WOUNDS.consumeClick()) {
            onShowWoundsPressed(mc);
        }
    }

    private static void onShowWoundsPressed(Minecraft mc) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
        if (damageModel == null) {
            return;
        }
        if (damageModel.getUnconsciousTicks() > 0) {
            mc.player.displayClientMessage(Component.translatable("firstaid.gui.unconscious_hint").withStyle(ChatFormatting.RED), true);
            return;
        }
        FirstAidClientNetworking.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.REQUEST_REFRESH));
        mc.setScreen(new GuiHealthScreen(damageModel));
    }

    private static boolean onPreAttack(Minecraft minecraft, net.minecraft.client.player.LocalPlayer player, int clickCount) {
        return isUnconscious(player);
    }

    private static void tooltipItems(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, net.minecraft.world.item.TooltipFlag flag, List<Component> lines) {
        if (stack.is(RegistryObjects.MORPHINE.get())) {
            lines.add(Component.translatable("firstaid.tooltip.morphine",
                    StringUtil.formatTickDuration(PlayerDamageModel.getMorphineActivationDelay(), 20F),
                    "3:30-4:30").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (stack.is(RegistryObjects.PAINKILLERS.get())) {
            lines.add(Component.translatable("firstaid.tooltip.painkillers",
                    StringUtil.formatTickDuration(PlayerDamageModel.getPainkillerActivationDelay(), 20F),
                    "2:00").withStyle(ChatFormatting.GRAY));
            return;
        }

        if (stack.getItem() instanceof ItemHealing itemHealing) {
            AbstractPartHealer healer = itemHealing.createNewHealer(stack);
            if (healer != null) {
                lines.add(Component.translatable("firstaid.tooltip.healer",
                        healer.maxHeal.getAsInt() / 2,
                        StringUtil.formatTickDuration(healer.ticksPerHeal.getAsInt(), 20F)));
            }
        }
    }

    private static void onDisconnect() {
        FirstAid.isSynced = false;
        HUDHandler.INSTANCE.ticker = -1;
        showedCriticalPrompt = false;
        resetGiveUpHoldState();
        HealingSoundController.clear();
        SUPPRESSION_FEEDBACK_CONTROLLER.clear();
        PROJECTILE_NEAR_MISS_DETECTOR.clear();
    }

    private static void onLogin(Minecraft mc) {
        resetGiveUpHoldState();
        if (mc.player == null) {
            return;
        }

        MutableComponent message = Component.empty()
                .append(Component.literal("! ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal("[First Aid] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("Press ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(ClientHooks.SHOW_WOUNDS.getTranslatedKeyMessage().getString()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.UNDERLINE))
                .append(Component.literal(" to open the health UI ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("and inspect or treat body parts.").withStyle(ChatFormatting.WHITE));
        mc.player.displayClientMessage(message, false);
    }

    public static boolean isUnconscious(net.minecraft.world.entity.player.Player player) {
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
            FirstAidClientNetworking.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.GIVE_UP));
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


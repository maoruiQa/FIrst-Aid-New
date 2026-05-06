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
import ichttt.mods.firstaid.common.network.MessageClientRequest.RequestType;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;

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
   private static ClientEventHandler.RescuePrompt rescuePrompt;

   private ClientEventHandler() {
   }

   public static void register() {
      ClientTickEvents.START_CLIENT_TICK.register(ClientEventHandler::clientTick);
      ClientPlayConnectionEvents.JOIN.register((Join)(handler, sender, client) -> onLogin(client));
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> onDisconnect());
      ItemTooltipCallback.EVENT.register(ClientEventHandler::tooltipItems);
      ClientPreAttackCallback.EVENT.register(ClientEventHandler::onPreAttack);
   }

   private static void clientTick(Minecraft mc) {
      if (mc.level == null || mc.player == null || mc.player.connection == null) {
         resetGiveUpHoldState();
      } else if (!mc.isPaused()) {
         SUPPRESSION_FEEDBACK_CONTROLLER.tick(mc);
         HealingSoundController.tick(mc);
         PROJECTILE_NEAR_MISS_DETECTOR.tick(mc);
         if (EventCalendar.isGuiFun()) {
            GuiHealthScreen.BED_ITEMSTACK.setDamageValue(id);
            if (mc.level.getGameTime() % 3L == 0L) {
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
            updateRescuePromptState(mc);
            boolean shouldShowCriticalPrompt = playerDamageModel.canGiveUp();
            if (shouldShowCriticalPrompt && !showedCriticalPrompt) {
               mc.player.displayClientMessage(Component.translatable("firstaid.gui.waiting_for_rescue").withStyle(ChatFormatting.RED), true);
               mc.player
                  .displayClientMessage(
                     Component.translatable("firstaid.gui.give_up_hint", new Object[]{ClientHooks.GIVE_UP.getTranslatedKeyMessage()})
                        .withStyle(ChatFormatting.RED),
                     false
                  );
            }

            showedCriticalPrompt = shouldShowCriticalPrompt;
         } else {
            showedCriticalPrompt = false;
            resetGiveUpHoldState();
            resetRescuePromptState();
         }

         if (damageModel != null && damageModel.getUnconsciousTicks() > 0 && mc.screen instanceof GuiHealthScreen) {
            mc.setScreen(null);
         }

         if (ClientHooks.SHOW_WOUNDS.consumeClick()) {
            onShowWoundsPressed(mc);
         }
      }
   }

   private static void onShowWoundsPressed(Minecraft mc) {
      AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
      if (damageModel != null) {
         if (damageModel.getUnconsciousTicks() > 0) {
            mc.player.displayClientMessage(Component.translatable("firstaid.gui.unconscious_hint").withStyle(ChatFormatting.RED), true);
         } else {
            FirstAidClientNetworking.sendToServer(new MessageClientRequest(RequestType.REQUEST_REFRESH));
            mc.setScreen(new GuiHealthScreen(damageModel));
         }
      }
   }

   private static boolean onPreAttack(Minecraft minecraft, LocalPlayer player, int clickCount) {
      return isUnconscious(player);
   }

   private static void tooltipItems(ItemStack stack, TooltipContext context, TooltipFlag flag, List<Component> lines) {
      if (stack.is((Item)RegistryObjects.MORPHINE.get())) {
         lines.add(
            Component.translatable(
                  "firstaid.tooltip.morphine", new Object[]{StringUtil.formatTickDuration(PlayerDamageModel.getMorphineActivationDelay(), 20.0F), "3:30-4:30"}
               )
               .withStyle(ChatFormatting.GRAY)
         );
      } else if (stack.is((Item)RegistryObjects.PAINKILLERS.get())) {
         lines.add(
            Component.translatable(
                  "firstaid.tooltip.painkillers", new Object[]{StringUtil.formatTickDuration(PlayerDamageModel.getPainkillerActivationDelay(), 20.0F), "2:00"}
               )
               .withStyle(ChatFormatting.GRAY)
         );
      } else {
         if (stack.getItem() instanceof ItemHealing itemHealing) {
            AbstractPartHealer healer = itemHealing.createNewHealer(stack);
            if (healer != null) {
               lines.add(
                  Component.translatable(
                     "firstaid.tooltip.healer",
                     new Object[]{healer.maxHeal.getAsInt() / 2, StringUtil.formatTickDuration(healer.ticksPerHeal.getAsInt(), 20.0F)}
                  )
               );
            }
         }
      }
   }

   private static void onDisconnect() {
      FirstAid.isSynced = false;
      HUDHandler.INSTANCE.ticker = -1;
      showedCriticalPrompt = false;
      resetGiveUpHoldState();
      resetRescuePromptState();
      HealingSoundController.clear();
      SUPPRESSION_FEEDBACK_CONTROLLER.clear();
      PROJECTILE_NEAR_MISS_DETECTOR.clear();
   }

   private static void onLogin(Minecraft mc) {
      resetGiveUpHoldState();
      resetRescuePromptState();
      if (mc.player != null) {
         MutableComponent message = Component.empty()
            .append(Component.literal("! ").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}))
            .append(Component.literal("[First Aid] ").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}))
            .append(Component.literal("Press ").withStyle(ChatFormatting.YELLOW))
            .append(
               Component.literal(ClientHooks.SHOW_WOUNDS.getTranslatedKeyMessage().getString())
                  .withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.UNDERLINE})
            )
            .append(Component.literal(" to open the health UI ").withStyle(ChatFormatting.AQUA))
            .append(Component.literal("and inspect or treat body parts.").withStyle(ChatFormatting.WHITE));
         mc.player.displayClientMessage(message, false);
      }
   }

   public static boolean isUnconscious(Player player) {
      AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
      return damageModel instanceof PlayerDamageModel playerDamageModel
         ? playerDamageModel.isUnconscious()
         : damageModel != null && damageModel.getUnconsciousTicks() > 0;
   }

   public static float getGiveUpHoldProgress(float partialTick) {
      return Math.min(1.0F, getDisplayedGiveUpHoldTicks(partialTick) / 60.0F);
   }

   public static float getGiveUpHoldSeconds(float partialTick) {
      return getDisplayedGiveUpHoldTicks(partialTick) / 20.0F;
   }

   public static float getGiveUpHoldDurationSeconds() {
      return 3.0F;
   }

   public static boolean hasRescuePrompt() {
      return rescuePrompt != null;
   }

   public static Component getRescuePromptTitle() {
      return rescuePrompt == null ? Component.empty() : Component.translatable("firstaid.gui.rescue_prompt_title", new Object[]{rescuePrompt.targetName()});
   }

   public static Component getRescuePromptDetail() {
      if (rescuePrompt == null) {
         return Component.empty();
      } else {
         return !rescuePrompt.hasValidItem()
            ? Component.translatable("firstaid.gui.rescue_prompt_item")
            : Component.translatable("firstaid.gui.rescue_prompt_crouch", new Object[]{formatSingleDecimal(getRescueHoldDurationSeconds())});
      }
   }

   public static float getRescueHoldProgress(float partialTick) {
      return Math.min(1.0F, getDisplayedRescueHoldTicks(partialTick) / RESCUE_HOLD_TICKS);
   }

   public static float getRescueHoldSeconds(float partialTick) {
      return getDisplayedRescueHoldTicks(partialTick) / 20.0F;
   }

   public static float getRescueHoldDurationSeconds() {
      return RESCUE_HOLD_TICKS / 20.0F;
   }

   public static SuppressionFeedbackController getSuppressionFeedbackController() {
      return SUPPRESSION_FEEDBACK_CONTROLLER;
   }

   private static float getDisplayedGiveUpHoldTicks(float partialTick) {
      if (giveUpHoldTicks <= 0) {
         return 0.0F;
      } else {
         float extraTicks = isGiveUpKeyHeld() && !giveUpTriggered ? Math.max(0.0F, partialTick) : 0.0F;
         return Math.min(60.0F, giveUpHoldTicks + extraTicks);
      }
   }

   private static void updateGiveUpHoldState(Minecraft mc, PlayerDamageModel playerDamageModel) {
      if (!playerDamageModel.canGiveUp() || mc.screen != null) {
         resetGiveUpHoldState();
      } else if (!isGiveUpKeyHeld()) {
         resetGiveUpHoldState();
      } else if (!giveUpTriggered) {
         giveUpHoldTicks = Math.min(60, giveUpHoldTicks + 1);
         if (giveUpHoldTicks >= 60) {
            giveUpTriggered = true;
            FirstAidClientNetworking.sendToServer(new MessageClientRequest(RequestType.GIVE_UP));
         }
      }
   }

   private static void updateRescuePromptState(Minecraft mc) {
      ClientEventHandler.RescuePrompt nextPrompt = findRescuePrompt(mc);
      if (rescuePrompt == null || nextPrompt == null || rescuePrompt.targetId() != nextPrompt.targetId()) {
         rescueHoldTicks = 0;
         rescueTriggered = false;
      }

      rescuePrompt = nextPrompt;
      if (rescuePrompt != null && mc.screen == null && rescuePrompt.hasValidItem() && rescuePrompt.isSneaking()) {
         rescueHoldTicks = Math.min(RESCUE_HOLD_TICKS, rescueHoldTicks + 1);
         if (rescueHoldTicks >= RESCUE_HOLD_TICKS && !rescueTriggered) {
            rescueTriggered = true;
            FirstAidClientNetworking.sendToServer(new MessageClientRequest(RequestType.ATTEMPT_RESCUE));
         }
      } else {
         rescueHoldTicks = 0;
         rescueTriggered = false;
      }
   }

   private static ClientEventHandler.RescuePrompt findRescuePrompt(Minecraft mc) {
      if (mc.player != null && mc.level != null && mc.player.isAlive() && !isUnconscious(mc.player)) {
         double maxDistanceSqr = PlayerDamageModel.getRescueRange() * PlayerDamageModel.getRescueRange();
         Player closestTarget = null;
         double closestDistanceSqr = maxDistanceSqr;

         for (Player candidate : mc.level.players()) {
            if (candidate != mc.player
               && candidate.isAlive()
               && CommonUtils.getExistingDamageModel(candidate) instanceof PlayerDamageModel playerDamageModel
               && playerDamageModel.canBeRescued()) {
               double distanceSqr = mc.player.distanceToSqr(candidate);
               if (!(distanceSqr > closestDistanceSqr)) {
                  closestDistanceSqr = distanceSqr;
                  closestTarget = candidate;
               }
            }
         }

         return closestTarget == null
            ? null
            : new ClientEventHandler.RescuePrompt(
               closestTarget.getId(), closestTarget.getDisplayName().copy(), getRescueHand(mc.player) != null, mc.player.isCrouching()
            );
      } else {
         return null;
      }
   }

   private static InteractionHand getRescueHand(Player player) {
      if (isRescueItem(player.getMainHandItem())) {
         return InteractionHand.MAIN_HAND;
      } else {
         return isRescueItem(player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
      }
   }

   private static boolean isRescueItem(ItemStack stack) {
      return stack.is((Item)RegistryObjects.BANDAGE.get()) || stack.is((Item)RegistryObjects.PLASTER.get());
   }

   private static float getDisplayedRescueHoldTicks(float partialTick) {
      if (rescueHoldTicks <= 0) {
         return 0.0F;
      } else {
         float extraTicks = rescuePrompt != null && rescuePrompt.hasValidItem() && rescuePrompt.isSneaking() && Minecraft.getInstance().screen == null
            ? Math.max(0.0F, partialTick)
            : 0.0F;
         return Math.min((float)RESCUE_HOLD_TICKS, rescueHoldTicks + extraTicks);
      }
   }

   private static String formatSingleDecimal(float value) {
      return String.format(Locale.ROOT, "%.1f", value);
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

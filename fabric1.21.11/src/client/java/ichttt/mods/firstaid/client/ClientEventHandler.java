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
   private static final int DEFIBRILLATOR_RESCUE_HOLD_TICKS = PlayerDamageModel.getDefibrillatorRescueDurationTicks();
   private static final int EXECUTION_HOLD_TICKS = PlayerDamageModel.getExecutionDurationTicks();
   private static final int RESCUE_SOUND_DELAY_TICKS = 10;
   private static final int SYNC_RETRY_TICKS = 20;
   private static final SuppressionFeedbackController SUPPRESSION_FEEDBACK_CONTROLLER = new SuppressionFeedbackController();
   private static final ProjectileNearMissDetector PROJECTILE_NEAR_MISS_DETECTOR = new ProjectileNearMissDetector(SUPPRESSION_FEEDBACK_CONTROLLER);
   private static final HeartbeatSoundController HEARTBEAT_SOUND_CONTROLLER = new HeartbeatSoundController();
   private static int id;
   private static boolean showedCriticalPrompt;
   private static int syncRetryTicks;
   private static int giveUpHoldTicks;
   private static boolean giveUpTriggered;
   private static int interactionHoldTicks;
   private static boolean interactionTriggered;
   private static boolean interactionSoundTriggered;
   private static ClientEventHandler.InteractionPrompt interactionPrompt;

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
         retryDamageModelSync(mc);
         SUPPRESSION_FEEDBACK_CONTROLLER.tick(mc);
         HEARTBEAT_SOUND_CONTROLLER.tick(mc);
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
            updateInteractionPromptState(mc);
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
            resetInteractionPromptState();
         }

         if (damageModel != null && damageModel.getUnconsciousTicks() > 0 && mc.screen instanceof GuiHealthScreen) {
            mc.setScreen(null);
         }

         if (ClientHooks.SHOW_WOUNDS.consumeClick() && mc.screen == null) {
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
      } else if (stack.is((Item)RegistryObjects.ADRENALINE_INJECTOR.get())) {
         lines.add(
            Component.translatable(
                  "firstaid.tooltip.adrenaline_injector",
                  new Object[]{StringUtil.formatTickDuration(40, 20.0F), StringUtil.formatTickDuration(PlayerDamageModel.getAdrenalineDuration(), 20.0F)}
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
      syncRetryTicks = 0;
      showedCriticalPrompt = false;
      resetGiveUpHoldState();
      resetInteractionPromptState();
      HealingSoundController.clear();
      HEARTBEAT_SOUND_CONTROLLER.clear();
      SUPPRESSION_FEEDBACK_CONTROLLER.clear();
      PROJECTILE_NEAR_MISS_DETECTOR.clear();
   }

   private static void onLogin(Minecraft mc) {
      FirstAid.isSynced = false;
      syncRetryTicks = 20;
      resetGiveUpHoldState();
      resetInteractionPromptState();
      if (mc.player != null) {
         FirstAidClientNetworking.sendToServer(new MessageClientRequest(RequestType.REQUEST_REFRESH));
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

   public static boolean hasInteractionPrompt() {
      return interactionPrompt != null;
   }

   public static Component getInteractionPromptTitle() {
      if (interactionPrompt == null) {
         return Component.empty();
      } else {
         return switch (interactionPrompt.type()) {
            case RESCUE -> Component.translatable("firstaid.gui.rescue_prompt_title", new Object[]{interactionPrompt.targetName()}).withStyle(ChatFormatting.GREEN);
            case EXECUTE -> Component.translatable("firstaid.gui.execute_prompt_title", new Object[]{interactionPrompt.targetName()}).withStyle(ChatFormatting.RED);
            default -> Component.translatable(
               "firstaid.gui.rescue_execute_prompt_title",
               new Object[]{getStyledRescueAction(), getStyledExecutionAction(), interactionPrompt.targetName()}
            );
         };
      }
   }

   public static Component getInteractionPromptDetail() {
      if (interactionPrompt == null) {
         return Component.empty();
      } else {
         return switch (interactionPrompt.type()) {
            case RESCUE -> Component.translatable("firstaid.gui.rescue_prompt_crouch", new Object[]{formatSingleDecimal(getInteractionHoldDurationSeconds())})
               .withStyle(ChatFormatting.GREEN);
            case EXECUTE -> Component.translatable("firstaid.gui.execute_prompt_crouch", new Object[]{formatSingleDecimal(getInteractionHoldDurationSeconds())})
               .withStyle(ChatFormatting.RED);
            default -> Component.translatable("firstaid.gui.rescue_execute_prompt_item", new Object[]{getStyledRescueAction(), getStyledExecutionAction()});
         };
      }
   }

   public static float getInteractionHoldProgress(float partialTick) {
      int holdDurationTicks = getCurrentInteractionHoldDurationTicks();
      return holdDurationTicks <= 0 ? 0.0F : Math.min(1.0F, getDisplayedInteractionHoldTicks(partialTick) / holdDurationTicks);
   }

   public static Component getInteractionPromptProgressText(float partialTick) {
      if (interactionPrompt == null) {
         return Component.empty();
      } else {
         return switch (interactionPrompt.type()) {
            case RESCUE -> Component.translatable(
               "firstaid.gui.rescue_progress", new Object[]{formatSingleDecimal(getInteractionHoldSeconds(partialTick)), formatSingleDecimal(getInteractionHoldDurationSeconds())}
            ).withStyle(ChatFormatting.GREEN);
            case EXECUTE -> Component.translatable(
               "firstaid.gui.execute_progress", new Object[]{formatSingleDecimal(getInteractionHoldSeconds(partialTick)), formatSingleDecimal(getInteractionHoldDurationSeconds())}
            ).withStyle(ChatFormatting.RED);
            default -> Component.empty();
         };
      }
   }

   public static boolean isRescueInteractionPrompt() {
      return interactionPrompt != null && interactionPrompt.type() == ClientEventHandler.InteractionType.RESCUE;
   }

   public static boolean isExecutionInteractionPrompt() {
      return interactionPrompt != null && interactionPrompt.type() == ClientEventHandler.InteractionType.EXECUTE;
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
         FirstAidClientNetworking.sendToServer(new MessageClientRequest(RequestType.REQUEST_REFRESH));
         syncRetryTicks = 20;
      } else {
         syncRetryTicks--;
      }
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

   private static void updateInteractionPromptState(Minecraft mc) {
      ClientEventHandler.InteractionPrompt nextPrompt = findInteractionPrompt(mc);
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
      if (interactionPrompt != null && mc.screen == null && interactionPrompt.type() != ClientEventHandler.InteractionType.INVALID_ITEM && interactionPrompt.isSneaking()) {
         int holdDurationTicks = getCurrentInteractionHoldDurationTicks();
         interactionHoldTicks = Math.min(holdDurationTicks, interactionHoldTicks + 1);
         if (interactionPrompt.type() == ClientEventHandler.InteractionType.RESCUE
            && interactionHoldTicks >= RESCUE_SOUND_DELAY_TICKS
            && !interactionSoundTriggered
            && mc.player != null) {
            HealingSoundController.playRescueInteractionSound(mc.player.getItemInHand(interactionPrompt.hand()));
            interactionSoundTriggered = true;
         }
         if (interactionHoldTicks >= holdDurationTicks && !interactionTriggered) {
            interactionTriggered = true;
            if (interactionPrompt.type() == ClientEventHandler.InteractionType.RESCUE) {
               FirstAidClientNetworking.sendToServer(new MessageClientRequest(RequestType.ATTEMPT_RESCUE));
            } else if (interactionPrompt.type() == ClientEventHandler.InteractionType.EXECUTE) {
               FirstAidClientNetworking.sendToServer(new MessageClientRequest(RequestType.ATTEMPT_EXECUTION));
            }
         }
      } else {
         interactionHoldTicks = 0;
         interactionTriggered = false;
         interactionSoundTriggered = false;
      }
   }

   private static ClientEventHandler.InteractionPrompt findInteractionPrompt(Minecraft mc) {
      if (mc.player != null && mc.level != null && mc.player.isAlive() && !isUnconscious(mc.player)) {
         Player closestTarget = findClosestRescueTarget(mc);
         if (closestTarget == null) {
            return null;
         } else {
            ClientEventHandler.InteractionSelection selection = getInteractionSelection(mc.player);
            ClientEventHandler.InteractionType type = selection == null ? ClientEventHandler.InteractionType.INVALID_ITEM : selection.type();
            InteractionHand hand = selection == null ? InteractionHand.MAIN_HAND : selection.hand();
            int holdDurationTicks = selection == null ? 0 : getInteractionHoldDurationTicks(mc.player.getItemInHand(hand), type);
            return new ClientEventHandler.InteractionPrompt(
               closestTarget.getId(), closestTarget.getDisplayName().copy(), type, mc.player.isCrouching(), hand, holdDurationTicks
            );
         }
      } else {
         return null;
      }
   }

   private static Player findClosestRescueTarget(Minecraft mc) {
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

      return closestTarget;
   }

   private static ClientEventHandler.InteractionSelection getInteractionSelection(Player player) {
      if (isRescueItem(player.getMainHandItem())) {
         return new ClientEventHandler.InteractionSelection(ClientEventHandler.InteractionType.RESCUE, InteractionHand.MAIN_HAND);
      } else if (CommonUtils.isExecutionItem(player.getMainHandItem())) {
         return new ClientEventHandler.InteractionSelection(ClientEventHandler.InteractionType.EXECUTE, InteractionHand.MAIN_HAND);
      } else if (isRescueItem(player.getOffhandItem())) {
         return new ClientEventHandler.InteractionSelection(ClientEventHandler.InteractionType.RESCUE, InteractionHand.OFF_HAND);
      } else {
         return CommonUtils.isExecutionItem(player.getOffhandItem())
            ? new ClientEventHandler.InteractionSelection(ClientEventHandler.InteractionType.EXECUTE, InteractionHand.OFF_HAND)
            : null;
      }
   }

   private static boolean isRescueItem(ItemStack stack) {
      return stack.is((Item)RegistryObjects.BANDAGE.get()) || stack.is((Item)RegistryObjects.PLASTER.get()) || isDefibrillator(stack);
   }

   private static boolean isDefibrillator(ItemStack stack) {
      return stack.is((Item)RegistryObjects.DEFIBRILLATOR.get());
   }

   private static int getCurrentInteractionHoldDurationTicks() {
      if (interactionPrompt == null) {
         return 0;
      } else {
         return interactionPrompt.holdDurationTicks();
      }
   }

   private static int getInteractionHoldDurationTicks(ItemStack stack, ClientEventHandler.InteractionType type) {
      return switch (type) {
         case RESCUE -> isDefibrillator(stack) ? DEFIBRILLATOR_RESCUE_HOLD_TICKS : RESCUE_HOLD_TICKS;
         case EXECUTE -> EXECUTION_HOLD_TICKS;
         default -> 0;
      };
   }

   private static float getDisplayedInteractionHoldTicks(float partialTick) {
      if (interactionHoldTicks <= 0) {
         return 0.0F;
      } else {
         int holdDurationTicks = getCurrentInteractionHoldDurationTicks();
         float extraTicks = interactionPrompt != null
            && interactionPrompt.type() != ClientEventHandler.InteractionType.INVALID_ITEM
            && interactionPrompt.isSneaking()
            && Minecraft.getInstance().screen == null
            ? Math.max(0.0F, partialTick)
            : 0.0F;
         return Math.min((float)holdDurationTicks, interactionHoldTicks + extraTicks);
      }
   }

   private static String formatSingleDecimal(float value) {
      return String.format(Locale.ROOT, "%.1f", value);
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

   private record InteractionPrompt(
      int targetId, Component targetName, ClientEventHandler.InteractionType type, boolean isSneaking, InteractionHand hand, int holdDurationTicks
   ) {
   }

   private record InteractionSelection(ClientEventHandler.InteractionType type, InteractionHand hand) {
   }

   private static enum InteractionType {
      RESCUE,
      EXECUTE,
      INVALID_ITEM;
   }
}

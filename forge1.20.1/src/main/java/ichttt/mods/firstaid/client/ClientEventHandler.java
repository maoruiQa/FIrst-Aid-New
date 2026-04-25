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

import com.mojang.blaze3d.vertex.PoseStack;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.api.medicine.ItemMedicine;
import ichttt.mods.firstaid.client.gui.FirstaidIngameGui;
import ichttt.mods.firstaid.client.gui.GuiHealthScreen;
import ichttt.mods.firstaid.client.tutorial.GuiTutorial;
import ichttt.mods.firstaid.client.util.EventCalendar;
import ichttt.mods.firstaid.client.util.PlayerModelRenderer;
import ichttt.mods.firstaid.common.AABBAlignedBoundingBox;
import ichttt.mods.firstaid.common.CapProvider;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.network.MessageApplyHealingItem;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.util.ArmorUtils;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.PlayerSizeHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ClientEventHandler {
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##");
    private static final NamedGuiOverlay PLAYER_HEALTH_LAYER = VanillaGuiOverlay.PLAYER_HEALTH.type();
    private static final int GIVE_UP_HOLD_TICKS = 20 * 3;
    private static final int RESCUE_HOLD_TICKS = PlayerDamageModel.getRescueDurationTicks();
    private static final int DEFIBRILLATOR_RESCUE_HOLD_TICKS = PlayerDamageModel.getDefibrillatorRescueDurationTicks();
    private static final int EXECUTION_HOLD_TICKS = PlayerDamageModel.getExecutionDurationTicks();
    private static final int RESCUE_SOUND_DELAY_TICKS = 10;
    private static final int SYNC_RETRY_TICKS = 20;
    private static final SuppressionFeedbackController SUPPRESSION_FEEDBACK_CONTROLLER = new SuppressionFeedbackController();
    private static final ProjectileNearMissDetector PROJECTILE_NEAR_MISS_DETECTOR = new ProjectileNearMissDetector(SUPPRESSION_FEEDBACK_CONTROLLER);
    private static final HeartbeatSoundController HEARTBEAT_SOUND_CONTROLLER = new HeartbeatSoundController();

    private static int id;
    private static int syncRetryTicks;
    private static boolean showedCriticalPrompt;
    private static int giveUpHoldTicks;
    private static boolean giveUpTriggered;
    private static int interactionHoldTicks;
    private static boolean interactionTriggered;
    private static boolean interactionSoundTriggered;
    private static InteractionPrompt interactionPrompt;
    private static PendingHealingSelection pendingHealingSelection;
    private static boolean requireUseReleaseBeforeHealingSelection;

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.connection == null) {
            syncRetryTicks = 0;
            showedCriticalPrompt = false;
            resetGiveUpHoldState();
            resetInteractionPromptState();
            clearPendingHealingSelection();
            requireUseReleaseBeforeHealingSelection = false;
            ItemMedicine.clearAllClientReuseBlocks();
            SUPPRESSION_FEEDBACK_CONTROLLER.clear();
            HEARTBEAT_SOUND_CONTROLLER.clear();
            PROJECTILE_NEAR_MISS_DETECTOR.clear();
            HealingSoundController.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }

        if (!mc.options.keyUse.isDown()) {
            requireUseReleaseBeforeHealingSelection = false;
            ItemMedicine.clearClientReuseBlock(mc.player);
        }
        if (shouldBlockHealingReuse(mc) && mc.player.isUsingItem()) {
            mc.player.stopUsingItem();
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
            updatePendingHealingState(mc, damageModel);
            updateInteractionPromptState(mc);
            updateMedicineUseFeedback(mc);
            boolean shouldShowCriticalPrompt = playerDamageModel.canGiveUp();
            if (shouldShowCriticalPrompt && !showedCriticalPrompt) {
                mc.player.displayClientMessage(Component.translatable("firstaid.gui.waiting_for_rescue").withStyle(ChatFormatting.RED), true);
                mc.player.displayClientMessage(Component.translatable("firstaid.gui.give_up_hint", ClientHooks.GIVE_UP.getTranslatedKeyMessage()).withStyle(ChatFormatting.RED), false);
            }
            showedCriticalPrompt = shouldShowCriticalPrompt;
            if (playerDamageModel.getUnconsciousTicks() > 0 && mc.screen instanceof GuiHealthScreen) {
                mc.setScreen(null);
            }
        } else {
            showedCriticalPrompt = false;
            resetGiveUpHoldState();
            resetInteractionPromptState();
            clearPendingHealingSelection();
        }

        if (damageModel instanceof PlayerDamageModel playerDamageModel && playerDamageModel.getUnconsciousTicks() > 0 && mc.screen instanceof GuiHealthScreen) {
            mc.setScreen(null);
        }
    }

    @SubscribeEvent
    public static void onKeyPress(InputEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
        if (!ClientHooks.SHOW_WOUNDS.consumeClick()) {
            return;
        }
        if (damageModel == null) {
            return;
        }
        if (isUnconscious(mc.player)) {
            mc.player.displayClientMessage(Component.translatable("firstaid.gui.unconscious_hint").withStyle(ChatFormatting.RED), true);
            return;
        }

        FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.Type.REQUEST_REFRESH));
        if (!damageModel.hasTutorial) {
            damageModel.hasTutorial = true;
            CapProvider.tutorialDone.add(mc.player.getName().getString());
            mc.setScreen(new GuiTutorial());
        } else {
            mc.setScreen(new GuiHealthScreen(damageModel));
        }
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && isUnconscious(mc.player)) {
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (event.isUseItem() && shouldBlockHealingReuse(mc)) {
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
    public static void preRender(RenderGuiOverlayEvent.Pre event) {
        if (!PLAYER_HEALTH_LAYER.equals(event.getOverlay())) {
            return;
        }

        FirstAidConfig.Client.VanillaHealthbarMode vanillaHealthBarMode = FirstAidConfig.CLIENT.vanillaHealthBarMode.get();
        if (vanillaHealthBarMode == FirstAidConfig.Client.VanillaHealthbarMode.NORMAL) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || !mc.gameMode.canHurtPlayer() || mc.options.hideGui || mc.player == null) {
            return;
        }

        event.setCanceled(true);
        if (vanillaHealthBarMode == FirstAidConfig.Client.VanillaHealthbarMode.HIDE) {
            FirstaidIngameGui.renderHealth((net.minecraftforge.client.gui.overlay.ForgeGui) mc.gui, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), event.getGuiGraphics());
            return;
        }

        if (FirstAidConfig.SERVER.vanillaHealthCalculation.get() == FirstAidConfig.Server.VanillaHealthCalculationMode.AVERAGE_ALL) {
            FirstaidIngameGui.renderHealth((net.minecraftforge.client.gui.overlay.ForgeGui) mc.gui, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), event.getGuiGraphics());
        }
    }

    @SubscribeEvent
    public static void onLivingRender(RenderLivingEvent.Post<Player, PlayerModel<Player>> event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            EntityRenderDispatcher renderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            if (renderDispatcher.shouldRenderHitBoxes()) {
                PoseStack poseStack = event.getPoseStack();
                poseStack.pushPose();
                if (entity.isCrouching()) {
                    poseStack.translate(0D, 0.125D, 0D);
                }
                AABB aabb = entity.getBoundingBox();

                Collection<AABBAlignedBoundingBox> allBoxes = PlayerSizeHelper.getBoxes(entity).values();
                float r = 0.25F;
                float g = 1.0F;
                float b = 1.0F;

                for (AABBAlignedBoundingBox box : allBoxes) {
                    AABB bbox = box.createAABB(aabb);
                    LevelRenderer.renderLineBox(poseStack, event.getMultiBufferSource().getBuffer(RenderType.lines()), bbox.inflate(0.02D).move(-entity.getX(), -entity.getY(), -entity.getZ()), r, g, b, 1.0F);
                    r += 0.25F;
                    g += 0.5F;
                    b += 0.1F;

                    r %= 1.0F;
                    g %= 1.0F;
                    b %= 1.0F;
                }
                poseStack.popPose();
            }
        }
    }

    private static Component makeArmorMsg(double value) {
        return Component.translatable("firstaid.specificarmor", FORMAT.format(value)).withStyle(ChatFormatting.BLUE);
    }

    private static Component makeToughnessMsg(double value) {
        return Component.translatable("firstaid.specifictoughness", FORMAT.format(value)).withStyle(ChatFormatting.BLUE);
    }

    private static <T> void replaceOrAppend(List<T> list, T search, T replace) {
        int index = list.indexOf(search);
        if (FirstAidConfig.CLIENT.armorTooltipMode.get() == FirstAidConfig.Client.TooltipMode.REPLACE && index >= 0) {
            list.set(index, replace);
        } else {
            list.add(replace);
        }
    }

    @SubscribeEvent
    public static void tooltipItems(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();
        if (item == RegistryObjects.MORPHINE.get()) {
            event.getToolTip().add(Component.translatable("firstaid.tooltip.morphine",
                    StringUtil.formatTickDuration(PlayerDamageModel.getMorphineActivationDelay()),
                    "7:30-8:30").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (item == RegistryObjects.PAINKILLERS.get()) {
            event.getToolTip().add(Component.translatable("firstaid.tooltip.painkillers",
                    StringUtil.formatTickDuration(PlayerDamageModel.getPainkillerActivationDelay()),
                    "4:00").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (item == RegistryObjects.ADRENALINE_INJECTOR.get()) {
            event.getToolTip().add(Component.translatable("firstaid.tooltip.adrenaline_injector",
                    StringUtil.formatTickDuration(40),
                    StringUtil.formatTickDuration(PlayerDamageModel.getAdrenalineDuration())).withStyle(ChatFormatting.GRAY));
            return;
        }

        if (FirstAidConfig.CLIENT.armorTooltipMode.get() != FirstAidConfig.Client.TooltipMode.NONE) {
            if (item instanceof ArmorItem armor) {
                List<Component> tooltip = event.getToolTip();

                double normalArmor = ArmorUtils.getArmor(stack, armor.getEquipmentSlot());
                double totalArmor = ArmorUtils.applyArmorModifier(armor.getEquipmentSlot(), normalArmor);
                if (totalArmor > 0D) {
                    Component original = Component.translatable("attribute.modifier.plus.0", FORMAT.format(normalArmor), Component.translatable("attribute.name.generic.armor")).withStyle(ChatFormatting.BLUE);
                    replaceOrAppend(tooltip, original, makeArmorMsg(totalArmor));
                }

                double normalToughness = ArmorUtils.getArmorToughness(stack, armor.getEquipmentSlot());
                double totalToughness = ArmorUtils.applyToughnessModifier(armor.getEquipmentSlot(), normalToughness);
                if (totalToughness > 0D) {
                    Component original = Component.translatable("attribute.modifier.plus.0", FORMAT.format(normalToughness), Component.translatable("attribute.name.generic.armor_toughness")).withStyle(ChatFormatting.BLUE);
                    replaceOrAppend(tooltip, original, makeToughnessMsg(totalToughness));
                }
            }
        }
        if (item instanceof PotionItem) {
            List<MobEffectInstance> list = PotionUtils.getMobEffects(stack);
            if (!list.isEmpty()) {
                for (MobEffectInstance potionEffect : list) {
                    if (potionEffect.getEffect() == MobEffects.DAMAGE_RESISTANCE) {
                        MobEffect potion = potionEffect.getEffect();
                        Map<Attribute, AttributeModifier> map = potion.getAttributeModifiers();

                        if (!map.isEmpty()) {
                            for (Map.Entry<Attribute, AttributeModifier> entry : map.entrySet()) {
                                AttributeModifier falseModifier = entry.getValue();
                                AttributeModifier realModifier = new AttributeModifier(falseModifier.getName(), potion.getAttributeModifierValue(potionEffect.getAmplifier(), falseModifier), falseModifier.getOperation());

                                double d1;
                                if (realModifier.getOperation() != AttributeModifier.Operation.MULTIPLY_BASE && realModifier.getOperation() != AttributeModifier.Operation.MULTIPLY_TOTAL) {
                                    d1 = realModifier.getAmount();
                                } else {
                                    d1 = realModifier.getAmount() * 100.0D;
                                }

                                Component raw = Component.translatable("attribute.modifier.plus." + realModifier.getOperation().toValue(), ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(d1), Component.translatable(entry.getKey().getDescriptionId())).withStyle(ChatFormatting.BLUE);
                                List<Component> toolTip = event.getToolTip();
                                int index = toolTip.indexOf(raw);
                                if (index != -1) {
                                    Component replacement = Component.translatable("attribute.modifier.plus." + realModifier.getOperation().toValue(), ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(d1 * ((float) FirstAidConfig.SERVER.resistanceReductionPercentPerLevel.get() / 20F)), Component.translatable(entry.getKey().getDescriptionId())).withStyle(ChatFormatting.BLUE);
                                    toolTip.set(index, replacement);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (stack.getItem() instanceof ItemHealing itemHealing) {
            AbstractPartHealer healer = itemHealing.createNewHealer(stack);
            if (healer != null && event.getEntity() != null) {
                event.getToolTip().add(Component.translatable("firstaid.tooltip.healer", healer.maxHeal.getAsInt() / 2, StringUtil.formatTickDuration(healer.ticksPerHeal.getAsInt())));
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
        clearPendingHealingSelection();
        ItemMedicine.clearAllClientReuseBlocks();
        HealingSoundController.clear();
        HEARTBEAT_SOUND_CONTROLLER.clear();
        SUPPRESSION_FEEDBACK_CONTROLLER.clear();
        PROJECTILE_NEAR_MISS_DETECTOR.clear();
        HUDHandler.INSTANCE.resetDebugState();
        StatusEffectLayer.INSTANCE.resetDebugState();
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        FirstAid.isSynced = false;
        syncRetryTicks = SYNC_RETRY_TICKS;
        showedCriticalPrompt = false;
        resetGiveUpHoldState();
        resetInteractionPromptState();
        clearPendingHealingSelection();
        ItemMedicine.clearAllClientReuseBlocks();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.Type.REQUEST_REFRESH));
        MutableComponent message = Component.empty()
                .append(Component.literal("[First Aid] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("Press ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(ClientHooks.SHOW_WOUNDS.getTranslatedKeyMessage().getString()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.UNDERLINE))
                .append(Component.literal(" to open the health UI and inspect or treat body parts.").withStyle(ChatFormatting.WHITE));
        mc.player.sendSystemMessage(message);
    }

    private static boolean isUnconscious(Player player) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        return damageModel instanceof PlayerDamageModel playerDamageModel
                ? playerDamageModel.isUnconscious()
                : false;
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

    public static boolean selectPendingHealing(EnumPlayerPart part, InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
        if (damageModel == null) {
            return false;
        }
        ItemStack stack = mc.player.getItemInHand(hand);
        if (!(stack.getItem() instanceof ItemHealing itemHealing)) {
            return false;
        }
        var damageablePart = damageModel.getFromEnum(part);
        if (damageablePart.activeHealer != null || CommonUtils.isPartVisuallyFull(damageablePart)) {
            return false;
        }
        ItemStack selectedStack = stack.copy();
        selectedStack.setCount(1);
        pendingHealingSelection = new PendingHealingSelection(
                part,
                hand,
                selectedStack,
                getHealingHoldDurationTicks(itemHealing, stack),
                0,
                false
        );
        return true;
    }

    public static boolean hasValidPendingHealingSelection(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (pendingHealingSelection == null || mc.player == null || pendingHealingSelection.hand() != hand) {
            return false;
        }
        ItemStack currentStack = mc.player.getItemInHand(hand);
        return !currentStack.isEmpty()
                && currentStack.getItem() instanceof ItemHealing
                && ItemStack.isSameItemSameTags(currentStack, pendingHealingSelection.selectedStack());
    }

    public static boolean canOpenHealingScreen(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (requireUseReleaseBeforeHealingSelection || mc.player == null || isUnconscious(mc.player)) {
            return false;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
        if (damageModel == null) {
            return false;
        }
        ItemStack stack = mc.player.getItemInHand(hand);
        if (!(stack.getItem() instanceof ItemHealing)) {
            return false;
        }
        for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
            var damageablePart = damageModel.getFromEnum(part);
            if (damageablePart.activeHealer == null && !CommonUtils.isPartVisuallyFull(damageablePart)) {
                return true;
            }
        }
        return false;
    }

    public static Component getInteractionPromptTitle() {
        if (interactionPrompt == null) {
            return Component.empty();
        }
        return switch (interactionPrompt.type()) {
            case HEAL_SELF -> Component.translatable("firstaid.gui.healing_prompt_title").withStyle(ChatFormatting.AQUA);
            case USE_MEDICINE_SELF -> Component.translatable("firstaid.gui.medicine_prompt_title").withStyle(ChatFormatting.AQUA);
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
            case HEAL_SELF -> Component.translatable(
                    "firstaid.gui.healing_prompt_detail",
                    Component.translatable("key.use").withStyle(ChatFormatting.GOLD),
                    Component.translatable("firstaid.gui." + pendingHealingSelection.part().toString().toLowerCase(Locale.ROOT)),
                    formatSingleDecimal(getInteractionHoldDurationSeconds())
            ).withStyle(ChatFormatting.AQUA);
            case USE_MEDICINE_SELF -> Component.translatable(
                    "firstaid.gui.medicine_prompt_detail",
                    Component.translatable("key.use").withStyle(ChatFormatting.GOLD),
                    interactionPrompt.targetName(),
                    formatSingleDecimal(getInteractionHoldDurationSeconds())
            ).withStyle(ChatFormatting.AQUA);
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
            case HEAL_SELF -> Component.translatable(
                    "firstaid.gui.healing_progress",
                    formatSingleDecimal(getInteractionHoldSeconds(partialTick)),
                    formatSingleDecimal(getInteractionHoldDurationSeconds())
            ).withStyle(ChatFormatting.AQUA);
            case USE_MEDICINE_SELF -> Component.translatable(
                    "firstaid.gui.medicine_progress",
                    interactionPrompt.targetName(),
                    formatSingleDecimal(getInteractionHoldSeconds(partialTick)),
                    formatSingleDecimal(getInteractionHoldDurationSeconds())
            ).withStyle(ChatFormatting.AQUA);
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

    public static boolean isExecutionInteractionPrompt() {
        return interactionPrompt != null && interactionPrompt.type() == InteractionType.EXECUTE;
    }

    public static boolean isHealingInteractionPrompt() {
        return interactionPrompt != null
                && (interactionPrompt.type() == InteractionType.HEAL_SELF || interactionPrompt.type() == InteractionType.USE_MEDICINE_SELF);
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
            FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.Type.REQUEST_REFRESH));
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
        } else if (!isGiveUpKeyHeld()) {
            resetGiveUpHoldState();
        } else if (!giveUpTriggered) {
            giveUpHoldTicks = Math.min(GIVE_UP_HOLD_TICKS, giveUpHoldTicks + 1);
            if (giveUpHoldTicks >= GIVE_UP_HOLD_TICKS) {
                giveUpTriggered = true;
                FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.Type.GIVE_UP));
            }
        }
    }

    private static void updateInteractionPromptState(Minecraft mc) {
        InteractionPrompt nextPrompt = findInteractionPrompt(mc);
        if (nextPrompt != null && (nextPrompt.type() == InteractionType.HEAL_SELF || nextPrompt.type() == InteractionType.USE_MEDICINE_SELF)) {
            resetInteractionPromptHoldState();
            interactionPrompt = nextPrompt;
            return;
        }

        if (interactionPrompt == null
                || nextPrompt == null
                || interactionPrompt.targetId() != nextPrompt.targetId()
                || interactionPrompt.type() != nextPrompt.type()
                || interactionPrompt.hand() != nextPrompt.hand()
                || interactionPrompt.holdDurationTicks() != nextPrompt.holdDurationTicks()) {
            resetInteractionPromptHoldState();
        }

        interactionPrompt = nextPrompt;
        if (interactionPrompt != null && mc.screen == null && interactionPrompt.type() != InteractionType.INVALID_ITEM && interactionPrompt.isSneaking()) {
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
                    FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.Type.ATTEMPT_RESCUE));
                } else if (interactionPrompt.type() == InteractionType.EXECUTE) {
                    FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.Type.ATTEMPT_EXECUTION));
                }
            }
        } else {
            resetInteractionPromptHoldState();
        }
    }

    private static InteractionPrompt findInteractionPrompt(Minecraft mc) {
        if (pendingHealingSelection != null && mc.player != null && mc.player.isAlive() && !isUnconscious(mc.player)) {
            return new InteractionPrompt(
                    mc.player.getId(),
                    mc.player.getDisplayName().copy(),
                    InteractionType.HEAL_SELF,
                    isHealingUseHeld(mc),
                    pendingHealingSelection.hand(),
                    pendingHealingSelection.holdDurationTicks()
            );
        }
        if (isMedicineUseActive(mc)) {
            return new InteractionPrompt(
                    mc.player.getId(),
                    mc.player.getUseItem().getHoverName().copy(),
                    InteractionType.USE_MEDICINE_SELF,
                    true,
                    getMedicineUseHand(mc.player),
                    getMedicineUseDurationTicks(mc.player)
            );
        }

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
            if (!(CommonUtils.getExistingDamageModel(candidate) instanceof PlayerDamageModel playerDamageModel) || !playerDamageModel.canBeRescued()) {
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
        return stack.is(RegistryObjects.BANDAGE.get()) || stack.is(RegistryObjects.PLASTER.get()) || stack.is(RegistryObjects.DEFIBRILLATOR.get());
    }

    private static int getCurrentInteractionHoldDurationTicks() {
        if (interactionPrompt == null) {
            return 0;
        } else if (interactionPrompt.type() == InteractionType.HEAL_SELF) {
            return pendingHealingSelection == null ? 0 : pendingHealingSelection.holdDurationTicks();
        } else if (interactionPrompt.type() == InteractionType.USE_MEDICINE_SELF) {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.player == null ? 0 : getMedicineUseDurationTicks(minecraft.player);
        }
        return interactionPrompt.holdDurationTicks();
    }

    private static int getInteractionHoldDurationTicks(ItemStack stack, InteractionType type) {
        return switch (type) {
            case RESCUE -> stack.is(RegistryObjects.DEFIBRILLATOR.get()) ? DEFIBRILLATOR_RESCUE_HOLD_TICKS : RESCUE_HOLD_TICKS;
            case EXECUTE -> EXECUTION_HOLD_TICKS;
            default -> 0;
        };
    }

    private static float getDisplayedInteractionHoldTicks(float partialTick) {
        if (interactionPrompt != null && interactionPrompt.type() == InteractionType.HEAL_SELF) {
            return getDisplayedPendingHealingHoldTicks(partialTick);
        } else if (interactionPrompt != null && interactionPrompt.type() == InteractionType.USE_MEDICINE_SELF) {
            return getDisplayedMedicineUseTicks(Minecraft.getInstance(), partialTick);
        }
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

    private static void updatePendingHealingState(Minecraft mc, AbstractPlayerDamageModel damageModel) {
        if (pendingHealingSelection == null || mc.player == null) {
            return;
        }
        if (!mc.player.isAlive() || isUnconscious(mc.player) || damageModel == null) {
            clearPendingHealingSelection(mc.player);
            return;
        }
        if (mc.screen != null && !(mc.screen instanceof GuiHealthScreen)) {
            clearPendingHealingSelection(mc.player);
            return;
        }

        ItemStack currentStack = mc.player.getItemInHand(pendingHealingSelection.hand());
        if (!(currentStack.getItem() instanceof ItemHealing) || !ItemStack.isSameItemSameTags(currentStack, pendingHealingSelection.selectedStack())) {
            clearPendingHealingSelectionWithFeedback(mc);
            return;
        }

        var damageablePart = damageModel.getFromEnum(pendingHealingSelection.part());
        if (damageablePart.activeHealer != null || CommonUtils.isPartVisuallyFull(damageablePart)) {
            clearPendingHealingSelection(mc.player);
            return;
        }

        mc.player.displayClientMessage(
                Component.translatable("firstaid.gui.healing_actionbar", formatSingleDecimal(getPendingHealingRemainingSeconds())).withStyle(ChatFormatting.AQUA),
                true
        );
        if (mc.screen instanceof GuiHealthScreen || !isHealingUseHeld(mc)) {
            pendingHealingSelection = pendingHealingSelection.withProgress(0, false);
            return;
        }

        int holdDurationTicks = pendingHealingSelection.holdDurationTicks();
        int nextHoldTicks = holdDurationTicks <= 0 ? 0 : Math.min(holdDurationTicks, pendingHealingSelection.holdTicks() + 1);
        if (!pendingHealingSelection.triggered() && (holdDurationTicks <= 0 || nextHoldTicks >= holdDurationTicks)) {
            ItemStack completedStack = currentStack.copy();
            completedStack.setCount(1);
            FirstAid.NETWORKING.sendToServer(new MessageApplyHealingItem(pendingHealingSelection.part(), pendingHealingSelection.hand()));
            HealingSoundController.playHealingApplySound(completedStack);
            requireUseReleaseBeforeHealingSelection = true;
            mc.options.keyUse.setDown(false);
            clearPendingHealingSelection(mc.player);
        } else {
            pendingHealingSelection = pendingHealingSelection.withProgress(nextHoldTicks, false);
        }
    }

    private static void updateMedicineUseFeedback(Minecraft mc) {
        if (isMedicineUseActive(mc)) {
            mc.player.displayClientMessage(
                    Component.translatable(
                                    "firstaid.gui.medicine_actionbar",
                                    mc.player.getUseItem().getHoverName(),
                                    formatSingleDecimal(getMedicineRemainingSeconds(mc.player))
                            )
                            .withStyle(ChatFormatting.AQUA),
                    true
            );
        }
    }

    private static int getHealingHoldDurationTicks(ItemHealing itemHealing, ItemStack stack) {
        return Mth.ceil(Math.max(0, itemHealing.getApplyTime(stack)) / 50.0F);
    }

    private static float getDisplayedPendingHealingHoldTicks(float partialTick) {
        if (pendingHealingSelection == null) {
            return 0.0F;
        }
        int holdDurationTicks = pendingHealingSelection.holdDurationTicks();
        if (holdDurationTicks <= 0) {
            return 0.0F;
        }
        float extraTicks = isHealingUseHeld(Minecraft.getInstance()) ? Math.max(0.0F, partialTick) : 0.0F;
        return Math.min(holdDurationTicks, pendingHealingSelection.holdTicks() + extraTicks);
    }

    private static float getPendingHealingRemainingSeconds() {
        if (pendingHealingSelection == null) {
            return 0.0F;
        }
        return Math.max(0.0F, (pendingHealingSelection.holdDurationTicks() - pendingHealingSelection.holdTicks()) / 20.0F);
    }

    private static boolean isMedicineUseActive(Minecraft mc) {
        return mc.player != null
                && mc.player.isAlive()
                && !isUnconscious(mc.player)
                && mc.screen == null
                && mc.player.isUsingItem()
                && mc.player.getUseItem().getItem() instanceof ItemMedicine;
    }

    private static InteractionHand getMedicineUseHand(Player player) {
        InteractionHand usedHand = player.getUsedItemHand();
        return usedHand == null ? InteractionHand.MAIN_HAND : usedHand;
    }

    private static int getMedicineUseDurationTicks(Player player) {
        ItemStack stack = player.getUseItem();
        return stack.getItem() instanceof ItemMedicine itemMedicine ? itemMedicine.getUseDuration(stack) : 0;
    }

    private static float getDisplayedMedicineUseTicks(Minecraft mc, float partialTick) {
        if (!isMedicineUseActive(mc)) {
            return 0.0F;
        }
        int holdDurationTicks = getMedicineUseDurationTicks(mc.player);
        if (holdDurationTicks <= 0) {
            return 0.0F;
        }
        int usedTicks = Math.max(0, holdDurationTicks - mc.player.getUseItemRemainingTicks());
        return Math.min((float) holdDurationTicks, usedTicks + Math.max(0.0F, partialTick));
    }

    private static float getMedicineRemainingSeconds(Player player) {
        return Math.max(0.0F, player.getUseItemRemainingTicks() / 20.0F);
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

    private static boolean isHealingUseHeld(Minecraft mc) {
        if (pendingHealingSelection == null || mc.player == null || mc.screen != null || !mc.options.keyUse.isDown()) {
            return false;
        }
        InteractionHand usedItemHand = mc.player.getUsedItemHand();
        return usedItemHand == null || usedItemHand == pendingHealingSelection.hand();
    }

    private static void resetGiveUpHoldState() {
        giveUpHoldTicks = 0;
        giveUpTriggered = false;
    }

    private static void resetInteractionPromptState() {
        resetInteractionPromptHoldState();
        interactionPrompt = null;
    }

    private static void resetInteractionPromptHoldState() {
        interactionHoldTicks = 0;
        interactionTriggered = false;
        interactionSoundTriggered = false;
    }

    private static void clearPendingHealingSelection() {
        pendingHealingSelection = null;
    }

    private static void clearPendingHealingSelection(Player player) {
        clearPendingHealingSelection();
        if (player != null && player.isUsingItem()) {
            player.stopUsingItem();
        }
    }

    private static void clearPendingHealingSelectionWithFeedback(Minecraft mc) {
        clearPendingHealingSelection(mc.player);
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("firstaid.gui.healing_selection_cleared").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    private static boolean shouldBlockHealingReuse(Minecraft mc) {
        if (!requireUseReleaseBeforeHealingSelection || mc.player == null) {
            if (!shouldBlockMedicineReuse(mc)) {
                return false;
            }
        } else {
            ItemStack mainHandStack = mc.player.getMainHandItem();
            ItemStack offHandStack = mc.player.getOffhandItem();
            if (mainHandStack.getItem() instanceof ItemHealing || offHandStack.getItem() instanceof ItemHealing) {
                return true;
            }
        }

        ItemStack mainHandStack = mc.player.getMainHandItem();
        ItemStack offHandStack = mc.player.getOffhandItem();
        return (mainHandStack.getItem() instanceof ItemMedicine || offHandStack.getItem() instanceof ItemMedicine)
                && ItemMedicine.isClientReuseBlocked(mc.player);
    }

    private static boolean shouldBlockMedicineReuse(Minecraft mc) {
        return mc.player != null && ItemMedicine.isClientReuseBlocked(mc.player);
    }

    private record InteractionPrompt(int targetId, Component targetName, InteractionType type, boolean isSneaking, InteractionHand hand, int holdDurationTicks) {
    }

    private record InteractionSelection(InteractionType type, InteractionHand hand) {
    }

    private record PendingHealingSelection(EnumPlayerPart part, InteractionHand hand, ItemStack selectedStack, int holdDurationTicks, int holdTicks, boolean triggered) {
        private PendingHealingSelection withProgress(int holdTicks, boolean triggered) {
            return new PendingHealingSelection(this.part, this.hand, this.selectedStack, this.holdDurationTicks, holdTicks, triggered);
        }
    }

    private enum InteractionType {
        HEAL_SELF,
        USE_MEDICINE_SELF,
        RESCUE,
        EXECUTE,
        INVALID_ITEM
    }
}

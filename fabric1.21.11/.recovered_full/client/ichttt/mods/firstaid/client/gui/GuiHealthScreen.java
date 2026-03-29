/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart
 *  ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel
 *  ichttt.mods.firstaid.api.enums.EnumPlayerPart
 *  ichttt.mods.firstaid.api.healing.ItemHealing
 *  ichttt.mods.firstaid.common.RegistryObjects
 *  ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel
 *  ichttt.mods.firstaid.common.network.MessageApplyHealingItem
 *  ichttt.mods.firstaid.common.network.MessageClientRequest
 *  ichttt.mods.firstaid.common.network.MessageClientRequest$RequestType
 *  ichttt.mods.firstaid.common.util.CommonUtils
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.gui.screens.inventory.InventoryScreen
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.client.resources.language.I18n
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.util.StringUtil
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.level.ItemLike
 */
package ichttt.mods.firstaid.client.gui;

import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.client.ClientHooks;
import ichttt.mods.firstaid.client.HealingSoundController;
import ichttt.mods.firstaid.client.network.FirstAidClientNetworking;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.network.MessageApplyHealingItem;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public class GuiHealthScreen
extends Screen {
    public static final int xSize = 256;
    public static final int ySize = 137;
    public static final ItemStack BED_ITEMSTACK = new ItemStack((ItemLike)Items.RED_BED);
    public static GuiHealthScreen INSTANCE;
    public static boolean isOpen;
    private static int funTicks;
    private final AbstractPlayerDamageModel damageModel;
    private final boolean disableButtons;
    private final InteractionHand activeHand;
    private final Map<EnumPlayerPart, Button> partButtons = new EnumMap<EnumPlayerPart, Button>(EnumPlayerPart.class);
    public int guiLeft;
    public int guiTop;
    public Button cancelButton;

    public GuiHealthScreen(AbstractPlayerDamageModel damageModel) {
        this(damageModel, null);
    }

    public GuiHealthScreen(AbstractPlayerDamageModel damageModel, InteractionHand activeHand) {
        super((Component)Component.translatable((String)"firstaid.gui.healthscreen"));
        this.damageModel = damageModel;
        this.activeHand = activeHand;
        this.disableButtons = activeHand == null;
    }

    public static void tickFun() {
        if (++funTicks > 500) {
            funTicks = 0;
        }
    }

    protected void init() {
        isOpen = true;
        this.guiLeft = (this.width - 256) / 2;
        this.guiTop = (this.height - 137) / 2;
        this.partButtons.clear();
        this.addPartButton(EnumPlayerPart.HEAD, this.guiLeft + 4, this.guiTop + 8);
        this.addPartButton(EnumPlayerPart.LEFT_ARM, this.guiLeft + 4, this.guiTop + 33);
        this.addPartButton(EnumPlayerPart.LEFT_LEG, this.guiLeft + 4, this.guiTop + 58);
        this.addPartButton(EnumPlayerPart.LEFT_FOOT, this.guiLeft + 4, this.guiTop + 83);
        this.addPartButton(EnumPlayerPart.BODY, this.guiLeft + 199, this.guiTop + 8);
        this.addPartButton(EnumPlayerPart.RIGHT_ARM, this.guiLeft + 199, this.guiTop + 33);
        this.addPartButton(EnumPlayerPart.RIGHT_LEG, this.guiLeft + 199, this.guiTop + 58);
        this.addPartButton(EnumPlayerPart.RIGHT_FOOT, this.guiLeft + 199, this.guiTop + 83);
        this.cancelButton = (Button)this.addRenderableWidget((GuiEventListener)Button.builder((Component)Component.translatable((String)(this.disableButtons ? "gui.done" : "gui.cancel")), button -> this.onClose()).bounds(this.width / 2 - 100, this.height - 48, 200, 20).build());
        if (this.minecraft.getDebugOverlay().showDebugScreen()) {
            this.addRenderableWidget((GuiEventListener)Button.builder((Component)Component.literal((String)"resync"), button -> {
                FirstAidClientNetworking.sendToServer((CustomPacketPayload)new MessageClientRequest(MessageClientRequest.RequestType.REQUEST_REFRESH));
                this.onClose();
            }).bounds(this.guiLeft + 208, this.guiTop + 108, 42, 20).build());
        }
    }

    private void addPartButton(EnumPlayerPart part, int x, int y) {
        AbstractDamageablePart damageablePart = this.damageModel.getFromEnum(part);
        Button button = Button.builder((Component)Component.translatable((String)("firstaid.gui." + part.toString().toLowerCase())), ignored -> this.applyHealing(part)).bounds(x, y, 52, 20).build();
        button.active = this.canTreat(damageablePart);
        this.partButtons.put(part, (Button)this.addRenderableWidget((GuiEventListener)button));
    }

    private boolean canTreat(AbstractDamageablePart part) {
        if (this.disableButtons || this.activeHand == null || this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        ItemStack stack = this.minecraft.player.getItemInHand(this.activeHand);
        if (!(stack.getItem() instanceof ItemHealing)) {
            return false;
        }
        return part.activeHealer == null && part.currentHealth < (float)part.getMaxHealth();
    }

    private void applyHealing(EnumPlayerPart part) {
        if (this.activeHand == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        FirstAidClientNetworking.sendToServer((CustomPacketPayload)new MessageApplyHealingItem(part, this.activeHand));
        AbstractPlayerDamageModel localModel = this.damageModel;
        AbstractPlayerDamageModel liveModel = CommonUtils.getDamageModel((Player)this.minecraft.player);
        if (liveModel != null) {
            localModel = liveModel;
        }
        AbstractDamageablePart damageablePart = localModel.getFromEnum(part);
        ItemStack itemInHand = this.minecraft.player.getItemInHand(this.activeHand);
        Item item = itemInHand.getItem();
        if (item instanceof ItemHealing) {
            ItemHealing itemHealing = (ItemHealing)item;
            damageablePart.activeHealer = itemHealing.createNewHealer(itemInHand.copyWithCount(1));
        }
        HealingSoundController.playHealingApplySound();
        this.onClose();
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        AbstractPlayerDamageModel liveModel;
        AbstractPlayerDamageModel renderModel = this.damageModel;
        if (this.minecraft != null && this.minecraft.player != null && (liveModel = CommonUtils.getDamageModel((Player)this.minecraft.player)) != null) {
            renderModel = liveModel;
        }
        for (Map.Entry entry : this.partButtons.entrySet()) {
            ((Button)entry.getValue()).active = this.canTreat(renderModel.getFromEnum((EnumPlayerPart)entry.getKey()));
        }
        guiGraphics.fill(0, 0, this.width, this.height, -2013265920);
        guiGraphics.fill(this.guiLeft, this.guiTop, this.guiLeft + 256, this.guiTop + 137, -872415232);
        HealthRenderUtils.blit(guiGraphics, HealthRenderUtils.SHOW_WOUNDS_LOCATION, 256, 256, this.guiLeft, this.guiTop, 0, 0, 256, 137);
        if (this.minecraft != null && this.minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse((GuiGraphics)guiGraphics, (int)(this.guiLeft + 98), (int)(this.guiTop + 9), (int)(this.guiLeft + 158), (int)(this.guiTop + 103), (int)30, (float)0.0625f, (float)mouseX, (float)mouseY, (LivingEntity)this.minecraft.player);
        }
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.guiTop + 6, 0xFFFFFF);
        this.drawHealth(guiGraphics, renderModel.getFromEnum(EnumPlayerPart.HEAD), false, 14);
        this.drawHealth(guiGraphics, renderModel.getFromEnum(EnumPlayerPart.LEFT_ARM), false, 39);
        this.drawHealth(guiGraphics, renderModel.getFromEnum(EnumPlayerPart.LEFT_LEG), false, 64);
        this.drawHealth(guiGraphics, renderModel.getFromEnum(EnumPlayerPart.LEFT_FOOT), false, 89);
        this.drawHealth(guiGraphics, renderModel.getFromEnum(EnumPlayerPart.BODY), true, 14);
        this.drawHealth(guiGraphics, renderModel.getFromEnum(EnumPlayerPart.RIGHT_ARM), true, 39);
        this.drawHealth(guiGraphics, renderModel.getFromEnum(EnumPlayerPart.RIGHT_LEG), true, 64);
        this.drawHealth(guiGraphics, renderModel.getFromEnum(EnumPlayerPart.RIGHT_FOOT), true, 89);
        int morphineTicks = renderModel.getMorphineTicks();
        if (morphineTicks > 0) {
            guiGraphics.drawCenteredString(this.font, I18n.get((String)"firstaid.gui.morphine_left", (Object[])new Object[]{StringUtil.formatTickDuration((int)morphineTicks, (float)20.0f)}), this.width / 2, this.guiTop + 137 - 22, 0xFFFFFF);
        } else if (this.activeHand != null) {
            guiGraphics.drawCenteredString(this.font, I18n.get((String)"firstaid.gui.apply_hint", (Object[])new Object[0]), this.width / 2, this.guiTop + 137 - 22, 0xFFFFFF);
        }
        this.renderStatusSummary(guiGraphics, renderModel);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawHealth(GuiGraphics guiGraphics, AbstractDamageablePart damageablePart, boolean right, int yOffset) {
        int xTranslation = this.guiLeft + (right ? GuiHealthScreen.getRightOffset(damageablePart) : 57);
        HealthRenderUtils.drawHealth(guiGraphics, this.font, damageablePart, xTranslation, this.guiTop + yOffset, true);
        if (damageablePart.activeHealer != null) {
            guiGraphics.drawString(this.font, (Component)Component.translatable((String)"firstaid.gui.next_heal", (Object[])new Object[]{Math.round((float)(damageablePart.activeHealer.ticksPerHeal.getAsInt() - damageablePart.activeHealer.getTicksPassed()) / 20.0f)}), xTranslation, this.guiTop + yOffset + 10, 0xA0FFA0);
        }
    }

    private static int getRightOffset(AbstractDamageablePart damageablePart) {
        if (HealthRenderUtils.drawAsString(damageablePart, true)) {
            return 160;
        }
        return 200 - Math.min(40, HealthRenderUtils.getMaxHearts(damageablePart.getMaxHealth()) * 9 + HealthRenderUtils.getMaxHearts(damageablePart.getAbsorption()) * 9 + 2);
    }

    private void renderStatusSummary(GuiGraphics guiGraphics, AbstractPlayerDamageModel renderModel) {
        PlayerDamageModel model;
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        LocalPlayer player = this.minecraft.player;
        PlayerDamageModel playerDamageModel = renderModel instanceof PlayerDamageModel ? (model = (PlayerDamageModel)renderModel) : null;
        int lineY = this.guiTop + 137 - 54;
        if (renderModel.getPainLevel() > 0) {
            boolean painSuppressed = player.hasEffect(RegistryObjects.MORPHINE_EFFECT) || player.hasEffect(RegistryObjects.PAINKILLER_EFFECT);
            MutableComponent painText = painSuppressed ? Component.translatable((String)"firstaid.gui.status.pain_suppressed") : Component.translatable((String)"firstaid.gui.status.pain", (Object[])new Object[]{Component.translatable((String)GuiHealthScreen.getPainSeverityKey(renderModel.getPainLevel()))});
            guiGraphics.drawString(this.font, (Component)painText, this.guiLeft + 8, lineY, painSuppressed ? 9425919 : 0xFF8A8A);
            lineY += 10;
        }
        if (player.hasEffect(RegistryObjects.PAINKILLER_EFFECT)) {
            guiGraphics.drawString(this.font, (Component)Component.translatable((String)"firstaid.gui.status.painkiller"), this.guiLeft + 8, lineY, 9425919);
            lineY += 10;
        }
        if (renderModel.getAdrenalineLevel() > 0) {
            int suppressionLevel = playerDamageModel != null ? playerDamageModel.getSuppressionLevel() : renderModel.getAdrenalineLevel();
            guiGraphics.drawString(this.font, (Component)Component.translatable((String)"firstaid.gui.status.suppression", (Object[])new Object[]{Component.translatable((String)GuiHealthScreen.getSuppressionSeverityKey(suppressionLevel))}), this.guiLeft + 8, lineY, 12637930);
            lineY += 10;
        }
        if (renderModel.getUnconsciousTicks() > 0) {
            guiGraphics.drawString(this.font, (Component)Component.translatable((String)(playerDamageModel != null ? playerDamageModel.getUnconsciousReasonKey() : (renderModel.isCriticalConditionActive() ? "firstaid.gui.critical_condition" : "firstaid.gui.unconscious"))), this.guiLeft + 8, lineY, 0xFFD5D5);
            guiGraphics.drawString(this.font, (Component)(playerDamageModel != null && playerDamageModel.canGiveUp() ? Component.translatable((String)"firstaid.gui.death_countdown_seconds", (Object[])new Object[]{playerDamageModel.getUnconsciousSecondsLeft()}) : Component.translatable((String)"firstaid.gui.unconscious_left", (Object[])new Object[]{StringUtil.formatTickDuration((int)renderModel.getUnconsciousTicks(), (float)20.0f)})), this.guiLeft + 8, lineY += 10, 0xFFD5D5);
            if (playerDamageModel != null && playerDamageModel.canGiveUp()) {
                guiGraphics.drawString(this.font, (Component)Component.translatable((String)"firstaid.gui.waiting_for_rescue"), this.guiLeft + 8, lineY += 10, 0xFFD5D5);
                guiGraphics.drawString(this.font, (Component)Component.translatable((String)"firstaid.gui.rescue_help"), this.guiLeft + 8, lineY += 10, 0xFFD5D5);
                guiGraphics.drawString(this.font, (Component)Component.translatable((String)"firstaid.gui.give_up_hint", (Object[])new Object[]{ClientHooks.GIVE_UP.getTranslatedKeyMessage()}), this.guiLeft + 8, lineY += 10, 0xFFB3B3);
            }
        }
    }

    private static String getPainSeverityKey(int painLevel) {
        return switch (painLevel) {
            case 1 -> "firstaid.gui.pain.mild";
            case 2 -> "firstaid.gui.pain.moderate";
            case 3 -> "firstaid.gui.pain.severe";
            case 4 -> "firstaid.gui.pain.extreme";
            default -> "firstaid.gui.pain.critical";
        };
    }

    private static String getSuppressionSeverityKey(int suppressionLevel) {
        return switch (suppressionLevel) {
            case 1 -> "firstaid.gui.suppression.low";
            case 2 -> "firstaid.gui.suppression.medium";
            default -> "firstaid.gui.suppression.high";
        };
    }

    public boolean keyPressed(KeyEvent event) {
        if (ClientHooks.SHOW_WOUNDS.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void onClose() {
        isOpen = false;
        super.onClose();
    }

    static {
        isOpen = false;
    }
}


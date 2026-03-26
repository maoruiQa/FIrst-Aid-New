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

package ichttt.mods.firstaid.client.gui;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.client.ClientHooks;
import ichttt.mods.firstaid.client.HealingSoundController;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.network.MessageApplyHealingItem;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumMap;
import java.util.Map;

public class GuiHealthScreen extends Screen {
    public static final int xSize = 256;
    public static final int ySize = 137;
    public static final ItemStack BED_ITEMSTACK = new ItemStack(Items.RED_BED);

    public static GuiHealthScreen INSTANCE;
    public static boolean isOpen = false;

    private static int funTicks;

    private final AbstractPlayerDamageModel damageModel;
    private final boolean disableButtons;
    private final InteractionHand activeHand;
    private final Map<EnumPlayerPart, Button> partButtons = new EnumMap<>(EnumPlayerPart.class);

    public int guiLeft;
    public int guiTop;
    public Button cancelButton;

    public GuiHealthScreen(AbstractPlayerDamageModel damageModel) {
        this(damageModel, null);
    }

    public GuiHealthScreen(AbstractPlayerDamageModel damageModel, InteractionHand activeHand) {
        super(Component.translatable("firstaid.gui.healthscreen"));
        this.damageModel = damageModel;
        this.activeHand = activeHand;
        this.disableButtons = activeHand == null;
    }

    public static void tickFun() {
        funTicks++;
        if (funTicks > 500) {
            funTicks = 0;
        }
    }

    @Override
    protected void init() {
        isOpen = true;
        guiLeft = (width - xSize) / 2;
        guiTop = (height - ySize) / 2;
        partButtons.clear();

        addPartButton(EnumPlayerPart.HEAD, guiLeft + 4, guiTop + 8);
        addPartButton(EnumPlayerPart.LEFT_ARM, guiLeft + 4, guiTop + 33);
        addPartButton(EnumPlayerPart.LEFT_LEG, guiLeft + 4, guiTop + 58);
        addPartButton(EnumPlayerPart.LEFT_FOOT, guiLeft + 4, guiTop + 83);
        addPartButton(EnumPlayerPart.BODY, guiLeft + 199, guiTop + 8);
        addPartButton(EnumPlayerPart.RIGHT_ARM, guiLeft + 199, guiTop + 33);
        addPartButton(EnumPlayerPart.RIGHT_LEG, guiLeft + 199, guiTop + 58);
        addPartButton(EnumPlayerPart.RIGHT_FOOT, guiLeft + 199, guiTop + 83);

        cancelButton = addRenderableWidget(Button.builder(Component.translatable(disableButtons ? "gui.done" : "gui.cancel"), button -> onClose())
                .bounds(width / 2 - 100, height - 48, 200, 20)
                .build());

        if (minecraft.getDebugOverlay().showDebugScreen()) {
            addRenderableWidget(Button.builder(Component.literal("resync"), button -> {
                        FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.REQUEST_REFRESH));
                        onClose();
                    }).bounds(guiLeft + 208, guiTop + 108, 42, 20)
                    .build());
        }
    }

    private void addPartButton(EnumPlayerPart part, int x, int y) {
        AbstractDamageablePart damageablePart = damageModel.getFromEnum(part);
        Button button = Button.builder(Component.translatable("firstaid.gui." + part.toString().toLowerCase()), ignored -> applyHealing(part))
                .bounds(x, y, 52, 20)
                .build();
        button.active = canTreat(damageablePart);
        partButtons.put(part, addRenderableWidget(button));
    }

    private boolean canTreat(AbstractDamageablePart part) {
        if (disableButtons || activeHand == null || minecraft == null || minecraft.player == null) {
            return false;
        }
        ItemStack stack = minecraft.player.getItemInHand(activeHand);
        if (!(stack.getItem() instanceof ItemHealing)) {
            return false;
        }
        return part.activeHealer == null && !CommonUtils.isPartVisuallyFull(part);
    }

    private void applyHealing(EnumPlayerPart part) {
        if (activeHand == null || minecraft == null || minecraft.player == null) {
            return;
        }
        FirstAid.NETWORKING.sendToServer(new MessageApplyHealingItem(part, activeHand));

        AbstractPlayerDamageModel localModel = damageModel;
        AbstractPlayerDamageModel liveModel = CommonUtils.getDamageModel(minecraft.player);
        if (liveModel != null) {
            localModel = liveModel;
        }
        AbstractDamageablePart damageablePart = localModel.getFromEnum(part);
        ItemStack itemInHand = minecraft.player.getItemInHand(activeHand);
        if (itemInHand.getItem() instanceof ItemHealing itemHealing) {
            damageablePart.activeHealer = itemHealing.createNewHealer(itemInHand.copyWithCount(1));
        }
        HealingSoundController.playHealingApplySound();
        onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        AbstractPlayerDamageModel renderModel = damageModel;
        if (minecraft != null && minecraft.player != null) {
            AbstractPlayerDamageModel liveModel = CommonUtils.getDamageModel(minecraft.player);
            if (liveModel != null) {
                renderModel = liveModel;
            }
        }

        for (Map.Entry<EnumPlayerPart, Button> entry : partButtons.entrySet()) {
            entry.getValue().active = canTreat(renderModel.getFromEnum(entry.getKey()));
        }

        guiGraphics.fill(0, 0, width, height, 0x88000000);
        guiGraphics.fill(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xCC000000);
        HealthRenderUtils.blit(guiGraphics, HealthRenderUtils.SHOW_WOUNDS_LOCATION, 256, 256, guiLeft, guiTop, 0, 0, xSize, ySize);
        if (minecraft != null && minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics,
                    guiLeft + 98, guiTop + 9,
                    guiLeft + 158, guiTop + 103,
                    30, 0.0625F, mouseX, mouseY, minecraft.player);
        }

        drawHealth(guiGraphics, renderModel.getFromEnum(EnumPlayerPart.HEAD), false, 14);
        drawHealth(guiGraphics, renderModel.getFromEnum(EnumPlayerPart.LEFT_ARM), false, 39);
        drawHealth(guiGraphics, renderModel.getFromEnum(EnumPlayerPart.LEFT_LEG), false, 64);
        drawHealth(guiGraphics, renderModel.getFromEnum(EnumPlayerPart.LEFT_FOOT), false, 89);
        drawHealth(guiGraphics, renderModel.getFromEnum(EnumPlayerPart.BODY), true, 14);
        drawHealth(guiGraphics, renderModel.getFromEnum(EnumPlayerPart.RIGHT_ARM), true, 39);
        drawHealth(guiGraphics, renderModel.getFromEnum(EnumPlayerPart.RIGHT_LEG), true, 64);
        drawHealth(guiGraphics, renderModel.getFromEnum(EnumPlayerPart.RIGHT_FOOT), true, 89);

        int morphineTicks = renderModel.getMorphineTicks();
        if (morphineTicks > 0) {
            guiGraphics.drawCenteredString(font, I18n.get("firstaid.gui.morphine_left", StringUtil.formatTickDuration(morphineTicks, 20F)), width / 2, guiTop + ySize - 22, 0xFFFFFF);
        } else if (activeHand != null) {
            guiGraphics.drawCenteredString(font, I18n.get("firstaid.gui.apply_hint"), width / 2, guiTop + ySize - 22, 0xFFFFFF);
        }

        renderStatusSummary(guiGraphics, renderModel);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Skip the default blurred background so the UI stays crisp.
    }

    private void drawHealth(GuiGraphics guiGraphics, AbstractDamageablePart damageablePart, boolean right, int yOffset) {
        int xTranslation = guiLeft + (right ? getRightOffset(damageablePart) : 57);
        drawPartHealthIndicator(guiGraphics, xTranslation, guiTop + yOffset, damageablePart);
        HealthRenderUtils.drawHealth(guiGraphics, font, damageablePart, xTranslation, guiTop + yOffset, true);
    }

    private static void drawPartHealthIndicator(GuiGraphics guiGraphics, int x, int y, AbstractDamageablePart damageablePart) {
        int color = 0xCC000000 | HealthRenderUtils.getHealthColor(damageablePart);
        guiGraphics.fill(x - 6, y, x - 3, y + 10, color);
    }

    private static int getRightOffset(AbstractDamageablePart damageablePart) {
        if (HealthRenderUtils.drawAsString(damageablePart, true)) {
            return 160;
        }
        return 200 - Math.min(40, HealthRenderUtils.getMaxHearts(damageablePart.getMaxHealth()) * 9
                + HealthRenderUtils.getMaxHearts(damageablePart.getAbsorption()) * 9 + 2);
    }

    private void renderStatusSummary(GuiGraphics guiGraphics, AbstractPlayerDamageModel renderModel) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        Player player = minecraft.player;
        PlayerDamageModel playerDamageModel = renderModel instanceof PlayerDamageModel model ? model : null;
        int lineY = guiTop + ySize - 54;

        if (renderModel.getAdrenalineLevel() > 0) {
            int suppressionLevel = playerDamageModel != null ? playerDamageModel.getSuppressionLevel() : renderModel.getAdrenalineLevel();
            guiGraphics.drawString(font,
                    Component.translatable("firstaid.gui.status.suppression", Component.translatable(getSuppressionSeverityKey(suppressionLevel))),
                    guiLeft + 8,
                    lineY,
                    0xC0D6EA);
            lineY += 10;
        }

        if (renderModel.getUnconsciousTicks() > 0) {
            guiGraphics.drawString(font,
                    Component.translatable(playerDamageModel != null
                            ? playerDamageModel.getUnconsciousReasonKey()
                            : renderModel.isCriticalConditionActive() ? "firstaid.gui.critical_condition" : "firstaid.gui.unconscious"),
                    guiLeft + 8,
                    lineY,
                    0xFFD5D5);
            lineY += 10;
            guiGraphics.drawString(font,
                    playerDamageModel != null && playerDamageModel.canGiveUp()
                            ? Component.translatable("firstaid.gui.death_countdown_seconds", playerDamageModel.getUnconsciousSecondsLeft())
                            : Component.translatable("firstaid.gui.unconscious_left", StringUtil.formatTickDuration(renderModel.getUnconsciousTicks(), 20F)),
                    guiLeft + 8,
                    lineY,
                    0xFFD5D5);
            if (playerDamageModel != null && playerDamageModel.canGiveUp()) {
                lineY += 10;
                guiGraphics.drawString(font,
                        Component.translatable("firstaid.gui.waiting_for_rescue"),
                        guiLeft + 8,
                        lineY,
                        0xFFD5D5);
                lineY += 10;
                guiGraphics.drawString(font,
                        Component.translatable("firstaid.gui.rescue_help"),
                        guiLeft + 8,
                        lineY,
                        0xFFD5D5);
                lineY += 10;
                guiGraphics.drawString(font,
                        Component.translatable("firstaid.gui.give_up_hint", ClientHooks.GIVE_UP.getTranslatedKeyMessage()),
                        guiLeft + 8,
                        lineY,
                        0xFFB3B3);
            }
        }
    }

    private static String getSuppressionSeverityKey(int suppressionLevel) {
        return switch (suppressionLevel) {
            case 1 -> "firstaid.gui.suppression.low";
            case 2 -> "firstaid.gui.suppression.medium";
            default -> "firstaid.gui.suppression.high";
        };
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientHooks.SHOW_WOUNDS.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        isOpen = false;
        super.onClose();
    }
}

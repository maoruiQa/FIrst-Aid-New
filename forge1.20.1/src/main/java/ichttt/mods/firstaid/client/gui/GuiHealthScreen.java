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

import com.mojang.blaze3d.platform.InputConstants;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.client.ClientEventHandler;
import ichttt.mods.firstaid.client.ClientHooks;
import ichttt.mods.firstaid.client.util.EventCalendar;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
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
    private final List<AbstractWidget> buttons = new ArrayList<>();

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
        INSTANCE = this;
        guiLeft = (width - xSize) / 2;
        guiTop = (height - ySize) / 2;
        partButtons.clear();
        buttons.clear();

        addPartButton(EnumPlayerPart.HEAD, guiLeft + 4, guiTop + 8);
        addPartButton(EnumPlayerPart.LEFT_ARM, guiLeft + 4, guiTop + 33);
        addPartButton(EnumPlayerPart.LEFT_LEG, guiLeft + 4, guiTop + 58);
        addPartButton(EnumPlayerPart.LEFT_FOOT, guiLeft + 4, guiTop + 83);
        addPartButton(EnumPlayerPart.BODY, guiLeft + 199, guiTop + 8);
        addPartButton(EnumPlayerPart.RIGHT_ARM, guiLeft + 199, guiTop + 33);
        addPartButton(EnumPlayerPart.RIGHT_LEG, guiLeft + 199, guiTop + 58);
        addPartButton(EnumPlayerPart.RIGHT_FOOT, guiLeft + 199, guiTop + 83);

        cancelButton = addTrackedButton(Button.builder(Component.translatable(disableButtons ? "gui.done" : "gui.cancel"), button -> onClose())
                .bounds(width / 2 - 100, height - 48, 200, 20)
                .build());

        if (minecraft != null && minecraft.options.renderDebug) {
            addTrackedButton(Button.builder(Component.literal("resync"), button -> {
                        FirstAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.Type.REQUEST_REFRESH));
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
        partButtons.put(part, addTrackedButton(button));
    }

    private <T extends AbstractWidget> T addTrackedButton(T button) {
        buttons.add(button);
        return addRenderableWidget(button);
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
        if (ClientEventHandler.selectPendingHealing(part, activeHand)) {
            onClose();
        }
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
        guiGraphics.blit(HealthRenderUtils.SHOW_WOUNDS_LOCATION, guiLeft, guiTop, 0, 0, xSize, ySize);

        if (minecraft != null && minecraft.player != null) {
            int entityLookX = this.guiLeft + (xSize / 2) - mouseX;
            int entityLookY = this.guiTop + 20 - mouseY;
            if (EventCalendar.isGuiFun()) {
                if (EventCalendar.isHalloween()) {
                    if ((funTicks > 250 && funTicks < 270) || (funTicks > 330 && funTicks < 340)) {
                        entityLookX = 0;
                        entityLookY = 0;
                    } else if ((funTicks > 480 && funTicks < 500) || (funTicks > 340 && funTicks < 350)) {
                        entityLookX = -entityLookX;
                        entityLookY = -entityLookY;
                    }
                } else {
                    entityLookX = -entityLookX;
                    entityLookY = -entityLookY;
                }
            }
            InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics, this.width / 2, this.height / 2 + 30, 45, entityLookX, entityLookY, minecraft.player);
        }

        guiGraphics.drawCenteredString(font, title, width / 2, guiTop + 6, 0xFFFFFF);

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
            guiGraphics.drawCenteredString(font, Component.translatable("firstaid.gui.morphine_left", StringUtil.formatTickDuration(morphineTicks)), width / 2, guiTop + ySize - 22, 0xFFFFFF);
        } else if (activeHand != null) {
            guiGraphics.drawCenteredString(font, Component.translatable("firstaid.gui.apply_hint"), width / 2, guiTop + ySize - 22, 0xFFFFFF);
        }

        tooltipButton(guiGraphics, partButtons.get(EnumPlayerPart.HEAD), renderModel.getFromEnum(EnumPlayerPart.HEAD), mouseX, mouseY);
        tooltipButton(guiGraphics, partButtons.get(EnumPlayerPart.LEFT_ARM), renderModel.getFromEnum(EnumPlayerPart.LEFT_ARM), mouseX, mouseY);
        tooltipButton(guiGraphics, partButtons.get(EnumPlayerPart.LEFT_LEG), renderModel.getFromEnum(EnumPlayerPart.LEFT_LEG), mouseX, mouseY);
        tooltipButton(guiGraphics, partButtons.get(EnumPlayerPart.LEFT_FOOT), renderModel.getFromEnum(EnumPlayerPart.LEFT_FOOT), mouseX, mouseY);
        tooltipButton(guiGraphics, partButtons.get(EnumPlayerPart.BODY), renderModel.getFromEnum(EnumPlayerPart.BODY), mouseX, mouseY);
        tooltipButton(guiGraphics, partButtons.get(EnumPlayerPart.RIGHT_ARM), renderModel.getFromEnum(EnumPlayerPart.RIGHT_ARM), mouseX, mouseY);
        tooltipButton(guiGraphics, partButtons.get(EnumPlayerPart.RIGHT_LEG), renderModel.getFromEnum(EnumPlayerPart.RIGHT_LEG), mouseX, mouseY);
        tooltipButton(guiGraphics, partButtons.get(EnumPlayerPart.RIGHT_FOOT), renderModel.getFromEnum(EnumPlayerPart.RIGHT_FOOT), mouseX, mouseY);

        renderStatusSummary(guiGraphics, renderModel);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    public void renderBackground(GuiGraphics guiGraphics) {
        // Skip the default blurred background so the UI stays crisp.
    }

    public List<AbstractWidget> getButtons() {
        return buttons;
    }

    private void tooltipButton(GuiGraphics guiGraphics, Button button, AbstractDamageablePart part, int mouseX, int mouseY) {
        if (button == null || part.activeHealer == null || !button.isHoveredOrFocused()) {
            return;
        }
        guiGraphics.renderComponentTooltip(font,
                Arrays.asList(
                        Component.literal(Component.translatable("firstaid.gui.active_item").getString() + ": " + Component.translatable(part.activeHealer.stack.getDescriptionId()).getString()),
                        Component.translatable("firstaid.gui.next_heal", Math.round((part.activeHealer.ticksPerHeal.getAsInt() - part.activeHealer.getTicksPassed()) / 20F))
                ),
                mouseX,
                mouseY);
    }

    private void drawHealth(GuiGraphics guiGraphics, AbstractDamageablePart damageablePart, boolean right, int yOffset) {
        int xTranslation = guiLeft + (right ? getRightOffset(damageablePart) : 57);
        drawPartHealthIndicator(guiGraphics, xTranslation, guiTop + yOffset, damageablePart);
        HealthRenderUtils.drawHealth(guiGraphics, font, damageablePart, xTranslation, guiTop + yOffset, true);
    }

    private static void drawPartHealthIndicator(GuiGraphics guiGraphics, int x, int y, AbstractDamageablePart damageablePart) {
        int color = 0xCC000000 | getHealthIndicatorColor(damageablePart);
        guiGraphics.fill(x - 6, y, x - 3, y + 10, color);
    }

    private static int getRightOffset(AbstractDamageablePart damageablePart) {
        if (HealthRenderUtils.drawAsString(damageablePart, true)) {
            return 160;
        }
        return 200 - Math.min(40, HealthRenderUtils.getMaxHearts(damageablePart.getMaxHealth()) * 9 + HealthRenderUtils.getMaxHearts(damageablePart.getAbsorption()) * 9 + 2);
    }

    private static int getHealthIndicatorColor(AbstractDamageablePart part) {
        float ratio = part.getMaxHealth() <= 0 ? 0.0F : part.currentHealth / part.getMaxHealth();
        if (ratio > 0.75F) {
            return 0x55FF55;
        }
        if (ratio > 0.5F) {
            return 0xB4FF5A;
        }
        if (ratio > 0.25F) {
            return 0xFFD85A;
        }
        if (ratio > 0.0F) {
            return 0xFF8A5A;
        }
        return 0xE23B3B;
    }

    private void renderStatusSummary(GuiGraphics guiGraphics, AbstractPlayerDamageModel renderModel) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        Player player = minecraft.player;
        PlayerDamageModel playerDamageModel = renderModel instanceof PlayerDamageModel model ? model : null;
        int lineY = guiTop + ySize - 54;

        int painLevel = playerDamageModel != null ? playerDamageModel.getPainLevel() : 0;
        if (painLevel <= 0) {
            painLevel = calculateLocalPainLevel(renderModel);
        }
        if (painLevel > 0) {
            boolean painSuppressed = player.hasEffect(RegistryObjects.MORPHINE_EFFECT.get()) || player.hasEffect(RegistryObjects.PAINKILLER_EFFECT.get());
            Component painText = painSuppressed
                    ? Component.translatable("firstaid.gui.status.pain_suppressed")
                    : Component.translatable("firstaid.gui.status.pain", Component.translatable(getPainSeverityKey(painLevel)));
            guiGraphics.drawString(font, painText, guiLeft + 8, lineY, painSuppressed ? 0x8FD3FF : 0xFF8A8A);
            lineY += 10;
        }

        if (player.hasEffect(RegistryObjects.PAINKILLER_EFFECT.get())) {
            guiGraphics.drawString(font, Component.translatable("firstaid.gui.status.painkiller"), guiLeft + 8, lineY, 0x8FD3FF);
            lineY += 10;
        }

        int adrenalineLevel = playerDamageModel != null ? playerDamageModel.getAdrenalineLevel() : 0;
        if (adrenalineLevel > 0) {
            int suppressionLevel = adrenalineLevel;
            guiGraphics.drawString(font,
                    Component.translatable("firstaid.gui.status.suppression", Component.translatable(getSuppressionSeverityKey(suppressionLevel))),
                    guiLeft + 8,
                    lineY,
                    0xC0D6EA);
            lineY += 10;
        }

        int unconsciousTicks = playerDamageModel != null ? playerDamageModel.getUnconsciousTicks() : 0;
        if (unconsciousTicks > 0) {
            guiGraphics.drawString(font,
                    Component.translatable(playerDamageModel != null
                            ? playerDamageModel.getUnconsciousReasonKey()
                            : "firstaid.gui.unconscious"),
                    guiLeft + 8,
                    lineY,
                    0xFFD5D5);
            lineY += 10;
            guiGraphics.drawString(font,
                    playerDamageModel != null && playerDamageModel.canGiveUp()
                            ? Component.translatable("firstaid.gui.death_countdown_seconds", playerDamageModel.getUnconsciousSecondsLeft())
                            : Component.translatable("firstaid.gui.unconscious_left", StringUtil.formatTickDuration(unconsciousTicks)),
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

    private static int calculateLocalPainLevel(AbstractPlayerDamageModel model) {
        boolean hasInjury = false;
        int fullyLostParts = 0;
        float maxSeverity = 0.0F;
        float weightedSeverity = 0.0F;
        float totalWeight = 0.0F;
        for (AbstractDamageablePart part : model) {
            float missingHealth = Math.max(0.0F, part.getMaxHealth() - part.currentHealth);
            if (missingHealth <= 0F) {
                continue;
            }
            hasInjury = true;
            float injuryRatio = missingHealth / part.getMaxHealth();
            if (part.currentHealth <= 0F) {
                fullyLostParts++;
                injuryRatio = part.canCauseDeath ? 1.0F : 0.85F;
            }
            if (part.canCauseDeath && injuryRatio >= 0.55F) {
                injuryRatio = Math.min(1.0F, injuryRatio + 0.15F);
            }
            float weight = part.canCauseDeath ? 1.35F : 1.0F;
            maxSeverity = Math.max(maxSeverity, injuryRatio);
            weightedSeverity += injuryRatio * weight;
            totalWeight += weight;
        }

        if (!hasInjury) {
            return 0;
        }
        if (!FirstAid.dynamicPainEnabled) {
            return Math.max(1, Math.min(5, FirstAid.mildPainLevel));
        }
        float averageSeverity = totalWeight <= 0.0F ? 0.0F : weightedSeverity / totalWeight;
        float combinedSeverity = Math.min(1.0F, maxSeverity * 0.65F + averageSeverity * 0.35F);
        int maxPainLevel = 5;
        int painLevel = Math.max(1, Math.min(maxPainLevel, 1 + (int) Math.floor(combinedSeverity * (maxPainLevel - 0.0001F))));
        if (fullyLostParts < 3 && painLevel >= maxPainLevel) {
            return maxPainLevel - 1;
        }
        return painLevel;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientHooks.SHOW_WOUNDS.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
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
        INSTANCE = null;
        isOpen = false;
        super.onClose();
    }

}

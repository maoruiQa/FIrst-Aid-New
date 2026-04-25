package ichttt.mods.firstaid.client.gui;

import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.client.ClientEventHandler;
import ichttt.mods.firstaid.client.ClientHooks;
import ichttt.mods.firstaid.client.MedicineStatusClientHelper;
import ichttt.mods.firstaid.client.network.FirstAidClientNetworking;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.api.medicine.MedicineStatusDisplay;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.network.MessageClientRequest.RequestType;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
      this.cancelButton = (Button)this.addRenderableWidget(
         Button.builder(Component.translatable(this.disableButtons ? "gui.done" : "gui.cancel"), button -> this.onClose())
            .bounds(this.width / 2 - 100, this.height - 48, 200, 20)
            .build()
      );
      if (this.minecraft.getDebugOverlay().showDebugScreen()) {
         this.addRenderableWidget(Button.builder(Component.literal("resync"), button -> {
            FirstAidClientNetworking.sendToServer(new MessageClientRequest(RequestType.REQUEST_REFRESH));
            this.onClose();
         }).bounds(this.guiLeft + 208, this.guiTop + 108, 42, 20).build());
      }
   }

   private void addPartButton(EnumPlayerPart part, int x, int y) {
      AbstractDamageablePart damageablePart = this.damageModel.getFromEnum(part);
      Button button = Button.builder(Component.translatable("firstaid.gui." + part.toString().toLowerCase()), ignored -> this.applyHealing(part))
         .bounds(x, y, 52, 20)
         .build();
      button.active = this.canTreat(damageablePart);
      this.partButtons.put(part, (Button)this.addRenderableWidget(button));
   }

   private boolean canTreat(AbstractDamageablePart part) {
      if (!this.disableButtons && this.activeHand != null && this.minecraft != null && this.minecraft.player != null) {
         ItemStack stack = this.minecraft.player.getItemInHand(this.activeHand);
         return !(stack.getItem() instanceof ItemHealing) ? false : part.activeHealer == null && !CommonUtils.isPartVisuallyFull(part);
      } else {
         return false;
      }
   }

   private void applyHealing(EnumPlayerPart part) {
      if (this.activeHand != null && this.minecraft != null && this.minecraft.player != null) {
         if (ClientEventHandler.selectPendingHealing(part, this.activeHand)) {
            this.onClose();
         }
      }
   }

   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      AbstractPlayerDamageModel renderModel = this.damageModel;
      if (this.minecraft != null && this.minecraft.player != null) {
         AbstractPlayerDamageModel liveModel = CommonUtils.getDamageModel(this.minecraft.player);
         if (liveModel != null) {
            renderModel = liveModel;
         }
      }

      for (Entry<EnumPlayerPart, Button> entry : this.partButtons.entrySet()) {
         entry.getValue().active = this.canTreat(renderModel.getFromEnum(entry.getKey()));
      }

      guiGraphics.fill(0, 0, this.width, this.height, -2013265920);
      guiGraphics.fill(this.guiLeft, this.guiTop, this.guiLeft + 256, this.guiTop + 137, -872415232);
      HealthRenderUtils.blit(guiGraphics, HealthRenderUtils.SHOW_WOUNDS_LOCATION, 256, 256, this.guiLeft, this.guiTop, 0, 0, 256, 137);
      if (this.minecraft != null && this.minecraft.player != null) {
         InventoryScreen.renderEntityInInventoryFollowsMouse(
            guiGraphics, this.guiLeft + 98, this.guiTop + 9, this.guiLeft + 158, this.guiTop + 103, 30, 0.0625F, mouseX, mouseY, this.minecraft.player
         );
      }

      guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.guiTop + 6, 16777215);
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
         guiGraphics.drawCenteredString(
            this.font,
            I18n.get("firstaid.gui.morphine_left", new Object[]{StringUtil.formatTickDuration(morphineTicks, 20.0F)}),
            this.width / 2,
            this.guiTop + 137 - 22,
            16777215
         );
      } else if (this.activeHand != null) {
         guiGraphics.drawCenteredString(this.font, I18n.get("firstaid.gui.apply_hint", new Object[0]), this.width / 2, this.guiTop + 137 - 22, 16777215);
      }

      this.renderStatusSummary(guiGraphics, renderModel);
      super.render(guiGraphics, mouseX, mouseY, partialTick);
   }

   private void drawHealth(GuiGraphics guiGraphics, AbstractDamageablePart damageablePart, boolean right, int yOffset) {
      int xTranslation = this.guiLeft + (right ? getRightOffset(damageablePart) : 57);
      drawPartHealthIndicator(guiGraphics, xTranslation, this.guiTop + yOffset, damageablePart);
      HealthRenderUtils.drawHealth(guiGraphics, this.font, damageablePart, xTranslation, this.guiTop + yOffset, true);
   }

   private static void drawPartHealthIndicator(GuiGraphics guiGraphics, int x, int y, AbstractDamageablePart damageablePart) {
      int color = -872415232 | HealthRenderUtils.getHealthColor(damageablePart);
      guiGraphics.fill(x - 6, y, x - 3, y + 10, color);
   }

   private static int getRightOffset(AbstractDamageablePart damageablePart) {
      return HealthRenderUtils.drawAsString(damageablePart, true)
         ? 160
         : 200 - Math.min(40, HealthRenderUtils.getHeartRenderWidth(damageablePart, true));
   }

   private void renderStatusSummary(GuiGraphics guiGraphics, AbstractPlayerDamageModel renderModel) {
      if (this.minecraft != null && this.minecraft.player != null) {
         Player player = this.minecraft.player;
         PlayerDamageModel playerDamageModel = renderModel instanceof PlayerDamageModel model ? model : null;
         int lineY = this.guiTop + 137 - 54;
         if (renderModel.getPainLevel() > 0) {
            boolean painSuppressed = player.hasEffect(RegistryObjects.MORPHINE_EFFECT) || player.hasEffect(RegistryObjects.PAINKILLER_EFFECT);
            Component painText = painSuppressed
               ? Component.translatable("firstaid.gui.status.pain_suppressed")
               : Component.translatable("firstaid.gui.status.pain", new Object[]{Component.translatable(getPainSeverityKey(renderModel.getPainLevel()))});
            guiGraphics.drawString(this.font, painText, this.guiLeft + 8, lineY, painSuppressed ? 9425919 : 16747146);
            lineY += 10;
         }

         if (renderModel.getAdrenalineLevel() > 0) {
            int suppressionLevel = playerDamageModel != null ? playerDamageModel.getSuppressionLevel() : renderModel.getAdrenalineLevel();
            guiGraphics.drawString(
               this.font,
               Component.translatable("firstaid.gui.status.suppression", new Object[]{Component.translatable(getSuppressionSeverityKey(suppressionLevel))}),
               this.guiLeft + 8,
               lineY,
               12637930
            );
            lineY += 10;
         }

         if (renderModel.getUnconsciousTicks() > 0) {
            guiGraphics.drawString(
               this.font,
               Component.translatable(
                  playerDamageModel != null
                     ? playerDamageModel.getUnconsciousReasonKey()
                     : (renderModel.isCriticalConditionActive() ? "firstaid.gui.critical_condition" : "firstaid.gui.unconscious")
               ),
               this.guiLeft + 8,
               lineY,
               16766421
            );
            lineY += 10;
            guiGraphics.drawString(
               this.font,
               playerDamageModel != null && playerDamageModel.canGiveUp()
                  ? Component.translatable("firstaid.gui.death_countdown_seconds", new Object[]{playerDamageModel.getUnconsciousSecondsLeft()})
                  : Component.translatable(
                     "firstaid.gui.unconscious_left", new Object[]{StringUtil.formatTickDuration(renderModel.getUnconsciousTicks(), 20.0F)}
                  ),
               this.guiLeft + 8,
               lineY,
               16766421
            );
            if (playerDamageModel != null && playerDamageModel.canGiveUp()) {
               lineY += 10;
               guiGraphics.drawString(this.font, Component.translatable("firstaid.gui.waiting_for_rescue"), this.guiLeft + 8, lineY, 16766421);
               lineY += 10;
               guiGraphics.drawString(this.font, Component.translatable("firstaid.gui.rescue_help"), this.guiLeft + 8, lineY, 16766421);
               lineY += 10;
               guiGraphics.drawString(
                  this.font,
                  Component.translatable("firstaid.gui.give_up_hint", new Object[]{ClientHooks.GIVE_UP.getTranslatedKeyMessage()}),
                  this.guiLeft + 8,
                  lineY,
                  16757683
               );
               lineY += 10;
            }
         }

         for (MedicineStatusDisplay display : MedicineStatusClientHelper.collect(player)) {
            lineY = MedicineStatusClientHelper.drawStatusLine(guiGraphics, this.font, display, this.guiLeft + 8, lineY);
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
      } else {
         return super.keyPressed(event);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
   }

   public void onClose() {
      isOpen = false;
      super.onClose();
   }
}

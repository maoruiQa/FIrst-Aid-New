package ichttt.mods.firstaid.client.tutorial;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.ClientHooks;
import ichttt.mods.firstaid.client.gui.GuiHealthScreen;
import ichttt.mods.firstaid.client.network.FirstAidClientNetworking;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.network.MessageClientRequest.RequestType;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

public class GuiTutorial extends Screen {
   public GuiTutorial() {
      super(Component.translatable("firstaid.tutorial"));
   }

   protected void init() {
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.done"), button -> this.finishTutorial()).bounds(this.width / 2 - 100, this.height - 40, 200, 20).build()
      );
   }

   private void finishTutorial() {
      FirstAidClientNetworking.sendToServer(new MessageClientRequest(RequestType.TUTORIAL_COMPLETE));
      AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(this.minecraft.player);
      if (damageModel != null) {
         this.minecraft.setScreen(new GuiHealthScreen(damageModel));
      } else {
         this.minecraft.setScreen(null);
      }
   }

   public void drawOffsetString(GuiGraphicsExtractor guiGraphics, String text, int yOffset) {
      int guiLeft = (this.width - 256) / 2;
      int guiTop = (this.height - 110) / 2;
      guiGraphics.text(this.font, text, guiLeft + 30, guiTop + yOffset, 16777215);
   }

   public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
      guiGraphics.fill(0, 0, this.width, this.height, -2013265920);
      int guiLeft = (this.width - 256) / 2;
      int guiTop = (this.height - 110) / 2;
      guiGraphics.fill(guiLeft, guiTop, guiLeft + 256, guiTop + 110, -872415232);
      HealthRenderUtils.blit(guiGraphics, HealthRenderUtils.SHOW_WOUNDS_LOCATION, 256, 256, guiLeft, guiTop, 0, 139, 256, 28);
      guiGraphics.centeredText(this.font, this.title, this.width / 2, guiTop + 8, 16777215);
      guiGraphics.textWithWordWrap(this.font, Component.literal(I18n.get("firstaid.tutorial.welcome", new Object[0])), guiLeft + 12, guiTop + 36, 232, 16777215);
      guiGraphics.textWithWordWrap(this.font, Component.literal(I18n.get("firstaid.tutorial.line1", new Object[0])), guiLeft + 12, guiTop + 50, 232, 16777215);
      guiGraphics.textWithWordWrap(this.font, Component.literal(I18n.get("firstaid.tutorial.line2", new Object[0])), guiLeft + 12, guiTop + 64, 232, 16777215);
      guiGraphics.textWithWordWrap(
         this.font,
         Component.literal(I18n.get("firstaid.tutorial.line8", new Object[]{ClientHooks.SHOW_WOUNDS.getTranslatedKeyMessage().getString()})),
         guiLeft + 12,
         guiTop + 78,
         232,
         16777215
      );
      super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
   }

   public void onClose() {
      GuiHealthScreen.isOpen = false;
      super.onClose();
   }
}

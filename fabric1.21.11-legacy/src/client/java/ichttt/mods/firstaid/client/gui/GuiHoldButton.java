package ichttt.mods.firstaid.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public class GuiHoldButton extends AbstractButton {
   public final int id;
   private int holdTime;
   public final boolean isRightSide;
   private long pressStart = -1L;
   private boolean mouseIsPressed = false;

   public GuiHoldButton(int id, int x, int y, int widthIn, int heightIn, Component buttonText, boolean isRightSide) {
      super(x, y, widthIn, heightIn, buttonText);
      this.id = id;
      this.isRightSide = isRightSide;
   }

   public void setup(int holdTime) {
      this.holdTime = holdTime;
   }

   public void mouseMoved(double xPos, double yPos) {
      super.mouseMoved(xPos, yPos);
      if (this.pressStart != -1L && !this.isMouseOver(xPos, yPos)) {
         this.pressStart = -1L;
      }
   }

   public void setFocused(boolean focused) {
      super.setFocused(focused);
      if (this.pressStart != -1L && !focused && !this.mouseIsPressed) {
         this.pressStart = -1L;
      }
   }

   public void onClick(MouseButtonEvent event, boolean doubleClick) {
      super.onClick(event, doubleClick);
      this.mouseIsPressed = true;
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      this.mouseIsPressed = false;
      if (event.button() != 0) {
         return false;
      } else {
         boolean result = this.pressStart != -1L && super.mouseReleased(event);
         if (result) {
            this.pressStart = -1L;
         }

         return result;
      }
   }

   public int getTimeLeft() {
      return this.pressStart == -1L ? -1 : (int)Math.max(0L, this.holdTime - (Util.getMillis() - this.pressStart));
   }

   public void reset() {
      this.pressStart = -1L;
   }

   public void onPress(InputWithModifiers input) {
      this.pressStart = Util.getMillis();
   }

   protected void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      guiGraphics.drawCenteredString(
         Minecraft.getInstance().font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, 16777215
      );
   }

   protected void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {
   }
}

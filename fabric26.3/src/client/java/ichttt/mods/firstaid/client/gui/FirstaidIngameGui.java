package ichttt.mods.firstaid.client.gui;

import ichttt.mods.firstaid.client.util.HeartSpriteHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class FirstaidIngameGui {
   private FirstaidIngameGui() {
   }

   public static void renderHealth(int width, int height, GuiGraphicsExtractor guiGraphics) {
      Player player = Minecraft.getInstance().player;
      if (player == null) {
         return;
      }
      int left = width / 2 - 91;
      int top = height - 39;
      float amount = player.getAbsorptionAmount();
      if (!Float.isFinite(amount) || amount <= 0.0F) {
         return;
      }
      int absorption = Mth.ceil(amount);
      int hearts = Mth.ceil(absorption / 2.0F);
      int rows = Mth.ceil(hearts / 10.0F);
      int rowHeight = Math.max(10 - (rows - 2), 3);
      for (int i = 0; i < hearts; i++) {
         int x = left + i % 10 * 8;
         int y = top - i / 10 * rowHeight;
         boolean half = i == hearts - 1 && absorption % 2 == 1;
         guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HeartSpriteHelper.container(player, false), x, y, 9, 9);
         guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HeartSpriteHelper.heart(player, true, half, false), x, y, 9, 9);
      }
   }
}

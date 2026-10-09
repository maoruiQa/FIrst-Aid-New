package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.gui.FirstaidIngameGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class GuiMixin {
   @Inject(method = "extractHearts", at = @At("HEAD"), cancellable = true)
   private void firstaid$extractHearts(
      GuiGraphicsExtractor guiGraphics,
      Player player,
      int left,
      int top,
      int rowHeight,
      int regen,
      float maxHealth,
      int currentHealth,
      int displayHealth,
      int absorption,
      boolean blinking,
      CallbackInfo ci
   ) {
      ci.cancel();
      Minecraft mc = Minecraft.getInstance();
      if (mc.gameMode != null && mc.gameMode.canHurtPlayer() && !mc.gui.hud.isHidden()) {
         FirstaidIngameGui.renderHealth(guiGraphics.guiWidth(), guiGraphics.guiHeight(), guiGraphics);
      }
   }
}

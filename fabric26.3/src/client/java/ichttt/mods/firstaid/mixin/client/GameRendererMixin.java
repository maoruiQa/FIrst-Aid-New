package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.ClientEventHandler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
   @Inject(method = "update", at = @At("TAIL"))
   private void firstaid$updatePostEffect(DeltaTracker deltaTracker, CallbackInfo ci) {
      ClientEventHandler.getPainVisualEffectsController().updatePostEffect(Minecraft.getInstance());
   }
}

package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.ClientEventHandler;
import ichttt.mods.firstaid.client.SuppressionFeedbackController;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
   @Shadow
   @Final
   private GameRenderState gameRenderState;

   @Inject(method = "extractCamera", at = @At("TAIL"))
   private void firstaid$extractCamera(net.minecraft.client.DeltaTracker deltaTracker, float partialTick, float tickProgress, CallbackInfo ci) {
      SuppressionFeedbackController controller = ClientEventHandler.getSuppressionFeedbackController();
      CameraRenderState cameraRenderState = this.gameRenderState.levelRenderState.cameraRenderState;
      cameraRenderState.hudFov = controller.applyFov(cameraRenderState.hudFov);
   }
}

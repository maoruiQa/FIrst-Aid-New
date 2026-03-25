package ichttt.mods.firstaid.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ichttt.mods.firstaid.client.RenderStateExtensions;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
   @Inject(method = "setupRotations", at = @At("HEAD"), cancellable = true)
   private void firstaid$setupRotations(AvatarRenderState renderState, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
      if (RenderStateExtensions.shouldApplyUnconsciousAttributes(renderState)) {
         float collapseProgress = RenderStateExtensions.getCollapseProgress(renderState);
         poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(collapseProgress, 180.0F - bodyRot, 90.0F - bodyRot)));
         poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F * collapseProgress));
         poseStack.mulPose(Axis.YP.rotationDegrees(270.0F * collapseProgress));
         poseStack.translate(0.0, -0.9 * collapseProgress, -0.1 * collapseProgress);
         ci.cancel();
      }
   }
}

package ichttt.mods.firstaid.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ichttt.mods.firstaid.client.RenderStateExtensions;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(
            method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void firstaid$setupRotations(AbstractClientPlayer entity, PoseStack poseStack, float bob, float bodyRot, float partialTick, float scale, CallbackInfo ci) {
        if (!RenderStateExtensions.shouldApplyUnconsciousAttributes(entity)) {
            return;
        }

        float collapseProgress = RenderStateExtensions.getCollapseProgress(entity);
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(collapseProgress, 180.0F - bodyRot, 90.0F - bodyRot)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F * collapseProgress));
        poseStack.mulPose(Axis.YP.rotationDegrees(270.0F * collapseProgress));
        poseStack.translate(0.0D, -0.9D * collapseProgress, -0.16D * collapseProgress);
        ci.cancel();
    }
}

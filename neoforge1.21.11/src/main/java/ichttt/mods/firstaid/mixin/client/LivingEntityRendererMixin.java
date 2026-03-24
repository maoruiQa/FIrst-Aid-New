package ichttt.mods.firstaid.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ichttt.mods.firstaid.client.RenderStateExtensions;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends EntityRenderer<T, S> {

    protected LivingEntityRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method = "setupRotations", at = @At("HEAD"), cancellable = true)
    private void firstaid$setupRotations(S renderState, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        if (!RenderStateExtensions.shouldApplyUnconsciousAttributes(renderState)) {
            return;
        }

        float collapseProgress = RenderStateExtensions.getCollapseProgress(renderState);
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(collapseProgress, 180.0F - bodyRot, 90.0F - bodyRot)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F * collapseProgress));
        poseStack.mulPose(Axis.YP.rotationDegrees(270.0F * collapseProgress));
        poseStack.translate(0.0D, -0.9D * collapseProgress, -0.1D * collapseProgress);
        ci.cancel();
    }
}

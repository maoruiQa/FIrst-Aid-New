/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  com.mojang.math.Axis
 *  net.minecraft.client.model.EntityModel
 *  net.minecraft.client.renderer.entity.EntityRenderer
 *  net.minecraft.client.renderer.entity.EntityRendererProvider$Context
 *  net.minecraft.client.renderer.entity.LivingEntityRenderer
 *  net.minecraft.client.renderer.entity.state.LivingEntityRenderState
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.LivingEntity
 *  org.joml.Quaternionfc
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
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
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={LivingEntityRenderer.class})
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>>
extends EntityRenderer<T, S> {
    protected LivingEntityRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method={"setupRotations"}, at={@At(value="HEAD")}, cancellable=true)
    private void firstaid$setupRotations(S renderState, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        if (!RenderStateExtensions.shouldApplyUnconsciousAttributes(renderState)) {
            return;
        }
        float collapseProgress = RenderStateExtensions.getCollapseProgress(renderState);
        poseStack.mulPose((Quaternionfc)Axis.YP.rotationDegrees(Mth.lerp((float)collapseProgress, (float)(180.0f - bodyRot), (float)(90.0f - bodyRot))));
        poseStack.mulPose((Quaternionfc)Axis.ZP.rotationDegrees(90.0f * collapseProgress));
        poseStack.mulPose((Quaternionfc)Axis.YP.rotationDegrees(270.0f * collapseProgress));
        poseStack.translate(0.0, -0.9 * (double)collapseProgress, -0.1 * (double)collapseProgress);
        ci.cancel();
    }
}


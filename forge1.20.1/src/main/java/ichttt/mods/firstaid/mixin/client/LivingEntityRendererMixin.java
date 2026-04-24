/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ichttt.mods.firstaid.client.RenderStateExtensions;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends net.minecraft.client.model.EntityModel<T>> {
    @Inject(method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V", at = @At("HEAD"), cancellable = true)
    private void firstaid$setupRotations(T entity, PoseStack poseStack, float bob, float bodyRot, float partialTick, CallbackInfo ci) {
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

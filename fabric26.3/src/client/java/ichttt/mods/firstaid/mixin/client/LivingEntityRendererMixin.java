package ichttt.mods.firstaid.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On 26.2, face-down crawl is driven by isVisuallySwimming + swimAmount.
 * Extra XP pitch/translate here buried the head in the ground.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>>
   extends EntityRenderer<T, S> {

   protected LivingEntityRendererMixin(Context context) {
      super(context);
   }

   @Inject(method = "setupRotations", at = @At("HEAD"))
   private void firstaid$setupRotations(S renderState, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
      // intentionally no-op
   }
}

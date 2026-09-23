package ichttt.mods.firstaid.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On 26.2, face-down crawl is driven by isVisuallySwimming + swimAmount.
 * Extra XP pitch/translate here buried the head in the ground.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
   @Inject(method = "setupRotations", at = @At("HEAD"))
   private void firstaid$setupRotations(AvatarRenderState renderState, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
      // intentionally no-op
   }
}

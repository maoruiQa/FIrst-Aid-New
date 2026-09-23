package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.RenderStateExtensions;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Speeds crawl limb animation while downed so limb cadence matches reduced crawl speed.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin extends HumanoidModel<AvatarRenderState> {
   private static final float CRAWL_ANIM_POS_MULT = 2.85F;
   private static final float CRAWL_ANIM_SPEED_MULT = 2.50F;
   private static final float CRAWL_ANIM_SPEED_MIN = 0.45F;

   protected PlayerModelMixin(ModelPart root) {
      super(root);
   }

   @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("HEAD"))
   private void firstaid$boostCrawlAnim(AvatarRenderState renderState, CallbackInfo ci) {
      boolean unconscious = renderState.getDataOrDefault(RenderStateExtensions.UNCONSCIOUS, false);
      if (!unconscious) {
         return;
      }
      renderState.walkAnimationPos *= CRAWL_ANIM_POS_MULT;
      if (renderState.walkAnimationSpeed > 0.01F) {
         renderState.walkAnimationSpeed = Mth.clamp(
            Math.max(renderState.walkAnimationSpeed * CRAWL_ANIM_SPEED_MULT, CRAWL_ANIM_SPEED_MIN),
            0.0F,
            1.0F
         );
      }
   }
}

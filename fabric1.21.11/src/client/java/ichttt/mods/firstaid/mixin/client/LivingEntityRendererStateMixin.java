package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.RenderStateExtensions;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererStateMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void firstaid$extractRenderState(T entity, S renderState, float partialTick, CallbackInfo ci) {
        if (!(renderState instanceof FabricRenderState fabricState)) {
            return;
        }
        fabricState.setData(RenderStateExtensions.PASSENGER, entity.isPassenger());
        boolean unconscious = false;
        float collapseProgress = 1.0F;
        if (entity instanceof Player player) {
            var damageModel = CommonUtils.getExistingDamageModel(player);
            if (damageModel instanceof PlayerDamageModel playerDamageModel) {
                unconscious = playerDamageModel.isUnconscious();
                collapseProgress = playerDamageModel.getCollapseAnimationProgress(partialTick);
            }
        }
        fabricState.setData(RenderStateExtensions.UNCONSCIOUS, unconscious);
        fabricState.setData(RenderStateExtensions.COLLAPSE_PROGRESS, collapseProgress);
    }
}

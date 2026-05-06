/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel
 *  ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel
 *  ichttt.mods.firstaid.common.util.CommonUtils
 *  net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState
 *  net.minecraft.client.model.EntityModel
 *  net.minecraft.client.renderer.entity.LivingEntityRenderer
 *  net.minecraft.client.renderer.entity.state.LivingEntityRenderState
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
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

@Mixin(value={LivingEntityRenderer.class})
public abstract class LivingEntityRendererStateMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Inject(method={"extractRenderState"}, at={@At(value="TAIL")})
    private void firstaid$extractRenderState(T entity, S renderState, float partialTick, CallbackInfo ci) {
        Player player;
        AbstractPlayerDamageModel damageModel;
        if (!(renderState instanceof FabricRenderState)) {
            return;
        }
        S fabricState = renderState;
        fabricState.setData(RenderStateExtensions.PASSENGER, (Object)entity.isPassenger());
        boolean unconscious = false;
        float collapseProgress = 1.0f;
        if (entity instanceof Player && (damageModel = CommonUtils.getExistingDamageModel((Player)(player = (Player)entity))) instanceof PlayerDamageModel) {
            PlayerDamageModel playerDamageModel = (PlayerDamageModel)damageModel;
            unconscious = playerDamageModel.isUnconscious();
            collapseProgress = playerDamageModel.getCollapseAnimationProgress(partialTick);
        }
        fabricState.setData(RenderStateExtensions.UNCONSCIOUS, (Object)unconscious);
        fabricState.setData(RenderStateExtensions.COLLAPSE_PROGRESS, (Object)Float.valueOf(collapseProgress));
    }
}


/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.model.HumanoidModel
 *  net.minecraft.client.model.geom.ModelPart
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.renderer.entity.state.AvatarRenderState
 *  net.minecraft.client.renderer.entity.state.LivingEntityRenderState
 *  net.minecraft.util.Mth
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.RenderStateExtensions;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={PlayerModel.class})
public abstract class PlayerModelMixin
extends HumanoidModel<AvatarRenderState> {
    protected PlayerModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method={"setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V"}, at={@At(value="TAIL")})
    private void firstaid$setupAnim(AvatarRenderState renderState, CallbackInfo ci) {
        if (!RenderStateExtensions.shouldApplyUnconsciousAttributes((LivingEntityRenderState)renderState)) {
            return;
        }
        float collapseProgress = RenderStateExtensions.getCollapseProgress((LivingEntityRenderState)renderState);
        PlayerModel model = (PlayerModel)this;
        model.head.xRot = Mth.lerp((float)collapseProgress, (float)model.head.xRot, (float)0.0f);
        model.head.yRot = Mth.lerp((float)collapseProgress, (float)model.head.yRot, (float)0.0f);
        model.rightArm.xRot = Mth.lerp((float)collapseProgress, (float)model.rightArm.xRot, (float)0.0f);
        model.rightArm.zRot = Mth.lerp((float)collapseProgress, (float)model.rightArm.zRot, (float)((float)Math.toRadians(20.0)));
        model.rightSleeve.xRot = model.rightArm.xRot;
        model.rightSleeve.zRot = model.rightArm.zRot;
        model.leftArm.xRot = Mth.lerp((float)collapseProgress, (float)model.leftArm.xRot, (float)0.0f);
        model.leftArm.zRot = Mth.lerp((float)collapseProgress, (float)model.leftArm.zRot, (float)((float)Math.toRadians(-40.0)));
        model.leftSleeve.xRot = model.leftArm.xRot;
        model.leftSleeve.zRot = model.leftArm.zRot;
        model.rightLeg.xRot = Mth.lerp((float)collapseProgress, (float)model.rightLeg.xRot, (float)0.0f);
        model.rightLeg.zRot = Mth.lerp((float)collapseProgress, (float)model.rightLeg.zRot, (float)((float)Math.toRadians(10.0)));
        model.rightPants.xRot = model.rightLeg.xRot;
        model.rightPants.zRot = model.rightLeg.zRot;
        model.leftLeg.xRot = Mth.lerp((float)collapseProgress, (float)model.leftLeg.xRot, (float)0.0f);
        model.leftLeg.zRot = Mth.lerp((float)collapseProgress, (float)model.leftLeg.zRot, (float)((float)Math.toRadians(-15.0)));
        model.leftPants.xRot = model.leftLeg.xRot;
        model.leftPants.zRot = model.leftLeg.zRot;
    }
}


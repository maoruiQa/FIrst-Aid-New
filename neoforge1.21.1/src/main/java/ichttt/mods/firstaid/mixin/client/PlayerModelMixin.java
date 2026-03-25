package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.RenderStateExtensions;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Shadow
    public float swimAmount;

    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL")
    )
    private void firstaid$setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!RenderStateExtensions.shouldApplyUnconsciousAttributes(entity)) {
            return;
        }

        float collapseProgress = RenderStateExtensions.getCollapseProgress(entity);
        PlayerModel model = (PlayerModel) (Object) this;
        this.swimAmount = 0.0F;
        model.body.xRot = Mth.lerp(collapseProgress, model.body.xRot, 0.0F);
        model.body.yRot = Mth.lerp(collapseProgress, model.body.yRot, 0.0F);
        model.body.zRot = Mth.lerp(collapseProgress, model.body.zRot, 0.0F);
        model.jacket.xRot = model.body.xRot;
        model.jacket.yRot = model.body.yRot;
        model.jacket.zRot = model.body.zRot;

        model.head.xRot = Mth.lerp(collapseProgress, model.head.xRot, 0.0F);
        model.head.yRot = Mth.lerp(collapseProgress, model.head.yRot, 0.0F);

        model.rightArm.xRot = Mth.lerp(collapseProgress, model.rightArm.xRot, 0.0F);
        model.rightArm.zRot = Mth.lerp(collapseProgress, model.rightArm.zRot, (float) Math.toRadians(20.0F));
        model.rightSleeve.xRot = model.rightArm.xRot;
        model.rightSleeve.zRot = model.rightArm.zRot;

        model.leftArm.xRot = Mth.lerp(collapseProgress, model.leftArm.xRot, 0.0F);
        model.leftArm.zRot = Mth.lerp(collapseProgress, model.leftArm.zRot, (float) Math.toRadians(-40.0F));
        model.leftSleeve.xRot = model.leftArm.xRot;
        model.leftSleeve.zRot = model.leftArm.zRot;

        model.rightLeg.xRot = Mth.lerp(collapseProgress, model.rightLeg.xRot, 0.0F);
        model.rightLeg.zRot = Mth.lerp(collapseProgress, model.rightLeg.zRot, (float) Math.toRadians(10.0F));
        model.rightPants.xRot = model.rightLeg.xRot;
        model.rightPants.zRot = model.rightLeg.zRot;

        model.leftLeg.xRot = Mth.lerp(collapseProgress, model.leftLeg.xRot, 0.0F);
        model.leftLeg.zRot = Mth.lerp(collapseProgress, model.leftLeg.zRot, (float) Math.toRadians(-15.0F));
        model.leftPants.xRot = model.leftLeg.xRot;
        model.leftPants.zRot = model.leftLeg.zRot;
    }
}

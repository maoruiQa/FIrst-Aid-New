package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.ClientEventHandler;
import ichttt.mods.firstaid.client.SuppressionFeedbackController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique private float firstaid$roll;
    @Shadow private Entity entity;
    @Shadow private float partialTickTime;

    @ModifyArgs(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 0))
    private void firstaid$cameraAngles(Args args) {
        SuppressionFeedbackController.CameraAngles angles = ClientEventHandler.getSuppressionFeedbackController()
            .applyCameraAngles(this.entity, this.partialTickTime, args.get(0), args.get(1));
        this.firstaid$roll = angles.roll();
        args.set(0, angles.yaw());
        args.set(1, angles.pitch());
    }

    // Roll around the view axis inside vanilla rotation so direction vectors and cached matrices stay coherent.
    @ModifyArg(method = "setRotation", at = @At(value = "INVOKE", target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;"), index = 2)
    private float firstaid$applyRoll(float roll) {
        return roll - this.firstaid$roll * ((float) Math.PI / 180.0F);
    }

    @Inject(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1))
    private void firstaid$mirrorRoll(CallbackInfo ci) {
        this.firstaid$roll = -this.firstaid$roll;
    }

    @Inject(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 2))
    private void firstaid$sleepingRoll(CallbackInfo ci) {
        this.firstaid$roll = 0.0F;
    }
}

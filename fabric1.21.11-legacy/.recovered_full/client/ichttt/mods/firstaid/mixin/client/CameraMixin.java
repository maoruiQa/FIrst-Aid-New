/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Camera
 *  net.minecraft.world.entity.Entity
 *  org.joml.Quaternionf
 *  org.joml.Quaternionfc
 *  org.joml.Vector3f
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.ClientEventHandler;
import ichttt.mods.firstaid.client.SuppressionFeedbackController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Camera.class})
public abstract class CameraMixin {
    @Shadow
    @Final
    private static Vector3f FORWARDS;
    @Shadow
    @Final
    private static Vector3f UP;
    @Shadow
    @Final
    private static Vector3f LEFT;
    @Shadow
    private float xRot;
    @Shadow
    private float yRot;
    @Shadow
    private float partialTickTime;
    @Shadow
    private Entity entity;
    @Shadow
    @Final
    private Quaternionf rotation;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    @Final
    private Vector3f up;
    @Shadow
    @Final
    private Vector3f left;

    @Inject(method={"setRotation"}, at={@At(value="TAIL")})
    private void firstaid$setRotation(float yRot, float xRot, CallbackInfo ci) {
        SuppressionFeedbackController controller = ClientEventHandler.getSuppressionFeedbackController();
        SuppressionFeedbackController.CameraAngles angles = controller.applyCameraAngles(this.entity, this.partialTickTime, yRot, xRot);
        if (angles.yaw() == yRot && angles.pitch() == xRot && angles.roll() == 0.0f) {
            return;
        }
        this.xRot = angles.pitch();
        this.yRot = angles.yaw();
        this.rotation.rotationYXZ((float)Math.PI - this.yRot * ((float)Math.PI / 180), -this.xRot * ((float)Math.PI / 180), -angles.roll() * ((float)Math.PI / 180));
        FORWARDS.rotate((Quaternionfc)this.rotation, this.forwards);
        UP.rotate((Quaternionfc)this.rotation, this.up);
        LEFT.rotate((Quaternionfc)this.rotation, this.left);
    }
}


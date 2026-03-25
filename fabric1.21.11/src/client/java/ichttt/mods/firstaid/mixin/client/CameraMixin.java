package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.ClientEventHandler;
import ichttt.mods.firstaid.client.SuppressionFeedbackController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
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

   @Inject(method = "setRotation", at = @At("TAIL"))
   private void firstaid$setRotation(float yRot, float xRot, CallbackInfo ci) {
      SuppressionFeedbackController controller = ClientEventHandler.getSuppressionFeedbackController();
      SuppressionFeedbackController.CameraAngles angles = controller.applyCameraAngles(this.entity, this.partialTickTime, yRot, xRot);
      if (angles.yaw() != yRot || angles.pitch() != xRot || angles.roll() != 0.0F) {
         this.xRot = angles.pitch();
         this.yRot = angles.yaw();
         this.rotation
            .rotationYXZ(
               (float) Math.PI - this.yRot * (float) (Math.PI / 180.0), -this.xRot * (float) (Math.PI / 180.0), -angles.roll() * (float) (Math.PI / 180.0)
            );
         FORWARDS.rotate(this.rotation, this.forwards);
         UP.rotate(this.rotation, this.up);
         LEFT.rotate(this.rotation, this.left);
      }
   }
}

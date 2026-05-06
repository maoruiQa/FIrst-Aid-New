/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Camera
 *  net.minecraft.client.renderer.GameRenderer
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.ClientEventHandler;
import ichttt.mods.firstaid.client.SuppressionFeedbackController;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={GameRenderer.class})
public abstract class GameRendererMixin {
    @Inject(method={"getFov"}, at={@At(value="RETURN")}, cancellable=true)
    private void firstaid$getFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        SuppressionFeedbackController controller = ClientEventHandler.getSuppressionFeedbackController();
        cir.setReturnValue((Object)Float.valueOf(controller.applyFov(cir.getReturnValueF())));
    }
}


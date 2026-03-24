package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.ClientEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    private static final double UNCONSCIOUS_TURN_SCALE = 0.12D;

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void firstaid$turnPlayer(double movementTime, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && ClientEventHandler.isUnconscious(mc.player)) {
            this.accumulatedDX *= UNCONSCIOUS_TURN_SCALE;
            this.accumulatedDY *= UNCONSCIOUS_TURN_SCALE;
        }
    }
}

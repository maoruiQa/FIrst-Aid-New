/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.MouseHandler
 *  net.minecraft.world.entity.player.Player
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.ClientEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={MouseHandler.class})
public abstract class MouseHandlerMixin {
    private static final double UNCONSCIOUS_TURN_SCALE = 0.12;
    @Shadow
    private double accumulatedDX;
    @Shadow
    private double accumulatedDY;

    @Inject(method={"turnPlayer"}, at={@At(value="HEAD")})
    private void firstaid$turnPlayer(double movementTime, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && ClientEventHandler.isUnconscious((Player)mc.player)) {
            this.accumulatedDX *= 0.12;
            this.accumulatedDY *= 0.12;
        }
    }
}


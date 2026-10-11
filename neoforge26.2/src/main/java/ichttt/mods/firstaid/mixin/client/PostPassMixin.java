package ichttt.mods.firstaid.mixin.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import ichttt.mods.firstaid.client.ClientEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostPass;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(PostPass.class)
public abstract class PostPassMixin {
    @Shadow @Final private String name;
    @Shadow @Final private Map<String, GpuBuffer> customUniforms;

    @Inject(method = "addToFrame", at = @At("HEAD"))
    private void firstaid$updateBlur(CallbackInfo ci) {
        GpuBuffer buffer = customUniforms.get("BlurSettings");
        if (!name.startsWith("firstaid:") || buffer == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float strength = ClientEventHandler.getPainVisualEffectsController().getBlurStrength(partialTick);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, 16)
                    .putVec2(0.5F, 0.5F).putInt(12).putFloat(strength).get();
            if ((buffer.usage() & GpuBuffer.USAGE_COPY_DST) == 0) {
                // Vanilla's baked uniform buffer is immutable; PostPass owns and closes the replacement.
                GpuBuffer writable = RenderSystem.getDevice().createBuffer(
                        () -> name + " / BlurSettings", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, data);
                customUniforms.put("BlurSettings", writable);
                buffer.close();
            } else {
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
            }
        }
    }
}

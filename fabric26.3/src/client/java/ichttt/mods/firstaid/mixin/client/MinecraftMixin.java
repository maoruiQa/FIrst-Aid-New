package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.ClientEventHandler;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
   @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
   private void firstaid$suspendPostEffects(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
      Minecraft client = (Minecraft) (Object) this;
      if (client.player != null) {
         ClientEventHandler.getPainVisualEffectsController().suspendForReload(client);
      }
   }

   @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"))
   private void firstaid$resumePostEffects(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
      Minecraft client = (Minecraft) (Object) this;
      if (client.player != null) {
         cir.getReturnValue().whenComplete((ignored, failure) -> client.execute(
               () -> ClientEventHandler.getPainVisualEffectsController().resumeAfterReload(client)
         ));
      }
   }
}

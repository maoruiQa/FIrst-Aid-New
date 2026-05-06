package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.ClientEventHandler;
import ichttt.mods.firstaid.client.SuppressionFeedbackController;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
   @ModifyVariable(method = "play", at = @At("HEAD"), argsOnly = true)
   private SoundInstance firstaid$modifySound(SoundInstance sound) {
      SuppressionFeedbackController controller = ClientEventHandler.getSuppressionFeedbackController();
      SoundInstance modified = controller.maybeMuffle(sound);
      return modified == null ? sound : modified;
   }
}

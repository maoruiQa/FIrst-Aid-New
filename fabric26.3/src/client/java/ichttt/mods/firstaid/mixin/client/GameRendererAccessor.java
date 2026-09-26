package ichttt.mods.firstaid.mixin.client;

import java.util.List;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
   @Invoker("preparePostEffects")
   void firstaid$preparePostEffects(List<Identifier> effects);
}

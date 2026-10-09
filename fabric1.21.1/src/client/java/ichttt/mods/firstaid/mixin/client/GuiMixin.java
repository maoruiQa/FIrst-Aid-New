package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.gui.FirstaidIngameGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "renderHearts", at = @At("HEAD"), cancellable = true)
    private void firstaid$renderHearts(GuiGraphics guiGraphics, Player player, int x, int y, int lineHeight, int regen, float maxHealth, int currentHealth, int displayHealth, int absorption, boolean blinking, CallbackInfo ci) {
        ci.cancel();
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null && mc.gameMode.canHurtPlayer() && !mc.options.hideGui) {
            FirstaidIngameGui.renderHealth((Gui) (Object) this, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), guiGraphics);
        }
    }
}

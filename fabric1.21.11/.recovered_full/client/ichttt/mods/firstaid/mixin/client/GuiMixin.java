/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ichttt.mods.firstaid.FirstAidConfig
 *  ichttt.mods.firstaid.FirstAidConfig$Client$VanillaHealthbarMode
 *  ichttt.mods.firstaid.FirstAidConfig$Server$VanillaHealthCalculationMode
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Gui
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.world.entity.player.Player
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.client.gui.FirstaidIngameGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Gui.class})
public abstract class GuiMixin {
    @Inject(method={"renderHearts"}, at={@At(value="HEAD")}, cancellable=true)
    private void firstaid$renderHearts(GuiGraphics guiGraphics, Player player, int x, int y, int lineHeight, int regen, float maxHealth, int currentHealth, int displayHealth, int absorption, boolean blinking, CallbackInfo ci) {
        FirstAidConfig.Client.VanillaHealthbarMode mode = (FirstAidConfig.Client.VanillaHealthbarMode)FirstAidConfig.CLIENT.vanillaHealthBarMode.get();
        if (mode == FirstAidConfig.Client.VanillaHealthbarMode.NORMAL) {
            return;
        }
        ci.cancel();
        if (mode != FirstAidConfig.Client.VanillaHealthbarMode.HIGHLIGHT_CRITICAL_PATH || FirstAidConfig.SERVER.vanillaHealthCalculation.get() != FirstAidConfig.Server.VanillaHealthCalculationMode.AVERAGE_ALL) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Gui gui = (Gui)this;
        if (mc.gameMode != null && mc.gameMode.canHurtPlayer() && !mc.options.hideGui) {
            FirstaidIngameGui.renderHealth(gui, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), guiGraphics);
        }
    }
}


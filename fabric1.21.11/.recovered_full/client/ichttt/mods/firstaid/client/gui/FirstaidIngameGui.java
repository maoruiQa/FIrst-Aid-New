/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart
 *  ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel
 *  ichttt.mods.firstaid.common.util.CommonUtils
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Gui
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.resources.Identifier
 *  net.minecraft.util.Mth
 *  net.minecraft.world.effect.MobEffects
 *  net.minecraft.world.entity.ai.attributes.AttributeInstance
 *  net.minecraft.world.entity.ai.attributes.Attributes
 *  net.minecraft.world.entity.player.Player
 */
package ichttt.mods.firstaid.client.gui;

import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public final class FirstaidIngameGui {
    private static final Identifier HEART_CONTAINER_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/container");
    private static final Identifier HEART_CONTAINER_BLINKING_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/container_blinking");
    private static final Identifier HEART_FULL_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/full");
    private static final Identifier HEART_FULL_BLINKING_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/full_blinking");
    private static final Identifier HEART_HALF_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/half");
    private static final Identifier HEART_HALF_BLINKING_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/half_blinking");
    private static final Identifier HEART_POISONED_FULL_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/poisoned_full");
    private static final Identifier HEART_POISONED_FULL_BLINKING_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/poisoned_full_blinking");
    private static final Identifier HEART_POISONED_HALF_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/poisoned_half");
    private static final Identifier HEART_POISONED_HALF_BLINKING_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/poisoned_half_blinking");
    private static final Identifier HEART_WITHERED_FULL_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/withered_full");
    private static final Identifier HEART_WITHERED_FULL_BLINKING_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/withered_full_blinking");
    private static final Identifier HEART_WITHERED_HALF_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/withered_half");
    private static final Identifier HEART_WITHERED_HALF_BLINKING_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/withered_half_blinking");
    private static final Identifier HEART_ABSORBING_FULL_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/absorbing_full");
    private static final Identifier HEART_ABSORBING_FULL_BLINKING_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/absorbing_full_blinking");
    private static final Identifier HEART_ABSORBING_HALF_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/absorbing_half");
    private static final Identifier HEART_ABSORBING_HALF_BLINKING_SPRITE = Identifier.withDefaultNamespace((String)"hud/heart/absorbing_half_blinking");

    private FirstaidIngameGui() {
    }

    public static void renderHealth(Gui gui, int width, int height, GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        AbstractPlayerDamageModel damageModel = CommonUtils.getOptionalDamageModel((Player)minecraft.player).orElse(null);
        int criticalHalfHearts = 0;
        if (damageModel != null) {
            float criticalHealth = Float.MAX_VALUE;
            for (AbstractDamageablePart part : damageModel) {
                if (!part.canCauseDeath) continue;
                criticalHealth = Math.min(criticalHealth, part.currentHealth);
            }
            criticalHealth = criticalHealth / (float)damageModel.getCurrentMaxHealth() * minecraft.player.getMaxHealth();
            criticalHalfHearts = Mth.ceil((float)criticalHealth);
        }
        int health = Mth.ceil((float)player.getHealth());
        AttributeInstance attrMaxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        float healthMax = Math.max((float)attrMaxHealth.getValue(), (float)health);
        int absorption = Mth.ceil((float)player.getAbsorptionAmount());
        int healthRows = Mth.ceil((float)((healthMax + (float)absorption) / 2.0f / 10.0f));
        int rowHeight = Math.max(10 - (healthRows - 2), 3);
        int left = width / 2 - 91;
        int top = height - 39;
        boolean poisoned = player.hasEffect(MobEffects.POISON);
        boolean withered = !poisoned && player.hasEffect(MobEffects.WITHER);
        float absorptionRemaining = absorption;
        for (int i = Mth.ceil((float)((healthMax + (float)absorption) / 2.0f)) - 1; i >= 0; --i) {
            boolean criticalHalf = i * 2 + 1 == criticalHalfHearts;
            boolean criticalBlink = i * 2 < criticalHalfHearts && !criticalHalf;
            int row = Mth.ceil((float)((float)(i + 1) / 10.0f)) - 1;
            int x = left + i % 10 * 8;
            int y = top - row * rowHeight;
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, criticalBlink ? HEART_CONTAINER_BLINKING_SPRITE : HEART_CONTAINER_SPRITE, x, y, 9, 9);
            if (absorptionRemaining > 0.0f) {
                boolean halfAbsorption;
                boolean bl = halfAbsorption = absorptionRemaining == (float)absorption && absorption % 2 == 1;
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, halfAbsorption ? (criticalBlink ? HEART_ABSORBING_HALF_BLINKING_SPRITE : HEART_ABSORBING_HALF_SPRITE) : (criticalBlink ? HEART_ABSORBING_FULL_BLINKING_SPRITE : HEART_ABSORBING_FULL_SPRITE), x, y, 9, 9);
                absorptionRemaining -= absorptionRemaining == (float)absorption && absorption % 2 == 1 ? 1.0f : 2.0f;
                continue;
            }
            if (criticalHalf) {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, FirstaidIngameGui.getHeartSprite(poisoned, withered, true, true), x, y, 9, 9);
            }
            if (i * 2 + 1 < health) {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, FirstaidIngameGui.getHeartSprite(poisoned, withered, false, criticalBlink), x, y, 9, 9);
                continue;
            }
            if (i * 2 + 1 != health || criticalHalf) continue;
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, FirstaidIngameGui.getHeartSprite(poisoned, withered, true, criticalBlink), x, y, 9, 9);
        }
    }

    private static Identifier getHeartSprite(boolean poisoned, boolean withered, boolean halfHeart, boolean blinking) {
        if (poisoned) {
            return halfHeart ? (blinking ? HEART_POISONED_HALF_BLINKING_SPRITE : HEART_POISONED_HALF_SPRITE) : (blinking ? HEART_POISONED_FULL_BLINKING_SPRITE : HEART_POISONED_FULL_SPRITE);
        }
        if (withered) {
            return halfHeart ? (blinking ? HEART_WITHERED_HALF_BLINKING_SPRITE : HEART_WITHERED_HALF_SPRITE) : (blinking ? HEART_WITHERED_FULL_BLINKING_SPRITE : HEART_WITHERED_FULL_SPRITE);
        }
        return halfHeart ? (blinking ? HEART_HALF_BLINKING_SPRITE : HEART_HALF_SPRITE) : (blinking ? HEART_FULL_BLINKING_SPRITE : HEART_FULL_SPRITE);
    }
}


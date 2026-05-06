/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState
 *  net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
 *  net.minecraft.client.renderer.entity.state.LivingEntityRenderState
 */
package ichttt.mods.firstaid.client;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public final class RenderStateExtensions {
    public static final RenderStateDataKey<Boolean> PASSENGER = RenderStateDataKey.create(() -> "firstaid:passenger");
    public static final RenderStateDataKey<Boolean> UNCONSCIOUS = RenderStateDataKey.create(() -> "firstaid:unconscious");
    public static final RenderStateDataKey<Float> COLLAPSE_PROGRESS = RenderStateDataKey.create(() -> "firstaid:collapse_progress");

    private RenderStateExtensions() {
    }

    public static boolean shouldApplyUnconsciousAttributes(LivingEntityRenderState renderState) {
        if (!(renderState instanceof FabricRenderState)) {
            return false;
        }
        LivingEntityRenderState fabricState = renderState;
        boolean unconscious = (Boolean)fabricState.getDataOrDefault(UNCONSCIOUS, (Object)false);
        boolean passenger = (Boolean)fabricState.getDataOrDefault(PASSENGER, (Object)false);
        return unconscious && !passenger;
    }

    public static float getCollapseProgress(LivingEntityRenderState renderState) {
        if (!(renderState instanceof FabricRenderState)) {
            return 1.0f;
        }
        LivingEntityRenderState fabricState = renderState;
        return ((Float)fabricState.getDataOrDefault(COLLAPSE_PROGRESS, (Object)Float.valueOf(1.0f))).floatValue();
    }
}


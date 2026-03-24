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
        if (!(renderState instanceof FabricRenderState fabricState)) {
            return false;
        }
        boolean unconscious = fabricState.getDataOrDefault(UNCONSCIOUS, false);
        boolean passenger = fabricState.getDataOrDefault(PASSENGER, false);
        return unconscious && !passenger;
    }

    public static float getCollapseProgress(LivingEntityRenderState renderState) {
        if (!(renderState instanceof FabricRenderState fabricState)) {
            return 1.0F;
        }
        return fabricState.getDataOrDefault(COLLAPSE_PROGRESS, 1.0F);
    }
}

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
      } else {
         boolean unconscious = (Boolean)renderState.getDataOrDefault(UNCONSCIOUS, false);
         boolean passenger = (Boolean)renderState.getDataOrDefault(PASSENGER, false);
         return unconscious && !passenger;
      }
   }

   public static float getCollapseProgress(LivingEntityRenderState renderState) {
      return renderState instanceof FabricRenderState ? (Float)renderState.getDataOrDefault(COLLAPSE_PROGRESS, 1.0F) : 1.0F;
   }
}

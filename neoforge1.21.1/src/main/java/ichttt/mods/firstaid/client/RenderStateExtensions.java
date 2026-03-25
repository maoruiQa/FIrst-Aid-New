package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class RenderStateExtensions {
    private RenderStateExtensions() {
    }

    public static boolean shouldApplyUnconsciousAttributes(LivingEntity entity) {
        if (entity == null || entity.isPassenger()) {
            return false;
        }
        if (!(entity instanceof Player player)) {
            return false;
        }
        var damageModel = CommonUtils.getExistingDamageModel(player);
        return damageModel instanceof PlayerDamageModel playerDamageModel && playerDamageModel.isUnconscious();
    }

    public static float getCollapseProgress(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return 1.0F;
        }
        var damageModel = CommonUtils.getExistingDamageModel(player);
        return damageModel instanceof PlayerDamageModel playerDamageModel ? playerDamageModel.getCollapseAnimationProgress() : 1.0F;
    }
}

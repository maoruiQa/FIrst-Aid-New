package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla recalculates pose every tick. While downed, force swimming/crawl pose
 * so the client and server stay prone with crawl limb animation.
 */
@Mixin(Player.class)
public abstract class PlayerPoseMixin {

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void firstaid$forceUnconsciousPose(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player.isPassenger()) {
            return;
        }
        if (CommonUtils.getExistingDamageModel(player) instanceof PlayerDamageModel model && model.isUnconscious()) {
            Pose pose = model.shouldUseCrampedUnconsciousDimensions(player) ? Pose.CROUCHING : Pose.SWIMMING;
            player.setPose(pose);
            ci.cancel();
        }
    }
}

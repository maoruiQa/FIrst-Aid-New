package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Mob.class)
public abstract class MobTargetMixin {
   @ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
   private LivingEntity firstaid$replaceTarget(LivingEntity target) {
      return target instanceof Player player
            && CommonUtils.getDamageModel(player) instanceof PlayerDamageModel playerDamageModel
            && playerDamageModel.isUnconscious()
         ? null
         : target;
   }
}

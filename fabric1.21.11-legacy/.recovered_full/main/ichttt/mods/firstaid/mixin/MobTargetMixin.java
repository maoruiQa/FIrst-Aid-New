/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.Mob
 *  net.minecraft.world.entity.player.Player
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.ModifyVariable
 */
package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value={Mob.class})
public abstract class MobTargetMixin {
    @ModifyVariable(method={"setTarget"}, at=@At(value="HEAD"), argsOnly=true)
    private LivingEntity firstaid$replaceTarget(LivingEntity target) {
        PlayerDamageModel playerDamageModel;
        Player player;
        AbstractPlayerDamageModel damageModel;
        if (target instanceof Player && (damageModel = CommonUtils.getDamageModel(player = (Player)target)) instanceof PlayerDamageModel && (playerDamageModel = (PlayerDamageModel)damageModel).isUnconscious()) {
            return null;
        }
        return target;
    }
}


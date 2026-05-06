package ichttt.mods.firstaid.common.compat.playerrevive;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

public interface IPRCompatHandler {
   boolean tryKnockOutPlayer(Player var1, DamageSource var2);

   boolean isBleeding(Player var1);
}

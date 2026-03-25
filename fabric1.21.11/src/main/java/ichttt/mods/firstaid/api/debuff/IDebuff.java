package ichttt.mods.firstaid.api.debuff;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public interface IDebuff {
   void handleDamageTaken(float var1, float var2, ServerPlayer var3);

   void handleHealing(float var1, float var2, ServerPlayer var3);

   default void update(Player player, float healthFraction) {
   }
}

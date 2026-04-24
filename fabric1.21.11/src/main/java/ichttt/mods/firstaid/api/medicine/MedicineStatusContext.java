package ichttt.mods.firstaid.api.medicine;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class MedicineStatusContext {
   private final Player player;
   private final Level level;
   @Nullable
   private final AbstractPlayerDamageModel damageModel;

   public MedicineStatusContext(Player player, Level level, @Nullable AbstractPlayerDamageModel damageModel) {
      this.player = player;
      this.level = level;
      this.damageModel = damageModel;
   }

   @Nonnull
   public Player getPlayer() {
      return this.player;
   }

   @Nonnull
   public Level getLevel() {
      return this.level;
   }

   @Nullable
   public AbstractPlayerDamageModel getDamageModel() {
      return this.damageModel;
   }
}

package ichttt.mods.firstaid.api.healing;

import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import javax.annotation.Nonnull;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class PartHealingContext {
   private final Player player;
   private final Level level;
   private final ItemStack stack;
   private final AbstractPlayerDamageModel damageModel;
   private final AbstractDamageablePart damageablePart;
   private final AbstractPartHealer healer;

   public PartHealingContext(
      Player player, Level level, ItemStack stack, AbstractPlayerDamageModel damageModel, AbstractDamageablePart damageablePart, AbstractPartHealer healer
   ) {
      this.player = player;
      this.level = level;
      this.stack = stack;
      this.damageModel = damageModel;
      this.damageablePart = damageablePart;
      this.healer = healer;
   }

   @Nonnull
   public Player getPlayer() {
      return this.player;
   }

   @Nonnull
   public Level getLevel() {
      return this.level;
   }

   @Nonnull
   public ItemStack getStack() {
      return this.stack;
   }

   @Nonnull
   public AbstractPlayerDamageModel getDamageModel() {
      return this.damageModel;
   }

   @Nonnull
   public AbstractDamageablePart getDamageablePart() {
      return this.damageablePart;
   }

   @Nonnull
   public AbstractPartHealer getHealer() {
      return this.healer;
   }
}

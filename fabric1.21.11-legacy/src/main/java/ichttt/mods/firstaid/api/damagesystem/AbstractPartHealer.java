package ichttt.mods.firstaid.api.damagesystem;

import java.util.function.IntSupplier;
import net.minecraft.world.item.ItemStack;

public abstract class AbstractPartHealer {
   public final IntSupplier maxHeal;
   public final ItemStack stack;
   public final IntSupplier ticksPerHeal;

   public AbstractPartHealer(IntSupplier maxHeal, ItemStack stack, IntSupplier ticksPerHeal) {
      this.maxHeal = maxHeal;
      this.stack = stack;
      this.ticksPerHeal = ticksPerHeal;
   }

   public abstract AbstractPartHealer loadNBT(int var1, int var2);

   public abstract boolean hasFinished();

   public abstract boolean tick();

   public abstract int getTicksPassed();

   public abstract int getHealsDone();
}

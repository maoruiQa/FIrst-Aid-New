package ichttt.mods.firstaid.api.damagesystem;

import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.util.INBTSerializable;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public abstract class AbstractDamageablePart implements INBTSerializable<CompoundTag> {
   public final int initialMaxHealth;
   public final boolean canCauseDeath;
   @Nonnull
   public final EnumPlayerPart part;
   @Nullable
   public AbstractPartHealer activeHealer;
   public float currentHealth;

   public AbstractDamageablePart(int maxHealth, boolean canCauseDeath, @Nonnull EnumPlayerPart playerPart) {
      this.initialMaxHealth = maxHealth;
      this.canCauseDeath = canCauseDeath;
      this.part = playerPart;
   }

   public abstract float heal(float var1, @Nullable Player var2, boolean var3);

   public abstract float damage(float var1, @Nullable Player var2, boolean var3);

   public abstract float damage(float var1, @Nullable Player var2, boolean var3, float var4);

   public void tick(Level world, Player player, boolean tickDebuffs) {
      this.tick(world, player, tickDebuffs, false);
   }

   public abstract void tick(Level var1, Player var2, boolean var3, boolean var4);

   public abstract void setAbsorption(float var1);

   public abstract float getAbsorption();

   public abstract void setMaxHealth(int var1);

   public abstract int getMaxHealth();

   public abstract void loadDebuffInfo(IDebuff[] var1);
}

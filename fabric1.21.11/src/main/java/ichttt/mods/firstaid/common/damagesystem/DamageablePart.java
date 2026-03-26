package ichttt.mods.firstaid.common.damagesystem;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DamageablePart extends AbstractDamageablePart {
   private static final float LOW_DEBUFF_DAMAGE_SCALE = 0.4F;
   private int maxHealth;
   @Nullable
   private IDebuff[] debuffs;
   private float absorption;

   public DamageablePart(int maxHealth, boolean canCauseDeath, @Nonnull EnumPlayerPart playerPart) {
      super(maxHealth, canCauseDeath, playerPart);
      this.maxHealth = maxHealth;
      this.currentHealth = maxHealth;
   }

   @Override
   public void loadDebuffInfo(IDebuff[] debuffs) {
      this.debuffs = debuffs;
   }

   @Override
   public float heal(float amount, @Nullable Player player, boolean applyDebuff) {
      if (amount <= 0.0F) {
         return 0.0F;
      } else {
         float notFitting = Math.abs(Math.min(0.0F, this.maxHealth - (this.currentHealth + amount)));
         this.currentHealth = Math.min((float)this.maxHealth, this.currentHealth + amount);
         if (notFitting > 0.0F) {
            float oldHealth = this.currentHealth;
            this.currentHealth = Math.min(this.currentHealth + notFitting, this.currentHealth);
            notFitting -= this.currentHealth - oldHealth;
         }

         if (applyDebuff && this.debuffs != null && FirstAid.injuryDebuffMode != FirstAid.InjuryDebuffMode.OFF) {
            Objects.requireNonNull(player, "Got null player with applyDebuff = true");
            float debuffHealing = amount - notFitting;
            float debuffHealthFraction = this.currentHealth / this.maxHealth;
            if (FirstAid.injuryDebuffMode == FirstAid.InjuryDebuffMode.LOW) {
               debuffHealing *= 0.4F;
               debuffHealthFraction = softenDebuffHealthFraction(debuffHealthFraction);
            }

            for (IDebuff debuff : this.debuffs) {
               debuff.handleHealing(debuffHealing, debuffHealthFraction, (ServerPlayer)player);
            }
         }

         return notFitting;
      }
   }

   @Override
   public float damage(float amount, @Nullable Player player, boolean applyDebuff) {
      return this.damage(amount, player, applyDebuff, 0.0F);
   }

   @Override
   public float damage(float amount, @Nullable Player player, boolean applyDebuff, float minHealth) {
      if (amount <= 0.0F) {
         return 0.0F;
      } else if (minHealth > this.maxHealth) {
         throw new IllegalArgumentException("Cannot damage part with minHealth " + minHealth + " while he has more max health (" + this.maxHealth + ")");
      } else {
         float origAmount = amount;
         if (this.absorption > 0.0F) {
            amount = Math.abs(Math.min(0.0F, this.absorption - amount));
            this.absorption = Math.max(0.0F, this.absorption - origAmount);
         }

         float notFitting = Math.abs(Math.min(minHealth, this.currentHealth - amount) - minHealth);
         this.currentHealth = Math.max(minHealth, this.currentHealth - amount);
         if (applyDebuff && this.debuffs != null && FirstAid.injuryDebuffMode != FirstAid.InjuryDebuffMode.OFF) {
            Objects.requireNonNull(player, "Got null player with applyDebuff = true");
            float debuffDamage = origAmount - notFitting;
            float debuffHealthFraction = this.currentHealth / this.maxHealth;
            if (FirstAid.injuryDebuffMode == FirstAid.InjuryDebuffMode.LOW) {
               debuffDamage *= 0.4F;
               debuffHealthFraction = softenDebuffHealthFraction(debuffHealthFraction);
            }

            for (IDebuff debuff : this.debuffs) {
               debuff.handleDamageTaken(debuffDamage, debuffHealthFraction, (ServerPlayer)player);
            }
         }

         return notFitting;
      }
   }

   @Override
   public void tick(Level world, Player player, boolean tickDebuffs, boolean pauseHealing) {
      if (!pauseHealing && this.activeHealer != null) {
         if (this.activeHealer.tick()) {
            this.heal(1.0F, player, !world.isClientSide());
         }

         if (this.activeHealer.hasFinished()) {
            this.activeHealer = null;
         }
      }

      if (!world.isClientSide() && tickDebuffs && this.debuffs != null && FirstAid.injuryDebuffMode != FirstAid.InjuryDebuffMode.OFF) {
         float debuffHealthFraction = this.currentHealth / this.maxHealth;
         if (FirstAid.injuryDebuffMode == FirstAid.InjuryDebuffMode.LOW) {
            debuffHealthFraction = softenDebuffHealthFraction(debuffHealthFraction);
         }

         for (IDebuff debuff : this.debuffs) {
            debuff.update(player, debuffHealthFraction);
         }
      }
   }

   public CompoundTag serializeNBT() {
      CompoundTag compound = new CompoundTag();
      compound.putFloat("health", this.currentHealth);
      if (FirstAidConfig.SERVER.scaleMaxHealth.get()) {
         compound.putInt("maxHealth", this.maxHealth);
      }

      if (this.absorption > 0.0F) {
         compound.putFloat("absorption", this.absorption);
      }

      if (this.activeHealer != null) {
         Identifier itemId = BuiltInRegistries.ITEM.getKey(this.activeHealer.stack.getItem());
         if (itemId != null) {
            compound.putString("healerItem", itemId.toString());
         }

         compound.putInt("itemTicks", this.activeHealer.getTicksPassed());
         compound.putInt("itemHeals", this.activeHealer.getHealsDone());
      }

      return compound;
   }

   public void deserializeNBT(@Nullable CompoundTag nbt) {
      if (nbt != null) {
         this.activeHealer = null;
         this.absorption = 0.0F;
         if (nbt.contains("maxHealth") && FirstAidConfig.SERVER.scaleMaxHealth.get()) {
            this.maxHealth = nbt.getIntOr("maxHealth", this.maxHealth);
         }

         this.currentHealth = Math.min((float)this.maxHealth, nbt.getFloatOr("health", this.currentHealth));
         ItemStack stack = null;
         if (nbt.contains("healerItem")) {
            Identifier itemId = Identifier.tryParse(nbt.getString("healerItem").orElse(""));
            if (itemId != null) {
               Item item = (Item)BuiltInRegistries.ITEM.getValue(itemId);
               if (item != null) {
                  stack = new ItemStack(item);
               }
            }
         }

         if (stack != null) {
            Item item = stack.getItem();
            AbstractPartHealer healer = null;
            if (item instanceof ItemHealing itemHealing) {
               healer = itemHealing.createNewHealer(stack);
            }

            if (healer == null) {
               FirstAid.LOGGER.warn("Failed to lookup healer for item {}", stack.getItem());
            } else {
               this.activeHealer = healer.loadNBT(nbt.getIntOr("itemTicks", 0), nbt.getIntOr("itemHeals", 0));
            }
         }

         if (nbt.contains("absorption")) {
            this.absorption = nbt.getFloatOr("absorption", 0.0F);
         }

         if (this.debuffs != null) {
            for (IDebuff debuff : this.debuffs) {
               debuff.handleHealing(0.0F, this.currentHealth / this.maxHealth, null);
            }
         }
      }
   }

   @Override
   public void setAbsorption(float absorption) {
      if (absorption > 4.0F && FirstAidConfig.SERVER.capMaxHealth.get()) {
         absorption = 4.0F;
      }

      if (absorption > 32.0F) {
         absorption = 32.0F;
      }

      this.absorption = absorption;
      this.currentHealth = Math.min(this.maxHealth + absorption, this.currentHealth);
   }

   @Override
   public float getAbsorption() {
      return this.absorption;
   }

   @Override
   public void setMaxHealth(int maxHealth) {
      if (maxHealth > 12 && FirstAidConfig.SERVER.capMaxHealth.get()) {
         maxHealth = 12;
      }

      if (maxHealth > 128) {
         maxHealth = 128;
      }

      this.maxHealth = Math.max(2, maxHealth);
      this.currentHealth = Math.min(this.currentHealth, (float)this.maxHealth);
   }

   @Override
   public int getMaxHealth() {
      return this.maxHealth;
   }

   private static float softenDebuffHealthFraction(float healthFraction) {
      return Math.min(1.0F, 0.5F + healthFraction * 0.5F);
   }
}

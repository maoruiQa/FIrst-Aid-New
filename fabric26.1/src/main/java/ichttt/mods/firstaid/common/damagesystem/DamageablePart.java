package ichttt.mods.firstaid.common.damagesystem;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.api.healing.PartHealingContext;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
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
   private int maxHealth;
   @Nullable
   private IDebuff[] debuffs;

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
               debuffHealing *= FirstAid.lowInjuryDebuffDamageScale;
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
         float notFitting = Math.abs(Math.min(minHealth, this.currentHealth - amount) - minHealth);
         this.currentHealth = Math.max(minHealth, this.currentHealth - amount);
         if (applyDebuff && this.debuffs != null && FirstAid.injuryDebuffMode != FirstAid.InjuryDebuffMode.OFF) {
            Objects.requireNonNull(player, "Got null player with applyDebuff = true");
            float debuffDamage = amount - notFitting;
            float debuffHealthFraction = this.currentHealth / this.maxHealth;
            if (FirstAid.injuryDebuffMode == FirstAid.InjuryDebuffMode.LOW) {
               debuffDamage *= FirstAid.lowInjuryDebuffDamageScale;
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
         AbstractPartHealer healer = this.activeHealer;
         ItemHealing healingItem = healer.stack.getItem() instanceof ItemHealing itemHealing ? itemHealing : null;
         PartHealingContext context = createHealingContext(player, world, healer);
         if (healer.tick()) {
            float previousHealth = this.currentHealth;
            this.heal(1.0F, player, !world.isClientSide());
            if (healingItem != null && context != null && this.currentHealth > previousHealth) {
               healingItem.onHealPulse(context);
            }
         }

         if (healer.hasFinished()) {
            if (healingItem != null && context != null) {
               healingItem.onTreatmentCompleted(context);
            }

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

         if (this.debuffs != null) {
            for (IDebuff debuff : this.debuffs) {
               debuff.handleHealing(0.0F, this.currentHealth / this.maxHealth, null);
            }
         }
      }
   }

   public void setAbsorption(float absorption) {}

   public float getAbsorption() {
      return 0F;
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

   @Nullable
   private PartHealingContext createHealingContext(Player player, Level world, AbstractPartHealer healer) {
      AbstractPlayerDamageModel damageModel = player == null ? null : ichttt.mods.firstaid.common.util.CommonUtils.getDamageModel(player);
      return damageModel == null ? null : new PartHealingContext(player, world, healer.stack, damageModel, this, healer);
   }

   private static float softenDebuffHealthFraction(float healthFraction) {
      return Math.min(1.0F, 0.5F + healthFraction * 0.5F);
   }
}

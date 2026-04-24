/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.common.damagesystem;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.api.enums.EnumDebuffSlot;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.api.healing.PartHealingContext;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public class DamageablePart extends AbstractDamageablePart {
    private static final float LOW_DEBUFF_DAMAGE_SCALE = 0.4F;
    private static final float CAPPED_MAX_ABSORPTION = 4.0F;
    private static final float UNCAPPED_MAX_ABSORPTION = 16.0F;
    private int maxHealth;
    private float absorption;
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
        if (amount <= 0F)
            return 0F;
        float notFitting = Math.abs(Math.min(0F, maxHealth - (currentHealth + amount)));
        currentHealth = Math.min(maxHealth, currentHealth + amount);
        if (notFitting > 0) {
            float oldHealth = currentHealth;
            currentHealth = Math.min(currentHealth + notFitting, currentHealth);
            notFitting = notFitting - (currentHealth - oldHealth);
        }
        final float finalNotFitting = notFitting;
        if (applyDebuff && debuffs != null && FirstAid.injuryDebuffMode != FirstAid.InjuryDebuffMode.OFF) {
            Objects.requireNonNull(player, "Got null player with applyDebuff = true");
            float debuffHealing = amount - finalNotFitting;
            float debuffHealthFraction = currentHealth / maxHealth;
            if (FirstAid.injuryDebuffMode == FirstAid.InjuryDebuffMode.LOW) {
                debuffHealing *= LOW_DEBUFF_DAMAGE_SCALE;
                debuffHealthFraction = softenDebuffHealthFraction(debuffHealthFraction);
            }
            for (IDebuff debuff : debuffs) {
                debuff.handleHealing(debuffHealing, debuffHealthFraction, (ServerPlayer) player);
            }
        }
        return notFitting;
    }

    @Override
    public float damage(float amount, @Nullable Player player, boolean applyDebuff) {
        return damage(amount, player, applyDebuff, 0F);
    }

    @Override
    public float damage(float amount, @Nullable Player player, boolean applyDebuff, float minHealth) {
        if (amount <= 0F)
            return 0F;
        if (minHealth > maxHealth)
            throw new IllegalArgumentException("Cannot damage part with minHealth " + minHealth + " while he has more max health (" + maxHealth + ")");
        float notFitting = Math.abs(Math.min(minHealth, currentHealth - amount) - minHealth);
        currentHealth = Math.max(minHealth, currentHealth - amount);
        if (applyDebuff && debuffs != null && FirstAid.injuryDebuffMode != FirstAid.InjuryDebuffMode.OFF) {
            Objects.requireNonNull(player, "Got null player with applyDebuff = true");
            float debuffDamage = amount - notFitting;
            float debuffHealthFraction = currentHealth / maxHealth;
            if (FirstAid.injuryDebuffMode == FirstAid.InjuryDebuffMode.LOW) {
                debuffDamage *= LOW_DEBUFF_DAMAGE_SCALE;
                debuffHealthFraction = softenDebuffHealthFraction(debuffHealthFraction);
            }
            for (IDebuff debuff : debuffs) {
                debuff.handleDamageTaken(debuffDamage, debuffHealthFraction, (ServerPlayer) player);
            }
        }
        return notFitting;
    }

    @Override
    public void tick(Level world, Player player, boolean tickDebuffs, boolean pauseHealing) {
        if (!pauseHealing && activeHealer != null) {
            AbstractPartHealer healer = activeHealer;
            ItemHealing healingItem = healer.stack.getItem() instanceof ItemHealing itemHealing ? itemHealing : null;
            PartHealingContext context = createHealingContext(player, world, healer);
            if (healer.tick()) {
                float previousHealth = currentHealth;
                heal(1F, player, !world.isClientSide());
                if (healingItem != null && context != null && currentHealth > previousHealth) {
                    healingItem.onHealPulse(context);
                }
            }
            if (healer.hasFinished()) {
                if (healingItem != null && context != null) {
                    healingItem.onTreatmentCompleted(context);
                }
                activeHealer = null;
            }
        }
        if (!world.isClientSide() && tickDebuffs && debuffs != null && FirstAid.injuryDebuffMode != FirstAid.InjuryDebuffMode.OFF) {
            float debuffHealthFraction = currentHealth / maxHealth;
            if (FirstAid.injuryDebuffMode == FirstAid.InjuryDebuffMode.LOW) {
                debuffHealthFraction = softenDebuffHealthFraction(debuffHealthFraction);
            }
            for (IDebuff debuff : debuffs) {
                debuff.update(player, debuffHealthFraction);
            }
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag compound = new CompoundTag();
        compound.putFloat("health", currentHealth);
        if (absorption > 0F) {
            compound.putFloat("absorption", absorption);
        }
        if (FirstAidConfig.SERVER.scaleMaxHealth.get())
            compound.putInt("maxHealth", maxHealth);
        if (activeHealer != null) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(activeHealer.stack.getItem());
            if (itemId != null) {
                compound.putString("healerItem", itemId.toString());
            }
            compound.putInt("itemTicks", activeHealer.getTicksPassed());
            compound.putInt("itemHeals", activeHealer.getHealsDone());
        }
        return compound;
    }

    @Override
    public void deserializeNBT(@Nullable CompoundTag nbt) {
        if (nbt == null)
            return;
        activeHealer = null;
        absorption = 0F;
        if (nbt.contains("maxHealth") && FirstAidConfig.SERVER.scaleMaxHealth.get())
            maxHealth = nbt.getIntOr("maxHealth", maxHealth);
        currentHealth = Math.min(maxHealth, nbt.getFloatOr("health", currentHealth));
        if (nbt.contains("absorption")) {
            setAbsorption(nbt.getFloatOr("absorption", 0F));
        }
        ItemStack stack = null;
        if (nbt.contains("healerItem")) {
            Identifier itemId = Identifier.tryParse(nbt.getString("healerItem").orElse(""));
            if (itemId != null) {
                Item item = BuiltInRegistries.ITEM.getValue(itemId);
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
                activeHealer = healer.loadNBT(nbt.getIntOr("itemTicks", 0), nbt.getIntOr("itemHeals", 0));
            }
        }
        //kick constant debuffs active
        if (debuffs != null) {
            for (IDebuff debuff : debuffs) {
                debuff.handleHealing(0F, currentHealth / maxHealth, null);
            }
        }
    }

    public void setAbsorption(float absorption) {
        if (!Float.isFinite(absorption)) {
            this.absorption = 0F;
            return;
        }
        this.absorption = Math.max(0F, Math.min(getAbsorptionCap(), absorption));
    }

    public float getAbsorption() {
        return absorption;
    }

    @Override
    public void setMaxHealth(int maxHealth) {
        if (maxHealth > 12 && FirstAidConfig.SERVER.capMaxHealth.get())
            maxHealth = 12;
        if (maxHealth > 128) //Apply a max cap even if disabled - This is already OP and I know no use case where the limit might be reached
            maxHealth = 128;
        this.maxHealth = Math.max(2, maxHealth); //set 2 as a minimum
        this.currentHealth = Math.min(currentHealth, this.maxHealth);
    }

    @Override
    public int getMaxHealth() {
        return maxHealth;
    }

    @Nullable
    private PartHealingContext createHealingContext(Player player, Level world, AbstractPartHealer healer) {
        AbstractPlayerDamageModel damageModel = player == null ? null : CommonUtils.getDamageModel(player);
        return damageModel == null ? null : new PartHealingContext(player, world, healer.stack, damageModel, this, healer);
    }

    private static float getAbsorptionCap() {
        return FirstAidConfig.SERVER.capMaxHealth.get() ? CAPPED_MAX_ABSORPTION : UNCAPPED_MAX_ABSORPTION;
    }

    private static float softenDebuffHealthFraction(float healthFraction) {
        return Math.min(1.0F, 0.5F + (healthFraction * 0.5F));
    }
}

/*
 * FirstAid API
 * Copyright (c) 2017-2024
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

package ichttt.mods.firstaid.api.medicine;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class MedicineUseContext {
   private final Player player;
   private final Level level;
   private final ItemStack stack;
   @Nullable
   private final AbstractPlayerDamageModel damageModel;

   public MedicineUseContext(Player player, Level level, ItemStack stack, @Nullable AbstractPlayerDamageModel damageModel) {
      this.player = player;
      this.level = level;
      this.stack = stack;
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

   @Nonnull
   public ItemStack getStack() {
      return this.stack;
   }

   @Nullable
   public AbstractPlayerDamageModel getDamageModel() {
      return this.damageModel;
   }

   public void queuePainkillerActivation() {
      if (this.damageModel instanceof PlayerDamageModel playerDamageModel) {
         playerDamageModel.queuePainkillerActivation();
      } else {
         this.player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT.get(), PlayerDamageModel.getPainkillerDuration(), 0, false, false));
      }
   }

   public void queueMorphineActivation() {
      if (this.damageModel instanceof PlayerDamageModel playerDamageModel) {
         playerDamageModel.queueMorphineActivation();
      } else if (this.damageModel != null) {
         this.damageModel.applyMorphine(this.player);
      }
   }

   public void applyAdrenalineInjection() {
      if (this.damageModel instanceof PlayerDamageModel playerDamageModel) {
         playerDamageModel.applyAdrenalineInjection(this.player);
      } else {
         MobEffectInstance activePainkiller = this.player.getEffect(RegistryObjects.PAINKILLER_EFFECT.get());
         int duration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activePainkiller == null ? 0 : activePainkiller.getDuration());
         MobEffectInstance activeAbsorption = this.player.getEffect(MobEffects.ABSORPTION);
         int absorptionDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeAbsorption == null ? 0 : activeAbsorption.getDuration());
         int absorptionAmplifier = Math.max(1, activeAbsorption == null ? 0 : activeAbsorption.getAmplifier());
         MobEffectInstance activeHaste = this.player.getEffect(MobEffects.DIG_SPEED);
         MobEffectInstance activeStrength = this.player.getEffect(MobEffects.DAMAGE_BOOST);
         MobEffectInstance activeSpeed = this.player.getEffect(MobEffects.MOVEMENT_SPEED);
         int hasteDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeHaste == null ? 0 : activeHaste.getDuration());
         int hasteAmplifier = Math.max(0, activeHaste == null ? 0 : activeHaste.getAmplifier());
         int strengthDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeStrength == null ? 0 : activeStrength.getDuration());
         int strengthAmplifier = Math.max(0, activeStrength == null ? 0 : activeStrength.getAmplifier());
         int speedDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeSpeed == null ? 0 : activeSpeed.getDuration());
         int speedAmplifier = Math.max(0, activeSpeed == null ? 0 : activeSpeed.getAmplifier());
         this.player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT.get(), duration, 0, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, absorptionDuration, absorptionAmplifier, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, hasteDuration, hasteAmplifier, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, strengthDuration, strengthAmplifier, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, speedDuration, speedAmplifier, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 140, 0, false, false));
      }
   }
}

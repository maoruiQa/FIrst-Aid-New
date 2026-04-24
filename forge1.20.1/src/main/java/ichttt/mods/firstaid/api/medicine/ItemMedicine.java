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

import ichttt.mods.firstaid.common.util.CommonUtils;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public abstract class ItemMedicine extends Item {
   protected ItemMedicine(Item.Properties properties) {
      super(properties);
   }

   @Nonnull
   @Override
   public InteractionResultHolder<ItemStack> use(Level world, Player player, @Nonnull InteractionHand hand) {
      player.startUsingItem(hand);
      return new InteractionResultHolder<>(net.minecraft.world.InteractionResult.SUCCESS, player.getItemInHand(hand));
   }

   @Nonnull
   @Override
   public ItemStack finishUsingItem(@Nonnull ItemStack stack, @Nonnull Level world, @Nonnull LivingEntity entityLiving) {
      if (entityLiving instanceof Player player) {
         MedicineUseContext context = new MedicineUseContext(player, world, stack, CommonUtils.getDamageModel(player));
         this.applyMedicine(context);
         this.consumeAfterUse(context);
         if (world.isClientSide()) {
            SoundEvent soundEvent = this.getUseFinishSound(stack);
            if (soundEvent != null) {
               player.playSound(soundEvent, 1.0F, 1.0F);
            }
         }
      }

      return stack;
   }

   @Override
   public void releaseUsing(@Nonnull ItemStack stack, @Nonnull Level world, @Nonnull LivingEntity entityLiving, int timeCharged) {
      if (timeCharged > 0 && entityLiving instanceof Player player) {
         this.onUseCancelled(new MedicineUseContext(player, world, stack, CommonUtils.getDamageModel(player)));
      }
   }

   @Nonnull
   @Override
   public UseAnim getUseAnimation(ItemStack stack) {
      return UseAnim.EAT;
   }

   @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

   @Nullable
   public SoundEvent getUseStartSound(ItemStack stack) {
      return null;
   }

   @Nullable
   public SoundEvent getUseLoopSound(ItemStack stack) {
      return null;
   }

   @Nullable
   public SoundEvent getUseFinishSound(ItemStack stack) {
      return null;
   }

   protected void consumeAfterUse(MedicineUseContext context) {
      if (!context.getPlayer().getAbilities().instabuild) {
         context.getStack().shrink(1);
      }
   }

   @Nullable
   public MedicineStatusDisplay getActiveStatus(MedicineStatusContext context) {
      return null;
   }

   public void onUseCancelled(MedicineUseContext context) {
   }

   public abstract void applyMedicine(MedicineUseContext context);
}

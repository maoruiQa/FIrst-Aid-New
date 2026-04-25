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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
   private static final Set<UUID> CLIENT_REUSE_BLOCKED_PLAYERS = ConcurrentHashMap.newKeySet();

   protected ItemMedicine(Item.Properties properties) {
      super(properties);
   }

   @Nonnull
   @Override
   public InteractionResultHolder<ItemStack> use(Level world, Player player, @Nonnull InteractionHand hand) {
      if (world.isClientSide() && isClientReuseBlocked(player)) {
         return InteractionResultHolder.fail(player.getItemInHand(hand));
      }

      player.startUsingItem(hand);
      return new InteractionResultHolder<>(net.minecraft.world.InteractionResult.SUCCESS, player.getItemInHand(hand));
   }

   @Nonnull
   @Override
   public ItemStack finishUsingItem(@Nonnull ItemStack stack, @Nonnull Level world, @Nonnull LivingEntity entityLiving) {
      if (entityLiving instanceof Player player) {
         InteractionHand usedHand = player.getUsedItemHand();
         ItemStack activeStack = usedHand == null ? stack : player.getItemInHand(usedHand);
         if (!world.isClientSide()) {
            MedicineUseContext context = new MedicineUseContext(player, world, activeStack, CommonUtils.getDamageModel(player));
            this.applyMedicine(context);
            this.consumeAfterUse(context);
         } else {
            SoundEvent soundEvent = this.getUseFinishSound(activeStack);
            if (soundEvent != null) {
               player.playSound(soundEvent, 1.0F, 1.0F);
            }

            markClientReuseBlock(player);
         }

         return activeStack;
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
      return UseAnim.NONE;
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

   public static boolean isClientReuseBlocked(@Nullable Player player) {
      return player != null && CLIENT_REUSE_BLOCKED_PLAYERS.contains(player.getUUID());
   }

   public static void markClientReuseBlock(@Nullable Player player) {
      if (player != null) {
         CLIENT_REUSE_BLOCKED_PLAYERS.add(player.getUUID());
      }
   }

   public static void clearClientReuseBlock(@Nullable Player player) {
      if (player != null) {
         CLIENT_REUSE_BLOCKED_PLAYERS.remove(player.getUUID());
      }
   }

   public static void clearAllClientReuseBlocks() {
      CLIENT_REUSE_BLOCKED_PLAYERS.clear();
   }

   protected void consumeAfterUse(MedicineUseContext context) {
      if (!context.getPlayer().getAbilities().instabuild) {
         if (context.getStack().isDamageableItem()) {
            int nextDamage = context.getStack().getDamageValue() + 1;
            if (nextDamage >= context.getStack().getMaxDamage()) {
               context.getStack().shrink(1);
            } else {
               context.getStack().setDamageValue(nextDamage);
            }
         } else {
            context.getStack().shrink(1);
         }
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

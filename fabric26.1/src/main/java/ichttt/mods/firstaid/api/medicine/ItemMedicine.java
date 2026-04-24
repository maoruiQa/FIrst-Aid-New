package ichttt.mods.firstaid.api.medicine;

import ichttt.mods.firstaid.common.util.CommonUtils;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

public abstract class ItemMedicine extends Item {
   protected ItemMedicine(Item.Properties properties) {
      super(properties);
   }

   @Nonnull
   @Override
   public InteractionResult use(Level world, Player player, @Nonnull InteractionHand hand) {
      player.startUsingItem(hand);
      return InteractionResult.SUCCESS;
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
   public boolean releaseUsing(@Nonnull ItemStack stack, @Nonnull Level world, @Nonnull LivingEntity entityLiving, int timeCharged) {
      if (timeCharged > 0 && entityLiving instanceof Player player) {
         this.onUseCancelled(new MedicineUseContext(player, world, stack, CommonUtils.getDamageModel(player)));
      }

      return false;
   }

   @Nonnull
   @Override
   public ItemUseAnimation getUseAnimation(ItemStack stack) {
      return ItemUseAnimation.EAT;
   }

   @Override
   public int getUseDuration(ItemStack stack, LivingEntity entity) {
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

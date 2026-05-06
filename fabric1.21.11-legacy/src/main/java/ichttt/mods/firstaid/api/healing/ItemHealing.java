package ichttt.mods.firstaid.api.healing;

import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvent;

public abstract class ItemHealing extends Item {
   public static ItemHealing create(Properties builder, final Function<ItemStack, AbstractPartHealer> healerFunction, final Function<ItemStack, Integer> time) {
      return create(builder, healerFunction, time, stack -> null, stack -> ItemHealing.ApplySoundMode.ON_COMPLETE);
   }

   public static ItemHealing create(
      Properties builder,
      final Function<ItemStack, AbstractPartHealer> healerFunction,
      final Function<ItemStack, Integer> time,
      final Function<ItemStack, SoundEvent> applySoundEventFunction,
      final Function<ItemStack, ItemHealing.ApplySoundMode> applySoundModeFunction
   ) {
      return new ItemHealing(builder, healerFunction, time) {
         @Override
         public AbstractPartHealer createNewHealer(ItemStack stack) {
            return healerFunction.apply(stack);
         }

         @Override
         public int getApplyTime(ItemStack stack) {
            return time.apply(stack);
         }

         @Nullable
         @Override
         public SoundEvent getApplySoundEvent(ItemStack stack) {
            return applySoundEventFunction.apply(stack);
         }

         @Override
         public ItemHealing.ApplySoundMode getApplySoundMode(ItemStack stack) {
            return applySoundModeFunction.apply(stack);
         }
      };
   }

   protected ItemHealing(Properties builder, Function<ItemStack, AbstractPartHealer> healerFunction, Function<ItemStack, Integer> time) {
      super(builder);
   }

   @Nonnull
   public InteractionResult use(Level worldIn, Player playerIn, @Nonnull InteractionHand handIn) {
      return HealingItemApiHelper.INSTANCE.onItemRightClick(this, worldIn, playerIn, handIn);
   }

   @Nonnull
   @Override
   public ItemUseAnimation getUseAnimation(ItemStack stack) {
      return ItemUseAnimation.NONE;
   }

   @Override
   public int getUseDuration(ItemStack stack, LivingEntity entity) {
      return 72000;
   }

   @Nullable
   public SoundEvent getApplySoundEvent(ItemStack stack) {
      return null;
   }

   public ItemHealing.ApplySoundMode getApplySoundMode(ItemStack stack) {
      return ItemHealing.ApplySoundMode.ON_COMPLETE;
   }

   public void onTreatmentStarted(PartHealingContext context) {
   }

   public void onHealPulse(PartHealingContext context) {
   }

   public void onTreatmentCompleted(PartHealingContext context) {
   }

   public abstract AbstractPartHealer createNewHealer(ItemStack var1);

   public abstract int getApplyTime(ItemStack var1);

   public static enum ApplySoundMode {
      WHILE_USING,
      ON_COMPLETE;
   }
}

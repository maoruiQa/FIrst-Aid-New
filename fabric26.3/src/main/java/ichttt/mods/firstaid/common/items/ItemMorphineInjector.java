package ichttt.mods.firstaid.common.items;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.medicine.ItemMedicine;
import ichttt.mods.firstaid.api.medicine.MedicineStatusContext;
import ichttt.mods.firstaid.api.medicine.MedicineStatusDisplay;
import ichttt.mods.firstaid.api.medicine.MedicineUseContext;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public class ItemMorphineInjector extends ItemMedicine {
   private static final Identifier STATUS_ID = Identifier.fromNamespaceAndPath("firstaid", "morphine");

   public ItemMorphineInjector(Properties properties) {
      super(properties.stacksTo(1));
   }

   @Override
   public void applyMedicine(MedicineUseContext context) {
      context.applyMorphineInjection();
   }

   @Nonnull
   @Override
   public ItemUseAnimation getUseAnimation(ItemStack stack) {
      return ItemUseAnimation.NONE;
   }

   @Override
   public SoundEvent getUseStartSound(ItemStack stack) {
      return (SoundEvent)RegistryObjects.ADRENALINE_INJECTOR_USE.value();
   }

   @Override
   public int getUseDuration(ItemStack stack, LivingEntity entity) {
      return FirstAidConfig.SERVER.morphineInjectorUseDuration.get();
   }

   @Override
   public MedicineStatusDisplay getActiveStatus(MedicineStatusContext context) {
      int morphineTicks = context.getDamageModel() == null ? 0 : context.getDamageModel().getMorphineTicks();
      return morphineTicks >= 20
         ? new MedicineStatusDisplay(
            STATUS_ID,
            Component.translatable("firstaid.gui.morphine_left", StringUtil.formatTickDuration(morphineTicks, 20.0F)),
            null,
            0xC8A2C8
         )
         : null;
   }

   @Override
   public void appendHoverText(
      ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltipAdder, TooltipFlag tooltipFlag
   ) {
      tooltipAdder.accept(
         Component.translatable(
               "firstaid.tooltip.morphine",
               StringUtil.formatTickDuration(FirstAidConfig.SERVER.morphineInjectorUseDuration.get(), 20.0F),
               "18:45-21:15",
               StringUtil.formatTickDuration(PlayerDamageModel.MORPHINE_INJECTOR_REGEN_TICKS, 20.0F)
            )
            .withStyle(ChatFormatting.GRAY)
      );
   }
}

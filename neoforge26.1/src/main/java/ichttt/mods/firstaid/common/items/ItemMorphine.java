package ichttt.mods.firstaid.common.items;

import ichttt.mods.firstaid.api.medicine.ItemMedicine;
import ichttt.mods.firstaid.api.medicine.MedicineStatusContext;
import ichttt.mods.firstaid.api.medicine.MedicineStatusDisplay;
import ichttt.mods.firstaid.api.medicine.MedicineUseContext;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public class ItemMorphine extends ItemMedicine {
   private static final Identifier STATUS_ID = Identifier.fromNamespaceAndPath("firstaid", "morphine");

   public ItemMorphine(Properties properties) {
      super(properties.stacksTo(16));
   }

   @Override
   public void applyMedicine(MedicineUseContext context) {
      context.queueMorphineActivation();
   }

   @Override
   public SoundEvent getUseLoopSound(ItemStack stack) {
      return (SoundEvent)RegistryObjects.PILLS_USE.value();
   }

   @Override
   public MedicineStatusDisplay getActiveStatus(MedicineStatusContext context) {
      int morphineTicks = context.getDamageModel() == null ? 0 : context.getDamageModel().getMorphineTicks();
      return morphineTicks > 0
         ? new MedicineStatusDisplay(
            STATUS_ID, Component.translatable("firstaid.gui.morphine_left", new Object[]{StringUtil.formatTickDuration(morphineTicks, 20.0F)}), null, 16777215
         )
         : null;
   }

   @Override
   public int getUseDuration(ItemStack stack, LivingEntity entity) {
      return 40;
   }

   @Override
   public void appendHoverText(
      ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltipAdder, TooltipFlag tooltipFlag
   ) {
      tooltipAdder.accept(
         Component.translatable(
               "firstaid.tooltip.morphine", new Object[]{StringUtil.formatTickDuration(PlayerDamageModel.getMorphineActivationDelay(), 20.0F), "7:30-8:30"}
            )
            .withStyle(ChatFormatting.GRAY)
      );
   }
}

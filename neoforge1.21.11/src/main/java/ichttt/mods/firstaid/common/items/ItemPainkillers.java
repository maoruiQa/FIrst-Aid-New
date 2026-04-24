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

public class ItemPainkillers extends ItemMedicine {
   private static final Identifier STATUS_ID = Identifier.fromNamespaceAndPath("firstaid", "painkiller");

   public ItemPainkillers(Properties properties) {
      super(properties.stacksTo(16));
   }

   @Override
   public void applyMedicine(MedicineUseContext context) {
      context.queuePainkillerActivation();
   }

   @Override
   public SoundEvent getUseLoopSound(ItemStack stack) {
      return (SoundEvent)RegistryObjects.PILLS_USE.value();
   }

   @Override
   public MedicineStatusDisplay getActiveStatus(MedicineStatusContext context) {
      return context.getPlayer().hasEffect(RegistryObjects.PAINKILLER_EFFECT) && !context.getPlayer().hasEffect(RegistryObjects.MORPHINE_EFFECT)
         ? new MedicineStatusDisplay(STATUS_ID, Component.translatable("firstaid.gui.status.painkiller"), null, 9425919)
         : null;
   }

   @Override
   public int getUseDuration(ItemStack stack, LivingEntity entity) {
      return 32;
   }

   @Override
   public void appendHoverText(
      ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltipAdder, TooltipFlag tooltipFlag
   ) {
      tooltipAdder.accept(
         Component.translatable(
               "firstaid.tooltip.painkillers", new Object[]{StringUtil.formatTickDuration(PlayerDamageModel.getPainkillerActivationDelay(), 20.0F), "2:00"}
            )
            .withStyle(ChatFormatting.GRAY)
      );
   }
}

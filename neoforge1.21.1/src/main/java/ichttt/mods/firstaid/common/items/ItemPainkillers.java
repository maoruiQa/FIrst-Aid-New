package ichttt.mods.firstaid.common.items;

import ichttt.mods.firstaid.api.medicine.ItemMedicine;
import ichttt.mods.firstaid.api.medicine.MedicineStatusContext;
import ichttt.mods.firstaid.api.medicine.MedicineStatusDisplay;
import ichttt.mods.firstaid.api.medicine.MedicineUseContext;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;

public class ItemPainkillers extends ItemMedicine {
   private static final ResourceLocation STATUS_ID = ResourceLocation.fromNamespaceAndPath("firstaid", "painkiller");

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
   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
      tooltipComponents.add(
         Component.translatable(
               "firstaid.tooltip.painkillers", new Object[]{StringUtil.formatTickDuration(PlayerDamageModel.getPainkillerActivationDelay(), 20.0F), "4:00"}
            )
            .withStyle(ChatFormatting.GRAY)
      );
   }
}

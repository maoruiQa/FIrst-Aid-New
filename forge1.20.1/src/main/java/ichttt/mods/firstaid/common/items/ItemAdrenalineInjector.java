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

package ichttt.mods.firstaid.common.items;

import ichttt.mods.firstaid.api.medicine.ItemMedicine;
import ichttt.mods.firstaid.api.medicine.MedicineStatusContext;
import ichttt.mods.firstaid.api.medicine.MedicineStatusDisplay;
import ichttt.mods.firstaid.api.medicine.MedicineUseContext;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class ItemAdrenalineInjector extends ItemMedicine {
    private static final ResourceLocation STATUS_ID = new ResourceLocation("firstaid", "adrenaline");

    public ItemAdrenalineInjector(Item.Properties properties) {
        super(properties.stacksTo(1).durability(2));
    }

    @Override
    public void applyMedicine(MedicineUseContext context) {
        context.applyAdrenalineInjection();
    }

    @Nonnull
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public SoundEvent getUseStartSound(ItemStack stack) {
        return RegistryObjects.ADRENALINE_INJECTOR_USE.get();
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 40;
    }

    @Override
    protected void consumeAfterUse(MedicineUseContext context) {
        if (!context.getPlayer().getAbilities().instabuild) {
            EquipmentSlot slot = context.getPlayer().getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            context.getStack().hurtAndBreak(1, context.getPlayer(), living -> living.broadcastBreakEvent(slot));
        }
    }

    @Override
    public MedicineStatusDisplay getActiveStatus(MedicineStatusContext context) {
        int adrenalineLevel = context.getDamageModel() instanceof PlayerDamageModel playerDamageModel ? playerDamageModel.getAdrenalineLevel() : 0;
        return adrenalineLevel > 0
                ? new MedicineStatusDisplay(
                        STATUS_ID,
                        Component.translatable("firstaid.gui.status.adrenaline", Component.translatable(getAdrenalineSeverityKey(adrenalineLevel))),
                        null,
                        12637930
                )
                : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(
                Component.translatable("firstaid.tooltip.adrenaline_injector", StringUtil.formatTickDuration(40), StringUtil.formatTickDuration(PlayerDamageModel.getAdrenalineDuration()))
                        .withStyle(ChatFormatting.GRAY)
        );
    }

    private static String getAdrenalineSeverityKey(int adrenalineLevel) {
        return switch (adrenalineLevel) {
            case 1 -> "firstaid.gui.adrenaline.low";
            case 2 -> "firstaid.gui.adrenaline.medium";
            default -> "firstaid.gui.adrenaline.high";
        };
    }
}

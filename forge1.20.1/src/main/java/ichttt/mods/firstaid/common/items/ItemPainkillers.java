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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class ItemPainkillers extends ItemMedicine {
    private static final ResourceLocation STATUS_ID = new ResourceLocation("firstaid", "painkiller");

    public ItemPainkillers(Item.Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public void applyMedicine(MedicineUseContext context) {
        context.queuePainkillerActivation();
    }

    @Override
    public SoundEvent getUseLoopSound(ItemStack stack) {
        return RegistryObjects.PILLS_USE.get();
    }

    @Override
    public MedicineStatusDisplay getActiveStatus(MedicineStatusContext context) {
        return context.getPlayer().hasEffect(RegistryObjects.PAINKILLER_EFFECT.get()) && !context.getPlayer().hasEffect(RegistryObjects.MORPHINE_EFFECT.get())
                ? new MedicineStatusDisplay(STATUS_ID, Component.translatable("firstaid.gui.status.painkiller"), null, 9425919)
                : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(
                Component.translatable("firstaid.tooltip.painkillers", StringUtil.formatTickDuration(PlayerDamageModel.getPainkillerActivationDelay()), "2:00")
                        .withStyle(ChatFormatting.GRAY)
        );
    }
}

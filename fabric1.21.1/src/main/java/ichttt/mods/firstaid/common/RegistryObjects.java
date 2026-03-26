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

package ichttt.mods.firstaid.common;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.common.damagesystem.PartHealer;
import ichttt.mods.firstaid.common.items.ItemMorphine;
import ichttt.mods.firstaid.common.items.ItemPainkillers;
import ichttt.mods.firstaid.common.potion.FirstAidPotion;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class RegistryObjects {
    public static final RegistryEntry<ItemHealing> BANDAGE;
    public static final RegistryEntry<ItemHealing> PLASTER;
    public static final RegistryEntry<Item> DEFIBRILLATOR;
    public static final RegistryEntry<ItemMorphine> MORPHINE;
    public static final RegistryEntry<ItemPainkillers> PAINKILLERS;

    public static final Holder<SoundEvent> HEARTBEAT;
    public static final Holder<SoundEvent> BANDAGE_USE;
    public static final Holder<SoundEvent> DEFIBRILLATOR_USE;
    public static final Holder<SoundEvent> PILLS_USE;

    public static final Holder<MobEffect> MORPHINE_EFFECT;
    public static final Holder<MobEffect> PAINKILLER_EFFECT;
    public static final Holder<MobEffect> POISON_PATCHED;

    public static final Holder<CreativeModeTab> CREATIVE_TAB;

    static {
        FirstAidConfig.Server server = FirstAidConfig.SERVER;

        BANDAGE = registerItem("bandage", ItemHealing.create(
                itemProperties("bandage").stacksTo(16),
                stack -> new PartHealer(() -> FirstAid.scaleMedicalTimingTicks(server.bandage.secondsPerHeal.get() * 20), server.bandage.totalHeals::get, stack),
                stack -> server.bandage.applyTime.get()
        ));
        PLASTER = registerItem("plaster", ItemHealing.create(
                itemProperties("plaster").stacksTo(16),
                stack -> new PartHealer(() -> FirstAid.scaleMedicalTimingTicks(server.plaster.secondsPerHeal.get() * 20), server.plaster.totalHeals::get, stack),
                stack -> server.plaster.applyTime.get()
        ));
        DEFIBRILLATOR = registerItem("defibrillator", new Item(itemProperties("defibrillator").durability(3)));
        MORPHINE = registerItem("morphine", new ItemMorphine(itemProperties("morphine")));
        PAINKILLERS = registerItem("painkillers", new ItemPainkillers(itemProperties("painkillers")));

        ResourceLocation soundLocation = ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "debuff.heartbeat");
        HEARTBEAT = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, soundLocation, SoundEvent.createVariableRangeEvent(soundLocation));
        ResourceLocation bandageSoundLocation = ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "item.use_bandage");
        BANDAGE_USE = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, bandageSoundLocation, SoundEvent.createVariableRangeEvent(bandageSoundLocation));
        ResourceLocation defibrillatorSoundLocation = ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "item.defibrillator_use");
        DEFIBRILLATOR_USE = Registry.registerForHolder(
                BuiltInRegistries.SOUND_EVENT, defibrillatorSoundLocation, SoundEvent.createVariableRangeEvent(defibrillatorSoundLocation)
        );
        ResourceLocation pillsSoundLocation = ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "item.take_pills");
        PILLS_USE = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, pillsSoundLocation, SoundEvent.createVariableRangeEvent(pillsSoundLocation));

        MORPHINE_EFFECT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id("morphine"), new FirstAidPotion(MobEffectCategory.BENEFICIAL, 0xDDD));
        PAINKILLER_EFFECT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id("painkiller"), new FirstAidPotion(MobEffectCategory.BENEFICIAL, 0x6EC5FF));
        POISON_PATCHED = MobEffects.POISON;

        CREATIVE_TAB = Registry.registerForHolder(BuiltInRegistries.CREATIVE_MODE_TAB, id("main_tab"), FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.firstaid"))
                .icon(() -> new ItemStack(BANDAGE.get()))
                .displayItems((context, entries) -> {
                    entries.accept(BANDAGE.get());
                    entries.accept(PLASTER.get());
                    entries.accept(DEFIBRILLATOR.get());
                    entries.accept(MORPHINE.get());
                    entries.accept(PAINKILLERS.get());
                })
                .build());
    }

    private RegistryObjects() {
    }

    public static void register() {
        // Trigger class loading to run static registration.
    }

    private static <T extends Item> RegistryEntry<T> registerItem(String name, T item) {
        return new RegistryEntry<>(Registry.register(BuiltInRegistries.ITEM, id(name), item));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, path);
    }

    private static Item.Properties itemProperties(String name) {
        return new Item.Properties();
    }

    public static final class RegistryEntry<T> {
        private final T value;

        private RegistryEntry(T value) {
            this.value = value;
        }

        public T get() {
            return value;
        }
    }
}

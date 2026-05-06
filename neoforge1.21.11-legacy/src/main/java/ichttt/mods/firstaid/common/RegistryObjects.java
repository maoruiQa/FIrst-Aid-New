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
import ichttt.mods.firstaid.common.items.ItemAdrenalineInjector;
import ichttt.mods.firstaid.common.items.ItemMorphine;
import ichttt.mods.firstaid.common.items.ItemPainkillers;
import ichttt.mods.firstaid.common.potion.FirstAidPotion;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RegistryObjects {
    private static final DeferredRegister.Items ITEM_REGISTER = DeferredRegister.createItems(FirstAid.MODID);
    private static final DeferredRegister<SoundEvent> SOUND_EVENT_REGISTER = DeferredRegister.create(Registries.SOUND_EVENT, FirstAid.MODID);
    private static final DeferredRegister<MobEffect> MOB_EFFECT_REGISTER = DeferredRegister.create(Registries.MOB_EFFECT, FirstAid.MODID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB_REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FirstAid.MODID);

    public static final DeferredItem<ItemHealing> BANDAGE;
    public static final DeferredItem<ItemHealing> PLASTER;
    public static final DeferredItem<Item> DEFIBRILLATOR;
    public static final DeferredItem<ItemAdrenalineInjector> ADRENALINE_INJECTOR;
    public static final DeferredItem<ItemMorphine> MORPHINE;
    public static final DeferredItem<ItemPainkillers> PAINKILLERS;

    public static final Holder<SoundEvent> HEARTBEAT;
    public static final Holder<SoundEvent> TINNITUS;
    public static final Holder<SoundEvent> BANDAGE_USE;
    public static final Holder<SoundEvent> DEFIBRILLATOR_USE;
    public static final Holder<SoundEvent> ADRENALINE_INJECTOR_USE;
    public static final Holder<SoundEvent> PILLS_USE;

    public static final Holder<MobEffect> MORPHINE_EFFECT;
    public static final Holder<MobEffect> PAINKILLER_EFFECT;
    public static final Holder<MobEffect> POISON_PATCHED;

    public static final Holder<CreativeModeTab> CREATIVE_TAB;

    static {
        FirstAidConfig.Server server = FirstAidConfig.SERVER;

        // ITEMS
        BANDAGE = ITEM_REGISTER.registerItem("bandage", properties -> ItemHealing.create(
                properties.stacksTo(16),
                stack -> new PartHealer(() -> FirstAid.scaleMedicalTimingTicks(server.bandage.secondsPerHeal.get() * 20), server.bandage.totalHeals::get, stack),
                stack -> server.bandage.applyTime.get(),
                stack -> RegistryObjects.BANDAGE_USE.value(),
                stack -> ItemHealing.ApplySoundMode.WHILE_USING
        ));
        PLASTER = ITEM_REGISTER.registerItem("plaster", properties -> ItemHealing.create(
                properties.stacksTo(16),
                stack -> new PartHealer(() -> FirstAid.scaleMedicalTimingTicks(server.plaster.secondsPerHeal.get() * 20), server.plaster.totalHeals::get, stack),
                stack -> server.plaster.applyTime.get(),
                stack -> RegistryObjects.BANDAGE_USE.value(),
                stack -> ItemHealing.ApplySoundMode.WHILE_USING
        ));
        DEFIBRILLATOR = ITEM_REGISTER.registerItem("defibrillator", properties -> new Item(properties.durability(3)));
        ADRENALINE_INJECTOR = ITEM_REGISTER.registerItem("adrenaline_injector", ItemAdrenalineInjector::new);
        MORPHINE = ITEM_REGISTER.registerItem("morphine", ItemMorphine::new);
        PAINKILLERS = ITEM_REGISTER.registerItem("painkillers", ItemPainkillers::new);

        // SOUNDS
        Identifier soundLocation = Identifier.fromNamespaceAndPath(FirstAid.MODID, "debuff.heartbeat");
        HEARTBEAT = SOUND_EVENT_REGISTER.register(soundLocation.getPath(), key -> SoundEvent.createVariableRangeEvent(soundLocation));
        Identifier tinnitusSoundLocation = Identifier.fromNamespaceAndPath(FirstAid.MODID, "debuff.tinnitus");
        TINNITUS = SOUND_EVENT_REGISTER.register(tinnitusSoundLocation.getPath(), key -> SoundEvent.createVariableRangeEvent(tinnitusSoundLocation));
        Identifier bandageSoundLocation = Identifier.fromNamespaceAndPath(FirstAid.MODID, "item.use_bandage");
        BANDAGE_USE = SOUND_EVENT_REGISTER.register(bandageSoundLocation.getPath(), key -> SoundEvent.createVariableRangeEvent(bandageSoundLocation));
        Identifier defibrillatorSoundLocation = Identifier.fromNamespaceAndPath(FirstAid.MODID, "item.defibrillator_use");
        DEFIBRILLATOR_USE = SOUND_EVENT_REGISTER.register(
                defibrillatorSoundLocation.getPath(), key -> SoundEvent.createVariableRangeEvent(defibrillatorSoundLocation)
        );
        Identifier adrenalineInjectorSoundLocation = Identifier.fromNamespaceAndPath(FirstAid.MODID, "item.adrenaline_injector_use");
        ADRENALINE_INJECTOR_USE = SOUND_EVENT_REGISTER.register(
                adrenalineInjectorSoundLocation.getPath(), key -> SoundEvent.createVariableRangeEvent(adrenalineInjectorSoundLocation)
        );
        Identifier pillsSoundLocation = Identifier.fromNamespaceAndPath(FirstAid.MODID, "item.take_pills");
        PILLS_USE = SOUND_EVENT_REGISTER.register(pillsSoundLocation.getPath(), key -> SoundEvent.createVariableRangeEvent(pillsSoundLocation));

        // MOB EFFECTS
        MORPHINE_EFFECT = MOB_EFFECT_REGISTER.register("morphine", () -> new FirstAidPotion(MobEffectCategory.BENEFICIAL, 0xDDD));
        PAINKILLER_EFFECT = MOB_EFFECT_REGISTER.register("painkiller", () -> new FirstAidPotion(MobEffectCategory.BENEFICIAL, 0x6EC5FF));
        POISON_PATCHED = MobEffects.POISON;

        // CREATIVE MODE TABS
        CREATIVE_TAB = CREATIVE_MODE_TAB_REGISTER.register("main_tab", key -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.firstaid"))
                .icon(() -> new ItemStack(BANDAGE.get()))
                .build());
    }


    public static void registerToBus(IEventBus bus) {
        ITEM_REGISTER.register(bus);
        SOUND_EVENT_REGISTER.register(bus);
        MOB_EFFECT_REGISTER.register(bus);
        CREATIVE_MODE_TAB_REGISTER.register(bus);
    }
}



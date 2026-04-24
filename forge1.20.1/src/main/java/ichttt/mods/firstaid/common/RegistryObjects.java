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
import ichttt.mods.firstaid.common.potion.PotionPoisonPatched;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class RegistryObjects {
    private static final DeferredRegister<Item> ITEM_REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, FirstAid.MODID);
    private static final DeferredRegister<SoundEvent> SOUND_EVENT_REGISTER = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, FirstAid.MODID);
    private static final DeferredRegister<MobEffect> MOB_EFFECT_REGISTER = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, FirstAid.MODID);
    private static final DeferredRegister<MobEffect> MOB_EFFECT_OVERRIDE_REGISTER = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, "minecraft");
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB_REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FirstAid.MODID);

    public static final RegistryObject<Item> BANDAGE;
    public static final RegistryObject<Item> PLASTER;
    public static final RegistryObject<Item> DEFIBRILLATOR;
    public static final RegistryObject<Item> ADRENALINE_INJECTOR;
    public static final RegistryObject<Item> MORPHINE;
    public static final RegistryObject<Item> PAINKILLERS;

    public static final RegistryObject<SoundEvent> HEARTBEAT;
    public static final RegistryObject<SoundEvent> TINNITUS;
    public static final RegistryObject<SoundEvent> BANDAGE_USE;
    public static final RegistryObject<SoundEvent> DEFIBRILLATOR_USE;
    public static final RegistryObject<SoundEvent> ADRENALINE_INJECTOR_USE;
    public static final RegistryObject<SoundEvent> PILLS_USE;

    public static final RegistryObject<MobEffect> MORPHINE_EFFECT;
    public static final RegistryObject<MobEffect> PAINKILLER_EFFECT;
    public static final RegistryObject<MobEffect> POISON_PATCHED;

    public static final RegistryObject<CreativeModeTab> CREATIVE_TAB;

    static {
        FirstAidConfig.Server server = FirstAidConfig.SERVER;

        // ITEMS
        BANDAGE = ITEM_REGISTER.register("bandage", () -> ItemHealing.create(
                new Item.Properties().stacksTo(16),
                stack -> new PartHealer(() -> server.bandage.secondsPerHeal.get() * 20, server.bandage.totalHeals::get, stack),
                stack -> server.bandage.applyTime.get(),
                stack -> getBandageUseSound(),
                stack -> ItemHealing.ApplySoundMode.WHILE_USING
        ));
        PLASTER = ITEM_REGISTER.register("plaster", () -> ItemHealing.create(
                new Item.Properties().stacksTo(16),
                stack -> new PartHealer(() -> server.plaster.secondsPerHeal.get() * 20, server.plaster.totalHeals::get, stack),
                stack -> server.plaster.applyTime.get(),
                stack -> getBandageUseSound(),
                stack -> ItemHealing.ApplySoundMode.WHILE_USING
        ));
        DEFIBRILLATOR = ITEM_REGISTER.register("defibrillator", () -> new Item(new Item.Properties().durability(3)));
        ADRENALINE_INJECTOR = ITEM_REGISTER.register("adrenaline_injector", () -> new ItemAdrenalineInjector(new Item.Properties()));
        MORPHINE = ITEM_REGISTER.register("morphine", ItemMorphine::new);
        PAINKILLERS = ITEM_REGISTER.register("painkillers", () -> new ItemPainkillers(new Item.Properties()));

        // SOUNDS
        ResourceLocation soundLocation = new ResourceLocation(FirstAid.MODID, "debuff.heartbeat");
        HEARTBEAT = SOUND_EVENT_REGISTER.register(soundLocation.getPath(), () -> SoundEvent.createVariableRangeEvent(soundLocation));
        ResourceLocation tinnitusSoundLocation = new ResourceLocation(FirstAid.MODID, "debuff.tinnitus");
        TINNITUS = SOUND_EVENT_REGISTER.register(tinnitusSoundLocation.getPath(), () -> SoundEvent.createVariableRangeEvent(tinnitusSoundLocation));
        ResourceLocation bandageSoundLocation = new ResourceLocation(FirstAid.MODID, "item.bandage_use");
        BANDAGE_USE = SOUND_EVENT_REGISTER.register(bandageSoundLocation.getPath(), () -> SoundEvent.createVariableRangeEvent(bandageSoundLocation));
        ResourceLocation defibrillatorSoundLocation = new ResourceLocation(FirstAid.MODID, "item.defibrillator_use");
        DEFIBRILLATOR_USE = SOUND_EVENT_REGISTER.register(defibrillatorSoundLocation.getPath(), () -> SoundEvent.createVariableRangeEvent(defibrillatorSoundLocation));
        ResourceLocation adrenalineSoundLocation = new ResourceLocation(FirstAid.MODID, "item.adrenaline_injector_use");
        ADRENALINE_INJECTOR_USE = SOUND_EVENT_REGISTER.register(adrenalineSoundLocation.getPath(), () -> SoundEvent.createVariableRangeEvent(adrenalineSoundLocation));
        ResourceLocation pillsSoundLocation = new ResourceLocation(FirstAid.MODID, "item.pills_use");
        PILLS_USE = SOUND_EVENT_REGISTER.register(pillsSoundLocation.getPath(), () -> SoundEvent.createVariableRangeEvent(pillsSoundLocation));

        // MOB EFFECTS
        MORPHINE_EFFECT = MOB_EFFECT_REGISTER.register("morphine", () -> new FirstAidPotion(MobEffectCategory.BENEFICIAL, 0xDDD));
        PAINKILLER_EFFECT = MOB_EFFECT_REGISTER.register("painkiller", () -> new FirstAidPotion(MobEffectCategory.BENEFICIAL, 0x6EC5FF));
        POISON_PATCHED = MOB_EFFECT_OVERRIDE_REGISTER.register("poison", () -> new PotionPoisonPatched(MobEffectCategory.HARMFUL, 5149489));

        // CREATIVE MODE TABS
        CREATIVE_TAB = CREATIVE_MODE_TAB_REGISTER.register("main_tab", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.firstaid"))
                .icon(() -> new ItemStack(BANDAGE.get()))
                .build());
    }


    public static void registerToBus(IEventBus bus) {
        ITEM_REGISTER.register(bus);
        SOUND_EVENT_REGISTER.register(bus);
        MOB_EFFECT_REGISTER.register(bus);
        MOB_EFFECT_OVERRIDE_REGISTER.register(bus);
        CREATIVE_MODE_TAB_REGISTER.register(bus);
    }

    private static SoundEvent getBandageUseSound() {
        return BANDAGE_USE.get();
    }
}

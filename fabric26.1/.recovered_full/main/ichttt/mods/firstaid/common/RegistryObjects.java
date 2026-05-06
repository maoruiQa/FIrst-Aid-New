/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
 *  net.minecraft.core.Holder
 *  net.minecraft.core.Registry
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.Identifier
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.world.effect.MobEffect
 *  net.minecraft.world.effect.MobEffectCategory
 *  net.minecraft.world.effect.MobEffects
 *  net.minecraft.world.item.CreativeModeTab
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.ItemLike
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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class RegistryObjects {
    public static final RegistryEntry<ItemHealing> BANDAGE;
    public static final RegistryEntry<ItemHealing> PLASTER;
    public static final RegistryEntry<ItemMorphine> MORPHINE;
    public static final RegistryEntry<ItemPainkillers> PAINKILLERS;
    public static final Holder<SoundEvent> HEARTBEAT;
    public static final Holder<SoundEvent> BANDAGE_USE;
    public static final Holder<SoundEvent> PILLS_USE;
    public static final Holder<MobEffect> MORPHINE_EFFECT;
    public static final Holder<MobEffect> PAINKILLER_EFFECT;
    public static final Holder<MobEffect> POISON_PATCHED;
    public static final Holder<CreativeModeTab> CREATIVE_TAB;

    private RegistryObjects() {
    }

    public static void register() {
    }

    private static <T extends Item> RegistryEntry<T> registerItem(String name, T item) {
        return new RegistryEntry<Item>((Item)Registry.register((Registry)BuiltInRegistries.ITEM, (Identifier)RegistryObjects.id(name), item));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath((String)"firstaid", (String)path);
    }

    private static Item.Properties itemProperties(String name) {
        return new Item.Properties().setId(RegistryObjects.itemKey(name));
    }

    private static ResourceKey<Item> itemKey(String name) {
        return ResourceKey.create((ResourceKey)Registries.ITEM, (Identifier)RegistryObjects.id(name));
    }

    static {
        FirstAidConfig.Server server = FirstAidConfig.SERVER;
        BANDAGE = RegistryObjects.registerItem("bandage", ItemHealing.create(RegistryObjects.itemProperties("bandage").stacksTo(16), stack -> new PartHealer(() -> FirstAid.scaleMedicalTimingTicks(server.bandage.secondsPerHeal.get() * 20), server.bandage.totalHeals::get, (ItemStack)stack), stack -> server.bandage.applyTime.get()));
        PLASTER = RegistryObjects.registerItem("plaster", ItemHealing.create(RegistryObjects.itemProperties("plaster").stacksTo(16), stack -> new PartHealer(() -> FirstAid.scaleMedicalTimingTicks(server.plaster.secondsPerHeal.get() * 20), server.plaster.totalHeals::get, (ItemStack)stack), stack -> server.plaster.applyTime.get()));
        MORPHINE = RegistryObjects.registerItem("morphine", new ItemMorphine(RegistryObjects.itemProperties("morphine")));
        PAINKILLERS = RegistryObjects.registerItem("painkillers", new ItemPainkillers(RegistryObjects.itemProperties("painkillers")));
        Identifier soundLocation = Identifier.fromNamespaceAndPath((String)"firstaid", (String)"debuff.heartbeat");
        HEARTBEAT = Registry.registerForHolder((Registry)BuiltInRegistries.SOUND_EVENT, (Identifier)soundLocation, (Object)SoundEvent.createVariableRangeEvent((Identifier)soundLocation));
        Identifier bandageSoundLocation = Identifier.fromNamespaceAndPath((String)"firstaid", (String)"item.use_bandage");
        BANDAGE_USE = Registry.registerForHolder((Registry)BuiltInRegistries.SOUND_EVENT, (Identifier)bandageSoundLocation, (Object)SoundEvent.createVariableRangeEvent((Identifier)bandageSoundLocation));
        Identifier pillsSoundLocation = Identifier.fromNamespaceAndPath((String)"firstaid", (String)"item.take_pills");
        PILLS_USE = Registry.registerForHolder((Registry)BuiltInRegistries.SOUND_EVENT, (Identifier)pillsSoundLocation, (Object)SoundEvent.createVariableRangeEvent((Identifier)pillsSoundLocation));
        MORPHINE_EFFECT = Registry.registerForHolder((Registry)BuiltInRegistries.MOB_EFFECT, (Identifier)RegistryObjects.id("morphine"), (Object)((Object)new FirstAidPotion(MobEffectCategory.BENEFICIAL, 3549)));
        PAINKILLER_EFFECT = Registry.registerForHolder((Registry)BuiltInRegistries.MOB_EFFECT, (Identifier)RegistryObjects.id("painkiller"), (Object)((Object)new FirstAidPotion(MobEffectCategory.BENEFICIAL, 7259647)));
        POISON_PATCHED = MobEffects.POISON;
        CREATIVE_TAB = Registry.registerForHolder((Registry)BuiltInRegistries.CREATIVE_MODE_TAB, (Identifier)RegistryObjects.id("main_tab"), (Object)FabricItemGroup.builder().title((Component)Component.translatable((String)"itemGroup.firstaid")).icon(() -> new ItemStack((ItemLike)BANDAGE.get())).displayItems((context, entries) -> {
            entries.accept((ItemLike)BANDAGE.get());
            entries.accept((ItemLike)PLASTER.get());
            entries.accept((ItemLike)MORPHINE.get());
            entries.accept((ItemLike)PAINKILLERS.get());
        }).build());
    }

    public static final class RegistryEntry<T> {
        private final T value;

        private RegistryEntry(T value) {
            this.value = value;
        }

        public T get() {
            return this.value;
        }
    }
}


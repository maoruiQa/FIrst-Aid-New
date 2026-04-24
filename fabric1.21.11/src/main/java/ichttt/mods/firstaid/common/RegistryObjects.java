package ichttt.mods.firstaid.common;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.common.damagesystem.PartHealer;
import ichttt.mods.firstaid.common.items.ItemAdrenalineInjector;
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
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.ItemLike;

public final class RegistryObjects {
   public static final RegistryObjects.RegistryEntry<ItemHealing> BANDAGE;
   public static final RegistryObjects.RegistryEntry<ItemHealing> PLASTER;
   public static final RegistryObjects.RegistryEntry<Item> DEFIBRILLATOR;
   public static final RegistryObjects.RegistryEntry<ItemAdrenalineInjector> ADRENALINE_INJECTOR;
   public static final RegistryObjects.RegistryEntry<ItemMorphine> MORPHINE = registerItem("morphine", new ItemMorphine(itemProperties("morphine")));
   public static final RegistryObjects.RegistryEntry<ItemPainkillers> PAINKILLERS = registerItem(
      "painkillers", new ItemPainkillers(itemProperties("painkillers"))
   );
   public static final Holder<SoundEvent> HEARTBEAT;
   public static final Holder<SoundEvent> TINNITUS;
   public static final Holder<SoundEvent> BANDAGE_USE;
   public static final Holder<SoundEvent> DEFIBRILLATOR_USE;
    public static final Holder<SoundEvent> ADRENALINE_INJECTOR_USE;
   public static final Holder<SoundEvent> PILLS_USE;
   public static final Holder<MobEffect> MORPHINE_EFFECT = Registry.registerForHolder(
      BuiltInRegistries.MOB_EFFECT, id("morphine"), new FirstAidPotion(MobEffectCategory.BENEFICIAL, 3549)
   );
   public static final Holder<MobEffect> PAINKILLER_EFFECT = Registry.registerForHolder(
      BuiltInRegistries.MOB_EFFECT, id("painkiller"), new FirstAidPotion(MobEffectCategory.BENEFICIAL, 7259647)
   );
   public static final Holder<MobEffect> POISON_PATCHED = MobEffects.POISON;
   public static final Holder<CreativeModeTab> CREATIVE_TAB;

   private RegistryObjects() {
   }

   public static void register() {
   }

   private static <T extends Item> RegistryObjects.RegistryEntry<T> registerItem(String name, T item) {
      return new RegistryObjects.RegistryEntry<>((T)Registry.register(BuiltInRegistries.ITEM, id(name), item));
   }

   private static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("firstaid", path);
   }

   private static Properties itemProperties(String name) {
      return new Properties().setId(itemKey(name));
   }

   private static ResourceKey<Item> itemKey(String name) {
      return ResourceKey.create(Registries.ITEM, id(name));
   }

   static {
      FirstAidConfig.Server server = FirstAidConfig.SERVER;
      BANDAGE = registerItem(
         "bandage",
         ItemHealing.create(
            itemProperties("bandage").stacksTo(16),
            stack -> new PartHealer(() -> FirstAid.scaleMedicalTimingTicks(server.bandage.secondsPerHeal.get() * 20), server.bandage.totalHeals::get, stack),
            stack -> server.bandage.applyTime.get(),
            stack -> RegistryObjects.BANDAGE_USE.value(),
            stack -> ItemHealing.ApplySoundMode.WHILE_USING
         )
      );
      PLASTER = registerItem(
         "plaster",
         ItemHealing.create(
            itemProperties("plaster").stacksTo(16),
            stack -> new PartHealer(() -> FirstAid.scaleMedicalTimingTicks(server.plaster.secondsPerHeal.get() * 20), server.plaster.totalHeals::get, stack),
            stack -> server.plaster.applyTime.get(),
            stack -> RegistryObjects.BANDAGE_USE.value(),
            stack -> ItemHealing.ApplySoundMode.WHILE_USING
         )
      );
      DEFIBRILLATOR = registerItem("defibrillator", new Item(itemProperties("defibrillator").durability(3)));
      ADRENALINE_INJECTOR = registerItem("adrenaline_injector", new ItemAdrenalineInjector(itemProperties("adrenaline_injector").durability(2)));
      CREATIVE_TAB = Registry.registerForHolder(
         BuiltInRegistries.CREATIVE_MODE_TAB,
         id("main_tab"),
         FabricItemGroup.builder()
            .title(Component.translatable("itemGroup.firstaid"))
            .icon(() -> new ItemStack((ItemLike)BANDAGE.get()))
            .displayItems((context, entries) -> {
               entries.accept((ItemLike)BANDAGE.get());
               entries.accept((ItemLike)PLASTER.get());
               entries.accept((ItemLike)DEFIBRILLATOR.get());
               entries.accept((ItemLike)ADRENALINE_INJECTOR.get());
               entries.accept((ItemLike)MORPHINE.get());
               entries.accept((ItemLike)PAINKILLERS.get());
            })
            .build()
      );
      Identifier soundLocation = Identifier.fromNamespaceAndPath("firstaid", "debuff.heartbeat");
      HEARTBEAT = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, soundLocation, SoundEvent.createVariableRangeEvent(soundLocation));
      Identifier tinnitusSoundLocation = Identifier.fromNamespaceAndPath("firstaid", "debuff.tinnitus");
      TINNITUS = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, tinnitusSoundLocation, SoundEvent.createVariableRangeEvent(tinnitusSoundLocation));
      Identifier bandageSoundLocation = Identifier.fromNamespaceAndPath("firstaid", "item.use_bandage");
      BANDAGE_USE = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, bandageSoundLocation, SoundEvent.createVariableRangeEvent(bandageSoundLocation));
      Identifier defibrillatorSoundLocation = Identifier.fromNamespaceAndPath("firstaid", "item.defibrillator_use");
      DEFIBRILLATOR_USE = Registry.registerForHolder(
         BuiltInRegistries.SOUND_EVENT, defibrillatorSoundLocation, SoundEvent.createVariableRangeEvent(defibrillatorSoundLocation)
      );
      Identifier adrenalineInjectorSoundLocation = Identifier.fromNamespaceAndPath("firstaid", "item.adrenaline_injector_use");
      ADRENALINE_INJECTOR_USE = Registry.registerForHolder(
         BuiltInRegistries.SOUND_EVENT, adrenalineInjectorSoundLocation, SoundEvent.createVariableRangeEvent(adrenalineInjectorSoundLocation)
      );
      Identifier pillsSoundLocation = Identifier.fromNamespaceAndPath("firstaid", "item.take_pills");
      PILLS_USE = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, pillsSoundLocation, SoundEvent.createVariableRangeEvent(pillsSoundLocation));
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

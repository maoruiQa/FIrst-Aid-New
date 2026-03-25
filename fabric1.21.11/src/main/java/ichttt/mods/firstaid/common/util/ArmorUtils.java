package ichttt.mods.firstaid.common.util;

import com.google.common.math.DoubleMath;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import javax.annotation.Nonnull;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class ArmorUtils {
   public static double getArmor(ItemStack stack, EquipmentSlot slot) {
      return getValueFromAttributes(Attributes.ARMOR, slot, stack);
   }

   public static double getArmorToughness(ItemStack stack, EquipmentSlot slot) {
      return getValueFromAttributes(Attributes.ARMOR_TOUGHNESS, slot, stack);
   }

   public static double applyArmorModifier(EquipmentSlot slot, double rawArmor) {
      if (rawArmor <= 0.0) {
         return 0.0;
      } else {
         rawArmor *= getArmorMultiplier(slot);
         return rawArmor + getArmorOffset(slot);
      }
   }

   public static double applyToughnessModifier(EquipmentSlot slot, double rawToughness) {
      if (rawToughness <= 0.0) {
         return 0.0;
      } else {
         rawToughness *= getToughnessMultiplier(slot);
         return rawToughness + getToughnessOffset(slot);
      }
   }

   private static double getArmorMultiplier(EquipmentSlot slot) {
      FirstAidConfig.Server config = FirstAidConfig.SERVER;
      switch (slot) {
         case HEAD:
            return config.headArmorMultiplier.get();
         case CHEST:
            return config.chestArmorMultiplier.get();
         case LEGS:
            return config.legsArmorMultiplier.get();
         case FEET:
            return config.feetArmorMultiplier.get();
         default:
            throw new IllegalArgumentException("Invalid slot " + slot);
      }
   }

   private static double getArmorOffset(EquipmentSlot slot) {
      FirstAidConfig.Server config = FirstAidConfig.SERVER;
      switch (slot) {
         case HEAD:
            return config.headArmorOffset.get();
         case CHEST:
            return config.chestArmorOffset.get();
         case LEGS:
            return config.legsArmorOffset.get();
         case FEET:
            return config.feetArmorOffset.get();
         default:
            throw new IllegalArgumentException("Invalid slot " + slot);
      }
   }

   private static double getToughnessMultiplier(EquipmentSlot slot) {
      FirstAidConfig.Server config = FirstAidConfig.SERVER;
      switch (slot) {
         case HEAD:
            return config.headThoughnessMultiplier.get();
         case CHEST:
            return config.chestThoughnessMultiplier.get();
         case LEGS:
            return config.legsThoughnessMultiplier.get();
         case FEET:
            return config.feetThoughnessMultiplier.get();
         default:
            throw new IllegalArgumentException("Invalid slot " + slot);
      }
   }

   private static double getToughnessOffset(EquipmentSlot slot) {
      FirstAidConfig.Server config = FirstAidConfig.SERVER;
      switch (slot) {
         case HEAD:
            return config.headThoughnessOffset.get();
         case CHEST:
            return config.chestThoughnessOffset.get();
         case LEGS:
            return config.legsThoughnessOffset.get();
         case FEET:
            return config.feetThoughnessOffset.get();
         default:
            throw new IllegalArgumentException("Invalid slot " + slot);
      }
   }

   private static double getValueFromAttributes(Holder<Attribute> attribute, EquipmentSlot slot, ItemStack stack) {
      double[] base = new double[]{0.0};
      double[] multiplyBase = new double[]{0.0};
      double[] multiplyTotal = new double[]{0.0};
      stack.forEachModifier(slot, (attr, modifier) -> {
         if (attr.equals(attribute)) {
            Operation operation = modifier.operation();
            if (operation == Operation.ADD_VALUE) {
               base[0] += modifier.amount();
            } else if (operation == Operation.ADD_MULTIPLIED_BASE) {
               multiplyBase[0] += modifier.amount();
            } else if (operation == Operation.ADD_MULTIPLIED_TOTAL) {
               multiplyTotal[0] += modifier.amount();
            }
         }
      });
      double value = base[0];
      if (multiplyBase[0] != 0.0) {
         value += base[0] * multiplyBase[0];
      }

      if (multiplyTotal[0] != 0.0) {
         value *= 1.0 + multiplyTotal[0];
      }

      return value;
   }

   private static double getGlobalRestAttribute(Player player, Holder<Attribute> attribute) {
      double sumOfAllAttributes = 0.0;

      for (EquipmentSlot slot : CommonUtils.ARMOR_SLOTS) {
         ItemStack otherStack = player.getItemBySlot(slot);
         sumOfAllAttributes += getValueFromAttributes(attribute, slot, otherStack);
      }

      double all = player.getAttributeValue(attribute);
      if (!DoubleMath.fuzzyEquals(sumOfAllAttributes, all, 0.001)) {
         double diff = all - sumOfAllAttributes;
         if (FirstAidConfig.GENERAL.debug.get()) {
            FirstAid.LOGGER
               .info(
                  "Attribute value for {} does not match sum! Diff is {}, distributing to all!",
                  BuiltInRegistries.ATTRIBUTE.getKey((Attribute)attribute.value()),
                  diff
               );
         }

         return diff;
      } else {
         return 0.0;
      }
   }

   public static float applyArmor(@Nonnull Player entity, @Nonnull ItemStack itemStack, @Nonnull DamageSource source, float damage, @Nonnull EquipmentSlot slot) {
      if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
         return damage;
      } else {
         Item item = itemStack.getItem();
         float totalArmor = 0.0F;
         float totalToughness = 0.0F;
         if (!itemStack.isEmpty()) {
            totalArmor = (float)getValueFromAttributes(Attributes.ARMOR, slot, itemStack);
            totalToughness = (float)getValueFromAttributes(Attributes.ARMOR_TOUGHNESS, slot, itemStack);
            totalArmor = (float)applyArmorModifier(slot, totalArmor);
            totalToughness = (float)applyToughnessModifier(slot, totalToughness);
         }

         totalArmor = (float)(totalArmor + getGlobalRestAttribute(entity, Attributes.ARMOR));
         totalToughness = (float)(totalToughness + getGlobalRestAttribute(entity, Attributes.ARMOR_TOUGHNESS));
         if (damage > 0.0F && (totalArmor > 0.0F || totalToughness > 0.0F)) {
            if (itemStack.isDamageableItem() && !source.is(DamageTypeTags.IS_FIRE)) {
               int itemDamage = Math.max((int)damage, 1);
               itemStack.hurtAndBreak(itemDamage, entity, slot);
            }

            damage = CombatRules.getDamageAfterAbsorb(entity, damage, source, totalArmor, totalToughness);
         }

         return damage;
      }
   }

   public static float applyGlobalPotionModifiers(Player player, DamageSource source, float damage) {
      if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
         return damage;
      } else {
         if (player.hasEffect(MobEffects.RESISTANCE) && source != player.damageSources().fellOutOfWorld()) {
            int i = (player.getEffect(MobEffects.RESISTANCE).getAmplifier() + 1) * FirstAidConfig.SERVER.resistanceReductionPercentPerLevel.get();
            int j = 100 - i;
            float f = damage * j;
            float f1 = damage;
            damage = Math.max(f / 100.0F, 0.0F);
            float f2 = f1 - damage;
            if (f2 > 0.0F && f2 < 3.4028235E37F) {
               if (player instanceof ServerPlayer) {
                  player.awardStat(Stats.DAMAGE_RESISTED, Math.round(f2 * 10.0F));
               } else if (source.getEntity() instanceof ServerPlayer) {
                  ((ServerPlayer)source.getEntity()).awardStat(Stats.DAMAGE_DEALT_RESISTED, Math.round(f2 * 10.0F));
               }
            }
         }

         return damage;
      }
   }

   public static float applyEnchantmentModifiers(Player player, EquipmentSlot slot, DamageSource source, float damage) {
      FirstAidConfig.Server.ArmorEnchantmentMode enchantmentMode = FirstAidConfig.SERVER.armorEnchantmentMode.get();
      int k;
      if (player.level() instanceof ServerLevel serverLevel) {
         if (enchantmentMode == FirstAidConfig.Server.ArmorEnchantmentMode.LOCAL_ENCHANTMENTS) {
            damage = EnchantmentHelper.modifyDamage(serverLevel, player.getItemBySlot(slot), player, source, damage);
            k = 0;
         } else {
            if (enchantmentMode != FirstAidConfig.Server.ArmorEnchantmentMode.GLOBAL_ENCHANTMENTS) {
               throw new RuntimeException("What dark magic is " + enchantmentMode);
            }

            k = Math.round(EnchantmentHelper.getDamageProtection(serverLevel, player, source));
         }
      } else {
         k = 0;
      }

      if (k > 0) {
         damage = CombatRules.getDamageAfterMagicAbsorb(damage, k);
      }

      return damage;
   }
}

/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.math.DoubleMath
 *  javax.annotation.Nonnull
 *  net.minecraft.core.Holder
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.stats.Stats
 *  net.minecraft.tags.DamageTypeTags
 *  net.minecraft.world.damagesource.CombatRules
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.effect.MobEffects
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.ai.attributes.Attribute
 *  net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation
 *  net.minecraft.world.entity.ai.attributes.Attributes
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.enchantment.EnchantmentHelper
 *  net.minecraft.world.level.Level
 */
package ichttt.mods.firstaid.common.util;

import com.google.common.math.DoubleMath;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.common.util.CommonUtils;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

public class ArmorUtils {
    public static double getArmor(ItemStack stack, EquipmentSlot slot) {
        return ArmorUtils.getValueFromAttributes((Holder<Attribute>)Attributes.ARMOR, slot, stack);
    }

    public static double getArmorToughness(ItemStack stack, EquipmentSlot slot) {
        return ArmorUtils.getValueFromAttributes((Holder<Attribute>)Attributes.ARMOR_TOUGHNESS, slot, stack);
    }

    public static double applyArmorModifier(EquipmentSlot slot, double rawArmor) {
        if (rawArmor <= 0.0) {
            return 0.0;
        }
        rawArmor *= ArmorUtils.getArmorMultiplier(slot);
        return rawArmor += ArmorUtils.getArmorOffset(slot);
    }

    public static double applyToughnessModifier(EquipmentSlot slot, double rawToughness) {
        if (rawToughness <= 0.0) {
            return 0.0;
        }
        rawToughness *= ArmorUtils.getToughnessMultiplier(slot);
        return rawToughness += ArmorUtils.getToughnessOffset(slot);
    }

    private static double getArmorMultiplier(EquipmentSlot slot) {
        FirstAidConfig.Server config = FirstAidConfig.SERVER;
        switch (slot) {
            case HEAD: {
                return config.headArmorMultiplier.get();
            }
            case CHEST: {
                return config.chestArmorMultiplier.get();
            }
            case LEGS: {
                return config.legsArmorMultiplier.get();
            }
            case FEET: {
                return config.feetArmorMultiplier.get();
            }
        }
        throw new IllegalArgumentException("Invalid slot " + String.valueOf(slot));
    }

    private static double getArmorOffset(EquipmentSlot slot) {
        FirstAidConfig.Server config = FirstAidConfig.SERVER;
        switch (slot) {
            case HEAD: {
                return config.headArmorOffset.get();
            }
            case CHEST: {
                return config.chestArmorOffset.get();
            }
            case LEGS: {
                return config.legsArmorOffset.get();
            }
            case FEET: {
                return config.feetArmorOffset.get();
            }
        }
        throw new IllegalArgumentException("Invalid slot " + String.valueOf(slot));
    }

    private static double getToughnessMultiplier(EquipmentSlot slot) {
        FirstAidConfig.Server config = FirstAidConfig.SERVER;
        switch (slot) {
            case HEAD: {
                return config.headThoughnessMultiplier.get();
            }
            case CHEST: {
                return config.chestThoughnessMultiplier.get();
            }
            case LEGS: {
                return config.legsThoughnessMultiplier.get();
            }
            case FEET: {
                return config.feetThoughnessMultiplier.get();
            }
        }
        throw new IllegalArgumentException("Invalid slot " + String.valueOf(slot));
    }

    private static double getToughnessOffset(EquipmentSlot slot) {
        FirstAidConfig.Server config = FirstAidConfig.SERVER;
        switch (slot) {
            case HEAD: {
                return config.headThoughnessOffset.get();
            }
            case CHEST: {
                return config.chestThoughnessOffset.get();
            }
            case LEGS: {
                return config.legsThoughnessOffset.get();
            }
            case FEET: {
                return config.feetThoughnessOffset.get();
            }
        }
        throw new IllegalArgumentException("Invalid slot " + String.valueOf(slot));
    }

    private static double getValueFromAttributes(Holder<Attribute> attribute, EquipmentSlot slot, ItemStack stack) {
        double[] base = new double[]{0.0};
        double[] multiplyBase = new double[]{0.0};
        double[] multiplyTotal = new double[]{0.0};
        stack.forEachModifier(slot, (attr, modifier) -> {
            if (!attr.equals((Object)attribute)) {
                return;
            }
            AttributeModifier.Operation operation = modifier.operation();
            if (operation == AttributeModifier.Operation.ADD_VALUE) {
                base[0] = base[0] + modifier.amount();
            } else if (operation == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                multiplyBase[0] = multiplyBase[0] + modifier.amount();
            } else if (operation == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                multiplyTotal[0] = multiplyTotal[0] + modifier.amount();
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
            sumOfAllAttributes += ArmorUtils.getValueFromAttributes(attribute, slot, otherStack);
        }
        double all = player.getAttributeValue(attribute);
        if (!DoubleMath.fuzzyEquals((double)sumOfAllAttributes, (double)all, (double)0.001)) {
            double diff = all - sumOfAllAttributes;
            if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
                FirstAid.LOGGER.info("Attribute value for {} does not match sum! Diff is {}, distributing to all!", (Object)BuiltInRegistries.ATTRIBUTE.getKey((Object)((Attribute)attribute.value())), (Object)diff);
            }
            return diff;
        }
        return 0.0;
    }

    public static float applyArmor(@Nonnull Player entity, @Nonnull ItemStack itemStack, @Nonnull DamageSource source, float damage, @Nonnull EquipmentSlot slot) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return damage;
        }
        Item item = itemStack.getItem();
        float totalArmor = 0.0f;
        float totalToughness = 0.0f;
        if (!itemStack.isEmpty()) {
            totalArmor = (float)ArmorUtils.getValueFromAttributes((Holder<Attribute>)Attributes.ARMOR, slot, itemStack);
            totalToughness = (float)ArmorUtils.getValueFromAttributes((Holder<Attribute>)Attributes.ARMOR_TOUGHNESS, slot, itemStack);
            totalArmor = (float)ArmorUtils.applyArmorModifier(slot, totalArmor);
            totalToughness = (float)ArmorUtils.applyToughnessModifier(slot, totalToughness);
        }
        totalArmor = (float)((double)totalArmor + ArmorUtils.getGlobalRestAttribute(entity, (Holder<Attribute>)Attributes.ARMOR));
        totalToughness = (float)((double)totalToughness + ArmorUtils.getGlobalRestAttribute(entity, (Holder<Attribute>)Attributes.ARMOR_TOUGHNESS));
        if (damage > 0.0f && (totalArmor > 0.0f || totalToughness > 0.0f)) {
            if (itemStack.isDamageableItem() && !source.is(DamageTypeTags.IS_FIRE)) {
                int itemDamage = Math.max((int)damage, 1);
                itemStack.hurtAndBreak(itemDamage, (LivingEntity)entity, slot);
            }
            damage = CombatRules.getDamageAfterAbsorb((LivingEntity)entity, (float)damage, (DamageSource)source, (float)totalArmor, (float)totalToughness);
        }
        return damage;
    }

    public static float applyGlobalPotionModifiers(Player player, DamageSource source, float damage) {
        int i;
        int j;
        float f;
        float f1;
        float f2;
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return damage;
        }
        if (player.hasEffect(MobEffects.RESISTANCE) && source != player.damageSources().fellOutOfWorld() && (f2 = (f1 = damage) - (damage = Math.max((f = damage * (float)(j = 100 - (i = (player.getEffect(MobEffects.RESISTANCE).getAmplifier() + 1) * FirstAidConfig.SERVER.resistanceReductionPercentPerLevel.get()))) / 100.0f, 0.0f))) > 0.0f && f2 < 3.4028235E37f) {
            if (player instanceof ServerPlayer) {
                player.awardStat(Stats.DAMAGE_RESISTED, Math.round(f2 * 10.0f));
            } else if (source.getEntity() instanceof ServerPlayer) {
                ((ServerPlayer)source.getEntity()).awardStat(Stats.DAMAGE_DEALT_RESISTED, Math.round(f2 * 10.0f));
            }
        }
        return damage;
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static float applyEnchantmentModifiers(Player player, EquipmentSlot slot, DamageSource source, float damage) {
        int k;
        FirstAidConfig.Server.ArmorEnchantmentMode enchantmentMode = FirstAidConfig.SERVER.armorEnchantmentMode.get();
        Level level = player.level();
        if (level instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel)level;
            if (enchantmentMode == FirstAidConfig.Server.ArmorEnchantmentMode.LOCAL_ENCHANTMENTS) {
                return EnchantmentHelper.modifyDamage((ServerLevel)serverLevel, (ItemStack)player.getItemBySlot(slot), (Entity)player, (DamageSource)source, (float)damage);
            }
            if (enchantmentMode != FirstAidConfig.Server.ArmorEnchantmentMode.GLOBAL_ENCHANTMENTS) throw new RuntimeException("What dark magic is " + String.valueOf((Object)enchantmentMode));
            k = Math.round(EnchantmentHelper.getDamageProtection((ServerLevel)serverLevel, (LivingEntity)player, (DamageSource)source));
        } else {
            k = 0;
        }
        if (k <= 0) return damage;
        return CombatRules.getDamageAfterMagicAbsorb((float)damage, (float)k);
    }
}


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

package ichttt.mods.firstaid.common.util;

import com.google.common.primitives.Ints;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.FirstAidDamageModelHolder;
import ichttt.mods.firstaid.common.compat.playerrevive.IPRCompatHandler;
import ichttt.mods.firstaid.common.compat.playerrevive.PRCompatManager;
import ichttt.mods.firstaid.common.damagesystem.distribution.HealthDistribution;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.fabricmc.loader.api.FabricLoader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class CommonUtils {
    @Nonnull
    public static final EquipmentSlot[] ARMOR_SLOTS;
    @Nonnull
    private static final Map<EquipmentSlot, List<EnumPlayerPart>> SLOT_TO_PARTS;

    static {
        ARMOR_SLOTS = new EquipmentSlot[4];
        ARMOR_SLOTS[3] = EquipmentSlot.HEAD;
        ARMOR_SLOTS[2] = EquipmentSlot.CHEST;
        ARMOR_SLOTS[1] = EquipmentSlot.LEGS;
        ARMOR_SLOTS[0] = EquipmentSlot.FEET;
        SLOT_TO_PARTS = new EnumMap<>(EquipmentSlot.class);
        SLOT_TO_PARTS.put(EquipmentSlot.HEAD, Collections.singletonList(EnumPlayerPart.HEAD));
        SLOT_TO_PARTS.put(EquipmentSlot.CHEST, Arrays.asList(EnumPlayerPart.LEFT_ARM, EnumPlayerPart.RIGHT_ARM, EnumPlayerPart.BODY));
        SLOT_TO_PARTS.put(EquipmentSlot.LEGS, Arrays.asList(EnumPlayerPart.LEFT_LEG, EnumPlayerPart.RIGHT_LEG));
        SLOT_TO_PARTS.put(EquipmentSlot.FEET, Arrays.asList(EnumPlayerPart.LEFT_FOOT, EnumPlayerPart.RIGHT_FOOT));
    }

    public static List<EnumPlayerPart> getPartListForSlot(EquipmentSlot slot) {
        List<EnumPlayerPart> parts = SLOT_TO_PARTS.get(slot);
        return parts == null ? new ArrayList<>() : new ArrayList<>(parts);
    }

    public static EnumPlayerPart[] getPartArrayForSlot(EquipmentSlot slot) {
        return getPartListForSlot(slot).toArray(new EnumPlayerPart[0]);
    }

    public static void killPlayer(@Nonnull AbstractPlayerDamageModel damageModel, @Nonnull Player player, @Nullable DamageSource source) {
        if (player.level().isClientSide()) {
            try {
                throw new RuntimeException("Tried to kill the player on the client!");
            } catch (RuntimeException e) {
                FirstAid.LOGGER.warn("Tried to kill the player on the client! This should only happen on the server! Ignoring...", e);
            }
        }
        IPRCompatHandler handler = PRCompatManager.getHandler();
        if (!handler.isBleeding(player)) {
            if (!handler.tryKnockOutPlayer(player, source)) {
                killPlayerDirectly(player, source);
            }
        }
    }

    public static void killPlayerDirectly(@Nonnull Player player, @Nullable DamageSource source) {
        DamageSource resolvedSource = source != null ? source : player.damageSources().generic();
        player.setHealth(0.0F);
        player.die(resolvedSource);
    }

    public static boolean isValidArmorSlot(EquipmentSlot slot) {
        return slot != null && slot.isArmor() && SLOT_TO_PARTS.containsKey(slot);
    }

    @Nonnull
    public static String getActiveModidSafe() {
        return FabricLoader.getInstance().getModContainer(FirstAid.MODID)
                .map(container -> container.getMetadata().getId())
                .orElse(FirstAid.MODID);
    }

    public static void healPlayerByPercentage(double percentage, AbstractPlayerDamageModel damageModel, Player player) {
        Objects.requireNonNull(damageModel);
        int healValue = Ints.checkedCast(Math.round(damageModel.getCurrentMaxHealth() * percentage));
        HealthDistribution.manageHealth(healValue, damageModel, player, true, false);
    }

    public static void healAllPartsByPercentage(double percentage, AbstractPlayerDamageModel damageModel, Player player) {
        Objects.requireNonNull(damageModel);
        boolean applyDebuff = !player.level().isClientSide();
        for (AbstractDamageablePart part : damageModel) {
            float missingHealth = part.getMaxHealth() - part.currentHealth;
            if (missingHealth <= 0.0F) {
                continue;
            }
            float healAmount = Math.max(0.0F, Math.round((float) (part.getMaxHealth() * percentage) * 100.0F) / 100.0F);
            if (healAmount <= 0.0F) {
                continue;
            }
            part.heal(healAmount, player, applyDebuff);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ichttt.mods.firstaid.common.network.FirstAidNetworking.sendDamageModelSync(serverPlayer, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
        }
    }

    public static void debugLogStacktrace(String name) {
        if (!FirstAidConfig.GENERAL.debug.get()) return;
        try {
            throw new RuntimeException("DEBUG:" + name);
        } catch (RuntimeException e) {
            FirstAid.LOGGER.info("DEBUG: " + name, e);
        }
    }

    /**
     * Returns the damage model if present.
     * If absent, an exception is thrown in debug mode, otherwise null is returned.
     */
    @Nullable
    public static AbstractPlayerDamageModel getDamageModel(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        if (!(player instanceof FirstAidDamageModelHolder holder)) {
            if (FirstAidConfig.GENERAL.debug.get()) {
                IllegalArgumentException e = new IllegalArgumentException("Player " + player.getName().getContents() + " is missing a damage model!");
                FirstAid.LOGGER.fatal("Mandatory damage model missing!", e);
                throw e;
            }
            FirstAid.LOGGER.error("Missing a damage model, skipping further processing!");
            return null;
        }
        return holder.firstaid$getDamageModel();
    }

    @Nonnull
    public static Optional<AbstractPlayerDamageModel> getOptionalDamageModel(@Nullable Player player) {
        return Optional.ofNullable(getExistingDamageModel(player));
    }

    @Nullable
    public static AbstractPlayerDamageModel getExistingDamageModel(@Nullable Player player) {
        if (!(player instanceof FirstAidDamageModelHolder holder)) {
            return null;
        }
        return holder.firstaid$getDamageModelNullable();
    }

    public static boolean hasDamageModel(Entity entity) {
        return entity instanceof Player;
    }

    public static boolean isExecutionItem(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(ItemTags.AXES)
                || stack.is(ItemTags.HOES)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.SWORDS)
                || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof MaceItem);
    }
}


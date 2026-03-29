/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.primitives.Ints
 *  javax.annotation.Nonnull
 *  javax.annotation.Nullable
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.player.Player
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
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

public class CommonUtils {
    @Nonnull
    public static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[4];
    @Nonnull
    private static final Map<EquipmentSlot, List<EnumPlayerPart>> SLOT_TO_PARTS;

    public static List<EnumPlayerPart> getPartListForSlot(EquipmentSlot slot) {
        List<EnumPlayerPart> parts = SLOT_TO_PARTS.get(slot);
        return parts == null ? new ArrayList<EnumPlayerPart>() : new ArrayList<EnumPlayerPart>(parts);
    }

    public static EnumPlayerPart[] getPartArrayForSlot(EquipmentSlot slot) {
        return CommonUtils.getPartListForSlot(slot).toArray(new EnumPlayerPart[0]);
    }

    public static void killPlayer(@Nonnull AbstractPlayerDamageModel damageModel, @Nonnull Player player, @Nullable DamageSource source) {
        IPRCompatHandler handler;
        if (player.level().isClientSide()) {
            try {
                throw new RuntimeException("Tried to kill the player on the client!");
            }
            catch (RuntimeException e) {
                FirstAid.LOGGER.warn("Tried to kill the player on the client! This should only happen on the server! Ignoring...", (Throwable)e);
            }
        }
        if (!(handler = PRCompatManager.getHandler()).isBleeding(player) && !handler.tryKnockOutPlayer(player, source)) {
            CommonUtils.killPlayerDirectly(player, source);
        }
    }

    public static void killPlayerDirectly(@Nonnull Player player, @Nullable DamageSource source) {
        DamageSource resolvedSource = source != null ? source : player.damageSources().generic();
        player.setHealth(0.0f);
        player.die(resolvedSource);
    }

    public static boolean isValidArmorSlot(EquipmentSlot slot) {
        return slot != null && slot.isArmor() && SLOT_TO_PARTS.containsKey(slot);
    }

    @Nonnull
    public static String getActiveModidSafe() {
        return FabricLoader.getInstance().getModContainer("firstaid").map(container -> container.getMetadata().getId()).orElse("firstaid");
    }

    public static void healPlayerByPercentage(double percentage, AbstractPlayerDamageModel damageModel, Player player) {
        Objects.requireNonNull(damageModel);
        int healValue = Ints.checkedCast((long)Math.round((double)damageModel.getCurrentMaxHealth() * percentage));
        HealthDistribution.manageHealth(healValue, damageModel, player, true, false);
    }

    public static void healAllPartsByPercentage(double percentage, AbstractPlayerDamageModel damageModel, Player player) {
        Objects.requireNonNull(damageModel);
        boolean applyDebuff = !player.level().isClientSide();
        for (AbstractDamageablePart part : damageModel) {
            float healAmount;
            float missingHealth = (float)part.getMaxHealth() - part.currentHealth;
            if (missingHealth <= 0.0f || (healAmount = Math.max(0.0f, (float)Math.round((float)((double)part.getMaxHealth() * percentage) * 100.0f) / 100.0f)) <= 0.0f) continue;
            part.heal(healAmount, player, applyDebuff);
        }
        if (player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer)player;
            FirstAidNetworking.sendDamageModelSync(serverPlayer, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
        }
    }

    public static void debugLogStacktrace(String name) {
        if (!FirstAidConfig.GENERAL.debug.get().booleanValue()) {
            return;
        }
        try {
            throw new RuntimeException("DEBUG:" + name);
        }
        catch (RuntimeException e) {
            FirstAid.LOGGER.info("DEBUG: " + name, (Throwable)e);
            return;
        }
    }

    @Nullable
    public static AbstractPlayerDamageModel getDamageModel(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        if (!(player instanceof FirstAidDamageModelHolder)) {
            if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
                IllegalArgumentException e = new IllegalArgumentException("Player " + String.valueOf(player.getName().getContents()) + " is missing a damage model!");
                FirstAid.LOGGER.fatal("Mandatory damage model missing!", (Throwable)e);
                throw e;
            }
            FirstAid.LOGGER.error("Missing a damage model, skipping further processing!");
            return null;
        }
        FirstAidDamageModelHolder holder = (FirstAidDamageModelHolder)player;
        return holder.firstaid$getDamageModel();
    }

    @Nonnull
    public static Optional<AbstractPlayerDamageModel> getOptionalDamageModel(@Nullable Player player) {
        return Optional.ofNullable(CommonUtils.getExistingDamageModel(player));
    }

    @Nullable
    public static AbstractPlayerDamageModel getExistingDamageModel(@Nullable Player player) {
        if (!(player instanceof FirstAidDamageModelHolder)) {
            return null;
        }
        FirstAidDamageModelHolder holder = (FirstAidDamageModelHolder)player;
        return holder.firstaid$getDamageModelNullable();
    }

    public static boolean hasDamageModel(Entity entity) {
        return entity instanceof Player;
    }

    static {
        CommonUtils.ARMOR_SLOTS[3] = EquipmentSlot.HEAD;
        CommonUtils.ARMOR_SLOTS[2] = EquipmentSlot.CHEST;
        CommonUtils.ARMOR_SLOTS[1] = EquipmentSlot.LEGS;
        CommonUtils.ARMOR_SLOTS[0] = EquipmentSlot.FEET;
        SLOT_TO_PARTS = new EnumMap<EquipmentSlot, List<EnumPlayerPart>>(EquipmentSlot.class);
        SLOT_TO_PARTS.put(EquipmentSlot.HEAD, Collections.singletonList(EnumPlayerPart.HEAD));
        SLOT_TO_PARTS.put(EquipmentSlot.CHEST, Arrays.asList(EnumPlayerPart.LEFT_ARM, EnumPlayerPart.RIGHT_ARM, EnumPlayerPart.BODY));
        SLOT_TO_PARTS.put(EquipmentSlot.LEGS, Arrays.asList(EnumPlayerPart.LEFT_LEG, EnumPlayerPart.RIGHT_LEG));
        SLOT_TO_PARTS.put(EquipmentSlot.FEET, Arrays.asList(EnumPlayerPart.LEFT_FOOT, EnumPlayerPart.RIGHT_FOOT));
    }
}


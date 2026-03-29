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
import ichttt.mods.firstaid.common.compat.playerrevive.IPRCompatHandler;
import ichttt.mods.firstaid.common.compat.playerrevive.PRCompatManager;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.distribution.HealthDistribution;
import ichttt.mods.firstaid.common.init.FirstAidDataAttachments;
import ichttt.mods.firstaid.common.network.MessageSyncDamageModel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.common.util.LazyOptional;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;

public class CommonUtils {
    @Nonnull
    public static final EquipmentSlot[] ARMOR_SLOTS;
    @Nonnull
    private static final Map<EquipmentSlot, List<EnumPlayerPart>> SLOT_TO_PARTS;
    private static final ThreadLocal<Integer> SET_HEALTH_INTERCEPTION_SUPPRESSION = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Deque<DamageSource>> ACTIVE_DAMAGE_SOURCES = ThreadLocal.withInitial(ArrayDeque::new);

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
        if (handler.isBleeding(player)) {
            if (damageModel instanceof PlayerDamageModel playerDamageModel) {
                playerDamageModel.markExternalRevivePending(player);
            }
        } else if (handler.tryKnockOutPlayer(player, source)) {
            if (damageModel instanceof PlayerDamageModel playerDamageModel) {
                playerDamageModel.markExternalRevivePending(player);
            }
        } else {
            killPlayerDirectly(player, source);
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
        ModContainer activeModContainer = ModLoadingContext.get().getActiveContainer();
        return activeModContainer == null ? "UNKNOWN-NULL" : activeModContainer.getModId();
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
            syncDamageModel(serverPlayer);
        }
    }

    public static void syncDamageModel(ServerPlayer player) {
        AbstractPlayerDamageModel damageModel = getDamageModel(player);
        if (damageModel == null) {
            return;
        }
        FirstAid.NETWORKING.sendDamageModelSync(player, new MessageSyncDamageModel(player.getId(), damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get()));
    }

    public static boolean drawsHealthAsText(AbstractDamageablePart part) {
        int maxHealth = getMaxHearts(part.getMaxHealth());
        return maxHealth > 8;
    }

    public static float getVisualHealth(AbstractDamageablePart part) {
        float maxHealth = part.getMaxHealth();
        float currentHealth = Mth.clamp(part.currentHealth, 0.0F, maxHealth);
        if (drawsHealthAsText(part)) {
            return Math.min(maxHealth, Math.round(currentHealth * 10.0F) / 10.0F);
        }

        return Math.min(maxHealth, (float) Math.ceil(currentHealth));
    }

    public static float getVisibleMissingHealth(AbstractDamageablePart part) {
        return Math.max(0.0F, part.getMaxHealth() - getVisualHealth(part));
    }

    public static float getVisibleHealthRatio(AbstractDamageablePart part) {
        return part.getMaxHealth() <= 0 ? 1.0F : getVisualHealth(part) / part.getMaxHealth();
    }

    public static boolean isPartVisuallyFull(AbstractDamageablePart part) {
        return getVisibleMissingHealth(part) <= 0.0F;
    }

    private static int getMaxHearts(float value) {
        int maxCurrentHearts = Mth.ceil(value);
        if (maxCurrentHearts % 2 != 0) {
            maxCurrentHearts++;
        }

        return maxCurrentHearts >> 1;
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
        LazyOptional<AbstractPlayerDamageModel> optionalDamageModel = getOptionalDamageModel(player);
        try {
            return optionalDamageModel.orElseThrow(() -> new IllegalArgumentException("Player " + player.getName().getContents() + " is missing a damage model!"));
        } catch (IllegalArgumentException e) {
            // This is a band-aid solution, as bug reports about this keep coming up and these are really hard to debug bugs
            // I don't have the time to correctly debug this, so it seems like there is no other way right now
            if (FirstAidConfig.GENERAL.debug.get()) {
                FirstAid.LOGGER.fatal("Mandatory damage model missing!", e);
                throw e;
            } else {
                FirstAid.LOGGER.error("Missing a damage model, skipping further processing!", e);
                return null;
            }
        }
    }

    @Nonnull
    public static LazyOptional<AbstractPlayerDamageModel> getOptionalDamageModel(@Nullable Player player) {
        if (player == null) {
            return LazyOptional.empty();
        }
        return LazyOptional.of(() -> player.getData(FirstAidDataAttachments.DAMAGE_MODEL.get()));
    }

    @Nullable
    public static AbstractPlayerDamageModel getExistingDamageModel(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        return player.getExistingDataOrNull(FirstAidDataAttachments.DAMAGE_MODEL.get());
    }

    public static boolean hasDamageModel(Entity entity) {
        return entity instanceof Player && !(entity instanceof FakePlayer);
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

    public static boolean isFootOnlyDamageSource(@Nullable DamageSource source) {
        if (source == null) {
            return false;
        }
        if (source.is(DamageTypes.FALL) || source.is(DamageTypes.HOT_FLOOR)) {
            return true;
        }
        String normalizedDamageId = normalizeDamageId(source.type().msgId());
        return "fall".equals(normalizedDamageId) || "hotfloor".equals(normalizedDamageId);
    }

    public static void pushActiveDamageSource(@Nullable DamageSource source) {
        if (source != null) {
            ACTIVE_DAMAGE_SOURCES.get().push(source);
        }
    }

    public static void popActiveDamageSource() {
        Deque<DamageSource> damageSources = ACTIVE_DAMAGE_SOURCES.get();
        if (!damageSources.isEmpty()) {
            damageSources.pop();
        }
        if (damageSources.isEmpty()) {
            ACTIVE_DAMAGE_SOURCES.remove();
        }
    }

    @Nullable
    public static DamageSource getActiveDamageSource() {
        Deque<DamageSource> damageSources = ACTIVE_DAMAGE_SOURCES.get();
        return damageSources.isEmpty() ? null : damageSources.peek();
    }

    public static boolean isSetHealthInterceptionSuppressed() {
        return SET_HEALTH_INTERCEPTION_SUPPRESSION.get() > 0;
    }

    public static void runWithoutSetHealthInterception(Runnable action) {
        callWithoutSetHealthInterception(() -> {
            action.run();
            return null;
        });
    }

    public static <T> T callWithoutSetHealthInterception(Supplier<T> action) {
        int depth = SET_HEALTH_INTERCEPTION_SUPPRESSION.get();
        SET_HEALTH_INTERCEPTION_SUPPRESSION.set(depth + 1);
        try {
            return action.get();
        } finally {
            if (depth == 0) {
                SET_HEALTH_INTERCEPTION_SUPPRESSION.remove();
            } else {
                SET_HEALTH_INTERCEPTION_SUPPRESSION.set(depth);
            }
        }
    }

    private static String normalizeDamageId(String damageId) {
        return damageId == null ? "" : damageId.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    @Nonnull
    public static ServerPlayer checkServer(NetworkEvent.Context context) {
        if (context.getDirection() != NetworkDirection.PLAY_TO_SERVER)
            throw new IllegalArgumentException("Wrong side for server packet handler " + context.getDirection());
        context.setPacketHandled(true);
        return Objects.requireNonNull(context.getSender());
    }

    public static void checkClient(NetworkEvent.Context context) {
        if (context.getDirection() != NetworkDirection.PLAY_TO_CLIENT)
            throw new IllegalArgumentException("Wrong side for client packet handler: " + context.getDirection());
        context.setPacketHandled(true);
    }
}


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
   private static final Map<EquipmentSlot, List<EnumPlayerPart>> SLOT_TO_PARTS = new EnumMap<>(EquipmentSlot.class);

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
         } catch (RuntimeException var4) {
            FirstAid.LOGGER.warn("Tried to kill the player on the client! This should only happen on the server! Ignoring...", var4);
         }
      }

      IPRCompatHandler handler = PRCompatManager.getHandler();
      if (!handler.isBleeding(player) && !handler.tryKnockOutPlayer(player, source)) {
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
      return FabricLoader.getInstance().getModContainer("firstaid").map(container -> container.getMetadata().getId()).orElse("firstaid");
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
         if (!(missingHealth <= 0.0F)) {
            float healAmount = Math.max(0.0F, Math.round((float)(part.getMaxHealth() * percentage) * 100.0F) / 100.0F);
            if (!(healAmount <= 0.0F)) {
               part.heal(healAmount, player, applyDebuff);
            }
         }
      }

      if (player instanceof ServerPlayer serverPlayer) {
         FirstAidNetworking.sendDamageModelSync(serverPlayer, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
      }
   }

   public static void debugLogStacktrace(String name) {
      if (FirstAidConfig.GENERAL.debug.get()) {
         try {
            throw new RuntimeException("DEBUG:" + name);
         } catch (RuntimeException var2) {
            FirstAid.LOGGER.info("DEBUG: " + name, var2);
         }
      }
   }

   @Nullable
   public static AbstractPlayerDamageModel getDamageModel(@Nullable Player player) {
      if (player == null) {
         return null;
      } else if (player instanceof FirstAidDamageModelHolder holder) {
         return holder.firstaid$getDamageModel();
      } else if (FirstAidConfig.GENERAL.debug.get()) {
         IllegalArgumentException e = new IllegalArgumentException("Player " + player.getName().getContents() + " is missing a damage model!");
         FirstAid.LOGGER.fatal("Mandatory damage model missing!", e);
         throw e;
      } else {
         FirstAid.LOGGER.error("Missing a damage model, skipping further processing!");
         return null;
      }
   }

   @Nonnull
   public static Optional<AbstractPlayerDamageModel> getOptionalDamageModel(@Nullable Player player) {
      return Optional.ofNullable(getExistingDamageModel(player));
   }

   @Nullable
   public static AbstractPlayerDamageModel getExistingDamageModel(@Nullable Player player) {
      return player instanceof FirstAidDamageModelHolder holder ? holder.firstaid$getDamageModelNullable() : null;
   }

   public static boolean hasDamageModel(Entity entity) {
      return entity instanceof Player;
   }

   static {
      ARMOR_SLOTS[3] = EquipmentSlot.HEAD;
      ARMOR_SLOTS[2] = EquipmentSlot.CHEST;
      ARMOR_SLOTS[1] = EquipmentSlot.LEGS;
      ARMOR_SLOTS[0] = EquipmentSlot.FEET;
      SLOT_TO_PARTS.put(EquipmentSlot.HEAD, Collections.singletonList(EnumPlayerPart.HEAD));
      SLOT_TO_PARTS.put(EquipmentSlot.CHEST, Arrays.asList(EnumPlayerPart.LEFT_ARM, EnumPlayerPart.RIGHT_ARM, EnumPlayerPart.BODY));
      SLOT_TO_PARTS.put(EquipmentSlot.LEGS, Arrays.asList(EnumPlayerPart.LEFT_LEG, EnumPlayerPart.RIGHT_LEG));
      SLOT_TO_PARTS.put(EquipmentSlot.FEET, Arrays.asList(EnumPlayerPart.LEFT_FOOT, EnumPlayerPart.RIGHT_FOOT));
   }
}

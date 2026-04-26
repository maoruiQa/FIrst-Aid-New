package ichttt.mods.firstaid.common.damagesystem.distribution;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class HealthDistribution {
   private static final List<EnumPlayerPart> parts;

   public static void manageHealth(float health, AbstractPlayerDamageModel damageModel, Player player, boolean sendChanges, boolean distribute) {
      if (sendChanges && player.level().isClientSide()) {
         FirstAid.LOGGER
            .warn(
               "The sendChanges flag was set on the client, it can however only work on the server!",
               new RuntimeException("sendChanges flag set on the client, this is not supported!")
            );
         sendChanges = false;
      } else if (sendChanges && !(player instanceof ServerPlayer)) {
         sendChanges = false;
      }

      float toHeal = distribute ? health / 8.0F : health;
      Collections.shuffle(parts);
      List<AbstractDamageablePart> damageableParts = new ArrayList<>(parts.size());

      for (EnumPlayerPart part : parts) {
         damageableParts.add(damageModel.getFromEnum(part));
      }

      if (distribute) {
         damageableParts.sort(Comparator.comparingDouble(value -> value.getMaxHealth() - value.currentHealth));
      }

      float[] healingDone = new float[8];

      for (int i = 0; i < 8; i++) {
         AbstractDamageablePart part = damageableParts.get(i);
         float diff = toHeal - part.heal(toHeal, player, !player.level().isClientSide());
         diff = Math.round(diff * 10000.0F) / 10000.0F;
         healingDone[part.part.ordinal()] = diff;
         health -= diff;
         if (distribute) {
            if (i < 7) {
               toHeal = health / (7.0F - i);
            }
         } else {
            toHeal -= diff;
            if (toHeal <= 0.0F) {
               break;
            }
         }
      }

      if (sendChanges) {
         ServerPlayer playerMP = (ServerPlayer)player;
         FirstAidNetworking.sendDamageModelSync(playerMP, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
      }
   }

   public static void distributeHealth(float health, Player player, boolean sendChanges) {
      AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
      if (damageModel != null) {
         manageHealth(health, damageModel, player, sendChanges, true);
      }
   }

   public static void addRandomHealth(float health, Player player, boolean sendChanges) {
      AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
      if (damageModel != null) {
         manageHealth(health, damageModel, player, sendChanges, false);
      }
   }

   public static void applyNaturalRegen(float health, Player player, boolean sendChanges) {
      AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
      if (damageModel == null || health <= 0.0F) {
         return;
      }

      if (sendChanges && player.level().isClientSide()) {
         sendChanges = false;
      } else if (sendChanges && !(player instanceof ServerPlayer)) {
         sendChanges = false;
      }

      if (FirstAid.naturalRegenMode == FirstAid.NaturalRegenMode.OFF) {
         return;
      }

      AbstractDamageablePart target = selectNaturalRegenTarget(damageModel);
      if (target == null) {
         return;
      }

      float limit = getNaturalRegenLimit(target);
      float healAmount = Math.min(health, Math.max(0.0F, limit - target.currentHealth));
      if (healAmount <= 0.0F) {
         return;
      }

      target.heal(healAmount, player, !player.level().isClientSide());
      if (sendChanges) {
         FirstAidNetworking.sendDamageModelSync((ServerPlayer)player, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
      }
   }

   public static boolean canApplyNaturalRegen(AbstractPlayerDamageModel damageModel) {
      return FirstAid.naturalRegenMode != FirstAid.NaturalRegenMode.OFF && selectNaturalRegenTarget(damageModel) != null;
   }

   private static AbstractDamageablePart selectNaturalRegenTarget(AbstractPlayerDamageModel damageModel) {
      List<AbstractDamageablePart> eligibleParts = getEligibleNaturalRegenParts(damageModel);
      if (eligibleParts.isEmpty()) {
         return null;
      } else if (FirstAid.naturalRegenStrategy == FirstAid.NaturalRegenStrategy.RANDOM) {
         return pickRandomPart(eligibleParts);
      } else {
         List<AbstractDamageablePart> criticalCandidates = new ArrayList<>(2);
         addCriticalCandidate(criticalCandidates, damageModel.getFromEnum(EnumPlayerPart.HEAD));
         addCriticalCandidate(criticalCandidates, damageModel.getFromEnum(EnumPlayerPart.BODY));
         if (!criticalCandidates.isEmpty()) {
            return criticalCandidates.stream().min(Comparator.comparingDouble(part -> part.currentHealth)).orElse(null);
         } else {
            List<AbstractDamageablePart> nonCriticalEligible = new ArrayList<>();

            for (AbstractDamageablePart part : eligibleParts) {
               if (part.part != EnumPlayerPart.HEAD && part.part != EnumPlayerPart.BODY) {
                  nonCriticalEligible.add(part);
               }
            }

            return nonCriticalEligible.isEmpty() ? pickRandomPart(eligibleParts) : pickRandomPart(nonCriticalEligible);
         }
      }
   }

   private static void addCriticalCandidate(List<AbstractDamageablePart> criticalCandidates, AbstractDamageablePart part) {
      if (part != null && isNaturalRegenEligible(part) && CommonUtils.getVisibleHealthRatio(part) <= FirstAid.naturalRegenCriticalPriorityRatio) {
         criticalCandidates.add(part);
      }
   }

   private static List<AbstractDamageablePart> getEligibleNaturalRegenParts(AbstractPlayerDamageModel damageModel) {
      List<AbstractDamageablePart> eligibleParts = new ArrayList<>(EnumPlayerPart.VALUES.length);

      for (EnumPlayerPart partId : EnumPlayerPart.VALUES) {
         AbstractDamageablePart part = damageModel.getFromEnum(partId);
         if (isNaturalRegenEligible(part)) {
            eligibleParts.add(part);
         }
      }

      return eligibleParts;
   }

   private static boolean isNaturalRegenEligible(AbstractDamageablePart part) {
      if (part == null || FirstAid.naturalRegenMode == FirstAid.NaturalRegenMode.LIMITED2 && part.currentHealth <= 0.0F) {
         return false;
      }

      return getNaturalRegenLimit(part) - part.currentHealth > 1.0E-4F;
   }

   private static float getNaturalRegenLimit(AbstractDamageablePart part) {
      return switch (FirstAid.naturalRegenMode) {
         case FULL -> part.getMaxHealth();
         case LIMITED, LIMITED2 -> getLimitedNaturalRegenCap(part);
         case OFF -> 0.0F;
      };
   }

   private static float getLimitedNaturalRegenCap(AbstractDamageablePart part) {
      float maxHealth = part.getMaxHealth();
      if (maxHealth <= 0.0F) {
         return 0.0F;
      } else if (drawsHealthAsText(part)) {
         return Math.min(maxHealth, (float)Math.floor(maxHealth * FirstAid.naturalRegenLimitRatio * 10.0F) / 10.0F);
      } else {
         return Math.min(maxHealth, (float)Math.floor(maxHealth * FirstAid.naturalRegenLimitRatio));
      }
   }

   private static boolean drawsHealthAsText(AbstractDamageablePart part) {
      int maxCurrentHearts = Mth.ceil(part.getMaxHealth());
      if (maxCurrentHearts % 2 != 0) {
         maxCurrentHearts++;
      }

      return (maxCurrentHearts >> 1) > 8;
   }

   private static AbstractDamageablePart pickRandomPart(List<AbstractDamageablePart> damageableParts) {
      return damageableParts.get(ThreadLocalRandom.current().nextInt(damageableParts.size()));
   }

   static {
      EnumPlayerPart[] partArray = EnumPlayerPart.VALUES;
      parts = new ArrayList<>(partArray.length);
      parts.addAll(Arrays.asList(partArray));
   }
}

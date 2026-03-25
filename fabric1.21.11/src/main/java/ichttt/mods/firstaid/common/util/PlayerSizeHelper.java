package ichttt.mods.firstaid.common.util;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.AABBAlignedBoundingBox;
import ichttt.mods.firstaid.common.damagesystem.distribution.StandardDamageDistributionAlgorithm;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PlayerSizeHelper {
   private static final Map<EquipmentSlot, AABBAlignedBoundingBox> NORMAL_BOXES;
   private static final Map<EquipmentSlot, AABBAlignedBoundingBox> SNEAKING_BOXES;
   private static final double CHEST_CENTER_THRESHOLD_RATIO = 0.18D;

   @Nonnull
   public static Map<EquipmentSlot, AABBAlignedBoundingBox> getBoxes(Entity entity) {
      switch (entity.getPose()) {
         case STANDING:
            return NORMAL_BOXES;
         case CROUCHING:
            return SNEAKING_BOXES;
         case SPIN_ATTACK:
         case FALL_FLYING:
            return Collections.emptyMap();
         case DYING:
         case SLEEPING:
         case SWIMMING:
         default:
            return Collections.emptyMap();
      }
   }

   @Nullable
   public static IDamageDistributionAlgorithm getProjectileDistribution(Player player, Vec3 hitPosition) {
      return createDistributionForHit(player, hitPosition);
   }

   @Nullable
   public static EquipmentSlot getSlotTypeForProjectileHit(Entity hittingObject, Player toTest) {
      Vec3 oldPosition = hittingObject.position();
      Vec3 newPosition = oldPosition.add(hittingObject.getDeltaMovement());
      Optional<Vec3> hitPoint = toTest.getBoundingBox().clip(oldPosition, newPosition);
      if (hitPoint.isPresent()) {
         return getSlotForHitPoint(toTest, hitPoint.get());
      }

      if (FirstAidConfig.GENERAL.debug.get()) {
         FirstAid.LOGGER.info("getSlotTypeForProjectileHit: Not found!");
      }

      return null;
   }

   public static IDamageDistributionAlgorithm getMeleeDistribution(Player player, DamageSource source) {
      Entity causingEntity = source.getEntity();
      if (causingEntity != null && causingEntity == source.getDirectEntity() && causingEntity instanceof LivingEntity attacker) {
         Vec3 estimatedHitPoint = getClosestPoint(player.getBoundingBox(), attacker.getEyePosition());
         IDamageDistributionAlgorithm distribution = createDistributionForHit(player, estimatedHitPoint);
         if (distribution != null) {
            return distribution;
         }
      }

      return null;
   }

   @Nullable
   public static EnumPlayerPart getPartForHitPoint(Player player, Vec3 hitPoint) {
      EquipmentSlot slot = getSlotForHitPoint(player, hitPoint);
      if (slot == null) {
         return null;
      } else {
         return switch (slot) {
            case HEAD -> EnumPlayerPart.HEAD;
            case CHEST -> getChestPart(player, hitPoint);
            case LEGS -> getLocalRightOffset(player, hitPoint) < 0.0D ? EnumPlayerPart.LEFT_LEG : EnumPlayerPart.RIGHT_LEG;
            case FEET -> getLocalRightOffset(player, hitPoint) < 0.0D ? EnumPlayerPart.LEFT_FOOT : EnumPlayerPart.RIGHT_FOOT;
            default -> null;
         };
      }
   }

   @Nullable
   private static IDamageDistributionAlgorithm createDistributionForHit(Player player, Vec3 hitPoint) {
      EnumPlayerPart part = getPartForHitPoint(player, hitPoint);
      if (part != null) {
         if (FirstAidConfig.GENERAL.debug.get()) {
            FirstAid.LOGGER.info("Resolved locational hit to {}", part);
         }

         return createPartDistribution(part);
      } else {
         EquipmentSlot slot = getSlotForHitPoint(player, hitPoint);
         if (slot != null) {
            if (FirstAidConfig.GENERAL.debug.get()) {
               FirstAid.LOGGER.info("Resolved locational hit to slot {}", slot);
            }

            return createSlotDistribution(slot);
         } else {
            return null;
         }
      }
   }

   @Nullable
   private static EquipmentSlot getSlotForHitPoint(Player player, Vec3 hitPoint) {
      PlayerSizeHelper.BodyProfile bodyProfile = getBodyProfile(player);
      if (bodyProfile == null) {
         return null;
      } else {
         AABB boundingBox = player.getBoundingBox();
         double height = boundingBox.getYsize();
         if (height <= 0.0D) {
            return null;
         } else {
            double normalizedY = Mth.clamp((hitPoint.y - boundingBox.minY) / height, 0.0D, 1.0D);
            if (normalizedY < bodyProfile.feetMaxY()) {
               return EquipmentSlot.FEET;
            } else if (normalizedY < bodyProfile.legsMaxY()) {
               return EquipmentSlot.LEGS;
            } else {
               return normalizedY < bodyProfile.chestMaxY() ? EquipmentSlot.CHEST : EquipmentSlot.HEAD;
            }
         }
      }
   }

   private static EnumPlayerPart getChestPart(Player player, Vec3 hitPoint) {
      double localRightOffset = getLocalRightOffset(player, hitPoint);
      double centerThreshold = Math.max(player.getBoundingBox().getXsize(), player.getBoundingBox().getZsize()) * CHEST_CENTER_THRESHOLD_RATIO;
      if (Math.abs(localRightOffset) <= centerThreshold) {
         return EnumPlayerPart.BODY;
      } else {
         return localRightOffset < 0.0D ? EnumPlayerPart.LEFT_ARM : EnumPlayerPart.RIGHT_ARM;
      }
   }

   private static double getLocalRightOffset(Player player, Vec3 hitPoint) {
      Vec3 center = player.getBoundingBox().getCenter();
      Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot());
      Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
      double length = Math.sqrt(right.x * right.x + right.z * right.z);
      if (length <= 1.0E-6D) {
         return 0.0D;
      } else {
         Vec3 normalizedRight = new Vec3(right.x / length, 0.0D, right.z / length);
         Vec3 offset = hitPoint.subtract(center);
         return offset.dot(normalizedRight);
      }
   }

   private static Vec3 getClosestPoint(AABB box, Vec3 point) {
      return new Vec3(Mth.clamp(point.x, box.minX, box.maxX), Mth.clamp(point.y, box.minY, box.maxY), Mth.clamp(point.z, box.minZ, box.maxZ));
   }

   private static IDamageDistributionAlgorithm createPartDistribution(EnumPlayerPart part) {
      Map<EquipmentSlot, List<EnumPlayerPart>> list = new LinkedHashMap<>();
      list.put(part.slot, Collections.singletonList(part));
      return new StandardDamageDistributionAlgorithm(list, false, true);
   }

   @Nullable
   private static IDamageDistributionAlgorithm createSlotDistribution(EquipmentSlot slot) {
      List<EnumPlayerPart> parts = CommonUtils.getPartListForSlot(slot);
      if (parts.isEmpty()) {
         return null;
      } else {
         Map<EquipmentSlot, List<EnumPlayerPart>> list = new LinkedHashMap<>();
         list.put(slot, parts);
         return new StandardDamageDistributionAlgorithm(list, false, true);
      }
   }

   @Nullable
   private static PlayerSizeHelper.BodyProfile getBodyProfile(Entity entity) {
      return switch (entity.getPose()) {
         case STANDING -> new PlayerSizeHelper.BodyProfile(0.15D, 0.45D, 0.8D);
         case CROUCHING -> new PlayerSizeHelper.BodyProfile(0.15D, 0.4D, 0.75D);
         default -> null;
      };
   }

   static {
      Map<EquipmentSlot, AABBAlignedBoundingBox> builder = new LinkedHashMap<>();
      builder.put(EquipmentSlot.FEET, new AABBAlignedBoundingBox(0.0, 0.0, 0.0, 1.0, 0.15, 1.0));
      builder.put(EquipmentSlot.LEGS, new AABBAlignedBoundingBox(0.0, 0.15, 0.0, 1.0, 0.45, 1.0));
      builder.put(EquipmentSlot.CHEST, new AABBAlignedBoundingBox(0.0, 0.45, 0.0, 1.0, 0.8, 1.0));
      builder.put(EquipmentSlot.HEAD, new AABBAlignedBoundingBox(0.0, 0.8, 0.0, 1.0, 1.0, 1.0));
      NORMAL_BOXES = Collections.unmodifiableMap(builder);
      builder = new LinkedHashMap<>();
      builder.put(EquipmentSlot.FEET, new AABBAlignedBoundingBox(0.0, 0.0, 0.0, 1.0, 0.15, 1.0));
      builder.put(EquipmentSlot.LEGS, new AABBAlignedBoundingBox(0.0, 0.15, 0.0, 1.0, 0.4, 1.0));
      builder.put(EquipmentSlot.CHEST, new AABBAlignedBoundingBox(0.0, 0.4, 0.0, 1.0, 0.75, 1.0));
      builder.put(EquipmentSlot.HEAD, new AABBAlignedBoundingBox(0.0, 0.75, 0.0, 1.0, 1.0, 1.0));
      SNEAKING_BOXES = Collections.unmodifiableMap(builder);
   }

   private record BodyProfile(double feetMaxY, double legsMaxY, double chestMaxY) {
   }
}

package ichttt.mods.firstaid.common.util;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.AABBAlignedBoundingBox;
import ichttt.mods.firstaid.common.damagesystem.distribution.StandardDamageDistributionAlgorithm;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import javax.annotation.Nonnull;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PlayerSizeHelper {
   private static final Map<EquipmentSlot, AABBAlignedBoundingBox> NORMAL_BOXES;
   private static final Map<EquipmentSlot, AABBAlignedBoundingBox> SNEAKING_BOXES;

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

   public static EquipmentSlot getSlotTypeForProjectileHit(Entity hittingObject, Player toTest) {
      Map<EquipmentSlot, AABBAlignedBoundingBox> toUse = getBoxes(toTest);
      Vec3 oldPosition = hittingObject.position();
      Vec3 newPosition = oldPosition.add(hittingObject.getDeltaMovement());
      float[] inflationSteps = new float[]{0.01F, 0.1F, 0.2F, 0.3F};

      for (float inflation : inflationSteps) {
         EquipmentSlot bestSlot = null;
         double bestValue = Double.MAX_VALUE;

         for (Entry<EquipmentSlot, AABBAlignedBoundingBox> entry : toUse.entrySet()) {
            AABB axisalignedbb = entry.getValue().createAABB(toTest.getBoundingBox()).inflate(inflation);
            Optional<Vec3> optional = axisalignedbb.clip(oldPosition, newPosition);
            if (optional.isPresent()) {
               double d1 = oldPosition.distanceToSqr(optional.get());
               double d2 = 0.0;
               if (d1 + d2 < bestValue) {
                  bestSlot = entry.getKey();
                  bestValue = d1 + d2;
               }
            }
         }

         if (bestSlot != null) {
            if (FirstAidConfig.GENERAL.debug.get()) {
               FirstAid.LOGGER.info("getSlotTypeForProjectileHit: Inflation: " + inflation + " best slot: " + bestSlot);
            }

            return bestSlot;
         }
      }

      if (FirstAidConfig.GENERAL.debug.get()) {
         FirstAid.LOGGER.info("getSlotTypeForProjectileHit: Not found!");
      }

      return null;
   }

   public static IDamageDistributionAlgorithm getMeleeDistribution(Player player, DamageSource source) {
      Entity causingEntity = source.getEntity();
      if (causingEntity != null && causingEntity == source.getDirectEntity() && causingEntity instanceof Mob mobEntity && mobEntity.getTarget() == player) {
         Map<EquipmentSlot, AABBAlignedBoundingBox> boxes = getBoxes(player);
         if (!boxes.isEmpty()) {
            List<EquipmentSlot> allowedParts = new ArrayList<>();
            AABB modAABB = mobEntity.getBoundingBox()
               .inflate(mobEntity.getBbWidth() * 2.0F + player.getBbWidth(), 0.0, mobEntity.getBbWidth() * 2.0F + player.getBbWidth());

            for (Entry<EquipmentSlot, AABBAlignedBoundingBox> entry : boxes.entrySet()) {
               AABB partAABB = entry.getValue().createAABB(player.getBoundingBox());
               if (modAABB.intersects(partAABB)) {
                  allowedParts.add(entry.getKey());
               }
            }

            if (FirstAidConfig.GENERAL.debug.get()) {
               FirstAid.LOGGER.info("getMeleeDistribution: Has distribution with {}", allowedParts);
            }

            if (allowedParts.isEmpty() && player.getY() > mobEntity.getY() && player.getY() - mobEntity.getY() < mobEntity.getBbHeight() * 2.0F) {
               if (FirstAidConfig.GENERAL.debug.get()) {
                  FirstAid.LOGGER.info("Hack adding feet");
               }

               allowedParts.add(EquipmentSlot.FEET);
            }

            if (!allowedParts.isEmpty() && !allowedParts.containsAll(Arrays.asList(CommonUtils.ARMOR_SLOTS))) {
               Map<EquipmentSlot, List<EnumPlayerPart>> list = new LinkedHashMap<>();

               for (EquipmentSlot allowedPart : allowedParts) {
                  list.put(allowedPart, CommonUtils.getPartListForSlot(allowedPart));
               }

               return new StandardDamageDistributionAlgorithm(list, true, true);
            }
         }
      }

      return null;
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
}

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class ProjectileNearMissDetector {
   private static final int TRACKED_PROJECTILE_AGE = 5;
   private static final double SCAN_RADIUS = 3.25;
   private static final double NEAR_MISS_RADIUS = 1.85;
   private static final double MINIMUM_PROJECTILE_SPEED = 0.2;
   private final SuppressionFeedbackController feedbackController;
   private final Map<Integer, ProjectileNearMissDetector.TrackedProjectile> trackedProjectiles = new HashMap<>();
   private Level trackedLevel;

   public ProjectileNearMissDetector(SuppressionFeedbackController feedbackController) {
      this.feedbackController = feedbackController;
   }

   public void tick(Minecraft client) {
      Player player = client.player;
      Level level = client.level;
      if (player != null && level != null && player.isAlive() && !player.isSpectator() && FirstAid.isSynced) {
         if (this.trackedLevel != level) {
            this.trackedProjectiles.clear();
            this.trackedLevel = level;
         }

         long gameTime = level.getGameTime();
         AABB scanBox = player.getBoundingBox().inflate(3.25);
         List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, scanBox, this::isValidProjectile);
         Set<Integer> seenIds = new HashSet<>();

         for (Projectile projectile : projectiles) {
            int id = projectile.getId();
            seenIds.add(id);
            Vec3 currentPosition = projectile.position();
            ProjectileNearMissDetector.TrackedProjectile trackedProjectile = this.trackedProjectiles
               .computeIfAbsent(id, key -> new ProjectileNearMissDetector.TrackedProjectile(currentPosition, gameTime));
            trackedProjectile.lastSeenGameTime = gameTime;
            if (!trackedProjectile.triggered && this.tryApplyNearMiss(player, projectile, trackedProjectile)) {
               trackedProjectile.triggered = true;
            }

            trackedProjectile.lastPosition = currentPosition;
         }

         this.cleanup(seenIds, gameTime);
      } else {
         this.clearIfLevelChanged(level);
      }
   }

   public void clear() {
      this.trackedProjectiles.clear();
      this.trackedLevel = null;
   }

   private void clearIfLevelChanged(Level level) {
      if (this.trackedLevel != level) {
         this.trackedLevel = level;
         this.trackedProjectiles.clear();
      }
   }

   private boolean tryApplyNearMiss(Player player, Projectile projectile, ProjectileNearMissDetector.TrackedProjectile trackedProjectile) {
      Entity owner = projectile.getOwner();
      if (owner != null && owner.getUUID().equals(player.getUUID())) {
         return false;
      } else {
         Vec3 currentPosition = projectile.position();
         Vec3 previousPosition = trackedProjectile.lastPosition;
         if (previousPosition.distanceToSqr(currentPosition) < 1.0E-6) {
            previousPosition = currentPosition.subtract(projectile.getDeltaMovement());
         }

         Vec3 endPosition = currentPosition.add(projectile.getDeltaMovement());
         Vec3 segment = endPosition.subtract(previousPosition);
         double speed = segment.length();
         if (speed < 0.2) {
            return false;
         } else {
            AABB playerBox = player.getBoundingBox().inflate(0.12);
            if (!playerBox.intersects(projectile.getBoundingBox()) && !playerBox.clip(previousPosition, endPosition).isPresent()) {
               Vec3 eyePosition = player.getEyePosition();
               ProjectileNearMissDetector.ClosestPointResult closestPointResult = this.closestPointOnSegment(previousPosition, endPosition, eyePosition);
               if (!(closestPointResult.progress <= 0.0) && !(closestPointResult.progress >= 1.0)) {
                  double distance = closestPointResult.point.distanceTo(eyePosition);
                  if (distance > 1.85) {
                     return false;
                  } else if (!this.hasLineOfSight(player, closestPointResult.point, eyePosition)) {
                     return false;
                  } else {
                     float severity = this.calculateSeverity(speed, distance);
                     if (severity <= 0.0F) {
                        return false;
                     } else {
                        Vec3 localOffset = closestPointResult.point.subtract(eyePosition);
                        float lateralSign = this.resolveLateralSign(player, localOffset);
                        float verticalSign = Math.signum((float)localOffset.y);
                        this.feedbackController.onNearMiss(player, severity, lateralSign, verticalSign);
                        return true;
                     }
                  }
               } else {
                  return false;
               }
            } else {
               return false;
            }
         }
      }
   }

   private float calculateSeverity(double speed, double distance) {
      float speedFactor = Mth.clamp((float)((speed - 0.2) / Math.max(0.15, 0.11000000000000001)), 0.0F, 1.0F);
      float distanceFactor = Mth.clamp(1.35F - (float)(distance / 1.85), 0.0F, 1.0F);
      return Mth.clamp(distanceFactor * (0.75F + 0.55F * speedFactor), 0.0F, 1.0F);
   }

   private boolean hasLineOfSight(Player player, Vec3 from, Vec3 to) {
      BlockHitResult hitResult = player.level().clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, player));
      return hitResult.getType() == Type.MISS;
   }

   private ProjectileNearMissDetector.ClosestPointResult closestPointOnSegment(Vec3 start, Vec3 end, Vec3 target) {
      Vec3 segment = end.subtract(start);
      double lengthSqr = segment.lengthSqr();
      if (lengthSqr < 1.0E-7) {
         return new ProjectileNearMissDetector.ClosestPointResult(start, 0.0);
      } else {
         double progress = Mth.clamp(target.subtract(start).dot(segment) / lengthSqr, 0.0, 1.0);
         return new ProjectileNearMissDetector.ClosestPointResult(start.add(segment.scale(progress)), progress);
      }
   }

   private boolean isValidProjectile(Projectile projectile) {
      return projectile.isAlive() && !projectile.isRemoved() && !FirstAid.isSuppressionBlacklisted(projectile);
   }

   private float resolveLateralSign(Player player, Vec3 offset) {
      Vec3 look = player.getViewVector(1.0F);
      Vec3 horizontalLook = new Vec3(look.x, 0.0, look.z);
      if (horizontalLook.lengthSqr() < 1.0E-4) {
         return 1.0F;
      } else {
         horizontalLook = horizontalLook.normalize();
         Vec3 right = new Vec3(horizontalLook.z, 0.0, -horizontalLook.x);
         float sign = Math.signum((float)offset.dot(right));
         return sign == 0.0F ? 1.0F : sign;
      }
   }

   private void cleanup(Set<Integer> seenIds, long gameTime) {
      Iterator<Entry<Integer, ProjectileNearMissDetector.TrackedProjectile>> iterator = this.trackedProjectiles.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<Integer, ProjectileNearMissDetector.TrackedProjectile> entry = iterator.next();
         ProjectileNearMissDetector.TrackedProjectile trackedProjectile = entry.getValue();
         if (!seenIds.contains(entry.getKey()) && gameTime - trackedProjectile.lastSeenGameTime > 5L) {
            iterator.remove();
         }
      }
   }

   private record ClosestPointResult(Vec3 point, double progress) {
   }

   private static final class TrackedProjectile {
      private Vec3 lastPosition;
      private long lastSeenGameTime;
      private boolean triggered;

      private TrackedProjectile(Vec3 lastPosition, long lastSeenGameTime) {
         this.lastPosition = lastPosition;
         this.lastSeenGameTime = lastSeenGameTime;
      }
   }
}

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

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProjectileNearMissDetector {
    private static final int TRACKED_PROJECTILE_AGE = 5;
    private static final double SCAN_RADIUS = 3.25D;
    private static final double NEAR_MISS_RADIUS = 1.85D;
    private static final double MINIMUM_PROJECTILE_SPEED = 0.20D;

    private final SuppressionFeedbackController feedbackController;
    private final Map<Integer, TrackedProjectile> trackedProjectiles = new HashMap<>();
    private Level trackedLevel;

    public ProjectileNearMissDetector(SuppressionFeedbackController feedbackController) {
        this.feedbackController = feedbackController;
    }

    public void tick(Minecraft client) {
        Player player = client.player;
        Level level = client.level;
        if (player == null || level == null || !player.isAlive() || player.isSpectator() || !FirstAid.isSynced) {
            clearIfLevelChanged(level);
            return;
        }

        if (trackedLevel != level) {
            trackedProjectiles.clear();
            trackedLevel = level;
        }

        long gameTime = level.getGameTime();
        AABB scanBox = player.getBoundingBox().inflate(SCAN_RADIUS);
        List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, scanBox, this::isValidProjectile);
        Set<Integer> seenIds = new HashSet<>();
        for (Projectile projectile : projectiles) {
            int id = projectile.getId();
            seenIds.add(id);
            Vec3 currentPosition = projectile.position();
            TrackedProjectile trackedProjectile = trackedProjectiles.computeIfAbsent(id, key -> new TrackedProjectile(currentPosition, gameTime));
            trackedProjectile.lastSeenGameTime = gameTime;
            if (!trackedProjectile.triggered && tryApplyNearMiss(player, projectile, trackedProjectile)) {
                trackedProjectile.triggered = true;
            }
            trackedProjectile.lastPosition = currentPosition;
        }
        cleanup(seenIds, gameTime);
    }

    public void clear() {
        trackedProjectiles.clear();
        trackedLevel = null;
    }

    private void clearIfLevelChanged(Level level) {
        if (trackedLevel != level) {
            trackedLevel = level;
            trackedProjectiles.clear();
        }
    }

    private boolean tryApplyNearMiss(Player player, Projectile projectile, TrackedProjectile trackedProjectile) {
        Entity owner = projectile.getOwner();
        if (owner != null && owner.getUUID().equals(player.getUUID())) {
            return false;
        }

        Vec3 currentPosition = projectile.position();
        Vec3 previousPosition = trackedProjectile.lastPosition;
        if (previousPosition.distanceToSqr(currentPosition) < 1.0E-6D) {
            previousPosition = currentPosition.subtract(projectile.getDeltaMovement());
        }
        Vec3 endPosition = currentPosition.add(projectile.getDeltaMovement());
        Vec3 segment = endPosition.subtract(previousPosition);
        double speed = segment.length();
        if (speed < MINIMUM_PROJECTILE_SPEED) {
            return false;
        }

        AABB playerBox = player.getBoundingBox().inflate(0.12D);
        if (playerBox.intersects(projectile.getBoundingBox()) || playerBox.clip(previousPosition, endPosition).isPresent()) {
            return false;
        }

        Vec3 eyePosition = player.getEyePosition();
        ClosestPointResult closestPointResult = closestPointOnSegment(previousPosition, endPosition, eyePosition);
        if (closestPointResult.progress <= 0.0D || closestPointResult.progress >= 1.0D) {
            return false;
        }

        double distance = closestPointResult.point.distanceTo(eyePosition);
        if (distance > NEAR_MISS_RADIUS) {
            return false;
        }

        if (!hasLineOfSight(player, closestPointResult.point, eyePosition)) {
            return false;
        }

        float severity = calculateSeverity(speed, distance);
        if (severity <= 0.0F) {
            return false;
        }

        Vec3 localOffset = closestPointResult.point.subtract(eyePosition);
        float lateralSign = resolveLateralSign(player, localOffset);
        float verticalSign = Math.signum((float) localOffset.y);
        feedbackController.onNearMiss(player, severity, lateralSign, verticalSign);
        return true;
    }

    private float calculateSeverity(double speed, double distance) {
        float speedFactor = Mth.clamp((float) ((speed - MINIMUM_PROJECTILE_SPEED) / Math.max(0.15D, MINIMUM_PROJECTILE_SPEED * 0.55D)), 0.0F, 1.0F);
        float distanceFactor = Mth.clamp(1.35F - (float) (distance / NEAR_MISS_RADIUS), 0.0F, 1.0F);
        return Mth.clamp(distanceFactor * (0.75F + 0.55F * speedFactor), 0.0F, 1.0F);
    }

    private boolean hasLineOfSight(Player player, Vec3 from, Vec3 to) {
        BlockHitResult hitResult = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hitResult.getType() == HitResult.Type.MISS;
    }

    private ClosestPointResult closestPointOnSegment(Vec3 start, Vec3 end, Vec3 target) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-7D) {
            return new ClosestPointResult(start, 0.0D);
        }
        double progress = Mth.clamp(target.subtract(start).dot(segment) / lengthSqr, 0.0D, 1.0D);
        return new ClosestPointResult(start.add(segment.scale(progress)), progress);
    }

    private boolean isValidProjectile(Projectile projectile) {
        return projectile.isAlive() && !projectile.isRemoved() && !FirstAid.isSuppressionBlacklisted(projectile);
    }

    private float resolveLateralSign(Player player, Vec3 offset) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-4D) {
            return 1.0F;
        }
        horizontalLook = horizontalLook.normalize();
        Vec3 right = new Vec3(horizontalLook.z, 0.0D, -horizontalLook.x);
        float sign = Math.signum((float) offset.dot(right));
        return sign == 0.0F ? 1.0F : sign;
    }

    private void cleanup(Set<Integer> seenIds, long gameTime) {
        Iterator<Map.Entry<Integer, TrackedProjectile>> iterator = trackedProjectiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TrackedProjectile> entry = iterator.next();
            TrackedProjectile trackedProjectile = entry.getValue();
            if (!seenIds.contains(entry.getKey()) && gameTime - trackedProjectile.lastSeenGameTime > TRACKED_PROJECTILE_AGE) {
                iterator.remove();
            }
        }
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

    private record ClosestPointResult(Vec3 point, double progress) {
    }
}

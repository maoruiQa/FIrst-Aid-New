/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ichttt.mods.firstaid.FirstAid
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.entity.projectile.Projectile
 *  net.minecraft.world.level.ClipContext
 *  net.minecraft.world.level.ClipContext$Block
 *  net.minecraft.world.level.ClipContext$Fluid
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.HitResult$Type
 *  net.minecraft.world.phys.Vec3
 */
package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.client.SuppressionFeedbackController;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
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

public final class ProjectileNearMissDetector {
    private static final int TRACKED_PROJECTILE_AGE = 5;
    private static final double SCAN_RADIUS = 3.25;
    private static final double NEAR_MISS_RADIUS = 1.85;
    private static final double MINIMUM_PROJECTILE_SPEED = 0.2;
    private final SuppressionFeedbackController feedbackController;
    private final Map<Integer, TrackedProjectile> trackedProjectiles = new HashMap<Integer, TrackedProjectile>();
    private Level trackedLevel;

    public ProjectileNearMissDetector(SuppressionFeedbackController feedbackController) {
        this.feedbackController = feedbackController;
    }

    public void tick(Minecraft client) {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null || !player.isAlive() || player.isSpectator() || !FirstAid.isSynced) {
            this.clearIfLevelChanged((Level)level);
            return;
        }
        if (this.trackedLevel != level) {
            this.trackedProjectiles.clear();
            this.trackedLevel = level;
        }
        long gameTime = level.getGameTime();
        AABB scanBox = player.getBoundingBox().inflate(3.25);
        List projectiles = level.getEntitiesOfClass(Projectile.class, scanBox, this::isValidProjectile);
        HashSet<Integer> seenIds = new HashSet<Integer>();
        for (Projectile projectile : projectiles) {
            int id = projectile.getId();
            seenIds.add(id);
            Vec3 currentPosition = projectile.position();
            TrackedProjectile trackedProjectile = this.trackedProjectiles.computeIfAbsent(id, key -> new TrackedProjectile(currentPosition, gameTime));
            trackedProjectile.lastSeenGameTime = gameTime;
            if (!trackedProjectile.triggered && this.tryApplyNearMiss((Player)player, projectile, trackedProjectile)) {
                trackedProjectile.triggered = true;
            }
            trackedProjectile.lastPosition = currentPosition;
        }
        this.cleanup(seenIds, gameTime);
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

    private boolean tryApplyNearMiss(Player player, Projectile projectile, TrackedProjectile trackedProjectile) {
        Vec3 endPosition;
        Vec3 segment;
        double speed;
        Entity owner = projectile.getOwner();
        if (owner != null && owner.getUUID().equals(player.getUUID())) {
            return false;
        }
        Vec3 previousPosition = trackedProjectile.lastPosition;
        Vec3 currentPosition = projectile.position();
        if (previousPosition.distanceToSqr(currentPosition) < 1.0E-6) {
            previousPosition = currentPosition.subtract(projectile.getDeltaMovement());
        }
        if ((speed = (segment = (endPosition = currentPosition.add(projectile.getDeltaMovement())).subtract(previousPosition)).length()) < 0.2) {
            return false;
        }
        AABB playerBox = player.getBoundingBox().inflate(0.12);
        if (playerBox.intersects(projectile.getBoundingBox()) || playerBox.clip(previousPosition, endPosition).isPresent()) {
            return false;
        }
        Vec3 eyePosition = player.getEyePosition();
        ClosestPointResult closestPointResult = this.closestPointOnSegment(previousPosition, endPosition, eyePosition);
        if (closestPointResult.progress <= 0.0 || closestPointResult.progress >= 1.0) {
            return false;
        }
        double distance = closestPointResult.point.distanceTo(eyePosition);
        if (distance > 1.85) {
            return false;
        }
        if (!this.hasLineOfSight(player, closestPointResult.point, eyePosition)) {
            return false;
        }
        float severity = this.calculateSeverity(speed, distance);
        if (severity <= 0.0f) {
            return false;
        }
        Vec3 localOffset = closestPointResult.point.subtract(eyePosition);
        float lateralSign = this.resolveLateralSign(player, localOffset);
        float verticalSign = Math.signum((float)localOffset.y);
        this.feedbackController.onNearMiss(player, severity, lateralSign, verticalSign);
        return true;
    }

    private float calculateSeverity(double speed, double distance) {
        float speedFactor = Mth.clamp((float)((float)((speed - 0.2) / Math.max(0.15, 0.11000000000000001))), (float)0.0f, (float)1.0f);
        float distanceFactor = Mth.clamp((float)(1.35f - (float)(distance / 1.85)), (float)0.0f, (float)1.0f);
        return Mth.clamp((float)(distanceFactor * (0.75f + 0.55f * speedFactor)), (float)0.0f, (float)1.0f);
    }

    private boolean hasLineOfSight(Player player, Vec3 from, Vec3 to) {
        BlockHitResult hitResult = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity)player));
        return hitResult.getType() == HitResult.Type.MISS;
    }

    private ClosestPointResult closestPointOnSegment(Vec3 start, Vec3 end, Vec3 target) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-7) {
            return new ClosestPointResult(start, 0.0);
        }
        double progress = Mth.clamp((double)(target.subtract(start).dot(segment) / lengthSqr), (double)0.0, (double)1.0);
        return new ClosestPointResult(start.add(segment.scale(progress)), progress);
    }

    private boolean isValidProjectile(Projectile projectile) {
        return projectile.isAlive() && !projectile.isRemoved();
    }

    private float resolveLateralSign(Player player, Vec3 offset) {
        Vec3 look = player.getViewVector(1.0f);
        Vec3 horizontalLook = new Vec3(look.x, 0.0, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-4) {
            return 1.0f;
        }
        horizontalLook = horizontalLook.normalize();
        Vec3 right = new Vec3(horizontalLook.z, 0.0, -horizontalLook.x);
        float sign = Math.signum((float)offset.dot(right));
        return sign == 0.0f ? 1.0f : sign;
    }

    private void cleanup(Set<Integer> seenIds, long gameTime) {
        Iterator<Map.Entry<Integer, TrackedProjectile>> iterator = this.trackedProjectiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TrackedProjectile> entry = iterator.next();
            TrackedProjectile trackedProjectile = entry.getValue();
            if (seenIds.contains(entry.getKey()) || gameTime - trackedProjectile.lastSeenGameTime <= 5L) continue;
            iterator.remove();
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


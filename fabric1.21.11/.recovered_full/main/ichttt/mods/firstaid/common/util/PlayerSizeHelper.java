/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.Mob
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 */
package ichttt.mods.firstaid.common.util;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.AABBAlignedBoundingBox;
import ichttt.mods.firstaid.common.damagesystem.distribution.StandardDamageDistributionAlgorithm;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            case STANDING: {
                return NORMAL_BOXES;
            }
            case CROUCHING: {
                return SNEAKING_BOXES;
            }
            case SPIN_ATTACK: 
            case FALL_FLYING: {
                return Collections.emptyMap();
            }
        }
        return Collections.emptyMap();
    }

    public static EquipmentSlot getSlotTypeForProjectileHit(Entity hittingObject, Player toTest) {
        float[] inflationSteps;
        Map<EquipmentSlot, AABBAlignedBoundingBox> toUse = PlayerSizeHelper.getBoxes((Entity)toTest);
        Vec3 oldPosition = hittingObject.position();
        Vec3 newPosition = oldPosition.add(hittingObject.getDeltaMovement());
        for (float inflation : inflationSteps = new float[]{0.01f, 0.1f, 0.2f, 0.3f}) {
            EquipmentSlot bestSlot = null;
            double bestValue = Double.MAX_VALUE;
            for (Map.Entry<EquipmentSlot, AABBAlignedBoundingBox> entry : toUse.entrySet()) {
                double d2;
                double d1;
                AABB axisalignedbb = entry.getValue().createAABB(toTest.getBoundingBox()).inflate((double)inflation);
                Optional optional = axisalignedbb.clip(oldPosition, newPosition);
                if (!optional.isPresent() || !((d1 = oldPosition.distanceToSqr((Vec3)optional.get())) + (d2 = 0.0) < bestValue)) continue;
                bestSlot = entry.getKey();
                bestValue = d1 + d2;
            }
            if (bestSlot == null) continue;
            if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
                FirstAid.LOGGER.info("getSlotTypeForProjectileHit: Inflation: " + inflation + " best slot: " + String.valueOf(bestSlot));
            }
            return bestSlot;
        }
        if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
            FirstAid.LOGGER.info("getSlotTypeForProjectileHit: Not found!");
        }
        return null;
    }

    public static IDamageDistributionAlgorithm getMeleeDistribution(Player player, DamageSource source) {
        Map<EquipmentSlot, AABBAlignedBoundingBox> boxes;
        Mob mobEntity;
        Entity causingEntity = source.getEntity();
        if (causingEntity != null && causingEntity == source.getDirectEntity() && causingEntity instanceof Mob && (mobEntity = (Mob)causingEntity).getTarget() == player && !(boxes = PlayerSizeHelper.getBoxes((Entity)player)).isEmpty()) {
            ArrayList<EquipmentSlot> allowedParts = new ArrayList<EquipmentSlot>();
            AABB modAABB = mobEntity.getBoundingBox().inflate((double)(mobEntity.getBbWidth() * 2.0f + player.getBbWidth()), 0.0, (double)(mobEntity.getBbWidth() * 2.0f + player.getBbWidth()));
            for (Map.Entry<EquipmentSlot, AABBAlignedBoundingBox> entry : boxes.entrySet()) {
                AABB partAABB = entry.getValue().createAABB(player.getBoundingBox());
                if (!modAABB.intersects(partAABB)) continue;
                allowedParts.add(entry.getKey());
            }
            if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
                FirstAid.LOGGER.info("getMeleeDistribution: Has distribution with {}", allowedParts);
            }
            if (allowedParts.isEmpty() && player.getY() > mobEntity.getY() && player.getY() - mobEntity.getY() < (double)(mobEntity.getBbHeight() * 2.0f)) {
                if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
                    FirstAid.LOGGER.info("Hack adding feet");
                }
                allowedParts.add(EquipmentSlot.FEET);
            }
            if (!allowedParts.isEmpty() && !allowedParts.containsAll(Arrays.asList(CommonUtils.ARMOR_SLOTS))) {
                LinkedHashMap<EquipmentSlot, List<EnumPlayerPart>> list = new LinkedHashMap<EquipmentSlot, List<EnumPlayerPart>>();
                for (EquipmentSlot allowedPart : allowedParts) {
                    list.put(allowedPart, CommonUtils.getPartListForSlot(allowedPart));
                }
                return new StandardDamageDistributionAlgorithm(list, true, true);
            }
        }
        return null;
    }

    static {
        LinkedHashMap<EquipmentSlot, AABBAlignedBoundingBox> builder = new LinkedHashMap<EquipmentSlot, AABBAlignedBoundingBox>();
        builder.put(EquipmentSlot.FEET, new AABBAlignedBoundingBox(0.0, 0.0, 0.0, 1.0, 0.15, 1.0));
        builder.put(EquipmentSlot.LEGS, new AABBAlignedBoundingBox(0.0, 0.15, 0.0, 1.0, 0.45, 1.0));
        builder.put(EquipmentSlot.CHEST, new AABBAlignedBoundingBox(0.0, 0.45, 0.0, 1.0, 0.8, 1.0));
        builder.put(EquipmentSlot.HEAD, new AABBAlignedBoundingBox(0.0, 0.8, 0.0, 1.0, 1.0, 1.0));
        NORMAL_BOXES = Collections.unmodifiableMap(builder);
        builder = new LinkedHashMap();
        builder.put(EquipmentSlot.FEET, new AABBAlignedBoundingBox(0.0, 0.0, 0.0, 1.0, 0.15, 1.0));
        builder.put(EquipmentSlot.LEGS, new AABBAlignedBoundingBox(0.0, 0.15, 0.0, 1.0, 0.4, 1.0));
        builder.put(EquipmentSlot.CHEST, new AABBAlignedBoundingBox(0.0, 0.4, 0.0, 1.0, 0.75, 1.0));
        builder.put(EquipmentSlot.HEAD, new AABBAlignedBoundingBox(0.0, 0.75, 0.0, 1.0, 1.0, 1.0));
        SNEAKING_BOXES = Collections.unmodifiableMap(builder);
    }
}


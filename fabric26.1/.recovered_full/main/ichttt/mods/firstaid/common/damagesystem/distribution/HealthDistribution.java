/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.player.Player
 */
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class HealthDistribution {
    private static final List<EnumPlayerPart> parts;

    public static void manageHealth(float health, AbstractPlayerDamageModel damageModel, Player player, boolean sendChanges, boolean distribute) {
        if (sendChanges && player.level().isClientSide()) {
            FirstAid.LOGGER.warn("The sendChanges flag was set on the client, it can however only work on the server!", (Throwable)new RuntimeException("sendChanges flag set on the client, this is not supported!"));
            sendChanges = false;
        } else if (sendChanges && !(player instanceof ServerPlayer)) {
            sendChanges = false;
        }
        float toHeal = distribute ? health / 8.0f : health;
        Collections.shuffle(parts);
        ArrayList<AbstractDamageablePart> damageableParts = new ArrayList<AbstractDamageablePart>(parts.size());
        for (EnumPlayerPart part : parts) {
            damageableParts.add(damageModel.getFromEnum(part));
        }
        if (distribute) {
            damageableParts.sort(Comparator.comparingDouble(value -> (float)value.getMaxHealth() - value.currentHealth));
        }
        float[] healingDone = new float[8];
        for (int i = 0; i < 8; ++i) {
            AbstractDamageablePart part = (AbstractDamageablePart)damageableParts.get(i);
            float diff = toHeal - part.heal(toHeal, player, !player.level().isClientSide());
            healingDone[part.part.ordinal()] = diff = (float)Math.round(diff * 10000.0f) / 10000.0f;
            health -= diff;
            if (distribute) {
                if (i >= 7) continue;
                toHeal = health / (7.0f - (float)i);
                continue;
            }
            if ((toHeal -= diff) <= 0.0f) break;
        }
        if (sendChanges) {
            ServerPlayer playerMP = (ServerPlayer)player;
            FirstAidNetworking.sendDamageModelSync(playerMP, damageModel, FirstAidConfig.SERVER.scaleMaxHealth.get());
        }
    }

    public static void distributeHealth(float health, Player player, boolean sendChanges) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) {
            return;
        }
        HealthDistribution.manageHealth(health, damageModel, player, sendChanges, true);
    }

    public static void addRandomHealth(float health, Player player, boolean sendChanges) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) {
            return;
        }
        HealthDistribution.manageHealth(health, damageModel, player, sendChanges, false);
    }

    static {
        EnumPlayerPart[] partArray = EnumPlayerPart.VALUES;
        parts = new ArrayList<EnumPlayerPart>(partArray.length);
        parts.addAll(Arrays.asList(partArray));
    }
}


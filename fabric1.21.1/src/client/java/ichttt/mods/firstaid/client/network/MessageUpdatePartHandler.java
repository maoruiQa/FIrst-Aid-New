package ichttt.mods.firstaid.client.network;

import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.network.MessageUpdatePart;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class MessageUpdatePartHandler {
    private MessageUpdatePartHandler() {
    }

    public static void handle(MessageUpdatePart message, Context context) {
        context.client().execute(() -> {
            Minecraft minecraft = context.client();
            if (minecraft.level == null || minecraft.player == null) {
                return;
            }

            Player targetPlayer = resolveTargetPlayer(minecraft, message.entityId());
            if (targetPlayer == null) {
                return;
            }

            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(targetPlayer);
            if (damageModel == null) {
                return;
            }

            AbstractDamageablePart damageablePart = damageModel.getFromEnum(EnumPlayerPart.VALUES[message.partId()]);
            damageablePart.setMaxHealth(message.maxHealth());
            damageablePart.setAbsorption(message.absorption());
            damageablePart.currentHealth = message.currentHealth();
        });
    }

    private static Player resolveTargetPlayer(Minecraft minecraft, int entityId) {
        if (minecraft.player != null && minecraft.player.getId() == entityId) {
            return minecraft.player;
        }

        return minecraft.level.getEntity(entityId) instanceof Player targetPlayer ? targetPlayer : null;
    }
}

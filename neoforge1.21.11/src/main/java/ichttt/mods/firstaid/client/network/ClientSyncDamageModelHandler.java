package ichttt.mods.firstaid.client.network;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.HUDHandler;
import ichttt.mods.firstaid.common.CapProvider;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.network.MessageSyncDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class ClientSyncDamageModelHandler {

    private ClientSyncDamageModelHandler() {
    }

    public static void handle(MessageSyncDamageModel message) {
        Minecraft minecraft = Minecraft.getInstance();
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

        if (message.shouldScaleMaxHealth()) {
            damageModel.runScaleLogic(targetPlayer);
        }

        boolean wasUnconscious = isUnconscious(damageModel);
        damageModel.deserializeNBT(message.playerDamageModel());
        if (wasUnconscious != isUnconscious(damageModel)) {
            targetPlayer.refreshDimensions();
        }
        if (targetPlayer == minecraft.player) {
            if (damageModel.hasTutorial) {
                CapProvider.tutorialDone.add(minecraft.player.getName().getString());
            }
            HUDHandler.INSTANCE.ticker = 200;
            FirstAid.isSynced = true;
        }
    }

    private static Player resolveTargetPlayer(Minecraft minecraft, int entityId) {
        if (minecraft.player != null && minecraft.player.getId() == entityId) {
            return minecraft.player;
        }

        return minecraft.level.getEntity(entityId) instanceof Player targetPlayer ? targetPlayer : null;
    }

    private static boolean isUnconscious(AbstractPlayerDamageModel damageModel) {
        return damageModel instanceof PlayerDamageModel playerDamageModel
                ? playerDamageModel.isUnconscious()
                : damageModel.getUnconsciousTicks() > 0;
    }
}

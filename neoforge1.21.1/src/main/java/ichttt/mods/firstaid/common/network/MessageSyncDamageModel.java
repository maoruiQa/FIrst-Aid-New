package ichttt.mods.firstaid.common.network;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.HUDHandler;
import ichttt.mods.firstaid.common.CapProvider;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class MessageSyncDamageModel implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MessageSyncDamageModel> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "sync_damage_model"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageSyncDamageModel> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            MessageSyncDamageModel::entityId,
            ByteBufCodecs.COMPOUND_TAG,
            MessageSyncDamageModel::playerDamageModel,
            ByteBufCodecs.BOOL,
            MessageSyncDamageModel::shouldScaleMaxHealth,
            MessageSyncDamageModel::new);

    private final int entityId;
    private final CompoundTag playerDamageModel;
    private final boolean scaleMaxHealth;

    private MessageSyncDamageModel(int entityId, CompoundTag playerDamageModel, boolean scaleMaxHealth) {
        this.entityId = entityId;
        this.playerDamageModel = playerDamageModel;
        this.scaleMaxHealth = scaleMaxHealth;
    }

    public MessageSyncDamageModel(int entityId, AbstractPlayerDamageModel damageModel, boolean scaleMaxHealth) {
        this(entityId, damageModel.serializeNBT(), scaleMaxHealth);
    }

    public int entityId() {
        return entityId;
    }

    public CompoundTag playerDamageModel() {
        return playerDamageModel;
    }

    public boolean shouldScaleMaxHealth() {
        return scaleMaxHealth;
    }

    @Override
    public CustomPacketPayload.Type<MessageSyncDamageModel> type() {
        return TYPE;
    }

    public static final class Handler {

        public static void onMessage(MessageSyncDamageModel message, IPayloadContext context) {
            context.enqueueWork(() -> {
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

                damageModel.deserializeNBT(message.playerDamageModel());
                if (targetPlayer == minecraft.player) {
                    if (damageModel.hasTutorial) {
                        CapProvider.tutorialDone.add(minecraft.player.getName().getString());
                    }
                    HUDHandler.INSTANCE.ticker = 200;
                    FirstAid.isSynced = true;
                }
            });
        }

        private static Player resolveTargetPlayer(Minecraft minecraft, int entityId) {
            if (minecraft.player != null && minecraft.player.getId() == entityId) {
                return minecraft.player;
            }

            return minecraft.level.getEntity(entityId) instanceof Player targetPlayer ? targetPlayer : null;
        }
    }
}

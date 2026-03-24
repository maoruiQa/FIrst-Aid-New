package ichttt.mods.firstaid.common.init;

import com.mojang.serialization.MapCodec;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.common.extensions.ValueOutputExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class FirstAidDataAttachments {
    private static boolean loggedClientDamageModelSync;

    public static final DeferredRegister<AttachmentType<?>> REGISTRY = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, FirstAid.MODID);

    public static final Supplier<AttachmentType<PlayerDamageModel>> DAMAGE_MODEL = REGISTRY.register("damage_model", () -> AttachmentType.builder(holder -> new PlayerDamageModel())
            .serialize(new DamageModelAttachmentSerializer())
            .sync(new DamageModelSyncHandler())
            .build());

    private FirstAidDataAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }

    private static final class DamageModelSyncHandler implements AttachmentSyncHandler<PlayerDamageModel> {

        @Override
        public boolean sendToPlayer(net.neoforged.neoforge.attachment.IAttachmentHolder holder, ServerPlayer to) {
            return holder == to;
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf, PlayerDamageModel attachment, boolean initialSync) {
            buf.writeNbt(attachment.serializeNBT());
        }

        @Override
        public @Nullable PlayerDamageModel read(net.neoforged.neoforge.attachment.IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable PlayerDamageModel previousValue) {
            PlayerDamageModel model = previousValue != null ? previousValue : new PlayerDamageModel();
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                model.deserializeNBT(tag);
            }
            FirstAid.isSynced = true;
            if (!loggedClientDamageModelSync) {
                FirstAid.LOGGER.info(
                        "Received client damage model attachment sync, holderType={}, reusedModel={}, hasTag={}",
                        holder.getClass().getName(),
                        previousValue != null,
                        tag != null
                );
                loggedClientDamageModelSync = true;
            }
            return model;
        }
    }

    private static final class DamageModelAttachmentSerializer implements IAttachmentSerializer<PlayerDamageModel> {

        @Override
        public PlayerDamageModel read(net.neoforged.neoforge.attachment.IAttachmentHolder holder, ValueInput input) {
            PlayerDamageModel model = new PlayerDamageModel();
            CompoundTag tag = input.read(MapCodec.assumeMapUnsafe(CompoundTag.CODEC)).orElseGet(CompoundTag::new);
            model.deserializeNBT(tag);
            return model;
        }

        @Override
        public boolean write(PlayerDamageModel attachment, ValueOutput output) {
            ((ValueOutputExtension) output).store(attachment.serializeNBT());
            return true;
        }
    }
}

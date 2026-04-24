package net.minecraftforge.network;

import net.minecraft.server.level.ServerPlayer;

public final class NetworkEvent {

    private NetworkEvent() {
    }

    public static class Context {
        private final NetworkDirection direction;
        private final ServerPlayer sender;

        public Context(NetworkDirection direction, ServerPlayer sender) {
            this.direction = direction;
            this.sender = sender;
        }

        public NetworkDirection getDirection() {
            return direction;
        }

        public ServerPlayer getSender() {
            return sender;
        }

        public void setPacketHandled(boolean handled) {
        }

        public void enqueueWork(Runnable runnable) {
            runnable.run();
        }
    }
}

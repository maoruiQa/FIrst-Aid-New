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

package ichttt.mods.firstaid.common.network;

import ichttt.mods.firstaid.FirstAid;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Tells the client to show the full-screen knockdown settings notice.
 */
public class MessageKnockdownNotice implements CustomPacketPayload {
    private static final String CLIENT_HANDLER_CLASS = "ichttt.mods.firstaid.client.KnockdownNoticeClient";
    public static final CustomPacketPayload.Type<MessageKnockdownNotice> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FirstAid.MODID, "knockdown_notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageKnockdownNotice> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            message -> message.mode.ordinal(),
            ByteBufCodecs.DOUBLE,
            MessageKnockdownNotice::coefficient,
            (ordinal, coefficient) -> new MessageKnockdownNotice(modeFromOrdinal(ordinal), coefficient));

    private final FirstAid.KnockdownMode mode;
    private final double coefficient;

    public MessageKnockdownNotice(FirstAid.KnockdownMode mode, double coefficient) {
        this.mode = mode;
        this.coefficient = coefficient;
    }

    public static MessageKnockdownNotice current() {
        return new MessageKnockdownNotice(FirstAid.knockdownMode, FirstAid.knockdownOverflowCoefficient);
    }

    private static FirstAid.KnockdownMode modeFromOrdinal(int ordinal) {
        FirstAid.KnockdownMode[] modes = FirstAid.KnockdownMode.values();
        if (ordinal < 0 || ordinal >= modes.length) {
            throw new IllegalArgumentException("Unknown knockdown mode ordinal " + ordinal);
        }
        return modes[ordinal];
    }

    public FirstAid.KnockdownMode mode() {
        return mode;
    }

    public double coefficient() {
        return coefficient;
    }

    @Override
    public CustomPacketPayload.Type<MessageKnockdownNotice> type() {
        return TYPE;
    }

    public static void handle(MessageKnockdownNotice message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!FMLEnvironment.getDist().isClient()) {
                return;
            }
            try {
                Class<?> handlerClass = Class.forName(CLIENT_HANDLER_CLASS);
                handlerClass.getMethod("show", FirstAid.KnockdownMode.class, double.class).invoke(null, message.mode, message.coefficient);
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException("Failed to dispatch MessageKnockdownNotice to the client handler", exception);
            }
        });
    }
}

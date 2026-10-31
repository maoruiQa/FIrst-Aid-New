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
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells the client to show the full-screen knockdown settings notice.
 */
public class MessageKnockdownNotice {
    private final FirstAid.KnockdownMode mode;
    private final double coefficient;

    public MessageKnockdownNotice(FriendlyByteBuf buffer) {
        this(buffer.readEnum(FirstAid.KnockdownMode.class), buffer.readDouble());
    }

    public MessageKnockdownNotice(FirstAid.KnockdownMode mode, double coefficient) {
        this.mode = mode;
        this.coefficient = coefficient;
    }

    public static MessageKnockdownNotice current() {
        return new MessageKnockdownNotice(FirstAid.knockdownMode, FirstAid.knockdownOverflowCoefficient);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(mode);
        buffer.writeDouble(coefficient);
    }

    public static final class Handler {

        public static void onMessage(MessageKnockdownNotice message, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context ctx = supplier.get();
            CommonUtils.checkClient(ctx);
            ctx.enqueueWork(() -> ichttt.mods.firstaid.client.KnockdownNoticeClient.show(message.mode, message.coefficient));
        }
    }
}

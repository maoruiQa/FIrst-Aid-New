/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.InteractionHand
 */
package ichttt.mods.firstaid.common;

import java.util.Objects;
import net.minecraft.world.InteractionHand;

public final class ClientAccess {
    private static ClientActions actions = new NoopClientActions();

    private ClientAccess() {
    }

    public static void install(ClientActions clientActions) {
        actions = Objects.requireNonNull(clientActions, "clientActions");
    }

    public static void showApplyHealth(InteractionHand hand) {
        actions.showApplyHealth(hand);
    }

    public static int getTextWidth(String text) {
        return actions.getTextWidth(text);
    }

    public static interface ClientActions {
        public void showApplyHealth(InteractionHand var1);

        public int getTextWidth(String var1);
    }

    private static final class NoopClientActions
    implements ClientActions {
        private NoopClientActions() {
        }

        @Override
        public void showApplyHealth(InteractionHand hand) {
        }

        @Override
        public int getTextWidth(String text) {
            return text == null ? 0 : text.length() * 6;
        }
    }
}


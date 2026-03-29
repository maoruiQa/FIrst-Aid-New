package ichttt.mods.firstaid.common;

import java.util.Objects;
import net.minecraft.world.InteractionHand;

public final class ClientAccess {
   private static ClientAccess.ClientActions actions = new ClientAccess.NoopClientActions();

   private ClientAccess() {
   }

   public static void install(ClientAccess.ClientActions clientActions) {
      actions = Objects.requireNonNull(clientActions, "clientActions");
   }

   public static void showApplyHealth(InteractionHand hand) {
      actions.showApplyHealth(hand);
   }

   public static int getTextWidth(String text) {
      return actions.getTextWidth(text);
   }

   public interface ClientActions {
      void showApplyHealth(InteractionHand var1);

      int getTextWidth(String var1);
   }

   private static final class NoopClientActions implements ClientAccess.ClientActions {
      @Override
      public void showApplyHealth(InteractionHand hand) {
      }

      @Override
      public int getTextWidth(String text) {
         return text == null ? 0 : text.length() * 6;
      }
   }
}

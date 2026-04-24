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

   public static boolean showApplyHealth(InteractionHand hand) {
      return actions.showApplyHealth(hand);
   }

   public static boolean beginApplyHealthUse(InteractionHand hand) {
      return actions.beginApplyHealthUse(hand);
   }

   public static int getTextWidth(String text) {
      return actions.getTextWidth(text);
   }

   public interface ClientActions {
      boolean showApplyHealth(InteractionHand var1);

      boolean beginApplyHealthUse(InteractionHand var1);

      int getTextWidth(String var1);
   }

   private static final class NoopClientActions implements ClientAccess.ClientActions {
      @Override
      public boolean showApplyHealth(InteractionHand hand) {
         return false;
      }

      @Override
      public boolean beginApplyHealthUse(InteractionHand hand) {
         return false;
      }

      @Override
      public int getTextWidth(String text) {
         return text == null ? 0 : text.length() * 6;
      }
   }
}

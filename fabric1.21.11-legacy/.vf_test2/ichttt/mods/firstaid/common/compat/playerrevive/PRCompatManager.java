package ichttt.mods.firstaid.common.compat.playerrevive;

public class PRCompatManager {
   private static IPRCompatHandler handler = new NoopPRCompatHandler();

   public static IPRCompatHandler getHandler() {
      return handler;
   }

   public static void init() {
      handler = new NoopPRCompatHandler();
   }
}

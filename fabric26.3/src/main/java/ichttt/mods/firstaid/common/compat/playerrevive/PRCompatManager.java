package ichttt.mods.firstaid.common.compat.playerrevive;

import ichttt.mods.firstaid.FirstAid;
import net.fabricmc.loader.api.FabricLoader;

public class PRCompatManager {
   private static IPRCompatHandler handler = new NoopPRCompatHandler();

   public static IPRCompatHandler getHandler() {
      return handler;
   }

   public static void init() {
      handler = new NoopPRCompatHandler();
      if (!FabricLoader.getInstance().isModLoaded("playerrevive")) {
         return;
      }

      if (PRPresentCompatHandler.canUse()) {
         handler = new PRPresentCompatHandler();
         FirstAid.LOGGER.info("Enabled PlayerRevive compatibility");
      } else {
         FirstAid.LOGGER.warn("PlayerRevive detected, but FirstAid could not resolve its runtime API. Falling back to vanilla death handling.");
      }
   }
}

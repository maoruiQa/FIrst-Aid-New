package ichttt.mods.firstaid;

import ichttt.mods.firstaid.client.ClientHooks;
import ichttt.mods.firstaid.client.network.FirstAidClientNetworking;
import ichttt.mods.firstaid.common.ClientAccess;
import ichttt.mods.firstaid.common.ClientAccess.ClientActions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

public final class FirstAidClient {
   private FirstAidClient() {
   }

   public static void initClient() {
      FirstAidConfig.loadClient();
      ClientHooks.setup();
      FirstAidClientNetworking.registerClient();
      ClientAccess.install(new ClientActions() {
         public void showApplyHealth(InteractionHand hand) {
            ClientHooks.showGuiApplyHealth(hand);
         }

         public int getTextWidth(String text) {
            return Minecraft.getInstance().font.width(text);
         }
      });
   }
}

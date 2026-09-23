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
         public boolean showApplyHealth(InteractionHand hand) {
            return ClientHooks.showGuiApplyHealth(hand);
         }

         public boolean beginApplyHealthUse(InteractionHand hand) {
            return ClientHooks.beginApplyHealthUse(hand);
         }

         public int getTextWidth(String text) {
            return Minecraft.getInstance().font.width(text);
         }
      });
   }
}

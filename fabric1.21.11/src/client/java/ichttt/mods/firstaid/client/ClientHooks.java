package ichttt.mods.firstaid.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.gui.GuiHealthScreen;
import ichttt.mods.firstaid.client.network.FirstAidClientNetworking;
import ichttt.mods.firstaid.client.util.EventCalendar;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.network.MessageClientRequest.RequestType;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionHand;

public final class ClientHooks {
   private static final Category CATEGORY = Category.register(Identifier.fromNamespaceAndPath("firstaid", "firstaid"));
   public static final KeyMapping SHOW_WOUNDS = new KeyMapping("keybinds.show_wounds", Type.KEYSYM, 72, CATEGORY);
   public static final KeyMapping GIVE_UP = new KeyMapping("keybinds.give_up", Type.KEYSYM, 71, CATEGORY);

   private ClientHooks() {
   }

   public static void setup() {
      FirstAid.LOGGER.debug("Loading ClientHooks");
      KeyBindingHelper.registerKeyBinding(SHOW_WOUNDS);
      KeyBindingHelper.registerKeyBinding(GIVE_UP);
      HudRenderCallback.EVENT.register(StatusEffectLayer.INSTANCE);
      HudRenderCallback.EVENT.register(HUDHandler.INSTANCE);
      ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(HUDHandler.INSTANCE);
      ClientEventHandler.register();
      EventCalendar.checkDate();
   }

   public static boolean showGuiApplyHealth(InteractionHand activeHand) {
      Minecraft mc = Minecraft.getInstance();
      if (ClientEventHandler.hasValidPendingHealingSelection(activeHand)) {
         return false;
      }

      if (!ClientEventHandler.canOpenHealingScreen(activeHand)) {
         return false;
      }

      AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
      if (damageModel != null) {
         FirstAidClientNetworking.sendToServer(new MessageClientRequest(RequestType.REQUEST_REFRESH));
         GuiHealthScreen.INSTANCE = new GuiHealthScreen(damageModel, activeHand);
         mc.setScreen(GuiHealthScreen.INSTANCE);
         return true;
      }

      return false;
   }

   public static boolean beginApplyHealthUse(InteractionHand hand) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || !ClientEventHandler.hasValidPendingHealingSelection(hand)) {
         return false;
      } else {
         mc.player.startUsingItem(hand);
         return true;
      }
   }
}

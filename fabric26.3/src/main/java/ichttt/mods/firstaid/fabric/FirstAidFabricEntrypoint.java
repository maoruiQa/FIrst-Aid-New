package ichttt.mods.firstaid.fabric;

import ichttt.mods.firstaid.FirstAid;
import net.fabricmc.api.ModInitializer;

public class FirstAidFabricEntrypoint implements ModInitializer {
   public void onInitialize() {
      FirstAid.initCommon();
   }
}

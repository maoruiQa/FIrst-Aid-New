package ichttt.mods.firstaid.fabric;

import ichttt.mods.firstaid.FirstAid;
import net.fabricmc.api.ModInitializer;

public class FirstAidFabricEntrypoint implements ModInitializer {
    @Override
    public void onInitialize() {
        FirstAid.init();
        FirstAid.LOGGER.info("FirstAid Fabric initialized");
    }
}

/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid;

import ichttt.mods.firstaid.client.ClientHooks;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.apiimpl.HealingItemApiHelperImpl;
import ichttt.mods.firstaid.common.compat.playerrevive.PRCompatManager;
import ichttt.mods.firstaid.common.init.FirstAidDataAttachments;
import ichttt.mods.firstaid.common.network.SimpleFirstAidChannel;
import ichttt.mods.firstaid.common.registries.FirstAidRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod(FirstAid.MODID)
public class FirstAid {
    public static final String MODID = "firstaid";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static final SimpleFirstAidChannel NETWORKING = new SimpleFirstAidChannel();
    public static boolean isSynced = false;
    public static boolean dynamicPainEnabled = true;
    public static boolean lowSuppressionEnabled = false;
    public static boolean rescueWakeUpEnabled = false;
    public static double rescueWakeUpDelaySeconds = 1.5D;
    public static MedicineEffectMode medicineEffectMode = MedicineEffectMode.REALISTIC;
    public static InjuryDebuffMode injuryDebuffMode = InjuryDebuffMode.NORMAL;
    public static final Map<ResourceLocation, InjuryDebuffMode> injuryDebuffOverrides = new ConcurrentHashMap<>();

    public enum MedicineEffectMode {
        REALISTIC(1.0F),
        ASSISTED(0.5F),
        CASUAL(0.25F);

        private final float timingMultiplier;

        MedicineEffectMode(float timingMultiplier) {
            this.timingMultiplier = timingMultiplier;
        }

        public float getTimingMultiplier() {
            return timingMultiplier;
        }
    }

    public enum InjuryDebuffMode {
        NORMAL,
        LOW,
        OFF
    }

    public static InjuryDebuffMode getInjuryDebuffMode(ResourceLocation effectId) {
        InjuryDebuffMode override = injuryDebuffOverrides.get(effectId);
        return override == null ? injuryDebuffMode : override;
    }

    public static void setInjuryDebuffOverride(ResourceLocation effectId, InjuryDebuffMode mode) {
        injuryDebuffOverrides.put(effectId, mode);
    }

    public static int scaleMedicalTimingTicks(int baseTicks) {
        return Math.max(1, Math.round(baseTicks * medicineEffectMode.getTimingMultiplier()));
    }

    public static int getRescueWakeUpDelayTicks() {
        return Math.max(0, (int) Math.round(rescueWakeUpDelaySeconds * 20.0D));
    }

    public FirstAid(IEventBus modEventBus, ModContainer container) {
        NeoForge.EVENT_BUS.register(EventHandler.class);
        modEventBus.addListener(this::init);
        modEventBus.addListener(this::registerCreativeTab);
        modEventBus.addListener(NETWORKING::register);
        RegistryObjects.registerToBus(modEventBus);
        FirstAidRegistries.setup(modEventBus);
        FirstAidDataAttachments.register(modEventBus);
        if (FMLEnvironment.dist.isClient()) {
            ClientHooks.setup(modEventBus);
        }

        container.registerConfig(ModConfig.Type.SERVER, FirstAidConfig.serverSpec);
        container.registerConfig(ModConfig.Type.COMMON, FirstAidConfig.generalSpec);
        container.registerConfig(ModConfig.Type.CLIENT, FirstAidConfig.clientSpec);

        //Setup API
        HealingItemApiHelperImpl.init();
    }

    private void registerCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(RegistryObjects.CREATIVE_TAB.getKey())) {
            event.accept(RegistryObjects.BANDAGE);
            event.accept(RegistryObjects.PLASTER);
            event.accept(RegistryObjects.DEFIBRILLATOR);
            event.accept(RegistryObjects.MORPHINE);
            event.accept(RegistryObjects.PAINKILLERS);
        }
    }

    @SuppressWarnings("Convert2MethodRef") //Fucking classloading
    public void init(FMLCommonSetupEvent event) {
        LOGGER.info("{} starting...", MODID);
        if (FirstAidConfig.GENERAL.debug.get()) {
            LOGGER.warn("DEBUG MODE ENABLED");
            LOGGER.warn("FirstAid may be slower than usual and will produce much noisier logs if debug mode is enabled");
            LOGGER.warn("Disable debug in firstaid config");
        }

        event.enqueueWork(() -> PRCompatManager.init());
    }
}


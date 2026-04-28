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

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

@Mod(FirstAid.MODID)
public class FirstAid {
    public static final String MODID = "firstaid";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static final double DEFAULT_RESCUE_WAKE_UP_DELAY_SECONDS = 20.0D;
    private static final ResourceLocation POTION_ENTITY_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "potion");
    private static final ResourceLocation SPLASH_POTION_ENTITY_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "splash_potion");
    private static final ResourceLocation LINGERING_POTION_ENTITY_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "lingering_potion");

    public static final SimpleFirstAidChannel NETWORKING = new SimpleFirstAidChannel();
    public static boolean isSynced = false;
    public static boolean dynamicPainEnabled = true;
    public static int mildPainLevel = 1;
    public static boolean lowSuppressionEnabled = false;
    public static float lowSuppressionMultiplier = 0.4F;
    public static boolean rescueWakeUpEnabled = false;
    public static double rescueWakeUpDelaySeconds = DEFAULT_RESCUE_WAKE_UP_DELAY_SECONDS;
    public static NaturalRegenMode naturalRegenMode = NaturalRegenMode.LIMITED;
    public static NaturalRegenStrategy naturalRegenStrategy = NaturalRegenStrategy.CRITICAL;
    public static float naturalRegenLimitRatio = 0.85F;
    public static float naturalRegenCriticalPriorityRatio = 0.85F;
    public static MedicineEffectMode medicineEffectMode = MedicineEffectMode.REALISTIC;
    public static float medicineTimingMultiplier = 1.0F;
    public static InjuryDebuffMode injuryDebuffMode = InjuryDebuffMode.NORMAL;
    public static float lowInjuryDebuffDamageScale = 0.4F;
    public static float lowInjuryDebuffAmplifierScale = 0.5F;
    public static float lowInjuryDebuffDurationScale = 0.5F;
    public static final Map<ResourceLocation, InjuryDebuffMode> injuryDebuffOverrides = new ConcurrentHashMap<>();
    public static final Set<ResourceLocation> suppressionEntityBlacklist = ConcurrentHashMap.newKeySet();

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

    public enum NaturalRegenMode {
        OFF,
        LIMITED,
        LIMITED2,
        FULL
    }

    public enum NaturalRegenStrategy {
        CRITICAL,
        RANDOM
    }

    public static InjuryDebuffMode getInjuryDebuffMode(ResourceLocation effectId) {
        InjuryDebuffMode override = injuryDebuffOverrides.get(effectId);
        return override == null ? injuryDebuffMode : override;
    }

    public static void setInjuryDebuffOverride(ResourceLocation effectId, InjuryDebuffMode mode) {
        injuryDebuffOverrides.put(effectId, mode);
    }

    public static boolean isSuppressionBlacklisted(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && (suppressionEntityBlacklist.contains(id) || isPotionAliasBlacklisted(id));
    }

    public static void setSuppressionEntityBlacklist(Iterable<ResourceLocation> entries) {
        suppressionEntityBlacklist.clear();
        for (ResourceLocation entry : entries) {
            suppressionEntityBlacklist.add(entry);
        }
    }

    public static Set<ResourceLocation> getDefaultSuppressionEntityBlacklist() {
        return Collections.singleton(POTION_ENTITY_ID);
    }

    private static boolean isPotionAliasBlacklisted(ResourceLocation id) {
        return (SPLASH_POTION_ENTITY_ID.equals(id) || LINGERING_POTION_ENTITY_ID.equals(id)) && suppressionEntityBlacklist.contains(POTION_ENTITY_ID);
    }

    public static int scaleMedicalTimingTicks(int baseTicks) {
        return Math.max(1, Math.round(baseTicks * medicineTimingMultiplier));
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
            event.accept(RegistryObjects.ADRENALINE_INJECTOR);
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


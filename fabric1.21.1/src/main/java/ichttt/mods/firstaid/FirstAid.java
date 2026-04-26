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

import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.apiimpl.HealingItemApiHelperImpl;
import ichttt.mods.firstaid.common.compat.playerrevive.PRCompatManager;
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import ichttt.mods.firstaid.common.registries.FirstAidDataReloadListener;
import ichttt.mods.firstaid.common.registries.FirstAidRegistries;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FirstAid {
    public static final String MODID = "firstaid";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static final double DEFAULT_RESCUE_WAKE_UP_DELAY_SECONDS = 20.0D;

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

    public static int scaleMedicalTimingTicks(int baseTicks) {
        return Math.max(1, Math.round(baseTicks * medicineTimingMultiplier));
    }

    public static int getRescueWakeUpDelayTicks() {
        return Math.max(0, (int) Math.round(rescueWakeUpDelaySeconds * 20.0D));
    }

    private FirstAid() {
    }

    public static void initCommon() {
        LOGGER.info("{} starting...", MODID);
        if (FirstAidConfig.GENERAL.debug.get()) {
            LOGGER.warn("DEBUG MODE ENABLED");
            LOGGER.warn("FirstAid may be slower than usual and will produce much noisier logs if debug mode is enabled");
            LOGGER.warn("Disable debug in firstaid config");
        }

        FirstAidConfig.loadServer();
        FirstAidConfig.loadGeneral();
        FirstAidConfig.applyCommandSettings();
        RegistryObjects.register();
        FirstAidRegistries.bootstrap();
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FirstAidDataReloadListener());
        EventHandler.registerServerEvents();
        FirstAidNetworking.registerCommon();
        HealingItemApiHelperImpl.init();
        PRCompatManager.init();
    }
}


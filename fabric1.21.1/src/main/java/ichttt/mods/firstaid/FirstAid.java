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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FirstAid {
    public static final String MODID = "firstaid";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static final double DEFAULT_RESCUE_WAKE_UP_DELAY_SECONDS = 20.0D;
    private static final ResourceLocation POTION_ENTITY_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "potion");
    private static final ResourceLocation SPLASH_POTION_ENTITY_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "splash_potion");
    private static final ResourceLocation LINGERING_POTION_ENTITY_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "lingering_potion");

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


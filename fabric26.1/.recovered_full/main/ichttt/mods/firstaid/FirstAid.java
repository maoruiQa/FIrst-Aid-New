/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
 *  net.fabricmc.fabric.api.resource.ResourceManagerHelper
 *  net.minecraft.resources.Identifier
 *  net.minecraft.server.packs.PackType
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package ichttt.mods.firstaid;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.apiimpl.HealingItemApiHelperImpl;
import ichttt.mods.firstaid.common.compat.playerrevive.PRCompatManager;
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import ichttt.mods.firstaid.common.registries.FirstAidDataReloadListener;
import ichttt.mods.firstaid.common.registries.FirstAidRegistries;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class FirstAid {
    public static final String MODID = "firstaid";
    public static final Logger LOGGER = LogManager.getLogger((String)"firstaid");
    public static boolean isSynced = false;
    public static boolean dynamicPainEnabled = true;
    public static boolean lowSuppressionEnabled = false;
    public static boolean rescueWakeUpEnabled = false;
    public static MedicineEffectMode medicineEffectMode = MedicineEffectMode.REALISTIC;
    public static InjuryDebuffMode injuryDebuffMode = InjuryDebuffMode.NORMAL;
    public static final Map<Identifier, InjuryDebuffMode> injuryDebuffOverrides = new ConcurrentHashMap<Identifier, InjuryDebuffMode>();

    public static InjuryDebuffMode getInjuryDebuffMode(Identifier effectId) {
        InjuryDebuffMode override = injuryDebuffOverrides.get(effectId);
        return override == null ? injuryDebuffMode : override;
    }

    public static void setInjuryDebuffOverride(Identifier effectId, InjuryDebuffMode mode) {
        injuryDebuffOverrides.put(effectId, mode);
    }

    public static int scaleMedicalTimingTicks(int baseTicks) {
        return Math.max(1, Math.round((float)baseTicks * medicineEffectMode.getTimingMultiplier()));
    }

    private FirstAid() {
    }

    public static void initCommon() {
        LOGGER.info("{} starting...", (Object)MODID);
        if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
            LOGGER.warn("DEBUG MODE ENABLED");
            LOGGER.warn("FirstAid may be slower than usual and will produce much noisier logs if debug mode is enabled");
            LOGGER.warn("Disable debug in firstaid config");
        }
        FirstAidConfig.loadServer();
        FirstAidConfig.loadGeneral();
        FirstAidConfig.applyCommandSettings();
        RegistryObjects.register();
        FirstAidRegistries.bootstrap();
        ResourceManagerHelper.get((PackType)PackType.SERVER_DATA).registerReloadListener((IdentifiableResourceReloadListener)new FirstAidDataReloadListener());
        EventHandler.registerServerEvents();
        FirstAidNetworking.registerCommon();
        HealingItemApiHelperImpl.init();
        PRCompatManager.init();
    }

    public static enum InjuryDebuffMode {
        NORMAL,
        LOW,
        OFF;

    }

    public static enum MedicineEffectMode {
        REALISTIC(1.0f),
        ASSISTED(0.5f),
        CASUAL(0.25f);

        private final float timingMultiplier;

        private MedicineEffectMode(float timingMultiplier) {
            this.timingMultiplier = timingMultiplier;
        }

        public float getTimingMultiplier() {
            return this.timingMultiplier;
        }
    }
}


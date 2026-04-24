package ichttt.mods.firstaid;

import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.apiimpl.HealingItemApiHelperImpl;
import ichttt.mods.firstaid.common.compat.playerrevive.PRCompatManager;
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import ichttt.mods.firstaid.common.registries.FirstAidDataReloadListener;
import ichttt.mods.firstaid.common.registries.FirstAidRegistries;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class FirstAid {
   public static final String MODID = "firstaid";
   public static final Logger LOGGER = LogManager.getLogger("firstaid");
   public static final double DEFAULT_RESCUE_WAKE_UP_DELAY_SECONDS = 20.0D;
   public static boolean isSynced = false;
   public static boolean dynamicPainEnabled = true;
   public static boolean lowSuppressionEnabled = false;
   public static boolean rescueWakeUpEnabled = false;
   public static double rescueWakeUpDelaySeconds = DEFAULT_RESCUE_WAKE_UP_DELAY_SECONDS;
   public static FirstAid.NaturalRegenMode naturalRegenMode = FirstAid.NaturalRegenMode.LIMITED;
   public static FirstAid.NaturalRegenStrategy naturalRegenStrategy = FirstAid.NaturalRegenStrategy.CRITICAL;
   public static FirstAid.MedicineEffectMode medicineEffectMode = FirstAid.MedicineEffectMode.REALISTIC;
   public static FirstAid.InjuryDebuffMode injuryDebuffMode = FirstAid.InjuryDebuffMode.NORMAL;
   public static final Map<Identifier, FirstAid.InjuryDebuffMode> injuryDebuffOverrides = new ConcurrentHashMap<>();

   public static FirstAid.InjuryDebuffMode getInjuryDebuffMode(Identifier effectId) {
      FirstAid.InjuryDebuffMode override = injuryDebuffOverrides.get(effectId);
      return override == null ? injuryDebuffMode : override;
   }

   public static void setInjuryDebuffOverride(Identifier effectId, FirstAid.InjuryDebuffMode mode) {
      injuryDebuffOverrides.put(effectId, mode);
   }

   public static int scaleMedicalTimingTicks(int baseTicks) {
      return Math.max(1, Math.round(baseTicks * medicineEffectMode.getTimingMultiplier()));
   }

   public static int getRescueWakeUpDelayTicks() {
      return Math.max(0, (int)Math.round(rescueWakeUpDelaySeconds * 20.0D));
   }

   private FirstAid() {
   }

   public static void initCommon() {
      LOGGER.info("{} starting...", "firstaid");
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

   public static enum InjuryDebuffMode {
      NORMAL,
      LOW,
      OFF;
   }

   public static enum NaturalRegenMode {
      OFF,
      LIMITED,
      FULL;
   }

   public static enum NaturalRegenStrategy {
      CRITICAL,
      RANDOM;
   }

   public static enum MedicineEffectMode {
      REALISTIC(1.0F),
      ASSISTED(0.5F),
      CASUAL(0.25F);

      private final float timingMultiplier;

      private MedicineEffectMode(float timingMultiplier) {
         this.timingMultiplier = timingMultiplier;
      }

      public float getTimingMultiplier() {
         return this.timingMultiplier;
      }
   }
}

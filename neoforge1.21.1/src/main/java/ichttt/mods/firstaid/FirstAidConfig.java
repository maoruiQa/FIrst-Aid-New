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

import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import com.electronwill.nightconfig.core.UnmodifiableConfig;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class FirstAidConfig {
    private static final int LEGACY_BANDAGE_APPLY_TIME = 2500;
    private static final int BANDAGE_APPLY_TIME = 3000;

    static final ModConfigSpec serverSpec;
    static final ModConfigSpec generalSpec;
    static final ModConfigSpec clientSpec;
    public static final FirstAidConfig.Server SERVER;
    public static final FirstAidConfig.General GENERAL;
    public static final FirstAidConfig.Client CLIENT;

    /*
     * firstaid-server.toml has two writers: First Aid's commands on the server thread and the loader's config watcher,
     * which reloads the file on its own thread after every write (Forge right away, NeoForge 0.5 s after the last one).
     * A reload that read the file before a newer setting was saved puts the older copy back, so the setting is lost in
     * memory and, with the next save, on disk. Values set through setServerValue are remembered and re-applied after a
     * reload for as long as the file still holds First Aid's own last save; edits made outside the game win as before.
     */
    private static final long COMMAND_VALUE_LIFETIME_NANOS = 5_000_000_000L;
    private static final Map<ModConfigSpec.ConfigValue<?>, Object> commandValues = new LinkedHashMap<>();
    private static final AtomicInteger serverConfigReloads = new AtomicInteger();
    private static volatile Path serverConfigPath;
    private static int handledServerConfigReloads;
    private static byte[] lastSavedServerConfig;
    private static long lastServerConfigSave;

    static {
        final Pair<FirstAidConfig.Server, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(FirstAidConfig.Server::new);
        serverSpec = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    static {
        final Pair<FirstAidConfig.General, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(FirstAidConfig.General::new);
        generalSpec = specPair.getRight();
        GENERAL = specPair.getLeft();
    }

    static {
        final Pair<FirstAidConfig.Client, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(FirstAidConfig.Client::new);
        clientSpec = specPair.getRight();
        CLIENT = specPair.getLeft();
    }

    public static void applyCommandSettings() {
        migrateLegacyBandageApplyTime();
        FirstAid.dynamicPainEnabled = SERVER.dynamicPainEnabled.get();
        FirstAid.mildPainLevel = SERVER.mildPainLevel.get();
        FirstAid.enablePainVignette = SERVER.enablePainVignette.get();
        FirstAid.enablePainBlur = SERVER.enablePainBlur.get();
        FirstAid.enablePainFovCompression = SERVER.enablePainFovCompression.get();
        FirstAid.enablePainAudioEffects = SERVER.enablePainAudioEffects.get();
        FirstAid.lowSuppressionEnabled = SERVER.lowSuppressionEnabled.get();
        FirstAid.projectileSuppressionEnabled = SERVER.projectileSuppressionEnabled.get();
        FirstAid.lowSuppressionMultiplier = SERVER.lowSuppressionMultiplier.get().floatValue();
        FirstAid.suppressionGainMultiplier = SERVER.suppressionGainMultiplier.get().floatValue();
        FirstAid.adrenalineReliefMultiplier = SERVER.adrenalineReliefMultiplier.get().floatValue();
        FirstAid.rescueWakeUpEnabled = SERVER.rescueWakeUpEnabled.get();
        FirstAid.rescueWakeUpDelaySeconds = SERVER.rescueWakeUpDelaySeconds.get();
        FirstAid.naturalRegenMode = SERVER.naturalRegenMode.get();
        FirstAid.naturalRegenStrategy = SERVER.naturalRegenStrategy.get();
        FirstAid.naturalRegenLimitRatio = SERVER.naturalRegenLimitRatio.get().floatValue();
        FirstAid.naturalRegenCriticalPriorityRatio = SERVER.naturalRegenCriticalPriorityRatio.get().floatValue();
        FirstAid.useFriendlyRandomDistribution = SERVER.useFriendlyRandomDistribution.get();
        FirstAid.friendlyRandomDistributionChance = SERVER.friendlyRandomDistributionChance.get().floatValue();
        FirstAid.medicineEffectMode = SERVER.medicineEffectMode.get();
        FirstAid.medicineTimingMultiplier = SERVER.medicineTimingMultiplier.get().floatValue();
        FirstAid.morphineActivationDelaySeconds = SERVER.morphineActivationDelaySeconds.get();
        FirstAid.painkillerActivationDelaySeconds = SERVER.painkillerActivationDelaySeconds.get();
        FirstAid.injuryDebuffMode = SERVER.injuryDebuffMode.get();
        FirstAid.knockdownMode = SERVER.knockdownMode.get();
        FirstAid.knockdownOverflowCoefficient = SERVER.knockdownOverflowCoefficient.get();
        FirstAid.lowInjuryDebuffDamageScale = SERVER.lowInjuryDebuffDamageScale.get().floatValue();
        FirstAid.lowInjuryDebuffAmplifierScale = SERVER.lowInjuryDebuffAmplifierScale.get().floatValue();
        FirstAid.lowInjuryDebuffDurationScale = SERVER.lowInjuryDebuffDurationScale.get().floatValue();
        FirstAid.injuryDebuffOverrides.clear();
        FirstAid.injuryDebuffOverrides.putAll(parseInjuryDebuffOverrides(SERVER.injuryDebuffOverrides.get()));
        FirstAid.setSuppressionEntityBlacklist(parseResourceLocationList(SERVER.suppressionEntityBlacklist.get()));
    }

    public static void saveClient() {
        clientSpec.save();
    }

    public static ModConfigSpec.IntValue getPartMaxHealth(EnumPlayerPart part) {
        return switch (part) {
            case HEAD -> SERVER.maxHealthHead;
            case BODY -> SERVER.maxHealthBody;
            case LEFT_ARM -> SERVER.maxHealthLeftArm;
            case RIGHT_ARM -> SERVER.maxHealthRightArm;
            case LEFT_LEG -> SERVER.maxHealthLeftLeg;
            case RIGHT_LEG -> SERVER.maxHealthRightLeg;
            case LEFT_FOOT -> SERVER.maxHealthLeftFoot;
            case RIGHT_FOOT -> SERVER.maxHealthRightFoot;
        };
    }

    public static void resetServerSettings() {
        resetValues(serverSpec.getValues());
        // Save before copying to the runtime state: saving re-applies defaults a concurrent reload put back.
        saveServer();
        applyCommandSettings();
    }

    private static void resetValues(UnmodifiableConfig values) {
        for (Object value : values.valueMap().values()) {
            if (value instanceof UnmodifiableConfig section) {
                resetValues(section);
            } else if (value instanceof ModConfigSpec.ConfigValue<?> configValue) {
                resetValue(configValue);
            }
        }
    }

    private static <T> void resetValue(ModConfigSpec.ConfigValue<T> value) {
        setServerValue(value, value.getDefault());
        value.clearCache();
    }

    public static void persistCommandSettings() {
        setServerValue(SERVER.dynamicPainEnabled, FirstAid.dynamicPainEnabled);
        setServerValue(SERVER.mildPainLevel, FirstAid.mildPainLevel);
        setServerValue(SERVER.enablePainVignette, FirstAid.enablePainVignette);
        setServerValue(SERVER.enablePainBlur, FirstAid.enablePainBlur);
        setServerValue(SERVER.enablePainFovCompression, FirstAid.enablePainFovCompression);
        setServerValue(SERVER.enablePainAudioEffects, FirstAid.enablePainAudioEffects);
        setServerValue(SERVER.lowSuppressionEnabled, FirstAid.lowSuppressionEnabled);
        setServerValue(SERVER.projectileSuppressionEnabled, FirstAid.projectileSuppressionEnabled);
        setServerValue(SERVER.lowSuppressionMultiplier, (double) FirstAid.lowSuppressionMultiplier);
        setServerValue(SERVER.suppressionGainMultiplier, (double) FirstAid.suppressionGainMultiplier);
        setServerValue(SERVER.adrenalineReliefMultiplier, (double) FirstAid.adrenalineReliefMultiplier);
        setServerValue(SERVER.rescueWakeUpEnabled, FirstAid.rescueWakeUpEnabled);
        setServerValue(SERVER.rescueWakeUpDelaySeconds, FirstAid.rescueWakeUpDelaySeconds);
        setServerValue(SERVER.naturalRegenMode, FirstAid.naturalRegenMode);
        setServerValue(SERVER.naturalRegenStrategy, FirstAid.naturalRegenStrategy);
        setServerValue(SERVER.naturalRegenLimitRatio, (double) FirstAid.naturalRegenLimitRatio);
        setServerValue(SERVER.naturalRegenCriticalPriorityRatio, (double) FirstAid.naturalRegenCriticalPriorityRatio);
        setServerValue(SERVER.allowNaturalRegeneration, FirstAid.naturalRegenMode != FirstAid.NaturalRegenMode.OFF);
        setServerValue(SERVER.useFriendlyRandomDistribution, FirstAid.useFriendlyRandomDistribution);
        setServerValue(SERVER.friendlyRandomDistributionChance, (double) FirstAid.clampFriendlyRandomDistributionChance(FirstAid.friendlyRandomDistributionChance));
        setServerValue(SERVER.medicineEffectMode, FirstAid.medicineEffectMode);
        setServerValue(SERVER.medicineTimingMultiplier, (double) FirstAid.medicineTimingMultiplier);
        setServerValue(SERVER.morphineActivationDelaySeconds, FirstAid.morphineActivationDelaySeconds);
        setServerValue(SERVER.painkillerActivationDelaySeconds, FirstAid.painkillerActivationDelaySeconds);
        setServerValue(SERVER.injuryDebuffMode, FirstAid.injuryDebuffMode);
        setServerValue(SERVER.knockdownMode, FirstAid.knockdownMode);
        setServerValue(SERVER.knockdownOverflowCoefficient, FirstAid.knockdownOverflowCoefficient);
        setServerValue(SERVER.lowInjuryDebuffDamageScale, (double) FirstAid.lowInjuryDebuffDamageScale);
        setServerValue(SERVER.lowInjuryDebuffAmplifierScale, (double) FirstAid.lowInjuryDebuffAmplifierScale);
        setServerValue(SERVER.lowInjuryDebuffDurationScale, (double) FirstAid.lowInjuryDebuffDurationScale);
        setServerValue(SERVER.injuryDebuffOverrides, serializeInjuryDebuffOverrides(FirstAid.injuryDebuffOverrides));
        setServerValue(SERVER.suppressionEntityBlacklist, serializeResourceLocationList(FirstAid.suppressionEntityBlacklist));
        saveServer();
    }

    /** Sets a server setting for a command; persist it with {@link #persistCommandSettings()}. Server thread only. */
    public static <T> void setServerValue(ModConfigSpec.ConfigValue<T> value, T newValue) {
        commandValues.put(value, newValue);
        if (!Objects.equals(currentValue(value), newValue)) {
            value.set(newValue);
        }
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        trackServerConfig(event.getConfig());
    }

    /** Fired by First Aid's own saves and, on the config watcher's thread, after every reload of the file. */
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (trackServerConfig(event.getConfig())) {
            serverConfigReloads.incrementAndGet();
        }
    }

    /** Runs at the end of every server tick, on the server thread. */
    public static void tickServerConfig() {
        int reloads = serverConfigReloads.get();
        boolean reloaded = reloads != handledServerConfigReloads;
        handledServerConfigReloads = reloads;
        if (commandValues.isEmpty()) {
            return;
        }
        if (reloaded) {
            restoreCommandValues();
        } else if (System.nanoTime() - lastServerConfigSave > COMMAND_VALUE_LIFETIME_NANOS) {
            // Every reload of First Aid's last save has long finished.
            forgetCommandValues();
        }
    }

    public static void forgetCommandValues() {
        commandValues.clear();
        lastSavedServerConfig = null;
    }

    private static boolean trackServerConfig(ModConfig config) {
        Object spec = config.getSpec();
        if (spec != serverSpec) {
            return false;
        }
        IConfigSpec.ILoadedConfig loaded = config.getLoadedConfig();
        // A config synced from a remote server only lives in memory.
        serverConfigPath = loaded == null || loaded.config().configFormat().isInMemory() ? null : config.getFullPath();
        return true;
    }

    private static void restoreCommandValues() {
        byte[] saved = readServerConfig();
        if (saved == null || !Arrays.equals(saved, lastSavedServerConfig)) {
            // The file was changed outside First Aid; keep what the loader reloaded from it.
            forgetCommandValues();
            return;
        }
        if (!commandValuesApplied()) {
            FirstAid.LOGGER.debug("A reload of an older {} reverted First Aid settings; restoring them", serverConfigPath.getFileName());
            saveServer();
        }
    }

    private static void saveServer() {
        // A reload may put an older copy back while this saves; save again until memory holds every value.
        for (int attempt = 0; attempt < 3; attempt++) {
            applyCommandValues();
            serverSpec.save();
            if (commandValuesApplied()) {
                break;
            }
        }
        lastSavedServerConfig = readServerConfig();
        lastServerConfigSave = System.nanoTime();
    }

    private static void applyCommandValues() {
        for (Map.Entry<ModConfigSpec.ConfigValue<?>, Object> entry : commandValues.entrySet()) {
            if (!Objects.equals(currentValue(entry.getKey()), entry.getValue())) {
                setUnchecked(entry.getKey(), entry.getValue());
            }
        }
    }

    private static boolean commandValuesApplied() {
        for (Map.Entry<ModConfigSpec.ConfigValue<?>, Object> entry : commandValues.entrySet()) {
            if (!Objects.equals(currentValue(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /** The value in the loaded config; the cache may still hold a value from before the last reload. */
    private static Object currentValue(ModConfigSpec.ConfigValue<?> value) {
        return value.getRaw();
    }

    @SuppressWarnings("unchecked")
    private static <T> void setUnchecked(ModConfigSpec.ConfigValue<T> value, Object newValue) {
        value.set((T) newValue);
    }

    private static byte[] readServerConfig() {
        Path path = serverConfigPath;
        if (path == null) {
            return null;
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException error) {
            FirstAid.LOGGER.warn("Could not read {} to verify First Aid's saved settings: {}", path, error.toString());
            return null;
        }
    }

    private static void migrateLegacyBandageApplyTime() {
        if (SERVER.bandage.applyTime.get() == LEGACY_BANDAGE_APPLY_TIME) {
            setServerValue(SERVER.bandage.applyTime, BANDAGE_APPLY_TIME);
            saveServer();
        }
    }

    private static Map<ResourceLocation, FirstAid.InjuryDebuffMode> parseInjuryDebuffOverrides(List<? extends String> entries) {
        Map<ResourceLocation, FirstAid.InjuryDebuffMode> values = new LinkedHashMap<>();
        if (entries == null) {
            return values;
        }
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("=", 2);
            ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
            if (id == null) {
                FirstAid.LOGGER.warn("Invalid injury debuff override entry {}", trimmed);
                continue;
            }
            FirstAid.InjuryDebuffMode mode = parts.length > 1
                    ? parseInjuryDebuffMode(parts[1].trim())
                    : FirstAid.InjuryDebuffMode.NORMAL;
            values.put(id, mode);
        }
        return values;
    }

    private static List<String> serializeInjuryDebuffOverrides(Map<ResourceLocation, FirstAid.InjuryDebuffMode> overrides) {
        List<String> values = new ArrayList<>();
        if (overrides == null || overrides.isEmpty()) {
            return values;
        }
        for (Map.Entry<ResourceLocation, FirstAid.InjuryDebuffMode> entry : overrides.entrySet()) {
            values.add(entry.getKey() + "=" + entry.getValue().name().toLowerCase(Locale.ROOT));
        }
        return values;
    }

    private static List<ResourceLocation> parseResourceLocationList(List<? extends String> entries) {
        List<ResourceLocation> values = new ArrayList<>();
        if (entries == null) {
            return values;
        }
        for (String entry : entries) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(entry.trim());
            if (id == null) {
                FirstAid.LOGGER.warn("Invalid suppression entity blacklist entry {}", entry);
                continue;
            }
            values.add(id);
        }
        return values;
    }

    private static List<String> serializeResourceLocationList(Iterable<ResourceLocation> entries) {
        List<String> values = new ArrayList<>();
        for (ResourceLocation entry : entries) {
            values.add(entry.toString());
        }
        return values;
    }

    private static FirstAid.InjuryDebuffMode parseInjuryDebuffMode(String raw) {
        if (raw == null) {
            return FirstAid.InjuryDebuffMode.NORMAL;
        }
        for (FirstAid.InjuryDebuffMode mode : FirstAid.InjuryDebuffMode.values()) {
            if (mode.name().equalsIgnoreCase(raw)) {
                return mode;
            }
        }
        return FirstAid.InjuryDebuffMode.NORMAL;
    }

    public static class Server {

        public enum VanillaHealthCalculationMode {
            AVERAGE_ALL, AVERAGE_CRITICAL, MIN_CRITICAL, CRITICAL_50_PERCENT_OTHER_50_PERCENT
        }

        public enum ArmorEnchantmentMode {
            GLOBAL_ENCHANTMENTS, LOCAL_ENCHANTMENTS
        }

        Server(ModConfigSpec.Builder builder) {
            builder.comment("Server to Client synced configuration settings").push("Damage System");

            maxHealthHead = healthEntry(builder, "Head", 4);
            maxHealthLeftArm = healthEntry(builder, "Left Arm", 4);
            maxHealthLeftLeg = healthEntry(builder, "Left Leg", 4);
            maxHealthLeftFoot = healthEntry(builder, "Left Foot", 4);
            maxHealthBody = healthEntry(builder, "Body", 6);
            maxHealthRightArm = healthEntry(builder, "Right Arm", 4);
            maxHealthRightLeg = healthEntry(builder, "Right Leg", 4);
            maxHealthRightFoot = healthEntry(builder, "Right Foot", 4);
            causeDeathHead = builder
                    .comment("True if the head can cause death if health drops to 0")
                    .translation("firstaid.config.causedeath.head")
                    .define("causeDeathHead", true);
            causeDeathBody = builder
                    .comment("True if the body can cause death if health drops to 0")
                    .translation("firstaid.config.causedeath.body")
                    .define("causeDeathBody", true);

            builder.pop().push("Locational Armor").push("Armor");

            headArmorMultiplier = multiplierEntry(builder, "Head", 6D);
            chestArmorMultiplier = multiplierEntry(builder, "Chest", 2.5D);
            legsArmorMultiplier = multiplierEntry(builder, "Legs", 3D);
            feetArmorMultiplier = multiplierEntry(builder, "Feet", 6D);

            headArmorOffset = offsetEntry(builder, "Head", 1D);
            chestArmorOffset = offsetEntry(builder, "Chest", 0D);
            legsArmorOffset = offsetEntry(builder, "Legs", 0D);
            feetArmorOffset = offsetEntry(builder, "Feet", 0D);

            builder.pop().push("Toughness");

            headThoughnessMultiplier = multiplierEntry(builder, "Head", 4D);
            chestThoughnessMultiplier = multiplierEntry(builder, "Chest", 3D);
            legsThoughnessMultiplier = multiplierEntry(builder, "Legs", 3D);
            feetThoughnessMultiplier = multiplierEntry(builder, "Feet", 3.5D);

            headThoughnessOffset = offsetEntry(builder, "Head", 0D);
            chestThoughnessOffset = offsetEntry(builder, "Chest", 0D);
            legsThoughnessOffset = offsetEntry(builder, "Legs", 0D);
            feetThoughnessOffset = offsetEntry(builder, "Feet", 0D);

            builder.pop(2).push("Internal Healing");

            bandage = new IEEntry(builder, "bandage", 4, 18, 3000);
            plaster = new IEEntry(builder, "plaster", 2, 22, 3000);

            builder.pop().push("External Healing");

            allowNaturalRegeneration = builder
                    .comment("Allow vanilla's natural regeneration. Requires \"allowOtherHealingItems\" to be true", "**WARNING** This sets the gamerule \"naturalRegeneration\" for all of your worlds internally, so it persists even if you remove the mod")
                    .translation("firstaid.config.allownaturalregeneration")
                    .worldRestart()
                    .define("allowNaturalRegeneration", false);

            allowOtherHealingItems = builder
                    .comment("If false, healing potions and other healing items will have no effect")
                    .translation("firstaid.config.allowotherhealingitems")
                    .define("allowOtherHealingItems", true);

            sleepHealPercentage = builder
                    .comment("Specifies how much percent of the max health should be restored when sleeping")
                    .translation("firstaid.config.sleephealpercentage")
                    .defineInRange("sleepHealPercentage", 0.07D, 0D, 1D);
            otherRegenMultiplier = builder
                    .comment("The value external regen will be multiplied with. Has no effect if \"allowOtherHealingItems\" is disabled")
                    .translation("firstaid.config.otherregenmultiplier")
                    .defineInRange("otherRegenMultiplier", 0.75D, 0D, 20D);
            naturalRegenMultiplier = builder
                    .comment("The value vanilla's natural regeneration will be multiplied with. Has no effect if \"allowNaturalRegeneration\" is disabled")
                    .translation("firstaid.config.naturalregenmultiplier")
                    .defineInRange("naturalRegenMultiplier", 0.5D, 0D, 20D);
            naturalRegenMode = builder
                    .comment("Persistent mode for /firstaid naturalregen")
                    .defineEnum("naturalRegenMode", FirstAid.NaturalRegenMode.LIMITED);
            naturalRegenStrategy = builder
                    .comment("Persistent strategy for /firstaid naturalregen")
                    .defineEnum("naturalRegenStrategy", FirstAid.NaturalRegenStrategy.CRITICAL);
            resistanceReductionPercentPerLevel = builder
                    .comment("Specifies how the vanilla resistance potion effect should reduce damage.", "By default, one level of resistance reduces 20% of damage. Changing this value to e.g. 10 will reduce the reduction to 10% damage reduction per level")
                    .translation("firstaid.config.resistancereductionpercentperlevel")
                    .defineInRange("resistanceReductionPercentPerLevel", 20, 1, 40);

            builder.pop();
            builder.push("misc");
            scaleMaxHealth = builder
                    .comment("If true, max health is scaled to your hearts, and the config entries get multiplier to match the max health")
                    .translation("firstaid.config.scalemaxhealth")
                    .worldRestart()
                    .define("scaleMaxHealth", true);

            capMaxHealth = builder
                    .comment("If true, max health will be capped at 6 hearts and absorption at 2 hearts per limb. If false, the health cap will be much higher (64 hearts normal and 16 absorption)")
                    .translation("firstaid.config.scalemaxhealth")
                    .define("capMaxHealth", true);

            vanillaHealthCalculation = builder
                    .comment("Specifies how the vanilla health is calculated. Affects the vanilla visual health bar, as well as the value other mods get to see when they query the player health.",
                            "AVERAGE_ALL simply takes all limbs and calculates the average of it.",
                            "AVERAGE_CRITICAL takes all critical limbs and calculates the average of it.",
                            "MIN_CRITICAl takes the smallest health value of all critical limb.",
                            "Does not have any effect if all critical limbs have been disabled.")
                    .defineEnum("vanillaHealthCalculation", VanillaHealthCalculationMode.AVERAGE_ALL);

            useFriendlyRandomDistribution = builder
                    .comment("If enabled, random damage distributions can leave critical limbs at 1hp if possible.",
                            "When there is too much damage, the damage will still kill the player. Non-random distributions are not affected by this.")
                    .define("useFriendlyRandomDistribution", true);
            friendlyRandomDistributionChance = builder
                    .comment("Chance for friendly random damage behavior to trigger. 0 disables the protection roll, 1 always applies it when useFriendlyRandomDistribution is enabled.")
                    .defineInRange("friendlyRandomDistributionChance", FirstAid.DEFAULT_FRIENDLY_RANDOM_DISTRIBUTION_CHANCE, 0D, 1D);

            armorEnchantmentMode = builder
                    .comment("If set to LOCAL_ENCHANTMENTS, only the enchantments for the armor for the body part that is currently being damaged is taken into account. The strength of the armor is multiplied by 4 (default value, can be changed by enchantmentMultiplier), so it matches the vanilla default",
                            "If set to GLOBAL_ENCHANTMENTS, the enchantments of all armor pieces are taken into account for all body parts that have any kind of armor.")
                    .defineEnum("armorEnchantmentMode", ArmorEnchantmentMode.LOCAL_ENCHANTMENTS);

            builder.pop();

            builder.push("Command Settings");
            dynamicPainEnabled = builder
                    .comment("Persistent toggle for /firstaid pain (dynamic vs mild)")
                    .define("dynamicPainEnabled", true);
            mildPainLevel = builder
                    .comment("Pain level used when /firstaid pain mild is active")
                    .defineInRange("mildPainLevel", 1, 1, 5);
            enablePainVignette = builder
                    .comment("Enable red screen vignette overlay when in pain")
                    .define("enablePainVignette", true);
            enablePainBlur = builder
                    .comment("Enable radial pain blur / weak adrenaline warp post-processing")
                    .define("enablePainBlur", true);
            enablePainFovCompression = builder
                    .comment("Enable FOV compression (tunnel vision) when in pain")
                    .define("enablePainFovCompression", true);
            enablePainAudioEffects = builder
                    .comment("Enable severe pain audio effects (tinnitus sound)")
                    .define("enablePainAudioEffects", true);
            lowSuppressionEnabled = builder
                    .comment("Persistent toggle for /firstaid adrenaline (dynamic vs mild)")
                    .define("lowSuppressionEnabled", false);
            projectileSuppressionEnabled = builder
                    .comment("Persistent toggle for projectile near-miss adrenaline")
                    .define("projectileSuppressionEnabled", true);
            lowSuppressionMultiplier = builder
                    .comment("Visual adrenaline intensity multiplier used when /firstaid adrenaline mild is active")
                    .defineInRange("lowSuppressionMultiplier", 0.4D, 0D, 1D);

            suppressionGainMultiplier = builder

                    .comment("Global adrenaline gain multiplier for hits and projectile near misses; also set by /firstaid adrenaline gain")

                    .defineInRange("suppressionGainMultiplier", 0.15D, 0.01D, 1D);
            adrenalineReliefMultiplier = builder.comment("Adrenaline protection for injury debuffs and pain FOV; 1 restores previous strength, does not change pain blur").defineInRange("adrenalineReliefMultiplier", 1.7D, 0D, 2D);
            encounterSightRange = builder.comment("Visible hostile encounter range in blocks").defineInRange("encounterSightRange", 16.0D, 1.0D, 128.0D);
            encounterThreatRange = builder.comment("Current threat range in blocks").defineInRange("encounterThreatRange", 12.0D, 1.0D, 128.0D);
            encounterRecentAttackerTicks = builder.comment("Recent player attacker memory in ticks").defineInRange("encounterRecentAttackerTicks", 600, 1, 72000);
            adrenalineFatigueEnabled = builder.comment("Apply fatigue after prolonged adrenaline when no threats remain").define("adrenalineFatigueEnabled", true);
            adrenalineFatigueThresholdSeconds = builder.comment("Continuous adrenaline seconds before fatigue can occur").defineInRange("adrenalineFatigueThresholdSeconds", 60, 1, 3600);
            adrenalineFatigueDurationRatio = builder.comment("Fatigue duration as a fraction of adrenaline duration").defineInRange("adrenalineFatigueDurationRatio", 0.10D, 0.0D, 1.0D);
            encounterBaseIntensity = builder.comment("Minimum adrenaline intensity from seeing or being targeted by hostiles; does not affect adrenaline gained from hits").defineInRange("encounterBaseIntensity", 0.20D, 0.0D, 1.0D);
            adrenalineMaxLevel = builder.comment("Highest adrenaline tier (0-5) that combat can build from hits, projectile near misses and nearby enemies; 0 keeps combat below tier I. Adrenaline and morphine injectors still reach full strength. Also set by /firstaid adrenaline maxlevel").defineInRange("adrenalineMaxLevel", 5, 0, 5);
            adrenalineAttackGainMultiplier = builder.comment("Multiplier for adrenaline gained by hitting enemies, applied on top of suppressionGainMultiplier; 0 disables hit-driven adrenaline. Also set by /firstaid adrenaline attackgain").defineInRange("adrenalineAttackGainMultiplier", 1.0D, 0.0D, 2.0D);
            adrenalineAttackCooldownTicks = builder.comment("Minimum ticks between hit-driven adrenaline gains per player (20 ticks = 1 second); hits in the same tick such as sweeps, multishot and piercing always count once. Also set by /firstaid adrenaline attackcooldown").defineInRange("adrenalineAttackCooldownTicks", 0, 0, 200);
            adrenalineAttackHoldTicks = builder.comment("Ticks adrenaline is held after a hit before it starts to decay. Also set by /firstaid adrenaline attackhold").defineInRange("adrenalineAttackHoldTicks", 40, 0, 200);
            rescueWakeUpEnabled = builder
                    .comment("Persistent toggle for /firstaid revivewakeup (on vs off)")
                    .define("rescueWakeUpEnabled", true);
            rescueWakeUpDelaySeconds = builder
                    .comment("Persistent delay in seconds for /firstaid revivewakeup on [seconds]")
                    .defineInRange("rescueWakeUpDelaySeconds", FirstAid.DEFAULT_RESCUE_WAKE_UP_DELAY_SECONDS, 0D, 3600D);
            naturalRegenLimitRatio = builder
                    .comment("Maximum health fraction natural regeneration can restore in limited modes")
                    .defineInRange("naturalRegenLimitRatio", 0.85D, 0D, 1D);
            naturalRegenCriticalPriorityRatio = builder
                    .comment("Critical limb health fraction at or below which critical natural regeneration is prioritized")
                    .defineInRange("naturalRegenCriticalPriorityRatio", 0.85D, 0D, 1D);
            medicineEffectMode = builder
                    .comment("Persistent toggle for /firstaid medicineeffect (realistic/assisted/casual)")
                    .defineEnum("medicineEffectMode", FirstAid.MedicineEffectMode.REALISTIC);
            medicineTimingMultiplier = builder
                    .comment("Multiplier applied to medicine timing; command presets set this to realistic=1.0, assisted=0.5, casual=0.25")
                    .defineInRange("medicineTimingMultiplier", 1D, 0.01D, 20D);
            morphineActivationDelaySeconds = builder
                    .comment("Exact morphine activation delay in seconds")
                    .defineInRange("morphineActivationDelaySeconds", FirstAid.DEFAULT_MORPHINE_ACTIVATION_DELAY_SECONDS, 0D, 3600D);
            painkillerActivationDelaySeconds = builder
                    .comment("Exact painkiller activation delay in seconds")
                    .defineInRange("painkillerActivationDelaySeconds", FirstAid.DEFAULT_PAINKILLER_ACTIVATION_DELAY_SECONDS, 0D, 3600D);
            morphineUseDuration = builder
                    .comment("Use duration of morphine in ticks (20 ticks = 1 second)")
                    .defineInRange("morphineUseDuration", 40, 1, 72000);
            painkillersUseDuration = builder
                    .comment("Use duration of painkillers in ticks (20 ticks = 1 second)")
                    .defineInRange("painkillersUseDuration", 32, 1, 72000);
            adrenalineUseDuration = builder
                    .comment("Use duration of adrenaline injector in ticks (20 ticks = 1 second)")
                    .defineInRange("adrenalineUseDuration", 40, 1, 72000);
            morphineInjectorUseDuration = builder
                    .comment("Use duration of morphine injector in ticks (20 ticks = 1 second)")
                    .defineInRange("morphineInjectorUseDuration", 40, 1, 72000);
            criticalCrawlEnabled = builder
                    .comment("If true, critically downed players can crawl slowly during the first 60% of the downed timer")
                    .define("criticalCrawlEnabled", true);
            addictionEnabled = builder
                    .comment("If true, opioid medicines build addiction and can trigger episodic withdrawal")
                    .define("addictionEnabled", true);
            morphineAddictionGain = builder.defineInRange("morphineAddictionGain", 7.8, 0, 100);
            morphineInjectorAddictionGain = builder.defineInRange("morphineInjectorAddictionGain", 20.0D, 0.0D, 100.0D);
            bandageCraftYield = builder.defineInRange("bandageCraftYield", 4, 1, 16);
            plasterCraftYield = builder.defineInRange("plasterCraftYield", 4, 1, 16);
            painkillersCraftYield = builder.defineInRange("painkillersCraftYield", 2, 1, 16);
            morphineCraftYield = builder.defineInRange("morphineCraftYield", 1, 1, 16);
            morphineInjectorCraftUses = builder.defineInRange("morphineInjectorCraftUses", 2, 1, 1000);
            adrenalineInjectorCraftUses = builder.defineInRange("adrenalineInjectorCraftUses", 4, 1, 1000);
            defibrillatorCraftUses = builder.defineInRange("defibrillatorCraftUses", 3, 1, 1000);
            injuryDebuffMode = builder
                    .comment("Persistent toggle for /firstaid injurydebuff (normal/low/off)")
                    .defineEnum("injuryDebuffMode", FirstAid.InjuryDebuffMode.NORMAL);
            lowInjuryDebuffDamageScale = builder
                    .comment("Damage and healing scale used when /firstaid injurydebuff low is active")
                    .defineInRange("lowInjuryDebuffDamageScale", 0.4D, 0D, 1D);
            lowInjuryDebuffAmplifierScale = builder
                    .comment("Amplifier scale used when /firstaid injurydebuff low is active")
                    .defineInRange("lowInjuryDebuffAmplifierScale", 0.5D, 0D, 1D);
            lowInjuryDebuffDurationScale = builder
                    .comment("Duration scale used when /firstaid injurydebuff low is active")
                    .defineInRange("lowInjuryDebuffDurationScale", 0.5D, 0D, 1D);
            injuryDebuffOverrides = builder
                    .comment("Per-effect overrides for /firstaid injurydebuff. Format: modid:effect=normal|low|off")
                    .defineList("injuryDebuffOverrides", Collections.emptyList(), o -> o != null && !o.toString().isBlank());
            suppressionEntityBlacklist = builder
                    .comment("Entity type ids that cannot trigger adrenaline near-miss effects")
                    .defineList("suppressionEntityBlacklist", serializeResourceLocationList(FirstAid.getDefaultSuppressionEntityBlacklist()), o -> o != null && ResourceLocation.tryParse(o.toString()) != null);
            commandTipsEnabled = builder
                    .comment("If true, operators receive the FirstAid admin command tip when joining a world")
                    .define("commandTipsEnabled", true);
            knockdownMode = builder
                    .comment("Persistent toggle for /firstaid knockdown. ALWAYS: a collapsed head or body always downs the player.",
                            "CHANCE: the downed chance is exp(-knockdownOverflowCoefficient * overflow / partMaxHealth), where overflow is the damage beyond the critical part's remaining health.",
                            "NEVER: the player dies instead of going down.")
                    .defineEnum("knockdownMode", FirstAid.KnockdownMode.CHANCE);
            knockdownOverflowCoefficient = builder
                    .comment("Overflow sensitivity used by knockdownMode CHANCE; 0 always downs, higher values make heavy overkill hits lethal more often")
                    .defineInRange("knockdownOverflowCoefficient", FirstAid.DEFAULT_KNOCKDOWN_OVERFLOW_COEFFICIENT, 0D, FirstAid.MAX_KNOCKDOWN_OVERFLOW_COEFFICIENT);
            builder.pop();

            builder.push("Enchantment Handling");

            enchantmentMultiplier = builder
                    .comment("Specifies the default modifier. This is only used if armorEnchantmentMode is LOCAL_ENCHANTMENTS, as this is used scale up the values to somewhat match what vanilla balances around (as vanilla balances around global enchantments)")
                    .defineInRange("enchantmentMultiplier", 4, 0, 4);

            enchMulOverrideResourceLocations = builder
                    .comment("Specifies the overrides for the the modifier. This is only used if armorEnchantmentMode is LOCAL_ENCHANTMENTS. This can be used to set another multiplier for special enchantments",
                            "This list specifies the resource location of the enchantment. Must be fully specified and cannot use wildcard. Example: minecraft:feather_falling. First entry here will be matched to first multiplier, second entry to second multiplier and so on")
                    .defineList("resourceLocations", Collections.singletonList("minecraft:feather_falling"), o -> {
                        if (o == null || o.toString().isEmpty()) {
                            FirstAid.LOGGER.warn("Ignored empty or invalid string for resourceLocations");
                            return false;
                        } else {
                            return true;
                        }
                    });
            enchMulOverrideMultiplier = builder
                    .comment("Specifies the overrides for the the modifier. This is only used if armorEnchantmentMode is LOCAL_ENCHANTMENTS. This can be used to set another multiplier for special enchantments",
                            "This list specifies the multiplier of the corresponding resource location. See resourceLocations comment. Must be between 0 and 4")
                    .defineList("overrideMultiplier", Collections.singletonList(2), o -> {
                        try {
                            int val = Integer.parseInt(o.toString());
                            return val >= 0 && val <= 4;
                        } catch (NumberFormatException ignored) {}
                        FirstAid.LOGGER.warn("Invalid entry " + o.toString() + " for overrideMultiplier found!");
                        return false;
                    });

            builder.pop();
        }

        public final ModConfigSpec.IntValue maxHealthHead;
        public final ModConfigSpec.BooleanValue causeDeathHead;
        public final ModConfigSpec.IntValue maxHealthLeftArm;
        public final ModConfigSpec.IntValue maxHealthLeftLeg;
        public final ModConfigSpec.IntValue maxHealthLeftFoot;
        public final ModConfigSpec.IntValue maxHealthBody;
        public final ModConfigSpec.BooleanValue causeDeathBody;
        public final ModConfigSpec.IntValue maxHealthRightArm;
        public final ModConfigSpec.IntValue maxHealthRightLeg;
        public final ModConfigSpec.IntValue maxHealthRightFoot;

        public final ModConfigSpec.DoubleValue headArmorMultiplier;
        public final ModConfigSpec.DoubleValue chestArmorMultiplier;
        public final ModConfigSpec.DoubleValue legsArmorMultiplier;
        public final ModConfigSpec.DoubleValue feetArmorMultiplier;

        public final ModConfigSpec.DoubleValue headArmorOffset;
        public final ModConfigSpec.DoubleValue chestArmorOffset;
        public final ModConfigSpec.DoubleValue legsArmorOffset;
        public final ModConfigSpec.DoubleValue feetArmorOffset;

        public final ModConfigSpec.DoubleValue headThoughnessMultiplier;
        public final ModConfigSpec.DoubleValue chestThoughnessMultiplier;
        public final ModConfigSpec.DoubleValue legsThoughnessMultiplier;
        public final ModConfigSpec.DoubleValue feetThoughnessMultiplier;

        public final ModConfigSpec.DoubleValue headThoughnessOffset;
        public final ModConfigSpec.DoubleValue chestThoughnessOffset;
        public final ModConfigSpec.DoubleValue legsThoughnessOffset;
        public final ModConfigSpec.DoubleValue feetThoughnessOffset;

        public final IEEntry bandage;
        public final IEEntry plaster;

        public final ModConfigSpec.BooleanValue allowNaturalRegeneration;
        public final ModConfigSpec.BooleanValue allowOtherHealingItems;
        public final ModConfigSpec.DoubleValue sleepHealPercentage;
        public final ModConfigSpec.DoubleValue otherRegenMultiplier;
        public final ModConfigSpec.DoubleValue naturalRegenMultiplier;
        public final ModConfigSpec.EnumValue<FirstAid.NaturalRegenMode> naturalRegenMode;
        public final ModConfigSpec.EnumValue<FirstAid.NaturalRegenStrategy> naturalRegenStrategy;
        public final ModConfigSpec.IntValue resistanceReductionPercentPerLevel;

        public final ModConfigSpec.BooleanValue scaleMaxHealth;
        public final ModConfigSpec.BooleanValue capMaxHealth;
        public final ModConfigSpec.EnumValue<VanillaHealthCalculationMode> vanillaHealthCalculation;
        public final ModConfigSpec.BooleanValue useFriendlyRandomDistribution;
        public final ModConfigSpec.DoubleValue friendlyRandomDistributionChance;
        public final ModConfigSpec.EnumValue<ArmorEnchantmentMode> armorEnchantmentMode;

        public final ModConfigSpec.IntValue enchantmentMultiplier;
        public final ModConfigSpec.ConfigValue<List<? extends String>> enchMulOverrideResourceLocations;
        public final ModConfigSpec.ConfigValue<List<? extends Integer>> enchMulOverrideMultiplier;
        public final ModConfigSpec.BooleanValue dynamicPainEnabled;
        public final ModConfigSpec.IntValue mildPainLevel;
        public final ModConfigSpec.BooleanValue enablePainVignette;
        public final ModConfigSpec.BooleanValue enablePainBlur;
        public final ModConfigSpec.BooleanValue enablePainFovCompression;
        public final ModConfigSpec.BooleanValue enablePainAudioEffects;
        public final ModConfigSpec.BooleanValue lowSuppressionEnabled;
        public final ModConfigSpec.BooleanValue projectileSuppressionEnabled;
        public final ModConfigSpec.DoubleValue lowSuppressionMultiplier;
        public final ModConfigSpec.DoubleValue suppressionGainMultiplier;
        public final ModConfigSpec.DoubleValue adrenalineReliefMultiplier;
        public final ModConfigSpec.DoubleValue encounterSightRange;
        public final ModConfigSpec.DoubleValue encounterThreatRange;
        public final ModConfigSpec.IntValue encounterRecentAttackerTicks;
        public final ModConfigSpec.BooleanValue adrenalineFatigueEnabled;
        public final ModConfigSpec.IntValue adrenalineFatigueThresholdSeconds;
        public final ModConfigSpec.DoubleValue adrenalineFatigueDurationRatio;
        public final ModConfigSpec.DoubleValue encounterBaseIntensity;
        public final ModConfigSpec.IntValue adrenalineMaxLevel;
        public final ModConfigSpec.DoubleValue adrenalineAttackGainMultiplier;
        public final ModConfigSpec.IntValue adrenalineAttackCooldownTicks;
        public final ModConfigSpec.IntValue adrenalineAttackHoldTicks;
        public final ModConfigSpec.BooleanValue rescueWakeUpEnabled;
        public final ModConfigSpec.DoubleValue rescueWakeUpDelaySeconds;
        public final ModConfigSpec.DoubleValue naturalRegenLimitRatio;
        public final ModConfigSpec.DoubleValue naturalRegenCriticalPriorityRatio;
        public final ModConfigSpec.EnumValue<FirstAid.MedicineEffectMode> medicineEffectMode;
        public final ModConfigSpec.DoubleValue medicineTimingMultiplier;
        public final ModConfigSpec.DoubleValue morphineActivationDelaySeconds;
        public final ModConfigSpec.DoubleValue painkillerActivationDelaySeconds;
        public final ModConfigSpec.IntValue morphineUseDuration;
        public final ModConfigSpec.IntValue painkillersUseDuration;
        public final ModConfigSpec.IntValue adrenalineUseDuration;
        public final ModConfigSpec.IntValue morphineInjectorUseDuration;
        public final ModConfigSpec.BooleanValue criticalCrawlEnabled;
        public final ModConfigSpec.BooleanValue addictionEnabled;
        public final ModConfigSpec.DoubleValue morphineAddictionGain;
        public final ModConfigSpec.DoubleValue morphineInjectorAddictionGain;
        public final ModConfigSpec.IntValue bandageCraftYield;
        public final ModConfigSpec.IntValue plasterCraftYield;
        public final ModConfigSpec.IntValue painkillersCraftYield;
        public final ModConfigSpec.IntValue morphineCraftYield;
        public final ModConfigSpec.IntValue morphineInjectorCraftUses;
        public final ModConfigSpec.IntValue adrenalineInjectorCraftUses;
        public final ModConfigSpec.IntValue defibrillatorCraftUses;
        public final ModConfigSpec.EnumValue<FirstAid.InjuryDebuffMode> injuryDebuffMode;
        public final ModConfigSpec.DoubleValue lowInjuryDebuffDamageScale;
        public final ModConfigSpec.DoubleValue lowInjuryDebuffAmplifierScale;
        public final ModConfigSpec.DoubleValue lowInjuryDebuffDurationScale;
        public final ModConfigSpec.ConfigValue<List<? extends String>> injuryDebuffOverrides;
        public final ModConfigSpec.ConfigValue<List<? extends String>> suppressionEntityBlacklist;
        public final ModConfigSpec.BooleanValue commandTipsEnabled;
        public final ModConfigSpec.EnumValue<FirstAid.KnockdownMode> knockdownMode;
        public final ModConfigSpec.DoubleValue knockdownOverflowCoefficient;


        private static ModConfigSpec.IntValue healthEntry(ModConfigSpec.Builder builder, String name, int defaultVal) {
            String noSpaceName = name.replace(' ', '_');
            return builder.comment("Max health of the " + name).translation("firstaid.config.maxhealth." + noSpaceName.toLowerCase(Locale.ENGLISH)).defineInRange("maxHealth" + noSpaceName, defaultVal, 2, Integer.MAX_VALUE);
        }

        private static ModConfigSpec.DoubleValue multiplierEntry(ModConfigSpec.Builder builder, String name, double defaultVal) {
            return builder.comment("The multiplier for local armor for every armor of slot " + name).translation("firstaid.config.multiplier." + name.toLowerCase(Locale.ROOT)).defineInRange("multiplier" + name, defaultVal, 0D, 16D);
        }

        private static ModConfigSpec.DoubleValue offsetEntry(ModConfigSpec.Builder builder, String name, double defaultVal) {
            return builder.comment("The static offset applied to any armor piece with a value greater than zero of slot " + name).translation("firstaid.config.offset." + name.toLowerCase(Locale.ROOT)).defineInRange("offset" + name, defaultVal, 0D, 4D);
        }

        public static class IEEntry {

            public final ModConfigSpec.IntValue totalHeals;
            public final ModConfigSpec.IntValue secondsPerHeal;
            public final ModConfigSpec.IntValue applyTime;

            IEEntry(ModConfigSpec.Builder builder, String name, int initialTotalHeals, int initialSecondsPerHeal, int initialApplyTime) {
                builder.push(name);
                totalHeals = builder
                        .comment("The total heals this item does when applied. 1 heal = half a heart")
                        .translation("firstaid.config.totalheals")
                        .defineInRange("totalsHeals", initialTotalHeals, 1, Byte.MAX_VALUE);
                secondsPerHeal = builder
                        .comment("The time it takes for a single heal to trigger. Total time this item is active = this * totalHeals")
                        .translation("firstaid.config.secondsperheal")
                        .defineInRange("secondsPerHeal", initialSecondsPerHeal, 1, Short.MAX_VALUE);
                applyTime = builder
                        .comment("The time it takes in the GUI to apply the item in milliseconds")
                        .translation("firstaid.config.applytime")
                        .defineInRange("applyTime", initialApplyTime, 0, 16000);
                builder.pop();
            }
        }
    }

    public static class General {

        public General(ModConfigSpec.Builder builder) {
            builder.comment("Generic configuration settings");
            builder.push("misc");
            debug = builder
                    .comment("Enabled additional debug logs - May slow down the game and will increase log file size",
                            "Only enable for special purposes")
                    .translation("firstaid.config.debug")
                    .define("debug", false);
            builder.pop();
        }

        public final ModConfigSpec.BooleanValue debug;
    }

    public static class Client {

        public enum OverlayMode {
            OFF, NUMBERS, HEARTS, PLAYER_MODEL, PLAYER_MODEL_4_COLORS;

            public boolean isPlayerModel() {
                return this == PLAYER_MODEL || this == PLAYER_MODEL_4_COLORS;
            }
        }

        public enum Position {
            TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT
        }

        public enum TooltipMode {
            REPLACE, APPEND, NONE
        }

        public enum VanillaHealthbarMode {
            NORMAL, HIGHLIGHT_CRITICAL_PATH, HIDE
        }

        public final ModConfigSpec.EnumValue<VanillaHealthbarMode> vanillaHealthBarMode;
        public final ModConfigSpec.EnumValue<OverlayMode> overlayMode;
        public final ModConfigSpec.EnumValue<Position> pos;
        public final ModConfigSpec.EnumValue<TooltipMode> armorTooltipMode;
        public final ModConfigSpec.IntValue xOffset;
        public final ModConfigSpec.IntValue yOffset;
        public final ModConfigSpec.IntValue alpha;
        public final ModConfigSpec.BooleanValue enableSounds;
        public final ModConfigSpec.BooleanValue tinnitusEnabled;
        public final ModConfigSpec.IntValue tinnitusDurationPercent;
        public final ModConfigSpec.BooleanValue enableEasterEggs;
        public final ModConfigSpec.IntValue visibleDurationTicks;
        public final ModConfigSpec.BooleanValue flash;
        public final ModConfigSpec.BooleanValue hidePlayerModelIndicators;

        public Client(ModConfigSpec.Builder builder) {
            builder.comment("Client only configuration settings").push("Overlay");
            vanillaHealthBarMode = builder
                    .comment("Legacy setting retained for existing configs.",
                            "All values hide normal health hearts and display only absorption hearts.")
                    .translation("firstaid.config.vanillahealthbarmode")
                    .defineEnum("vanillaHealthBarMode", VanillaHealthbarMode.HIDE);

            overlayMode = builder
                    .comment("The design to use to visualize the health")
                    .defineEnum("overlayMode", OverlayMode.PLAYER_MODEL);

            pos = builder
                    .comment("The relative point of the overlay")
                    .translation("firstaid.config.position")
                    .defineEnum("overlayPosition", Position.TOP_LEFT);

            armorTooltipMode = builder
                    .comment("Determines how first aid should display armor on item tooltips.", "REPLACE replaces the vanilla description with the one fitting first aid\nAPPEND will add the first aid values at the bottom", "NONE will show the old vanilla values. Be advised this is purly visual, interally, the first aid value will always be used")
                    .defineEnum("armorTooltipMode", TooltipMode.REPLACE);

            xOffset = builder
                    .comment("The offset on the x axis")
                    .translation("firstaid.config.xoffset")
                    .defineInRange("xOffset", 0, Short.MIN_VALUE, Short.MAX_VALUE);

            yOffset = builder
                    .comment("The offset on the y axis")
                    .translation("firstaid.config.yoffset")
                    .defineInRange("yOffset", 1, Short.MIN_VALUE, Short.MAX_VALUE);

            alpha = builder
                    .comment("Determines the transparency of the overlay. 200 = Maximum transparency, 0 = Fully opaque")
                    .translation("firstaid.config.alpha")
                    .defineInRange("alpha", 50, 0 ,200);

            this.visibleDurationTicks = builder
                    .translation("firstaid.config.visibledurationticks")
                    .comment("Specifies how many ticks (20 ticks = 1 second) the overlay should be visible after health changed (healing/damaging)",
                            "If set to -1, the HUD is always visible")
                    .defineInRange("visibleDurationTicks", -1, -1, 600);

            this.flash = builder
                    .translation("firstaid.config.flash")
                    .comment("If set to true, the overlay will flash for a short moment if the health changed. Only affects PLAYER_MODEL overlay")
                    .define("flash", true);

            this.hidePlayerModelIndicators = builder
                    .comment("If true, hides status indicator lines under the player-model HUD. Default true.")
                    .define("hidePlayerModelIndicators", true);
            builder.pop();


            builder.push("Misc");
            tinnitusEnabled = builder
                    .comment("Enable tinnitus sounds from injuries and morphine. Suppression never triggers tinnitus.")
                    .translation("firstaid.config.tinnitusenabled")
                    .define("tinnitusEnabled", true);
            tinnitusDurationPercent = builder
                    .comment("Tinnitus duration as a percentage of the original clip (1-400). Values above 100 loop the clip.")
                    .translation("firstaid.config.tinnitusdurationpercent")
                    .defineInRange("tinnitusDurationPercent", 75, 1, 400);
            enableSounds = builder
                    .comment("Set to true to enable the debuff sounds. Only matters when debuffs are enabled")
                    .translation("firstaid.config.enablesoundsystem")
                    .define("enableSoundSystem", true);
            enableEasterEggs = builder
                    .comment("Disables the funny easter eggs on certain events if false")
                    .define("enableEasterEggs", true);
            builder.pop();
        }
    }

//    @Config.Comment("If true, all usages of setHealth from other mods will be captured. Should not cause any problems, but allow mods like scaling health bandages to apply")
//    @Config.LangKey("firstaid.config.sethealth")
//    @ExtraConfig.Advanced
    public static final boolean watchSetHealth = true; //If we need this at all, this is server as well
}

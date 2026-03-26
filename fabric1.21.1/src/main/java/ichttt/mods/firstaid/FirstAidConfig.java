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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public final class FirstAidConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("firstaid");

    public static final Server SERVER = new Server();
    public static final General GENERAL = new General();
    public static final Client CLIENT = new Client();

    private FirstAidConfig() {
    }

    public static void loadServer() {
        loadSection(SERVER, "firstaid-server.json");
    }

    public static void loadGeneral() {
        loadSection(GENERAL, "firstaid-general.json");
    }

    public static void loadClient() {
        loadSection(CLIENT, "firstaid-client.json");
    }

    public static JsonObject serializeServerBundle() {
        JsonObject root = new JsonObject();
        root.add("server", SERVER.write());
        root.add("general", GENERAL.write());
        return root;
    }

    public static void applyServerBundle(JsonObject bundle) {
        if (bundle == null) {
            return;
        }
        JsonObject server = bundle.has("server") && bundle.get("server").isJsonObject()
                ? bundle.getAsJsonObject("server")
                : new JsonObject();
        JsonObject general = bundle.has("general") && bundle.get("general").isJsonObject()
                ? bundle.getAsJsonObject("general")
                : new JsonObject();
        SERVER.read(server);
        GENERAL.read(general);
        applyCommandSettings();
    }

    public static void applyCommandSettings() {
        FirstAid.dynamicPainEnabled = SERVER.dynamicPainEnabled.get();
        FirstAid.lowSuppressionEnabled = SERVER.lowSuppressionEnabled.get();
        FirstAid.rescueWakeUpEnabled = SERVER.rescueWakeUpEnabled.get();
        FirstAid.rescueWakeUpDelaySeconds = SERVER.rescueWakeUpDelaySeconds.get();
        FirstAid.medicineEffectMode = SERVER.medicineEffectMode.get();
        FirstAid.injuryDebuffMode = SERVER.injuryDebuffMode.get();
        FirstAid.injuryDebuffOverrides.clear();
        FirstAid.injuryDebuffOverrides.putAll(SERVER.injuryDebuffOverrides.get());
    }

    public static void persistCommandSettings() {
        SERVER.dynamicPainEnabled.set(FirstAid.dynamicPainEnabled);
        SERVER.lowSuppressionEnabled.set(FirstAid.lowSuppressionEnabled);
        SERVER.rescueWakeUpEnabled.set(FirstAid.rescueWakeUpEnabled);
        SERVER.rescueWakeUpDelaySeconds.set(FirstAid.rescueWakeUpDelaySeconds);
        SERVER.medicineEffectMode.set(FirstAid.medicineEffectMode);
        SERVER.injuryDebuffMode.set(FirstAid.injuryDebuffMode);
        SERVER.injuryDebuffOverrides.set(new LinkedHashMap<>(FirstAid.injuryDebuffOverrides));
        saveServer();
    }

    private static void loadSection(ConfigSection section, String fileName) {
        Objects.requireNonNull(section);
        Path file = CONFIG_DIR.resolve(fileName);
        JsonObject data = readJson(file);
        section.read(data);
        writeJson(file, section.write());
    }

    private static JsonObject readJson(Path file) {
        try {
            if (!Files.exists(file)) {
                return new JsonObject();
            }
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                return new JsonObject();
            }
            JsonElement element = com.google.gson.JsonParser.parseString(raw);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (IOException | JsonParseException e) {
            FirstAid.LOGGER.warn("Failed reading config {}: {}", file, e.getMessage());
            return new JsonObject();
        }
    }

    private static void writeJson(Path file, JsonObject data) {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(file, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            FirstAid.LOGGER.warn("Failed writing config {}: {}", file, e.getMessage());
        }
    }

    private static void saveServer() {
        writeJson(CONFIG_DIR.resolve("firstaid-server.json"), SERVER.write());
    }

    public static final class Server extends ConfigSection {

        public enum VanillaHealthCalculationMode {
            AVERAGE_ALL, AVERAGE_CRITICAL, MIN_CRITICAL, CRITICAL_50_PERCENT_OTHER_50_PERCENT
        }

        public enum ArmorEnchantmentMode {
            GLOBAL_ENCHANTMENTS, LOCAL_ENCHANTMENTS
        }

        public final ConfigValue<Integer> maxHealthHead;
        public final ConfigValue<Boolean> causeDeathHead;
        public final ConfigValue<Integer> maxHealthLeftArm;
        public final ConfigValue<Integer> maxHealthLeftLeg;
        public final ConfigValue<Integer> maxHealthLeftFoot;
        public final ConfigValue<Integer> maxHealthBody;
        public final ConfigValue<Boolean> causeDeathBody;
        public final ConfigValue<Integer> maxHealthRightArm;
        public final ConfigValue<Integer> maxHealthRightLeg;
        public final ConfigValue<Integer> maxHealthRightFoot;

        public final ConfigValue<Double> headArmorMultiplier;
        public final ConfigValue<Double> chestArmorMultiplier;
        public final ConfigValue<Double> legsArmorMultiplier;
        public final ConfigValue<Double> feetArmorMultiplier;

        public final ConfigValue<Double> headArmorOffset;
        public final ConfigValue<Double> chestArmorOffset;
        public final ConfigValue<Double> legsArmorOffset;
        public final ConfigValue<Double> feetArmorOffset;

        public final ConfigValue<Double> headThoughnessMultiplier;
        public final ConfigValue<Double> chestThoughnessMultiplier;
        public final ConfigValue<Double> legsThoughnessMultiplier;
        public final ConfigValue<Double> feetThoughnessMultiplier;

        public final ConfigValue<Double> headThoughnessOffset;
        public final ConfigValue<Double> chestThoughnessOffset;
        public final ConfigValue<Double> legsThoughnessOffset;
        public final ConfigValue<Double> feetThoughnessOffset;

        public final IEEntry bandage;
        public final IEEntry plaster;

        public final ConfigValue<Boolean> allowNaturalRegeneration;
        public final ConfigValue<Boolean> allowOtherHealingItems;
        public final ConfigValue<Double> sleepHealPercentage;
        public final ConfigValue<Double> otherRegenMultiplier;
        public final ConfigValue<Double> naturalRegenMultiplier;
        public final ConfigValue<Integer> resistanceReductionPercentPerLevel;

        public final ConfigValue<Boolean> scaleMaxHealth;
        public final ConfigValue<Boolean> capMaxHealth;
        public final ConfigValue<VanillaHealthCalculationMode> vanillaHealthCalculation;
        public final ConfigValue<Boolean> useFriendlyRandomDistribution;
        public final ConfigValue<ArmorEnchantmentMode> armorEnchantmentMode;

        public final ConfigValue<Integer> enchantmentMultiplier;
        public final ConfigValue<List<String>> enchMulOverrideIdentifiers;
        public final ConfigValue<List<Integer>> enchMulOverrideMultiplier;
        public final ConfigValue<Boolean> dynamicPainEnabled;
        public final ConfigValue<Boolean> lowSuppressionEnabled;
        public final ConfigValue<Boolean> rescueWakeUpEnabled;
        public final ConfigValue<Double> rescueWakeUpDelaySeconds;
        public final ConfigValue<FirstAid.MedicineEffectMode> medicineEffectMode;
        public final ConfigValue<FirstAid.InjuryDebuffMode> injuryDebuffMode;
        public final ConfigValue<Map<ResourceLocation, FirstAid.InjuryDebuffMode>> injuryDebuffOverrides;

        public Server() {
            maxHealthHead = define(intValue("maxHealthHead", 7, 2, 12));
            maxHealthLeftArm = define(intValue("maxHealthLeftArm", 4, 2, 12));
            maxHealthLeftLeg = define(intValue("maxHealthLeftLeg", 4, 2, 12));
            maxHealthLeftFoot = define(intValue("maxHealthLeftFoot", 4, 2, 12));
            maxHealthBody = define(intValue("maxHealthBody", 11, 2, 12));
            maxHealthRightArm = define(intValue("maxHealthRightArm", 4, 2, 12));
            maxHealthRightLeg = define(intValue("maxHealthRightLeg", 4, 2, 12));
            maxHealthRightFoot = define(intValue("maxHealthRightFoot", 4, 2, 12));
            causeDeathHead = define(boolValue("causeDeathHead", true));
            causeDeathBody = define(boolValue("causeDeathBody", true));

            headArmorMultiplier = define(doubleValue("headArmorMultiplier", 6D, 1D, 16D));
            chestArmorMultiplier = define(doubleValue("chestArmorMultiplier", 2.5D, 1D, 16D));
            legsArmorMultiplier = define(doubleValue("legsArmorMultiplier", 3D, 1D, 16D));
            feetArmorMultiplier = define(doubleValue("feetArmorMultiplier", 6D, 1D, 16D));

            headArmorOffset = define(doubleValue("headArmorOffset", 1D, 0D, 4D));
            chestArmorOffset = define(doubleValue("chestArmorOffset", 0D, 0D, 4D));
            legsArmorOffset = define(doubleValue("legsArmorOffset", 0D, 0D, 4D));
            feetArmorOffset = define(doubleValue("feetArmorOffset", 0D, 0D, 4D));

            headThoughnessMultiplier = define(doubleValue("headThoughnessMultiplier", 4D, 1D, 16D));
            chestThoughnessMultiplier = define(doubleValue("chestThoughnessMultiplier", 3D, 1D, 16D));
            legsThoughnessMultiplier = define(doubleValue("legsThoughnessMultiplier", 3D, 1D, 16D));
            feetThoughnessMultiplier = define(doubleValue("feetThoughnessMultiplier", 3.5D, 1D, 16D));

            headThoughnessOffset = define(doubleValue("headThoughnessOffset", 0D, 0D, 4D));
            chestThoughnessOffset = define(doubleValue("chestThoughnessOffset", 0D, 0D, 4D));
            legsThoughnessOffset = define(doubleValue("legsThoughnessOffset", 0D, 0D, 4D));
            feetThoughnessOffset = define(doubleValue("feetThoughnessOffset", 0D, 0D, 4D));

            bandage = new IEEntry(this, "bandage", 4, 18, 2500);
            plaster = new IEEntry(this, "plaster", 2, 22, 3000);

            allowNaturalRegeneration = define(boolValue("allowNaturalRegeneration", false));
            allowOtherHealingItems = define(boolValue("allowOtherHealingItems", true));
            sleepHealPercentage = define(doubleValue("sleepHealPercentage", 0.07D, 0D, 1D));
            otherRegenMultiplier = define(doubleValue("otherRegenMultiplier", 0.75D, 0D, 20D));
            naturalRegenMultiplier = define(doubleValue("naturalRegenMultiplier", 0.5D, 0D, 20D));
            resistanceReductionPercentPerLevel = define(intValue("resistanceReductionPercentPerLevel", 20, 1, 40));

            scaleMaxHealth = define(boolValue("scaleMaxHealth", true));
            capMaxHealth = define(boolValue("capMaxHealth", true));
            vanillaHealthCalculation = define(enumValue("vanillaHealthCalculation", VanillaHealthCalculationMode.AVERAGE_ALL, VanillaHealthCalculationMode.class));
            useFriendlyRandomDistribution = define(boolValue("useFriendlyRandomDistribution", false));
            armorEnchantmentMode = define(enumValue("armorEnchantmentMode", ArmorEnchantmentMode.LOCAL_ENCHANTMENTS, ArmorEnchantmentMode.class));

            enchantmentMultiplier = define(intValue("enchantmentMultiplier", 4, 1, 4));
            enchMulOverrideIdentifiers = define(stringList("enchantmentOverrideIdentifiers", Collections.singletonList("minecraft:feather_falling"), value -> !value.isBlank()));
            enchMulOverrideMultiplier = define(intList("enchantmentOverrideMultiplier", Collections.singletonList(2), value -> value >= 1 && value <= 4));

            dynamicPainEnabled = define(boolValue("dynamicPainEnabled", true));
            lowSuppressionEnabled = define(boolValue("lowSuppressionEnabled", false));
            rescueWakeUpEnabled = define(boolValue("rescueWakeUpEnabled", false));
            rescueWakeUpDelaySeconds = define(doubleValue("rescueWakeUpDelaySeconds", FirstAid.DEFAULT_RESCUE_WAKE_UP_DELAY_SECONDS, 0D, 3600D));
            medicineEffectMode = define(enumValue("medicineEffectMode", FirstAid.MedicineEffectMode.REALISTIC, FirstAid.MedicineEffectMode.class));
            injuryDebuffMode = define(enumValue("injuryDebuffMode", FirstAid.InjuryDebuffMode.NORMAL, FirstAid.InjuryDebuffMode.class));
            injuryDebuffOverrides = define(injuryDebuffOverridesValue("injuryDebuffOverrides"));
        }

        public static final class IEEntry {
            public final ConfigValue<Integer> totalHeals;
            public final ConfigValue<Integer> secondsPerHeal;
            public final ConfigValue<Integer> applyTime;

            IEEntry(Server owner, String name, int initialTotalHeals, int initialSecondsPerHeal, int initialApplyTime) {
                totalHeals = owner.define(intValue(name + "TotalHeals", initialTotalHeals, 1, Byte.MAX_VALUE));
                secondsPerHeal = owner.define(intValue(name + "SecondsPerHeal", initialSecondsPerHeal, 1, Short.MAX_VALUE));
                applyTime = owner.define(intValue(name + "ApplyTime", initialApplyTime, 0, 16000));
            }
        }
    }

    public static final class General extends ConfigSection {
        public final ConfigValue<Boolean> debug;

        public General() {
            debug = define(boolValue("debug", false));
        }
    }

    public static final class Client extends ConfigSection {

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

        public final ConfigValue<VanillaHealthbarMode> vanillaHealthBarMode;
        public final ConfigValue<OverlayMode> overlayMode;
        public final ConfigValue<Position> pos;
        public final ConfigValue<TooltipMode> armorTooltipMode;
        public final ConfigValue<Integer> xOffset;
        public final ConfigValue<Integer> yOffset;
        public final ConfigValue<Integer> alpha;
        public final ConfigValue<Boolean> enableSounds;
        public final ConfigValue<Boolean> enableEasterEggs;
        public final ConfigValue<Integer> visibleDurationTicks;
        public final ConfigValue<Boolean> flash;

        public Client() {
            vanillaHealthBarMode = define(enumValue("vanillaHealthBarMode", VanillaHealthbarMode.HIDE, VanillaHealthbarMode.class));
            overlayMode = define(enumValue("overlayMode", OverlayMode.PLAYER_MODEL, OverlayMode.class));
            pos = define(enumValue("overlayPosition", Position.TOP_LEFT, Position.class));
            armorTooltipMode = define(enumValue("armorTooltipMode", TooltipMode.REPLACE, TooltipMode.class));
            xOffset = define(intValue("xOffset", 0, Short.MIN_VALUE, Short.MAX_VALUE));
            yOffset = define(intValue("yOffset", 1, Short.MIN_VALUE, Short.MAX_VALUE));
            alpha = define(intValue("alpha", 50, 0, 200));
            visibleDurationTicks = define(intValue("visibleDurationTicks", -1, -1, 600));
            flash = define(boolValue("flash", true));
            enableSounds = define(boolValue("enableSoundSystem", true));
            enableEasterEggs = define(boolValue("enableEasterEggs", true));
        }
    }

    public static final boolean watchSetHealth = true;

    private abstract static class ConfigSection {
        private final Map<String, ConfigValue<?>> values = new LinkedHashMap<>();

        protected final <T> ConfigValue<T> define(ConfigValue<T> value) {
            values.put(value.key, value);
            return value;
        }

        protected void read(JsonObject data) {
            for (ConfigValue<?> value : values.values()) {
                value.load(data);
            }
        }

        protected JsonObject write() {
            JsonObject root = new JsonObject();
            for (ConfigValue<?> value : values.values()) {
                value.write(root);
            }
            return root;
        }
    }

    public static final class ConfigValue<T> {
        private final String key;
        private final T defaultValue;
        private final Function<JsonElement, T> parser;
        private final Function<T, JsonElement> serializer;
        private final Predicate<T> validator;
        private T value;

        private ConfigValue(String key, T defaultValue, Function<JsonElement, T> parser, Function<T, JsonElement> serializer, Predicate<T> validator) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.parser = parser;
            this.serializer = serializer;
            this.validator = validator;
            this.value = defaultValue;
        }

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = Objects.requireNonNull(value);
        }

        private void load(JsonObject data) {
            if (data == null || !data.has(key)) {
                value = defaultValue;
                return;
            }
            try {
                T parsed = parser.apply(data.get(key));
                if (validator != null && !validator.test(parsed)) {
                    FirstAid.LOGGER.warn("Config value {} out of range, resetting to default", key);
                    value = defaultValue;
                } else {
                    value = parsed;
                }
            } catch (Exception e) {
                FirstAid.LOGGER.warn("Failed parsing config value {}: {}", key, e.getMessage());
                value = defaultValue;
            }
        }

        private void write(JsonObject data) {
            data.add(key, serializer.apply(value));
        }
    }

    private static ConfigValue<Boolean> boolValue(String key, boolean def) {
        return new ConfigValue<>(key, def, JsonElement::getAsBoolean, value -> new com.google.gson.JsonPrimitive(value), null);
    }

    private static ConfigValue<Integer> intValue(String key, int def, int min, int max) {
        return new ConfigValue<>(key, def, JsonElement::getAsInt, value -> new com.google.gson.JsonPrimitive(value), value -> value >= min && value <= max);
    }

    private static ConfigValue<Double> doubleValue(String key, double def, double min, double max) {
        return new ConfigValue<>(key, def, JsonElement::getAsDouble, value -> new com.google.gson.JsonPrimitive(value), value -> value >= min && value <= max);
    }

    private static <T extends Enum<T>> ConfigValue<T> enumValue(String key, T def, Class<T> type) {
        return new ConfigValue<>(key, def, element -> {
            String name = element.getAsString();
            for (T val : type.getEnumConstants()) {
                if (val.name().equalsIgnoreCase(name)) {
                    return val;
                }
            }
            return def;
        }, value -> new com.google.gson.JsonPrimitive(value.name().toLowerCase(Locale.ROOT)), null);
    }

    private static ConfigValue<List<String>> stringList(String key, List<String> def, Predicate<String> validator) {
        return new ConfigValue<>(key, def, element -> {
            if (!element.isJsonArray()) {
                return def;
            }
            List<String> values = new ArrayList<>();
            JsonArray array = element.getAsJsonArray();
            for (JsonElement entry : array) {
                if (!entry.isJsonPrimitive()) {
                    continue;
                }
                String value = entry.getAsString();
                if (validator != null && !validator.test(value)) {
                    continue;
                }
                values.add(value);
            }
            return values.isEmpty() ? def : values;
        }, value -> {
            JsonArray array = new JsonArray();
            for (String entry : value) {
                array.add(entry);
            }
            return array;
        }, null);
    }

    private static ConfigValue<List<Integer>> intList(String key, List<Integer> def, Predicate<Integer> validator) {
        return new ConfigValue<>(key, def, element -> {
            if (!element.isJsonArray()) {
                return def;
            }
            List<Integer> values = new ArrayList<>();
            JsonArray array = element.getAsJsonArray();
            for (JsonElement entry : array) {
                if (!entry.isJsonPrimitive()) {
                    continue;
                }
                try {
                    int value = entry.getAsInt();
                    if (validator != null && !validator.test(value)) {
                        continue;
                    }
                    values.add(value);
                } catch (Exception ignored) {
                }
            }
            return values.isEmpty() ? def : values;
        }, value -> {
            JsonArray array = new JsonArray();
            for (Integer entry : value) {
                array.add(entry);
            }
            return array;
        }, null);
    }

    private static ConfigValue<Map<ResourceLocation, FirstAid.InjuryDebuffMode>> injuryDebuffOverridesValue(String key) {
        return new ConfigValue<>(key, new LinkedHashMap<>(), element -> {
            if (element == null || !element.isJsonObject()) {
                return new LinkedHashMap<>();
            }
            JsonObject object = element.getAsJsonObject();
            Map<ResourceLocation, FirstAid.InjuryDebuffMode> values = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id == null || !entry.getValue().isJsonPrimitive()) {
                    continue;
                }
                values.put(id, parseInjuryDebuffMode(entry.getValue().getAsString()));
            }
            return values;
        }, value -> {
            JsonObject object = new JsonObject();
            for (Map.Entry<ResourceLocation, FirstAid.InjuryDebuffMode> entry : value.entrySet()) {
                object.addProperty(entry.getKey().toString(), entry.getValue().name().toLowerCase(Locale.ROOT));
            }
            return object;
        }, null);
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
}

/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParseException
 *  com.google.gson.JsonParser
 *  com.google.gson.JsonPrimitive
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.resources.Identifier
 */
package ichttt.mods.firstaid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import ichttt.mods.firstaid.FirstAid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

public final class FirstAidConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("firstaid");
    public static final Server SERVER = new Server();
    public static final General GENERAL = new General();
    public static final Client CLIENT = new Client();
    public static final boolean watchSetHealth = true;

    private FirstAidConfig() {
    }

    public static void loadServer() {
        FirstAidConfig.loadSection(SERVER, "firstaid-server.json");
    }

    public static void loadGeneral() {
        FirstAidConfig.loadSection(GENERAL, "firstaid-general.json");
    }

    public static void loadClient() {
        FirstAidConfig.loadSection(CLIENT, "firstaid-client.json");
    }

    public static JsonObject serializeServerBundle() {
        JsonObject root = new JsonObject();
        root.add("server", (JsonElement)SERVER.write());
        root.add("general", (JsonElement)GENERAL.write());
        return root;
    }

    public static void applyServerBundle(JsonObject bundle) {
        if (bundle == null) {
            return;
        }
        JsonObject server = bundle.has("server") && bundle.get("server").isJsonObject() ? bundle.getAsJsonObject("server") : new JsonObject();
        JsonObject general = bundle.has("general") && bundle.get("general").isJsonObject() ? bundle.getAsJsonObject("general") : new JsonObject();
        SERVER.read(server);
        GENERAL.read(general);
        FirstAidConfig.applyCommandSettings();
    }

    public static void applyCommandSettings() {
        FirstAid.dynamicPainEnabled = FirstAidConfig.SERVER.dynamicPainEnabled.get();
        FirstAid.lowSuppressionEnabled = FirstAidConfig.SERVER.lowSuppressionEnabled.get();
        FirstAid.rescueWakeUpEnabled = FirstAidConfig.SERVER.rescueWakeUpEnabled.get();
        FirstAid.medicineEffectMode = FirstAidConfig.SERVER.medicineEffectMode.get();
        FirstAid.injuryDebuffMode = FirstAidConfig.SERVER.injuryDebuffMode.get();
        FirstAid.injuryDebuffOverrides.clear();
        FirstAid.injuryDebuffOverrides.putAll(FirstAidConfig.SERVER.injuryDebuffOverrides.get());
    }

    public static void persistCommandSettings() {
        FirstAidConfig.SERVER.dynamicPainEnabled.set(FirstAid.dynamicPainEnabled);
        FirstAidConfig.SERVER.lowSuppressionEnabled.set(FirstAid.lowSuppressionEnabled);
        FirstAidConfig.SERVER.rescueWakeUpEnabled.set(FirstAid.rescueWakeUpEnabled);
        FirstAidConfig.SERVER.medicineEffectMode.set(FirstAid.medicineEffectMode);
        FirstAidConfig.SERVER.injuryDebuffMode.set(FirstAid.injuryDebuffMode);
        FirstAidConfig.SERVER.injuryDebuffOverrides.set(new LinkedHashMap<Identifier, FirstAid.InjuryDebuffMode>(FirstAid.injuryDebuffOverrides));
        FirstAidConfig.saveServer();
    }

    private static void loadSection(ConfigSection section, String fileName) {
        Objects.requireNonNull(section);
        Path file = CONFIG_DIR.resolve(fileName);
        JsonObject data = FirstAidConfig.readJson(file);
        section.read(data);
        FirstAidConfig.writeJson(file, section.write());
    }

    private static JsonObject readJson(Path file) {
        try {
            if (!Files.exists(file, new LinkOption[0])) {
                return new JsonObject();
            }
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                return new JsonObject();
            }
            JsonElement element = JsonParser.parseString((String)raw);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        }
        catch (JsonParseException | IOException e) {
            FirstAid.LOGGER.warn("Failed reading config {}: {}", (Object)file, (Object)e.getMessage());
            return new JsonObject();
        }
    }

    private static void writeJson(Path file, JsonObject data) {
        try {
            Files.createDirectories(CONFIG_DIR, new FileAttribute[0]);
            Files.writeString(file, (CharSequence)GSON.toJson((JsonElement)data), StandardCharsets.UTF_8, new OpenOption[0]);
        }
        catch (IOException e) {
            FirstAid.LOGGER.warn("Failed writing config {}: {}", (Object)file, (Object)e.getMessage());
        }
    }

    private static void saveServer() {
        FirstAidConfig.writeJson(CONFIG_DIR.resolve("firstaid-server.json"), SERVER.write());
    }

    private static ConfigValue<Boolean> boolValue(String key, boolean def) {
        return new ConfigValue<Boolean>(key, def, JsonElement::getAsBoolean, value -> new JsonPrimitive(value), null);
    }

    private static ConfigValue<Integer> intValue(String key, int def, int min, int max) {
        return new ConfigValue<Integer>(key, def, JsonElement::getAsInt, value -> new JsonPrimitive((Number)value), value -> value >= min && value <= max);
    }

    private static ConfigValue<Double> doubleValue(String key, double def, double min, double max) {
        return new ConfigValue<Double>(key, def, JsonElement::getAsDouble, value -> new JsonPrimitive((Number)value), value -> value >= min && value <= max);
    }

    private static <T extends Enum<T>> ConfigValue<T> enumValue(String key, T def, Class<T> type) {
        return new ConfigValue<Enum>(key, def, element -> {
            String name = element.getAsString();
            for (Enum val : (Enum[])type.getEnumConstants()) {
                if (!val.name().equalsIgnoreCase(name)) continue;
                return val;
            }
            return def;
        }, value -> new JsonPrimitive(value.name().toLowerCase(Locale.ROOT)), null);
    }

    private static ConfigValue<List<String>> stringList(String key, List<String> def, Predicate<String> validator) {
        return new ConfigValue<List<String>>(key, def, element -> {
            if (!element.isJsonArray()) {
                return def;
            }
            ArrayList<String> values = new ArrayList<String>();
            JsonArray array = element.getAsJsonArray();
            for (JsonElement entry : array) {
                if (!entry.isJsonPrimitive()) continue;
                String value = entry.getAsString();
                if (validator != null && !validator.test(value)) continue;
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
        return new ConfigValue<List<Integer>>(key, def, element -> {
            if (!element.isJsonArray()) {
                return def;
            }
            ArrayList<Integer> values = new ArrayList<Integer>();
            JsonArray array = element.getAsJsonArray();
            for (JsonElement entry : array) {
                if (!entry.isJsonPrimitive()) continue;
                try {
                    int value = entry.getAsInt();
                    if (validator != null && !validator.test(value)) continue;
                    values.add(value);
                }
                catch (Exception exception) {}
            }
            return values.isEmpty() ? def : values;
        }, value -> {
            JsonArray array = new JsonArray();
            for (Integer entry : value) {
                array.add((Number)entry);
            }
            return array;
        }, null);
    }

    private static ConfigValue<Map<Identifier, FirstAid.InjuryDebuffMode>> injuryDebuffOverridesValue(String key) {
        return new ConfigValue<Map<Identifier, FirstAid.InjuryDebuffMode>>(key, new LinkedHashMap(), element -> {
            if (element == null || !element.isJsonObject()) {
                return new LinkedHashMap();
            }
            JsonObject object = element.getAsJsonObject();
            LinkedHashMap<Identifier, FirstAid.InjuryDebuffMode> values = new LinkedHashMap<Identifier, FirstAid.InjuryDebuffMode>();
            for (Map.Entry entry : object.entrySet()) {
                Identifier id = Identifier.tryParse((String)((String)entry.getKey()));
                if (id == null || !((JsonElement)entry.getValue()).isJsonPrimitive()) continue;
                values.put(id, FirstAidConfig.parseInjuryDebuffMode(((JsonElement)entry.getValue()).getAsString()));
            }
            return values;
        }, value -> {
            JsonObject object = new JsonObject();
            for (Map.Entry entry : value.entrySet()) {
                object.addProperty(((Identifier)entry.getKey()).toString(), ((FirstAid.InjuryDebuffMode)((Object)((Object)entry.getValue()))).name().toLowerCase(Locale.ROOT));
            }
            return object;
        }, null);
    }

    private static FirstAid.InjuryDebuffMode parseInjuryDebuffMode(String raw) {
        if (raw == null) {
            return FirstAid.InjuryDebuffMode.NORMAL;
        }
        for (FirstAid.InjuryDebuffMode mode : FirstAid.InjuryDebuffMode.values()) {
            if (!mode.name().equalsIgnoreCase(raw)) continue;
            return mode;
        }
        return FirstAid.InjuryDebuffMode.NORMAL;
    }

    public static final class Server
    extends ConfigSection {
        public final ConfigValue<Integer> maxHealthHead = this.define(FirstAidConfig.intValue("maxHealthHead", 7, 2, 12));
        public final ConfigValue<Boolean> causeDeathHead;
        public final ConfigValue<Integer> maxHealthLeftArm = this.define(FirstAidConfig.intValue("maxHealthLeftArm", 4, 2, 12));
        public final ConfigValue<Integer> maxHealthLeftLeg = this.define(FirstAidConfig.intValue("maxHealthLeftLeg", 4, 2, 12));
        public final ConfigValue<Integer> maxHealthLeftFoot = this.define(FirstAidConfig.intValue("maxHealthLeftFoot", 4, 2, 12));
        public final ConfigValue<Integer> maxHealthBody = this.define(FirstAidConfig.intValue("maxHealthBody", 11, 2, 12));
        public final ConfigValue<Boolean> causeDeathBody;
        public final ConfigValue<Integer> maxHealthRightArm = this.define(FirstAidConfig.intValue("maxHealthRightArm", 4, 2, 12));
        public final ConfigValue<Integer> maxHealthRightLeg = this.define(FirstAidConfig.intValue("maxHealthRightLeg", 4, 2, 12));
        public final ConfigValue<Integer> maxHealthRightFoot = this.define(FirstAidConfig.intValue("maxHealthRightFoot", 4, 2, 12));
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
        public final ConfigValue<FirstAid.MedicineEffectMode> medicineEffectMode;
        public final ConfigValue<FirstAid.InjuryDebuffMode> injuryDebuffMode;
        public final ConfigValue<Map<Identifier, FirstAid.InjuryDebuffMode>> injuryDebuffOverrides;

        public Server() {
            this.causeDeathHead = this.define(FirstAidConfig.boolValue("causeDeathHead", true));
            this.causeDeathBody = this.define(FirstAidConfig.boolValue("causeDeathBody", true));
            this.headArmorMultiplier = this.define(FirstAidConfig.doubleValue("headArmorMultiplier", 6.0, 1.0, 16.0));
            this.chestArmorMultiplier = this.define(FirstAidConfig.doubleValue("chestArmorMultiplier", 2.5, 1.0, 16.0));
            this.legsArmorMultiplier = this.define(FirstAidConfig.doubleValue("legsArmorMultiplier", 3.0, 1.0, 16.0));
            this.feetArmorMultiplier = this.define(FirstAidConfig.doubleValue("feetArmorMultiplier", 6.0, 1.0, 16.0));
            this.headArmorOffset = this.define(FirstAidConfig.doubleValue("headArmorOffset", 1.0, 0.0, 4.0));
            this.chestArmorOffset = this.define(FirstAidConfig.doubleValue("chestArmorOffset", 0.0, 0.0, 4.0));
            this.legsArmorOffset = this.define(FirstAidConfig.doubleValue("legsArmorOffset", 0.0, 0.0, 4.0));
            this.feetArmorOffset = this.define(FirstAidConfig.doubleValue("feetArmorOffset", 0.0, 0.0, 4.0));
            this.headThoughnessMultiplier = this.define(FirstAidConfig.doubleValue("headThoughnessMultiplier", 4.0, 1.0, 16.0));
            this.chestThoughnessMultiplier = this.define(FirstAidConfig.doubleValue("chestThoughnessMultiplier", 3.0, 1.0, 16.0));
            this.legsThoughnessMultiplier = this.define(FirstAidConfig.doubleValue("legsThoughnessMultiplier", 3.0, 1.0, 16.0));
            this.feetThoughnessMultiplier = this.define(FirstAidConfig.doubleValue("feetThoughnessMultiplier", 3.5, 1.0, 16.0));
            this.headThoughnessOffset = this.define(FirstAidConfig.doubleValue("headThoughnessOffset", 0.0, 0.0, 4.0));
            this.chestThoughnessOffset = this.define(FirstAidConfig.doubleValue("chestThoughnessOffset", 0.0, 0.0, 4.0));
            this.legsThoughnessOffset = this.define(FirstAidConfig.doubleValue("legsThoughnessOffset", 0.0, 0.0, 4.0));
            this.feetThoughnessOffset = this.define(FirstAidConfig.doubleValue("feetThoughnessOffset", 0.0, 0.0, 4.0));
            this.bandage = new IEEntry(this, "bandage", 4, 18, 2500);
            this.plaster = new IEEntry(this, "plaster", 2, 22, 3000);
            this.allowNaturalRegeneration = this.define(FirstAidConfig.boolValue("allowNaturalRegeneration", false));
            this.allowOtherHealingItems = this.define(FirstAidConfig.boolValue("allowOtherHealingItems", true));
            this.sleepHealPercentage = this.define(FirstAidConfig.doubleValue("sleepHealPercentage", 0.07, 0.0, 1.0));
            this.otherRegenMultiplier = this.define(FirstAidConfig.doubleValue("otherRegenMultiplier", 0.75, 0.0, 20.0));
            this.naturalRegenMultiplier = this.define(FirstAidConfig.doubleValue("naturalRegenMultiplier", 0.5, 0.0, 20.0));
            this.resistanceReductionPercentPerLevel = this.define(FirstAidConfig.intValue("resistanceReductionPercentPerLevel", 20, 1, 40));
            this.scaleMaxHealth = this.define(FirstAidConfig.boolValue("scaleMaxHealth", true));
            this.capMaxHealth = this.define(FirstAidConfig.boolValue("capMaxHealth", true));
            this.vanillaHealthCalculation = this.define(FirstAidConfig.enumValue("vanillaHealthCalculation", VanillaHealthCalculationMode.AVERAGE_ALL, VanillaHealthCalculationMode.class));
            this.useFriendlyRandomDistribution = this.define(FirstAidConfig.boolValue("useFriendlyRandomDistribution", false));
            this.armorEnchantmentMode = this.define(FirstAidConfig.enumValue("armorEnchantmentMode", ArmorEnchantmentMode.LOCAL_ENCHANTMENTS, ArmorEnchantmentMode.class));
            this.enchantmentMultiplier = this.define(FirstAidConfig.intValue("enchantmentMultiplier", 4, 1, 4));
            this.enchMulOverrideIdentifiers = this.define(FirstAidConfig.stringList("enchantmentOverrideIdentifiers", Collections.singletonList("minecraft:feather_falling"), value -> !value.isBlank()));
            this.enchMulOverrideMultiplier = this.define(FirstAidConfig.intList("enchantmentOverrideMultiplier", Collections.singletonList(2), value -> value >= 1 && value <= 4));
            this.dynamicPainEnabled = this.define(FirstAidConfig.boolValue("dynamicPainEnabled", true));
            this.lowSuppressionEnabled = this.define(FirstAidConfig.boolValue("lowSuppressionEnabled", false));
            this.rescueWakeUpEnabled = this.define(FirstAidConfig.boolValue("rescueWakeUpEnabled", false));
            this.medicineEffectMode = this.define(FirstAidConfig.enumValue("medicineEffectMode", FirstAid.MedicineEffectMode.REALISTIC, FirstAid.MedicineEffectMode.class));
            this.injuryDebuffMode = this.define(FirstAidConfig.enumValue("injuryDebuffMode", FirstAid.InjuryDebuffMode.NORMAL, FirstAid.InjuryDebuffMode.class));
            this.injuryDebuffOverrides = this.define(FirstAidConfig.injuryDebuffOverridesValue("injuryDebuffOverrides"));
        }

        public static final class IEEntry {
            public final ConfigValue<Integer> totalHeals;
            public final ConfigValue<Integer> secondsPerHeal;
            public final ConfigValue<Integer> applyTime;

            IEEntry(Server owner, String name, int initialTotalHeals, int initialSecondsPerHeal, int initialApplyTime) {
                this.totalHeals = owner.define(FirstAidConfig.intValue(name + "TotalHeals", initialTotalHeals, 1, 127));
                this.secondsPerHeal = owner.define(FirstAidConfig.intValue(name + "SecondsPerHeal", initialSecondsPerHeal, 1, Short.MAX_VALUE));
                this.applyTime = owner.define(FirstAidConfig.intValue(name + "ApplyTime", initialApplyTime, 0, 16000));
            }
        }

        public static enum VanillaHealthCalculationMode {
            AVERAGE_ALL,
            AVERAGE_CRITICAL,
            MIN_CRITICAL,
            CRITICAL_50_PERCENT_OTHER_50_PERCENT;

        }

        public static enum ArmorEnchantmentMode {
            GLOBAL_ENCHANTMENTS,
            LOCAL_ENCHANTMENTS;

        }
    }

    private static abstract class ConfigSection {
        private final Map<String, ConfigValue<?>> values = new LinkedHashMap();

        private ConfigSection() {
        }

        protected final <T> ConfigValue<T> define(ConfigValue<T> value) {
            this.values.put(value.key, value);
            return value;
        }

        protected void read(JsonObject data) {
            for (ConfigValue<?> value : this.values.values()) {
                value.load(data);
            }
        }

        protected JsonObject write() {
            JsonObject root = new JsonObject();
            for (ConfigValue<?> value : this.values.values()) {
                value.write(root);
            }
            return root;
        }
    }

    public static final class General
    extends ConfigSection {
        public final ConfigValue<Boolean> debug = this.define(FirstAidConfig.boolValue("debug", false));
    }

    public static final class Client
    extends ConfigSection {
        public final ConfigValue<VanillaHealthbarMode> vanillaHealthBarMode = this.define(FirstAidConfig.enumValue("vanillaHealthBarMode", VanillaHealthbarMode.HIDE, VanillaHealthbarMode.class));
        public final ConfigValue<OverlayMode> overlayMode = this.define(FirstAidConfig.enumValue("overlayMode", OverlayMode.PLAYER_MODEL, OverlayMode.class));
        public final ConfigValue<Position> pos = this.define(FirstAidConfig.enumValue("overlayPosition", Position.TOP_LEFT, Position.class));
        public final ConfigValue<TooltipMode> armorTooltipMode = this.define(FirstAidConfig.enumValue("armorTooltipMode", TooltipMode.REPLACE, TooltipMode.class));
        public final ConfigValue<Integer> xOffset = this.define(FirstAidConfig.intValue("xOffset", 0, Short.MIN_VALUE, Short.MAX_VALUE));
        public final ConfigValue<Integer> yOffset = this.define(FirstAidConfig.intValue("yOffset", 1, Short.MIN_VALUE, Short.MAX_VALUE));
        public final ConfigValue<Integer> alpha = this.define(FirstAidConfig.intValue("alpha", 50, 0, 200));
        public final ConfigValue<Boolean> enableSounds;
        public final ConfigValue<Boolean> enableEasterEggs;
        public final ConfigValue<Integer> visibleDurationTicks = this.define(FirstAidConfig.intValue("visibleDurationTicks", -1, -1, 600));
        public final ConfigValue<Boolean> flash = this.define(FirstAidConfig.boolValue("flash", true));

        public Client() {
            this.enableSounds = this.define(FirstAidConfig.boolValue("enableSoundSystem", true));
            this.enableEasterEggs = this.define(FirstAidConfig.boolValue("enableEasterEggs", true));
        }

        public static enum VanillaHealthbarMode {
            NORMAL,
            HIGHLIGHT_CRITICAL_PATH,
            HIDE;

        }

        public static enum OverlayMode {
            OFF,
            NUMBERS,
            HEARTS,
            PLAYER_MODEL,
            PLAYER_MODEL_4_COLORS;


            public boolean isPlayerModel() {
                return this == PLAYER_MODEL || this == PLAYER_MODEL_4_COLORS;
            }
        }

        public static enum Position {
            TOP_LEFT,
            TOP_RIGHT,
            BOTTOM_RIGHT,
            BOTTOM_LEFT;

        }

        public static enum TooltipMode {
            REPLACE,
            APPEND,
            NONE;

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
            return this.value;
        }

        public void set(T value) {
            this.value = Objects.requireNonNull(value);
        }

        private void load(JsonObject data) {
            if (data == null || !data.has(this.key)) {
                this.value = this.defaultValue;
                return;
            }
            try {
                T parsed = this.parser.apply(data.get(this.key));
                if (this.validator != null && !this.validator.test(parsed)) {
                    FirstAid.LOGGER.warn("Config value {} out of range, resetting to default", (Object)this.key);
                    this.value = this.defaultValue;
                } else {
                    this.value = parsed;
                }
            }
            catch (Exception e) {
                FirstAid.LOGGER.warn("Failed parsing config value {}: {}", (Object)this.key, (Object)e.getMessage());
                this.value = this.defaultValue;
            }
        }

        private void write(JsonObject data) {
            data.add(this.key, this.serializer.apply(this.value));
        }
    }
}


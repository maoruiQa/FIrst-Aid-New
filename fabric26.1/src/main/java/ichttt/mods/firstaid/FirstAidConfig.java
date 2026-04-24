package ichttt.mods.firstaid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

public final class FirstAidConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("firstaid");
   private static final int LEGACY_BANDAGE_APPLY_TIME = 2500;
   private static final int BANDAGE_APPLY_TIME = 3000;
   public static final FirstAidConfig.Server SERVER = new FirstAidConfig.Server();
   public static final FirstAidConfig.General GENERAL = new FirstAidConfig.General();
   public static final FirstAidConfig.Client CLIENT = new FirstAidConfig.Client();
   public static final boolean watchSetHealth = true;

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
      if (bundle != null) {
         JsonObject server = bundle.has("server") && bundle.get("server").isJsonObject() ? bundle.getAsJsonObject("server") : new JsonObject();
         JsonObject general = bundle.has("general") && bundle.get("general").isJsonObject() ? bundle.getAsJsonObject("general") : new JsonObject();
         SERVER.read(server);
         GENERAL.read(general);
         applyCommandSettings();
      }
   }

   public static void applyCommandSettings() {
      FirstAid.dynamicPainEnabled = SERVER.dynamicPainEnabled.get();
      FirstAid.lowSuppressionEnabled = SERVER.lowSuppressionEnabled.get();
      FirstAid.rescueWakeUpEnabled = SERVER.rescueWakeUpEnabled.get();
      FirstAid.rescueWakeUpDelaySeconds = SERVER.rescueWakeUpDelaySeconds.get();
      FirstAid.naturalRegenMode = SERVER.naturalRegenMode.get();
      FirstAid.naturalRegenStrategy = SERVER.naturalRegenStrategy.get();
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
      SERVER.naturalRegenMode.set(FirstAid.naturalRegenMode);
      SERVER.naturalRegenStrategy.set(FirstAid.naturalRegenStrategy);
      SERVER.allowNaturalRegeneration.set(FirstAid.naturalRegenMode != FirstAid.NaturalRegenMode.OFF);
      SERVER.medicineEffectMode.set(FirstAid.medicineEffectMode);
      SERVER.injuryDebuffMode.set(FirstAid.injuryDebuffMode);
      SERVER.injuryDebuffOverrides.set(new LinkedHashMap<>(FirstAid.injuryDebuffOverrides));
      saveServer();
   }

   private static void loadSection(FirstAidConfig.ConfigSection section, String fileName) {
      Objects.requireNonNull(section);
      Path file = CONFIG_DIR.resolve(fileName);
      JsonObject data = readJson(file);
      section.read(data);
      if (section == SERVER) {
         migrateLegacyBandageApplyTime();
      }
      writeJson(file, section.write());
   }

   private static void migrateLegacyBandageApplyTime() {
      if (SERVER.bandage.applyTime.get() == LEGACY_BANDAGE_APPLY_TIME) {
         SERVER.bandage.applyTime.set(BANDAGE_APPLY_TIME);
      }
   }

   private static JsonObject readJson(Path file) {
      try {
         if (!Files.exists(file)) {
            return new JsonObject();
         } else {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            if (raw.isBlank()) {
               return new JsonObject();
            } else {
               JsonElement element = JsonParser.parseString(raw);
               return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
            }
         }
      } catch (JsonParseException | IOException var3) {
         FirstAid.LOGGER.warn("Failed reading config {}: {}", file, var3.getMessage());
         return new JsonObject();
      }
   }

   private static void writeJson(Path file, JsonObject data) {
      try {
         Files.createDirectories(CONFIG_DIR);
         Files.writeString(file, GSON.toJson(data), StandardCharsets.UTF_8);
      } catch (IOException var3) {
         FirstAid.LOGGER.warn("Failed writing config {}: {}", file, var3.getMessage());
      }
   }

   private static void saveServer() {
      writeJson(CONFIG_DIR.resolve("firstaid-server.json"), SERVER.write());
   }

   private static FirstAidConfig.ConfigValue<Boolean> boolValue(String key, boolean def) {
      return new FirstAidConfig.ConfigValue<>(key, def, JsonElement::getAsBoolean, value -> new JsonPrimitive(value), null);
   }

   private static FirstAidConfig.ConfigValue<Integer> intValue(String key, int def, int min, int max) {
      return new FirstAidConfig.ConfigValue<>(key, def, JsonElement::getAsInt, value -> new JsonPrimitive(value), value -> value >= min && value <= max);
   }

   private static FirstAidConfig.ConfigValue<Double> doubleValue(String key, double def, double min, double max) {
      return new FirstAidConfig.ConfigValue<>(key, def, JsonElement::getAsDouble, value -> new JsonPrimitive(value), value -> value >= min && value <= max);
   }

   private static <T extends Enum<T>> FirstAidConfig.ConfigValue<T> enumValue(String key, T def, Class<T> type) {
      return new FirstAidConfig.ConfigValue<>(key, def, element -> {
         String name = element.getAsString();

         for (T val : type.getEnumConstants()) {
            if (val.name().equalsIgnoreCase(name)) {
               return val;
            }
         }

         return def;
      }, value -> new JsonPrimitive(value.name().toLowerCase(Locale.ROOT)), null);
   }

   private static FirstAidConfig.ConfigValue<List<String>> stringList(String key, List<String> def, Predicate<String> validator) {
      return new FirstAidConfig.ConfigValue<>(key, def, element -> {
         if (!element.isJsonArray()) {
            return def;
         } else {
            List<String> values = new ArrayList<>();

            for (JsonElement entry : element.getAsJsonArray()) {
               if (entry.isJsonPrimitive()) {
                  String value = entry.getAsString();
                  if (validator == null || validator.test(value)) {
                     values.add(value);
                  }
               }
            }

            return values.isEmpty() ? def : values;
         }
      }, value -> {
         JsonArray array = new JsonArray();

         for (String entry : value) {
            array.add(entry);
         }

         return array;
      }, null);
   }

   private static FirstAidConfig.ConfigValue<List<Integer>> intList(String key, List<Integer> def, Predicate<Integer> validator) {
      return new FirstAidConfig.ConfigValue<>(key, def, element -> {
         if (!element.isJsonArray()) {
            return def;
         } else {
            List<Integer> values = new ArrayList<>();

            for (JsonElement entry : element.getAsJsonArray()) {
               if (entry.isJsonPrimitive()) {
                  try {
                     int value = entry.getAsInt();
                     if (validator == null || validator.test(value)) {
                        values.add(value);
                     }
                  } catch (Exception var8) {
                  }
               }
            }

            return values.isEmpty() ? def : values;
         }
      }, value -> {
         JsonArray array = new JsonArray();

         for (Integer entry : value) {
            array.add(entry);
         }

         return array;
      }, null);
   }

   private static FirstAidConfig.ConfigValue<Map<Identifier, FirstAid.InjuryDebuffMode>> injuryDebuffOverridesValue(String key) {
      return new FirstAidConfig.ConfigValue<>(key, new LinkedHashMap<>(), element -> {
         if (element != null && element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            Map<Identifier, FirstAid.InjuryDebuffMode> values = new LinkedHashMap<>();

            for (Entry<String, JsonElement> entry : object.entrySet()) {
               Identifier id = Identifier.tryParse(entry.getKey());
               if (id != null && entry.getValue().isJsonPrimitive()) {
                  values.put(id, parseInjuryDebuffMode(entry.getValue().getAsString()));
               }
            }

            return values;
         } else {
            return new LinkedHashMap<>();
         }
      }, value -> {
         JsonObject object = new JsonObject();

         for (Entry<Identifier, FirstAid.InjuryDebuffMode> entry : value.entrySet()) {
            object.addProperty(entry.getKey().toString(), entry.getValue().name().toLowerCase(Locale.ROOT));
         }

         return object;
      }, null);
   }

   private static FirstAid.InjuryDebuffMode parseInjuryDebuffMode(String raw) {
      if (raw == null) {
         return FirstAid.InjuryDebuffMode.NORMAL;
      } else {
         for (FirstAid.InjuryDebuffMode mode : FirstAid.InjuryDebuffMode.values()) {
            if (mode.name().equalsIgnoreCase(raw)) {
               return mode;
            }
         }

         return FirstAid.InjuryDebuffMode.NORMAL;
      }
   }

   public static final class Client extends FirstAidConfig.ConfigSection {
      public final FirstAidConfig.ConfigValue<FirstAidConfig.Client.VanillaHealthbarMode> vanillaHealthBarMode = this.define(
         FirstAidConfig.enumValue("vanillaHealthBarMode", FirstAidConfig.Client.VanillaHealthbarMode.HIDE, FirstAidConfig.Client.VanillaHealthbarMode.class)
      );
      public final FirstAidConfig.ConfigValue<FirstAidConfig.Client.OverlayMode> overlayMode = this.define(
         FirstAidConfig.enumValue("overlayMode", FirstAidConfig.Client.OverlayMode.PLAYER_MODEL, FirstAidConfig.Client.OverlayMode.class)
      );
      public final FirstAidConfig.ConfigValue<FirstAidConfig.Client.Position> pos = this.define(
         FirstAidConfig.enumValue("overlayPosition", FirstAidConfig.Client.Position.TOP_LEFT, FirstAidConfig.Client.Position.class)
      );
      public final FirstAidConfig.ConfigValue<FirstAidConfig.Client.TooltipMode> armorTooltipMode = this.define(
         FirstAidConfig.enumValue("armorTooltipMode", FirstAidConfig.Client.TooltipMode.REPLACE, FirstAidConfig.Client.TooltipMode.class)
      );
      public final FirstAidConfig.ConfigValue<Integer> xOffset = this.define(FirstAidConfig.intValue("xOffset", 0, -32768, 32767));
      public final FirstAidConfig.ConfigValue<Integer> yOffset = this.define(FirstAidConfig.intValue("yOffset", 1, -32768, 32767));
      public final FirstAidConfig.ConfigValue<Integer> alpha = this.define(FirstAidConfig.intValue("alpha", 50, 0, 200));
      public final FirstAidConfig.ConfigValue<Boolean> enableSounds;
      public final FirstAidConfig.ConfigValue<Boolean> enableEasterEggs;
      public final FirstAidConfig.ConfigValue<Integer> visibleDurationTicks = this.define(FirstAidConfig.intValue("visibleDurationTicks", -1, -1, 600));
      public final FirstAidConfig.ConfigValue<Boolean> flash = this.define(FirstAidConfig.boolValue("flash", true));

      public Client() {
         this.enableSounds = this.define(FirstAidConfig.boolValue("enableSoundSystem", true));
         this.enableEasterEggs = this.define(FirstAidConfig.boolValue("enableEasterEggs", true));
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

      public static enum VanillaHealthbarMode {
         NORMAL,
         HIGHLIGHT_CRITICAL_PATH,
         HIDE;
      }
   }

   private abstract static class ConfigSection {
      private final Map<String, FirstAidConfig.ConfigValue<?>> values = new LinkedHashMap<>();

      protected final <T> FirstAidConfig.ConfigValue<T> define(FirstAidConfig.ConfigValue<T> value) {
         this.values.put(value.key, value);
         return value;
      }

      protected void read(JsonObject data) {
         for (FirstAidConfig.ConfigValue<?> value : this.values.values()) {
            value.load(data);
         }
      }

      protected JsonObject write() {
         JsonObject root = new JsonObject();

         for (FirstAidConfig.ConfigValue<?> value : this.values.values()) {
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
         return this.value;
      }

      public void set(T value) {
         this.value = Objects.requireNonNull(value);
      }

      private void load(JsonObject data) {
         if (data != null && data.has(this.key)) {
            try {
               T parsed = this.parser.apply(data.get(this.key));
               if (this.validator != null && !this.validator.test(parsed)) {
                  FirstAid.LOGGER.warn("Config value {} out of range, resetting to default", this.key);
                  this.value = this.defaultValue;
               } else {
                  this.value = parsed;
               }
            } catch (Exception var3) {
               FirstAid.LOGGER.warn("Failed parsing config value {}: {}", this.key, var3.getMessage());
               this.value = this.defaultValue;
            }
         } else {
            this.value = this.defaultValue;
         }
      }

      private void write(JsonObject data) {
         data.add(this.key, this.serializer.apply(this.value));
      }
   }

   public static final class General extends FirstAidConfig.ConfigSection {
      public final FirstAidConfig.ConfigValue<Boolean> debug = this.define(FirstAidConfig.boolValue("debug", false));
   }

   public static final class Server extends FirstAidConfig.ConfigSection {
      public final FirstAidConfig.ConfigValue<Integer> maxHealthHead = this.define(FirstAidConfig.intValue("maxHealthHead", 7, 2, 12));
      public final FirstAidConfig.ConfigValue<Boolean> causeDeathHead;
      public final FirstAidConfig.ConfigValue<Integer> maxHealthLeftArm = this.define(FirstAidConfig.intValue("maxHealthLeftArm", 4, 2, 12));
      public final FirstAidConfig.ConfigValue<Integer> maxHealthLeftLeg = this.define(FirstAidConfig.intValue("maxHealthLeftLeg", 4, 2, 12));
      public final FirstAidConfig.ConfigValue<Integer> maxHealthLeftFoot = this.define(FirstAidConfig.intValue("maxHealthLeftFoot", 4, 2, 12));
      public final FirstAidConfig.ConfigValue<Integer> maxHealthBody = this.define(FirstAidConfig.intValue("maxHealthBody", 11, 2, 12));
      public final FirstAidConfig.ConfigValue<Boolean> causeDeathBody;
      public final FirstAidConfig.ConfigValue<Integer> maxHealthRightArm = this.define(FirstAidConfig.intValue("maxHealthRightArm", 4, 2, 12));
      public final FirstAidConfig.ConfigValue<Integer> maxHealthRightLeg = this.define(FirstAidConfig.intValue("maxHealthRightLeg", 4, 2, 12));
      public final FirstAidConfig.ConfigValue<Integer> maxHealthRightFoot = this.define(FirstAidConfig.intValue("maxHealthRightFoot", 4, 2, 12));
      public final FirstAidConfig.ConfigValue<Double> headArmorMultiplier;
      public final FirstAidConfig.ConfigValue<Double> chestArmorMultiplier;
      public final FirstAidConfig.ConfigValue<Double> legsArmorMultiplier;
      public final FirstAidConfig.ConfigValue<Double> feetArmorMultiplier;
      public final FirstAidConfig.ConfigValue<Double> headArmorOffset;
      public final FirstAidConfig.ConfigValue<Double> chestArmorOffset;
      public final FirstAidConfig.ConfigValue<Double> legsArmorOffset;
      public final FirstAidConfig.ConfigValue<Double> feetArmorOffset;
      public final FirstAidConfig.ConfigValue<Double> headThoughnessMultiplier;
      public final FirstAidConfig.ConfigValue<Double> chestThoughnessMultiplier;
      public final FirstAidConfig.ConfigValue<Double> legsThoughnessMultiplier;
      public final FirstAidConfig.ConfigValue<Double> feetThoughnessMultiplier;
      public final FirstAidConfig.ConfigValue<Double> headThoughnessOffset;
      public final FirstAidConfig.ConfigValue<Double> chestThoughnessOffset;
      public final FirstAidConfig.ConfigValue<Double> legsThoughnessOffset;
      public final FirstAidConfig.ConfigValue<Double> feetThoughnessOffset;
      public final FirstAidConfig.Server.IEEntry bandage;
      public final FirstAidConfig.Server.IEEntry plaster;
      public final FirstAidConfig.ConfigValue<Boolean> allowNaturalRegeneration;
      public final FirstAidConfig.ConfigValue<Boolean> allowOtherHealingItems;
      public final FirstAidConfig.ConfigValue<Double> sleepHealPercentage;
      public final FirstAidConfig.ConfigValue<Double> otherRegenMultiplier;
      public final FirstAidConfig.ConfigValue<Double> naturalRegenMultiplier;
      public final FirstAidConfig.ConfigValue<FirstAid.NaturalRegenMode> naturalRegenMode;
      public final FirstAidConfig.ConfigValue<FirstAid.NaturalRegenStrategy> naturalRegenStrategy;
      public final FirstAidConfig.ConfigValue<Integer> resistanceReductionPercentPerLevel;
      public final FirstAidConfig.ConfigValue<Boolean> scaleMaxHealth;
      public final FirstAidConfig.ConfigValue<Boolean> capMaxHealth;
      public final FirstAidConfig.ConfigValue<FirstAidConfig.Server.VanillaHealthCalculationMode> vanillaHealthCalculation;
      public final FirstAidConfig.ConfigValue<Boolean> useFriendlyRandomDistribution;
      public final FirstAidConfig.ConfigValue<FirstAidConfig.Server.ArmorEnchantmentMode> armorEnchantmentMode;
      public final FirstAidConfig.ConfigValue<Integer> enchantmentMultiplier;
      public final FirstAidConfig.ConfigValue<List<String>> enchMulOverrideIdentifiers;
      public final FirstAidConfig.ConfigValue<List<Integer>> enchMulOverrideMultiplier;
      public final FirstAidConfig.ConfigValue<Boolean> dynamicPainEnabled;
      public final FirstAidConfig.ConfigValue<Boolean> lowSuppressionEnabled;
      public final FirstAidConfig.ConfigValue<Boolean> rescueWakeUpEnabled;
      public final FirstAidConfig.ConfigValue<Double> rescueWakeUpDelaySeconds;
      public final FirstAidConfig.ConfigValue<FirstAid.MedicineEffectMode> medicineEffectMode;
      public final FirstAidConfig.ConfigValue<FirstAid.InjuryDebuffMode> injuryDebuffMode;
      public final FirstAidConfig.ConfigValue<Map<Identifier, FirstAid.InjuryDebuffMode>> injuryDebuffOverrides;

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
         this.bandage = new FirstAidConfig.Server.IEEntry(this, "bandage", 4, 18, 3000);
         this.plaster = new FirstAidConfig.Server.IEEntry(this, "plaster", 2, 22, 3000);
         this.allowNaturalRegeneration = this.define(FirstAidConfig.boolValue("allowNaturalRegeneration", false));
         this.allowOtherHealingItems = this.define(FirstAidConfig.boolValue("allowOtherHealingItems", true));
         this.sleepHealPercentage = this.define(FirstAidConfig.doubleValue("sleepHealPercentage", 0.07, 0.0, 1.0));
         this.otherRegenMultiplier = this.define(FirstAidConfig.doubleValue("otherRegenMultiplier", 0.75, 0.0, 20.0));
         this.naturalRegenMultiplier = this.define(FirstAidConfig.doubleValue("naturalRegenMultiplier", 0.5, 0.0, 20.0));
         this.naturalRegenMode = this.define(FirstAidConfig.enumValue("naturalRegenMode", FirstAid.NaturalRegenMode.LIMITED, FirstAid.NaturalRegenMode.class));
         this.naturalRegenStrategy = this.define(FirstAidConfig.enumValue("naturalRegenStrategy", FirstAid.NaturalRegenStrategy.CRITICAL, FirstAid.NaturalRegenStrategy.class));
         this.resistanceReductionPercentPerLevel = this.define(FirstAidConfig.intValue("resistanceReductionPercentPerLevel", 20, 1, 40));
         this.scaleMaxHealth = this.define(FirstAidConfig.boolValue("scaleMaxHealth", true));
         this.capMaxHealth = this.define(FirstAidConfig.boolValue("capMaxHealth", true));
         this.vanillaHealthCalculation = this.define(
            FirstAidConfig.enumValue(
               "vanillaHealthCalculation",
               FirstAidConfig.Server.VanillaHealthCalculationMode.AVERAGE_ALL,
               FirstAidConfig.Server.VanillaHealthCalculationMode.class
            )
         );
         this.useFriendlyRandomDistribution = this.define(FirstAidConfig.boolValue("useFriendlyRandomDistribution", false));
         this.armorEnchantmentMode = this.define(
            FirstAidConfig.enumValue(
               "armorEnchantmentMode", FirstAidConfig.Server.ArmorEnchantmentMode.LOCAL_ENCHANTMENTS, FirstAidConfig.Server.ArmorEnchantmentMode.class
            )
         );
         this.enchantmentMultiplier = this.define(FirstAidConfig.intValue("enchantmentMultiplier", 4, 1, 4));
         this.enchMulOverrideIdentifiers = this.define(
            FirstAidConfig.stringList("enchantmentOverrideIdentifiers", Collections.singletonList("minecraft:feather_falling"), value -> !value.isBlank())
         );
         this.enchMulOverrideMultiplier = this.define(
            FirstAidConfig.intList("enchantmentOverrideMultiplier", Collections.singletonList(2), value -> value >= 1 && value <= 4)
         );
         this.dynamicPainEnabled = this.define(FirstAidConfig.boolValue("dynamicPainEnabled", true));
         this.lowSuppressionEnabled = this.define(FirstAidConfig.boolValue("lowSuppressionEnabled", false));
         this.rescueWakeUpEnabled = this.define(FirstAidConfig.boolValue("rescueWakeUpEnabled", false));
         this.rescueWakeUpDelaySeconds = this.define(
            FirstAidConfig.doubleValue("rescueWakeUpDelaySeconds", FirstAid.DEFAULT_RESCUE_WAKE_UP_DELAY_SECONDS, 0.0, 3600.0)
         );
         this.medicineEffectMode = this.define(
            FirstAidConfig.enumValue("medicineEffectMode", FirstAid.MedicineEffectMode.REALISTIC, FirstAid.MedicineEffectMode.class)
         );
         this.injuryDebuffMode = this.define(FirstAidConfig.enumValue("injuryDebuffMode", FirstAid.InjuryDebuffMode.NORMAL, FirstAid.InjuryDebuffMode.class));
         this.injuryDebuffOverrides = this.define(FirstAidConfig.injuryDebuffOverridesValue("injuryDebuffOverrides"));
      }

      public static enum ArmorEnchantmentMode {
         GLOBAL_ENCHANTMENTS,
         LOCAL_ENCHANTMENTS;
      }

      public static final class IEEntry {
         public final FirstAidConfig.ConfigValue<Integer> totalHeals;
         public final FirstAidConfig.ConfigValue<Integer> secondsPerHeal;
         public final FirstAidConfig.ConfigValue<Integer> applyTime;

         IEEntry(FirstAidConfig.Server owner, String name, int initialTotalHeals, int initialSecondsPerHeal, int initialApplyTime) {
            this.totalHeals = owner.define(FirstAidConfig.intValue(name + "TotalHeals", initialTotalHeals, 1, 127));
            this.secondsPerHeal = owner.define(FirstAidConfig.intValue(name + "SecondsPerHeal", initialSecondsPerHeal, 1, 32767));
            this.applyTime = owner.define(FirstAidConfig.intValue(name + "ApplyTime", initialApplyTime, 0, 16000));
         }
      }

      public static enum VanillaHealthCalculationMode {
         AVERAGE_ALL,
         AVERAGE_CRITICAL,
         MIN_CRITICAL,
         CRITICAL_50_PERCENT_OTHER_50_PERCENT;
      }
   }
}

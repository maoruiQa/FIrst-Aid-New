package ichttt.mods.firstaid;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2378;
import net.minecraft.class_2960;
import net.minecraft.class_5321;
import net.minecraft.class_7706;
import net.minecraft.class_7923;
import net.minecraft.class_7924;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FirstAid {
    public static final String MODID = "firstaid";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public static final class_1792 BANDAGE = registerItem("bandage");
    public static final class_1792 PLASTER = registerItem("plaster");
    public static final class_1792 MORPHINE = registerItem("morphine");

    private FirstAid() {
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(class_7706.field_41062).register(entries -> {
            entries.method_45420(new class_1799(BANDAGE));
            entries.method_45420(new class_1799(PLASTER));
            entries.method_45420(new class_1799(MORPHINE));
        });
    }

    private static class_1792 registerItem(String path) {
        class_2960 id = class_2960.method_60655(MODID, path);
        class_1792.class_1793 properties = new class_1792.class_1793().method_63686(class_5321.method_29179(class_7924.field_41197, id));
        return class_2378.method_10230(class_7923.field_41178, id, new class_1792(properties));
    }
}

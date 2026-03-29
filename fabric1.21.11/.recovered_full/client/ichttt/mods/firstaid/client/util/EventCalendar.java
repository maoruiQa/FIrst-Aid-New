/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ichttt.mods.firstaid.FirstAidConfig
 */
package ichttt.mods.firstaid.client.util;

import ichttt.mods.firstaid.FirstAidConfig;
import java.util.Calendar;

public class EventCalendar {
    public static final boolean disableEasterEggs = Boolean.parseBoolean(System.getProperty("firstaid.disableEasterEgg", "false"));
    private static boolean isNewYear;
    private static boolean isValentinesDay;
    private static boolean isAFDay;
    private static boolean isHalloween;
    private static boolean isChristmas;
    public static int day;

    public static void checkDate() {
        if (disableEasterEggs) {
            return;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        day = calendar.get(5);
        switch (calendar.get(2)) {
            case 0: {
                if (day != 1) break;
                isNewYear = true;
                break;
            }
            case 1: {
                if (day != 14) break;
                isValentinesDay = true;
                break;
            }
            case 3: {
                if (day != 1) break;
                isAFDay = true;
                break;
            }
            case 9: {
                if (day != 31) break;
                isHalloween = true;
                break;
            }
            case 11: {
                if (day != 25) break;
                isChristmas = true;
            }
        }
    }

    public static boolean isGuiFun() {
        return (isHalloween || isAFDay) && (Boolean)FirstAidConfig.CLIENT.enableEasterEggs.get() != false;
    }

    public static boolean isNewYear() {
        return isNewYear;
    }

    public static boolean isValentinesDay() {
        return isValentinesDay;
    }

    public static boolean isAFDay() {
        return isAFDay;
    }

    public static boolean isHalloween() {
        return isHalloween;
    }

    public static boolean isChristmas() {
        return isChristmas;
    }
}


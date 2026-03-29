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
      if (!disableEasterEggs) {
         Calendar calendar = Calendar.getInstance();
         calendar.setTimeInMillis(System.currentTimeMillis());
         day = calendar.get(5);
         switch (calendar.get(2)) {
            case 0:
               if (day == 1) {
                  isNewYear = true;
               }
               break;
            case 1:
               if (day == 14) {
                  isValentinesDay = true;
               }
            case 2:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 10:
            default:
               break;
            case 3:
               if (day == 1) {
                  isAFDay = true;
               }
               break;
            case 9:
               if (day == 31) {
                  isHalloween = true;
               }
               break;
            case 11:
               if (day == 25) {
                  isChristmas = true;
               }
         }
      }
   }

   public static boolean isGuiFun() {
      return (isHalloween || isAFDay) && (Boolean)FirstAidConfig.CLIENT.enableEasterEggs.get();
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

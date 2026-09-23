package ichttt.mods.firstaid.common.compat.playerrevive;

import ichttt.mods.firstaid.FirstAid;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

public class PRPresentCompatHandler implements IPRCompatHandler {
   private static final String PLAYER_REVIVE_SERVER_CLASS = "team.creative.playerrevive.server.PlayerReviveServer";
   private static final String BLEEDING_CLASS = "team.creative.playerrevive.api.IBleeding";
   @Nullable
   private static final Method GET_BLEEDING_METHOD = findGetBleedingMethod();
   @Nullable
   private static final Class<?> RESOLVED_BLEEDING_CLASS = findBleedingClass();
   @Nullable
   private static final Method IS_BLEEDING_METHOD = findBleedingMethod("isBleeding");
   @Nullable
   private static final Method KNOCK_OUT_METHOD = findBleedingMethod("knockOut", Player.class, DamageSource.class);
   @Nullable
   private static final Method TIME_LEFT_METHOD = findBleedingMethod("timeLeft");
   private static boolean loggedFailure;

   public static boolean canUse() {
      return GET_BLEEDING_METHOD != null && RESOLVED_BLEEDING_CLASS != null && IS_BLEEDING_METHOD != null && KNOCK_OUT_METHOD != null;
   }

   @Override
   public boolean tryKnockOutPlayer(Player player, DamageSource source) {
      Object bleeding = getBleeding(player);
      if (bleeding == null || KNOCK_OUT_METHOD == null) {
         return false;
      }

      try {
         KNOCK_OUT_METHOD.invoke(bleeding, player, source);
         return true;
      } catch (IllegalAccessException | InvocationTargetException e) {
         logCompatFailure("start a PlayerRevive knockout", e);
         return false;
      }
   }

   @Override
   public boolean isBleeding(Player player) {
      Object bleeding = getBleeding(player);
      if (bleeding == null || IS_BLEEDING_METHOD == null) {
         return false;
      }

      try {
         boolean isBleeding = (Boolean)IS_BLEEDING_METHOD.invoke(bleeding);
         if (!isBleeding) {
            return false;
         }

         if (TIME_LEFT_METHOD == null) {
            return true;
         }

         return (Integer)TIME_LEFT_METHOD.invoke(bleeding) > 0;
      } catch (IllegalAccessException | InvocationTargetException e) {
         logCompatFailure("read PlayerRevive bleeding state", e);
         return false;
      }
   }

   @Nullable
   private static Object getBleeding(Player player) {
      if (GET_BLEEDING_METHOD == null) {
         return null;
      }

      try {
         return GET_BLEEDING_METHOD.invoke(null, player);
      } catch (IllegalAccessException | InvocationTargetException e) {
         logCompatFailure("resolve PlayerRevive bleeding data", e);
         return null;
      }
   }

   @Nullable
   private static Method findGetBleedingMethod() {
      try {
         return Class.forName(PLAYER_REVIVE_SERVER_CLASS).getMethod("getBleeding", Player.class);
      } catch (ReflectiveOperationException e) {
         return null;
      }
   }

   @Nullable
   private static Class<?> findBleedingClass() {
      try {
         return Class.forName(BLEEDING_CLASS);
      } catch (ReflectiveOperationException e) {
         return null;
      }
   }

   @Nullable
   private static Method findBleedingMethod(String name, Class<?>... parameterTypes) {
      if (RESOLVED_BLEEDING_CLASS == null) {
         return null;
      }

      try {
         return RESOLVED_BLEEDING_CLASS.getMethod(name, parameterTypes);
      } catch (ReflectiveOperationException e) {
         return null;
      }
   }

   private static void logCompatFailure(String action, Exception exception) {
      if (!loggedFailure) {
         loggedFailure = true;
         FirstAid.LOGGER.warn("Failed to {} through PlayerRevive compatibility. Falling back to vanilla handling.", action, exception);
      }
   }
}

package ichttt.mods.firstaid.api.enums;

import java.util.Locale;
import javax.annotation.Nonnull;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum EnumDebuffSlot implements StringRepresentable {
   HEAD(EnumPlayerPart.HEAD),
   BODY(EnumPlayerPart.BODY),
   ARMS(EnumPlayerPart.LEFT_ARM, EnumPlayerPart.RIGHT_ARM),
   LEGS_AND_FEET(EnumPlayerPart.LEFT_LEG, EnumPlayerPart.RIGHT_LEG, EnumPlayerPart.LEFT_FOOT, EnumPlayerPart.RIGHT_FOOT);

   @Nonnull
   public final EnumPlayerPart[] playerParts;
   private final String serializedName;

   private EnumDebuffSlot(@Nonnull EnumPlayerPart... playerParts) {
      this.playerParts = playerParts;
      this.serializedName = this.name().toLowerCase(Locale.ROOT);
   }

   @NotNull
   public String getSerializedName() {
      return this.serializedName;
   }
}

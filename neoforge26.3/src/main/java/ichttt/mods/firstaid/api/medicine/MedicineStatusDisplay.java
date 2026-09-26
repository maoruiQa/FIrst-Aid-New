package ichttt.mods.firstaid.api.medicine;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class MedicineStatusDisplay {
   private final Identifier statusId;
   private final Component text;
   @Nullable
   private final Identifier iconTexture;
   private final int color;

   public MedicineStatusDisplay(Identifier statusId, Component text, @Nullable Identifier iconTexture, int color) {
      this.statusId = statusId;
      this.text = text;
      this.iconTexture = iconTexture;
      this.color = color;
   }

   @Nonnull
   public Identifier getStatusId() {
      return this.statusId;
   }

   @Nonnull
   public Component getText() {
      return this.text;
   }

   @Nullable
   public Identifier getIconTexture() {
      return this.iconTexture;
   }

   public int getColor() {
      return this.color;
   }
}

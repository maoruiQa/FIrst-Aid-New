package ichttt.mods.firstaid.api.medicine;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class MedicineStatusDisplay {
   private final ResourceLocation statusId;
   private final Component text;
   @Nullable
   private final ResourceLocation iconTexture;
   private final int color;

   public MedicineStatusDisplay(ResourceLocation statusId, Component text, @Nullable ResourceLocation iconTexture, int color) {
      this.statusId = statusId;
      this.text = text;
      this.iconTexture = iconTexture;
      this.color = color;
   }

   @Nonnull
   public ResourceLocation getStatusId() {
      return this.statusId;
   }

   @Nonnull
   public Component getText() {
      return this.text;
   }

   @Nullable
   public ResourceLocation getIconTexture() {
      return this.iconTexture;
   }

   public int getColor() {
      return this.color;
   }
}

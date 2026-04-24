/*
 * FirstAid API
 * Copyright (c) 2017-2024
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

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

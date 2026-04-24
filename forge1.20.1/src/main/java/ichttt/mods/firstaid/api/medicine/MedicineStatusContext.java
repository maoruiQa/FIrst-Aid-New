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

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class MedicineStatusContext {
   private final Player player;
   private final Level level;
   @Nullable
   private final AbstractPlayerDamageModel damageModel;

   public MedicineStatusContext(Player player, Level level, @Nullable AbstractPlayerDamageModel damageModel) {
      this.player = player;
      this.level = level;
      this.damageModel = damageModel;
   }

   @Nonnull
   public Player getPlayer() {
      return this.player;
   }

   @Nonnull
   public Level getLevel() {
      return this.level;
   }

   @Nullable
   public AbstractPlayerDamageModel getDamageModel() {
      return this.damageModel;
   }
}

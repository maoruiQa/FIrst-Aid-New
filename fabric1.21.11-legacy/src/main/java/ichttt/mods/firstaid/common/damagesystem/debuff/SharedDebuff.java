package ichttt.mods.firstaid.common.damagesystem.debuff;

import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.api.enums.EnumDebuffSlot;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class SharedDebuff implements IDebuff {
   private final IDebuff debuff;
   private final EnumPlayerPart[] parts;
   private float damage;
   private float healingDone;
   private int damageCount;
   private int healingCount;

   public SharedDebuff(IDebuff debuff, EnumDebuffSlot slot) {
      if (slot.playerParts.length <= 1) {
         throw new IllegalArgumentException("Only slots with more then more parts can be wrapped by SharedDebuff!");
      } else {
         this.debuff = debuff;
         this.parts = slot.playerParts;
      }
   }

   @Override
   public void handleDamageTaken(float damage, float healthFraction, ServerPlayer player) {
      this.damage += damage;
      this.damageCount++;
   }

   @Override
   public void handleHealing(float healingDone, float healthFraction, ServerPlayer player) {
      this.healingDone += healingDone;
      this.healingCount++;
   }

   public void tick(Player player) {
      if (player instanceof ServerPlayer) {
         AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
         if (damageModel != null) {
            float healthFraction = 0.0F;

            for (EnumPlayerPart part : this.parts) {
               AbstractDamageablePart damageablePart = damageModel.getFromEnum(part);
               healthFraction += damageablePart.currentHealth / damageablePart.getMaxHealth();
            }

            healthFraction /= this.parts.length;
            if (this.healingCount > 0) {
               this.debuff.handleHealing(this.healingDone / this.healingCount, healthFraction, (ServerPlayer)player);
            }

            if (this.damageCount > 0) {
               this.debuff.handleDamageTaken(this.damage / this.damageCount, healthFraction, (ServerPlayer)player);
            }

            this.healingDone = 0.0F;
            this.damage = 0.0F;
            this.damageCount = 0;
            this.healingCount = 0;
            this.debuff.update(player, healthFraction);
         }
      }
   }
}

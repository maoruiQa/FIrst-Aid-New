package ichttt.mods.firstaid.common.damagesystem.debuff;

import ichttt.mods.firstaid.FirstAid;
import java.util.List;
import javax.annotation.Nonnull;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

public class ConstantDebuff extends AbstractDebuff {
   private int ticks = 0;
   private int activeMultiplier = 0;
   private final List<ConstantDebuffEntry> amplifierBoundaries;

   public ConstantDebuff(@Nonnull Identifier potionName, @Nonnull List<ConstantDebuffEntry> amplifierBoundaries) {
      super(potionName);
      this.amplifierBoundaries = amplifierBoundaries;
   }

   private void syncMultiplier(float healthPerMax) {
      boolean found = false;

      for (ConstantDebuffEntry entry : this.amplifierBoundaries) {
         if (healthPerMax < entry.healthFractionThreshold()) {
            this.ticks = 0;
            this.activeMultiplier = entry.effectAmplifier();
            found = true;
            break;
         }
      }

      if (!found) {
         this.activeMultiplier = 0;
      }
   }

   @Override
   public void handleDamageTaken(float damage, float healthFraction, ServerPlayer player) {
      this.syncMultiplier(healthFraction);
   }

   @Override
   public void handleHealing(float healingDone, float healthFraction, ServerPlayer player) {
      this.syncMultiplier(healthFraction);
   }

   @Override
   public void update(Player player, float healthFraction) {
      FirstAid.InjuryDebuffMode mode = this.getDebuffMode();
      if (mode == FirstAid.InjuryDebuffMode.OFF) {
         this.ticks = 0;
         this.activeMultiplier = 0;
      } else if (this.activeMultiplier == 0) {
         this.ticks = 0;
      } else {
         if (this.ticks == 0) {
            if (healthFraction != -1.0F) {
               this.syncMultiplier(healthFraction);
            }

            if (this.activeMultiplier != 0) {
               int amplifier = this.activeMultiplier - 1;
               if (mode == FirstAid.InjuryDebuffMode.LOW) {
                  amplifier = this.scaleAmplifierForMode(amplifier);
               }

               player.addEffect(new MobEffectInstance(this.effect, 60, amplifier, false, false));
            }
         }

         this.ticks++;
         if (this.ticks >= 45) {
            this.ticks = 0;
         }
      }
   }
}

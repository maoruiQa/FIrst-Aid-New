package ichttt.mods.firstaid.common.damagesystem.debuff;

import ichttt.mods.firstaid.FirstAid;
import java.util.List;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import javax.annotation.Nonnull;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

public class ConstantDebuff extends AbstractDebuff {
   private int ticks = 0;
   private int activeMultiplier = 0;
   private float lastAdrenalineRelief = 0.0F;
   private int lastCombatMultiplier = -1;
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
      if (this.activeMultiplier == 0 && player != null && this.injuryEffectId.equals("minecraft:weakness")
            && CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model) {
         model.replaceTrackedInjuryEffect(player, this.injuryEffectId, null);
      }
   }

   @Override
   public void update(Player player, float healthFraction) {
      if (this.updateAdrenalineDebuff(player, healthFraction)) return;
      FirstAid.InjuryDebuffMode mode = this.getDebuffMode();
      if (mode == FirstAid.InjuryDebuffMode.OFF) {
         this.ticks = 0;
         this.activeMultiplier = 0;
         return;
      }
      if (this.ticks == 0 && healthFraction != -1.0F) {
         this.syncMultiplier(healthFraction);
      }
      if (this.activeMultiplier == 0) {
         this.ticks = 0;
      } else {
         if (this.ticks == 0) {
           if (this.activeMultiplier != 0) {
               int amplifier = this.activeMultiplier - 1;
               if (mode == FirstAid.InjuryDebuffMode.LOW) {
                  amplifier = this.scaleAmplifierForMode(amplifier);
               }

               this.applyInjuryEffect(player, new MobEffectInstance(this.effect, 60, amplifier, false, false));
            }
         }

         this.ticks++;
         if (this.ticks >= 45) {
            this.ticks = 0;
         }
      }
   }

   private boolean updateAdrenalineDebuff(Player player, float healthFraction) {
      if (!this.injuryEffectId.equals("minecraft:slowness")
         && !this.injuryEffectId.equals("minecraft:weakness")
         && !this.injuryEffectId.equals("minecraft:mining_fatigue")) return false;
      if (!(CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model)) return false;
      float relief = model.getAdrenalineDebuffRelief();
      if (relief == 0.0F && this.lastAdrenalineRelief == 0.0F) return false;
      if (healthFraction < 0.0F) return false;
      int level = calculateAdrenalineMultiplier(this.amplifierBoundaries, healthFraction, relief);
      FirstAid.InjuryDebuffMode mode = this.getDebuffMode();
      if (mode == FirstAid.InjuryDebuffMode.OFF) level = 0;
      else if (mode == FirstAid.InjuryDebuffMode.LOW && level > 0) level = this.scaleAmplifierForMode(level - 1) + 1;
      if (level != this.lastCombatMultiplier || this.ticks == 0
         || level > 0 && !player.hasEffect(this.effect)) {
         model.replaceTrackedInjuryEffect(player, this.injuryEffectId, level == 0 ? null
            : new MobEffectInstance(this.effect, 60, level - 1, false, false));
         this.ticks = 0;
      }
      this.lastCombatMultiplier = level;
      this.lastAdrenalineRelief = relief;
      if (++this.ticks >= 45) this.ticks = 0;
      if (relief == 0.0F) this.syncMultiplier(healthFraction);
      return true;
   }

   public static int calculateAdrenalineMultiplier(List<ConstantDebuffEntry> boundaries, float healthFraction, float relief) {
      float firstThreshold = 0.0F;
      int maxLevel = 0;
      for (ConstantDebuffEntry entry : boundaries) {
         firstThreshold = Math.max(firstThreshold, entry.healthFractionThreshold());
         maxLevel = Math.max(maxLevel, entry.effectAmplifier());
      }
      float target = Math.min(firstThreshold, 0.50F);
      float scale = firstThreshold <= 0.0F ? 1.0F : 1.0F + relief * (target / firstThreshold - 1.0F);
      int cap = Math.max(1, Math.round(maxLevel + relief * (1 - maxLevel)));
      for (ConstantDebuffEntry entry : boundaries) {
         float threshold = entry.healthFractionThreshold() * scale;
         if (relief >= 1.0F) threshold = Math.min(threshold, target);
         boolean firstAtHalf = relief >= 1.0F && firstThreshold >= 0.50F
            && entry.healthFractionThreshold() == firstThreshold && healthFraction <= 0.50F;
         if (healthFraction < threshold || firstAtHalf) return Math.min(entry.effectAmplifier(), cap);
      }
      return 0;
   }
}

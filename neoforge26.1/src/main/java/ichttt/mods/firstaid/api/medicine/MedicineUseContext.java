package ichttt.mods.firstaid.api.medicine;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class MedicineUseContext {
   private final Player player;
   private final Level level;
   private final ItemStack stack;
   @Nullable
   private final AbstractPlayerDamageModel damageModel;

   public MedicineUseContext(Player player, Level level, ItemStack stack, @Nullable AbstractPlayerDamageModel damageModel) {
      this.player = player;
      this.level = level;
      this.stack = stack;
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

   @Nonnull
   public ItemStack getStack() {
      return this.stack;
   }

   @Nullable
   public AbstractPlayerDamageModel getDamageModel() {
      return this.damageModel;
   }

   public void queuePainkillerActivation() {
      if (this.damageModel instanceof PlayerDamageModel playerDamageModel) {
         playerDamageModel.queuePainkillerActivation();
      } else {
         this.player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT, PlayerDamageModel.getPainkillerDuration(), 0, false, false));
      }
   }

   public void queueMorphineActivation() {
      if (this.damageModel instanceof PlayerDamageModel playerDamageModel) {
         playerDamageModel.queueMorphineActivation();
      } else if (this.damageModel != null) {
         this.damageModel.applyMorphine(this.player);
      }
   }

   public void applyAdrenalineInjection() {
      if (this.damageModel instanceof PlayerDamageModel playerDamageModel) {
         playerDamageModel.applyAdrenalineInjection(this.player);
      } else {
         MobEffectInstance activePainkiller = this.player.getEffect(RegistryObjects.PAINKILLER_EFFECT);
         int duration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activePainkiller == null ? 0 : activePainkiller.getDuration());
         MobEffectInstance activeAbsorption = this.player.getEffect(MobEffects.ABSORPTION);
         int absorptionDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeAbsorption == null ? 0 : activeAbsorption.getDuration());
         int absorptionAmplifier = Math.max(1, activeAbsorption == null ? 0 : activeAbsorption.getAmplifier());
         MobEffectInstance activeHaste = this.player.getEffect(MobEffects.HASTE);
         MobEffectInstance activeStrength = this.player.getEffect(MobEffects.STRENGTH);
         MobEffectInstance activeSpeed = this.player.getEffect(MobEffects.SPEED);
         int hasteDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeHaste == null ? 0 : activeHaste.getDuration());
         int hasteAmplifier = Math.max(0, activeHaste == null ? 0 : activeHaste.getAmplifier());
         int strengthDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeStrength == null ? 0 : activeStrength.getDuration());
         int strengthAmplifier = Math.max(0, activeStrength == null ? 0 : activeStrength.getAmplifier());
         int speedDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeSpeed == null ? 0 : activeSpeed.getDuration());
         int speedAmplifier = Math.max(0, activeSpeed == null ? 0 : activeSpeed.getAmplifier());
         this.player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT, duration, 0, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, absorptionDuration, absorptionAmplifier, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.HASTE, hasteDuration, hasteAmplifier, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, strengthDuration, strengthAmplifier, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.SPEED, speedDuration, speedAmplifier, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 140, 0, false, false));
      }
   }
}

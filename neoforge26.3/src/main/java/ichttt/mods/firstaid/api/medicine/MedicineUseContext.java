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

   public void applyMorphineInjection() {
      if (this.damageModel instanceof PlayerDamageModel playerDamageModel) {
         playerDamageModel.applyMorphineInjection(this.player);
      } else {
         int basePainRelief = PlayerDamageModel.getRandMorphineDuration();
         int painDuration = Math.round(basePainRelief * PlayerDamageModel.MORPHINE_INJECTOR_DURATION_MULTIPLIER * PlayerDamageModel.MORPHINE_INJECTOR_EFFECT_DURATION_MULTIPLIER);
         this.player.addEffect(new MobEffectInstance(RegistryObjects.MORPHINE_EFFECT, Math.round(basePainRelief * PlayerDamageModel.MORPHINE_INJECTOR_EFFECT_DURATION_MULTIPLIER), 0, false, false));
         this.player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT, painDuration, 0, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, PlayerDamageModel.MORPHINE_INJECTOR_REGEN_TICKS, 0, false, false));
      }
   }

   public void applyAdrenalineInjection() {
      if (this.damageModel instanceof PlayerDamageModel playerDamageModel) {
         playerDamageModel.applyAdrenalineInjection(this.player);
      } else {
         MobEffectInstance activeAbsorption = this.player.getEffect(MobEffects.ABSORPTION);
         int absorptionDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeAbsorption == null ? 0 : activeAbsorption.getDuration());
         int absorptionAmplifier = Math.max(1, activeAbsorption == null ? 0 : activeAbsorption.getAmplifier());
         this.player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, absorptionDuration, absorptionAmplifier, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 140, 0, false, false));
      }
   }
}

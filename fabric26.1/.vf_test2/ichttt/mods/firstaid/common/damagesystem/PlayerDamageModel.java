package ichttt.mods.firstaid.common.damagesystem;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.api.enums.EnumDebuffSlot;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.CapProvider;
import ichttt.mods.firstaid.common.ClientAccess;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.compat.playerrevive.PRCompatManager;
import ichttt.mods.firstaid.common.damagesystem.debuff.SharedDebuff;
import ichttt.mods.firstaid.common.network.FirstAidNetworking;
import ichttt.mods.firstaid.common.registries.FirstAidRegistryLookups;
import ichttt.mods.firstaid.common.registries.LookupReloadListener;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PlayerDamageModel extends AbstractPlayerDamageModel implements LookupReloadListener {
   private static final DecimalFormat TEXT_FORMAT = new DecimalFormat("0.0");
   private static final int MAX_PAIN_LEVEL = 5;
   private static final int MAX_ADRENALINE_LEVEL = 3;
   private static final int MAX_ADRENALINE_TICKS = 200;
   private static final float MAX_SUPPRESSION_INTENSITY = 1.0F;
   private static final float SUPPRESSION_GAIN_MULTIPLIER = 0.48F;
   private static final int SUPPRESSION_HOLD_TICKS = 80;
   private static final float SUPPRESSION_DECAY_STEP = 0.03F;
   private static final int SUPPRESSION_DECAY_INTERVAL = 4;
   private static final int PAINKILLER_ACTIVATION_DELAY_TICKS = 600;
   private static final int MORPHINE_ACTIVATION_DELAY_TICKS = 200;
   private static final int CRITICAL_UNCONSCIOUS_TICKS = 3000;
   private static final int RESCUE_WAKE_UP_DELAY = Math.max(1, Math.round(30.000002F));
   private static final int RESCUE_DURATION_TICKS = 160;
   private static final double RESCUE_RANGE = 3.0;
   private static final int COLLAPSE_ANIMATION_TICKS = 12;
   private static final int COLLAPSE_SEARCH_RADIUS = 2;
   private static final double COLLAPSE_SUPPORT_PROBE_DEPTH = 0.125;
   private static final EntityDimensions UNCONSCIOUS_DIMENSIONS = EntityDimensions.scalable(1.4F, 1.0F);
   private static final EntityDimensions CRAMPED_UNCONSCIOUS_DIMENSIONS = EntityDimensions.scalable(0.6F, 1.0F);
   private static final Identifier ATTR_UNCONSCIOUS = Identifier.fromNamespaceAndPath("firstaid", "unconscious");
   private static final String UNCONSCIOUS_REASON_NONE = "";
   private static final String UNCONSCIOUS_REASON_CRITICAL = "firstaid.gui.critical_condition";
   private static final String UNCONSCIOUS_REASON_RECOVERING = "firstaid.gui.stabilizing";
   private final Set<SharedDebuff> sharedDebuffs = new HashSet<>();
   private int morphineTicksLeft = 0;
   private int pendingPainkillerTicks = 0;
   private int pendingMorphineDelayTicks = 0;
   private int pendingMorphineEffectTicks = 0;
   private int sleepBlockTicks = 0;
   private float prevHealthCurrent = -1.0F;
   private float prevScaleFactor;
   private final boolean noCritical;
   private boolean needsMorphineUpdate = false;
   private int resyncTimer = -1;
   private int painLevel = 0;
   private int adrenalineLevel = 0;
   private int adrenalineTicks = 0;
   private float suppressionIntensity = 0.0F;
   private int suppressionHoldTicks = 0;
   private int suppressionDecayTicker = 0;
   private int unconsciousTicks = 0;
   private boolean criticalConditionActive = false;
   private boolean unconsciousAllowsGiveUp = false;
   private boolean unconsciousCausesDeath = false;
   private String unconsciousReasonKey = "";
   private int collapseAnimationTicks = 0;
   private boolean collapsePlacementPending = false;

   public PlayerDamageModel() {
      super(
         new DamageablePart(FirstAidConfig.SERVER.maxHealthHead.get(), FirstAidConfig.SERVER.causeDeathHead.get(), EnumPlayerPart.HEAD),
         new DamageablePart(FirstAidConfig.SERVER.maxHealthLeftArm.get(), false, EnumPlayerPart.LEFT_ARM),
         new DamageablePart(FirstAidConfig.SERVER.maxHealthLeftLeg.get(), false, EnumPlayerPart.LEFT_LEG),
         new DamageablePart(FirstAidConfig.SERVER.maxHealthLeftFoot.get(), false, EnumPlayerPart.LEFT_FOOT),
         new DamageablePart(FirstAidConfig.SERVER.maxHealthBody.get(), FirstAidConfig.SERVER.causeDeathBody.get(), EnumPlayerPart.BODY),
         new DamageablePart(FirstAidConfig.SERVER.maxHealthRightArm.get(), false, EnumPlayerPart.RIGHT_ARM),
         new DamageablePart(FirstAidConfig.SERVER.maxHealthRightLeg.get(), false, EnumPlayerPart.RIGHT_LEG),
         new DamageablePart(FirstAidConfig.SERVER.maxHealthRightFoot.get(), false, EnumPlayerPart.RIGHT_FOOT)
      );
      this.noCritical = !FirstAidConfig.SERVER.causeDeathBody.get() && !FirstAidConfig.SERVER.causeDeathHead.get();
      FirstAidRegistryLookups.registerReloadListener(this);
   }

   public CompoundTag serializeNBT() {
      CompoundTag tagCompound = new CompoundTag();
      tagCompound.put("head", (Tag)this.HEAD.serializeNBT());
      tagCompound.put("leftArm", (Tag)this.LEFT_ARM.serializeNBT());
      tagCompound.put("leftLeg", (Tag)this.LEFT_LEG.serializeNBT());
      tagCompound.put("leftFoot", (Tag)this.LEFT_FOOT.serializeNBT());
      tagCompound.put("body", (Tag)this.BODY.serializeNBT());
      tagCompound.put("rightArm", (Tag)this.RIGHT_ARM.serializeNBT());
      tagCompound.put("rightLeg", (Tag)this.RIGHT_LEG.serializeNBT());
      tagCompound.put("rightFoot", (Tag)this.RIGHT_FOOT.serializeNBT());
      tagCompound.putBoolean("hasTutorial", this.hasTutorial);
      tagCompound.putInt("pendingPainkillerTicks", this.pendingPainkillerTicks);
      tagCompound.putInt("pendingMorphineDelayTicks", this.pendingMorphineDelayTicks);
      tagCompound.putInt("pendingMorphineEffectTicks", this.pendingMorphineEffectTicks);
      tagCompound.putInt("painLevel", this.painLevel);
      tagCompound.putInt("adrenalineLevel", this.adrenalineLevel);
      tagCompound.putInt("adrenalineTicks", this.adrenalineTicks);
      tagCompound.putFloat("suppressionIntensity", this.suppressionIntensity);
      tagCompound.putInt("suppressionHoldTicks", this.suppressionHoldTicks);
      tagCompound.putInt("suppressionDecayTicker", this.suppressionDecayTicker);
      tagCompound.putInt("unconsciousTicks", this.unconsciousTicks);
      tagCompound.putBoolean("criticalConditionActive", this.criticalConditionActive);
      tagCompound.putBoolean("unconsciousAllowsGiveUp", this.unconsciousAllowsGiveUp);
      tagCompound.putBoolean("unconsciousCausesDeath", this.unconsciousCausesDeath);
      tagCompound.putInt("collapseAnimationTicks", this.collapseAnimationTicks);
      if (!this.unconsciousReasonKey.isEmpty()) {
         tagCompound.putString("unconsciousReasonKey", this.unconsciousReasonKey);
      }

      return tagCompound;
   }

   public void deserializeNBT(CompoundTag nbt) {
      this.HEAD.deserializeNBT(nbt.getCompoundOrEmpty("head"));
      this.LEFT_ARM.deserializeNBT(nbt.getCompoundOrEmpty("leftArm"));
      this.LEFT_LEG.deserializeNBT(nbt.getCompoundOrEmpty("leftLeg"));
      this.LEFT_FOOT.deserializeNBT(nbt.getCompoundOrEmpty("leftFoot"));
      this.BODY.deserializeNBT(nbt.getCompoundOrEmpty("body"));
      this.RIGHT_ARM.deserializeNBT(nbt.getCompoundOrEmpty("rightArm"));
      this.RIGHT_LEG.deserializeNBT(nbt.getCompoundOrEmpty("rightLeg"));
      this.RIGHT_FOOT.deserializeNBT(nbt.getCompoundOrEmpty("rightFoot"));
      if (nbt.contains("morphineTicks")) {
         this.morphineTicksLeft = nbt.getIntOr("morphineTicks", 0);
         this.needsMorphineUpdate = true;
      }

      this.pendingPainkillerTicks = nbt.getIntOr("pendingPainkillerTicks", 0);
      this.pendingMorphineDelayTicks = nbt.getIntOr("pendingMorphineDelayTicks", 0);
      this.pendingMorphineEffectTicks = nbt.getIntOr("pendingMorphineEffectTicks", 0);
      if (nbt.contains("hasTutorial")) {
         this.hasTutorial = nbt.getBooleanOr("hasTutorial", false);
      }

      this.painLevel = nbt.getIntOr("painLevel", 0);
      this.adrenalineLevel = nbt.getIntOr("adrenalineLevel", 0);
      this.adrenalineTicks = nbt.getIntOr("adrenalineTicks", 0);
      this.suppressionIntensity = nbt.contains("suppressionIntensity")
         ? Mth.clamp(nbt.getFloatOr("suppressionIntensity", 0.0F), 0.0F, 1.0F)
         : Mth.clamp(this.adrenalineTicks / 200.0F, 0.0F, 1.0F);
      this.suppressionHoldTicks = nbt.getIntOr("suppressionHoldTicks", 0);
      this.suppressionDecayTicker = nbt.getIntOr("suppressionDecayTicker", 0);
      this.unconsciousTicks = nbt.getIntOr("unconsciousTicks", 0);
      this.criticalConditionActive = nbt.getBooleanOr("criticalConditionActive", false);
      this.unconsciousAllowsGiveUp = nbt.getBooleanOr("unconsciousAllowsGiveUp", this.criticalConditionActive);
      this.unconsciousCausesDeath = nbt.getBooleanOr("unconsciousCausesDeath", this.criticalConditionActive);
      this.unconsciousReasonKey = nbt.getStringOr("unconsciousReasonKey", this.criticalConditionActive ? "firstaid.gui.critical_condition" : "");
      this.collapseAnimationTicks = nbt.getIntOr("collapseAnimationTicks", 0);
      this.collapsePlacementPending = false;
      this.refreshSuppressionSnapshot();
   }

   @Override
   public void onLookupsReloaded() {
      FirstAid.LOGGER.debug("Reloaded lookups");
      this.sharedDebuffs.clear();

      for (EnumDebuffSlot debuffSlot : EnumDebuffSlot.values()) {
         IDebuff[] debuffs = FirstAidRegistryLookups.getDebuffs(debuffSlot);

         for (EnumPlayerPart playerPart : debuffSlot.playerParts) {
            this.getFromEnum(playerPart).loadDebuffInfo(debuffs);
         }

         for (IDebuff debuff : debuffs) {
            if (debuff instanceof SharedDebuff sharedDebuff) {
               this.sharedDebuffs.add(sharedDebuff);
            }
         }
      }
   }

   @Override
   public void tick(Level world, Player player) {
      if (!this.isDead(player)) {
         if (this.sleepBlockTicks > 0) {
            this.sleepBlockTicks--;
         } else if (this.sleepBlockTicks < 0) {
            throw new RuntimeException("Negative sleepBlockTicks " + this.sleepBlockTicks);
         }

         float newCurrentHealth = this.calculateNewCurrentHealth(player);
         if (Float.isNaN(newCurrentHealth)) {
            FirstAid.LOGGER.warn("New current health is not a number, setting it to 0!");
            newCurrentHealth = 0.0F;
         }

         if (newCurrentHealth <= 0.0F) {
            FirstAid.LOGGER.error("Got {} health left, but isn't marked as dead!", newCurrentHealth);
         } else {
            if (!world.isClientSide() && this.resyncTimer != -1) {
               this.resyncTimer--;
               if (this.resyncTimer == 0) {
                  this.resyncTimer = -1;
                  if (player instanceof ServerPlayer serverPlayer) {
                     FirstAidNetworking.sendDamageModelSync(serverPlayer, this, FirstAidConfig.SERVER.scaleMaxHealth.get());
                  }
               }
            }

            if (Float.isInfinite(newCurrentHealth)) {
               FirstAid.LOGGER.error("Error calculating current health: Value was infinite");
            } else {
               if (newCurrentHealth != this.prevHealthCurrent) {
                  player.setHealth(newCurrentHealth);
               }

               this.prevHealthCurrent = newCurrentHealth;
            }

            if (!this.hasTutorial) {
               this.hasTutorial = CapProvider.tutorialDone.contains(player.getName().getString());
            }

            this.runScaleLogic(player);
            Holder<MobEffect> morphineEffect = RegistryObjects.MORPHINE_EFFECT;
            Holder<MobEffect> painkillerEffect = RegistryObjects.PAINKILLER_EFFECT;
            if (this.needsMorphineUpdate) {
               player.addEffect(new MobEffectInstance(morphineEffect, this.morphineTicksLeft, 0, false, false));
               player.addEffect(new MobEffectInstance(painkillerEffect, this.morphineTicksLeft, 0, false, false));
            }

            MobEffectInstance morphine = player.getEffect(morphineEffect);
            MobEffectInstance painkiller = player.getEffect(painkillerEffect);
            if (!this.needsMorphineUpdate) {
               this.morphineTicksLeft = morphine == null ? 0 : morphine.getDuration();
            }

            this.needsMorphineUpdate = false;
            if (!world.isClientSide()) {
               this.tickPendingMedicineActivations(player);
               this.updateMedicalState(player);
               if (this.unconsciousTicks > 0) {
                  this.applyUnconsciousPenalties(player);
               } else {
                  this.clearUnconsciousPenalties(player);
               }
            }

            boolean painSuppressed = morphine != null || painkiller != null;
            boolean healingStateChanged = false;

            for (AbstractDamageablePart part : this) {
               float previousHealth = part.currentHealth;
               boolean hadHealer = part.activeHealer != null;
               part.tick(world, player, !painSuppressed);
               if (!world.isClientSide() && (Float.compare(previousHealth, part.currentHealth) != 0 || hadHealer != (part.activeHealer != null))) {
                  healingStateChanged = true;
               }
            }

            if (!painSuppressed && !world.isClientSide()) {
               this.sharedDebuffs.forEach(sharedDebuff -> sharedDebuff.tick(player));
            }

            if (healingStateChanged && player instanceof ServerPlayer serverPlayer) {
               FirstAidNetworking.sendDamageModelSync(serverPlayer, this, FirstAidConfig.SERVER.scaleMaxHealth.get());
            }
         }
      }
   }

   public static int getRandMorphineDuration() {
      return EventHandler.RAND.nextInt(5) * 20 * 15 + 4200;
   }

   public static int getMorphineActivationDelay() {
      return FirstAid.scaleMedicalTimingTicks(200);
   }

   public static int getPainkillerDuration() {
      return 2400;
   }

   public static int getPainkillerActivationDelay() {
      return FirstAid.scaleMedicalTimingTicks(600);
   }

   public void queuePainkillerActivation() {
      this.pendingPainkillerTicks = Math.max(this.pendingPainkillerTicks, getPainkillerActivationDelay());
      this.scheduleResync();
   }

   public void queueMorphineActivation() {
      this.pendingMorphineDelayTicks = Math.max(this.pendingMorphineDelayTicks, getMorphineActivationDelay());
      this.pendingMorphineEffectTicks = Math.max(this.pendingMorphineEffectTicks, getRandMorphineDuration());
      this.scheduleResync();
   }

   @Deprecated
   @Override
   public void applyMorphine() {
      this.morphineTicksLeft = getRandMorphineDuration();
      this.needsMorphineUpdate = true;
   }

   @Override
   public void applyMorphine(Player player) {
      int duration = getRandMorphineDuration();
      player.addEffect(new MobEffectInstance(RegistryObjects.MORPHINE_EFFECT, duration, 0, false, false));
      player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT, duration, 0, false, false));
   }

   @Deprecated
   @Override
   public int getMorphineTicks() {
      return this.morphineTicksLeft;
   }

   @Override
   public int getPainLevel() {
      return this.painLevel;
   }

   @Override
   public int getAdrenalineLevel() {
      return this.adrenalineLevel;
   }

   @Override
   public int getAdrenalineTicks() {
      return this.adrenalineTicks;
   }

   public int getSuppressionLevel() {
      return this.adrenalineLevel;
   }

   public float getSuppressionIntensity() {
      return this.suppressionIntensity;
   }

   public int getSuppressionHoldTicks() {
      return this.suppressionHoldTicks;
   }

   @Override
   public int getUnconsciousTicks() {
      return this.unconsciousTicks;
   }

   @Override
   public boolean isCriticalConditionActive() {
      return this.criticalConditionActive;
   }

   public boolean isUnconscious() {
      return this.unconsciousTicks > 0;
   }

   public boolean canGiveUp() {
      return this.isUnconscious() && this.unconsciousAllowsGiveUp;
   }

   public float getCollapseAnimationProgress(float partialTick) {
      return !this.isUnconscious()
         ? 1.0F
         : Mth.clamp(1.0F - (Math.max(0.0F, (float)this.collapseAnimationTicks) - Math.max(0.0F, partialTick)) / 12.0F, 0.0F, 1.0F);
   }

   public float getCollapseAnimationProgress() {
      return this.getCollapseAnimationProgress(0.0F);
   }

   public String getUnconsciousReasonKey() {
      return this.unconsciousReasonKey.isEmpty() ? "firstaid.gui.unconscious" : this.unconsciousReasonKey;
   }

   public int getUnconsciousSecondsLeft() {
      return Math.max(1, (int)Math.ceil(this.unconsciousTicks / 20.0));
   }

   public static int getRescueDurationTicks() {
      return 160;
   }

   public static double getRescueRange() {
      return 3.0;
   }

   public float getPainVisualStrength() {
      return this.painLevel <= 0 ? 0.0F : Math.min(1.0F, this.painLevel / 5.0F);
   }

   public float getDeathCountdownDangerProgress() {
      if (!this.canGiveUp()) {
         return 0.0F;
      } else {
         float remaining = Math.max(0.0F, (float)this.unconsciousTicks);
         float progress = 1.0F - remaining / 3000.0F;
         return Math.max(0.0F, Math.min(1.0F, progress));
      }
   }

   public void registerAdrenalineNearMiss(float strength) {
      float clampedStrength = Mth.clamp(strength, 0.35F, 1.45F);
      float previousIntensity = this.suppressionIntensity;
      int previousHoldTicks = this.suppressionHoldTicks;
      int previousAdrenalineTicks = this.adrenalineTicks;
      float baseIntensity = 0.28F + clampedStrength * 0.24F;
      float addedIntensity = Mth.clamp(baseIntensity * 0.48F, 0.1728F, 0.2976F);
      this.suppressionIntensity = Mth.clamp(this.suppressionIntensity + addedIntensity, 0.0F, 1.0F);
      this.suppressionHoldTicks = Math.max(this.suppressionHoldTicks, 80);
      this.suppressionDecayTicker = 0;
      this.refreshSuppressionSnapshot();
      if (previousIntensity != this.suppressionIntensity || previousHoldTicks != this.suppressionHoldTicks || previousAdrenalineTicks != this.adrenalineTicks) {
         this.scheduleResync();
      }
   }

   public void clearStatusEffects() {
      this.morphineTicksLeft = 0;
      this.pendingPainkillerTicks = 0;
      this.pendingMorphineDelayTicks = 0;
      this.pendingMorphineEffectTicks = 0;
      this.painLevel = 0;
      this.adrenalineLevel = 0;
      this.adrenalineTicks = 0;
      this.suppressionIntensity = 0.0F;
      this.suppressionHoldTicks = 0;
      this.suppressionDecayTicker = 0;
      this.unconsciousTicks = 0;
      this.criticalConditionActive = false;
      this.unconsciousAllowsGiveUp = false;
      this.unconsciousCausesDeath = false;
      this.unconsciousReasonKey = "";
   }

   public void handlePostDamage(Player player) {
      if (this.hasNoRemainingBodyHealth()) {
         this.criticalConditionActive = false;
         this.clearUnconsciousState();
         this.clearUnconsciousPenalties(player);
         this.scheduleResync();
      } else if (!this.criticalConditionActive && this.hasCriticalPartCollapsed()) {
         this.criticalConditionActive = true;
         this.setUnconsciousState(3000, true, true, "firstaid.gui.critical_condition");
         this.painLevel = Math.max(this.painLevel, 5);
         player.setHealth(Math.max(player.getHealth(), 1.0F));
         this.scheduleResync();
      }
   }

   public boolean canBeRescued() {
      return this.criticalConditionActive && this.isUnconscious();
   }

   public boolean rescueFromCriticalState(Player player, @Nullable AbstractPartHealer healer) {
      return this.rescueFromCriticalState(player, healer, FirstAid.rescueWakeUpEnabled);
   }

   public boolean rescueFromCriticalState(Player player, @Nullable AbstractPartHealer healer, boolean keepWakeUpDelay) {
      if (!this.canBeRescued()) {
         return false;
      } else {
         this.rescueCriticalParts(keepWakeUpDelay ? 1.0F : 2.0F);
         if (!keepWakeUpDelay) {
            this.rescueNonCriticalZeroParts(1.0F);
         }

         this.criticalConditionActive = false;
         this.painLevel = Math.max(2, this.painLevel);
         if (keepWakeUpDelay) {
            AbstractDamageablePart rescueTarget = this.getFirstCriticalRescueTarget();
            if (healer != null && rescueTarget != null && rescueTarget.activeHealer == null) {
               rescueTarget.activeHealer = healer;
            }

            this.setUnconsciousState(Math.max(this.unconsciousTicks, RESCUE_WAKE_UP_DELAY), false, false, "firstaid.gui.stabilizing");
         } else {
            this.clearUnconsciousState();
            this.clearUnconsciousPenalties(player);
            player.setHealth(Math.max(player.getHealth(), 1.0F));
         }

         this.scheduleResync();
         if (player instanceof ServerPlayer serverPlayer) {
            FirstAidNetworking.sendDamageModelSync(serverPlayer, this, FirstAidConfig.SERVER.scaleMaxHealth.get());
         }

         return true;
      }
   }

   public void giveUp(Player player) {
      if (this.canGiveUp()) {
         this.criticalConditionActive = false;
         this.clearUnconsciousState();
         this.clearUnconsciousPenalties(player);
         this.scheduleResync();
         CommonUtils.killPlayer(this, player, null);
      }
   }

   public void refreshPainState(Player player) {
      int previousPainLevel = this.painLevel;
      this.painLevel = this.calculatePainLevel();
      if (previousPainLevel != this.painLevel) {
         this.scheduleResync();
         if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            FirstAidNetworking.sendDamageModelSync(serverPlayer, this, FirstAidConfig.SERVER.scaleMaxHealth.get());
         }
      }
   }

   @Nonnull
   @Override
   public Iterator<AbstractDamageablePart> iterator() {
      return new Iterator<AbstractDamageablePart>() {
         private byte count = 0;

         @Override
         public boolean hasNext() {
            return this.count < 8;
         }

         public AbstractDamageablePart next() {
            if (this.count >= 8) {
               throw new NoSuchElementException();
            } else {
               AbstractDamageablePart part = PlayerDamageModel.this.getFromEnum(EnumPlayerPart.VALUES[this.count]);
               this.count++;
               return part;
            }
         }
      };
   }

   private float calculateNewCurrentHealth(Player player) {
      float currentHealth = 0.0F;
      FirstAidConfig.Server.VanillaHealthCalculationMode mode = FirstAidConfig.SERVER.vanillaHealthCalculation.get();
      if (this.noCritical) {
         mode = FirstAidConfig.Server.VanillaHealthCalculationMode.AVERAGE_ALL;
      }
      float scaledHealth = switch (mode) {
         case AVERAGE_CRITICAL -> {
            int maxHealth = 0;

            for (AbstractDamageablePart part : this) {
               if (part.canCauseDeath) {
                  currentHealth += part.currentHealth;
                  maxHealth += part.getMaxHealth();
               }
            }

            yield currentHealth / maxHealth;
         }
         case MIN_CRITICAL -> {
            AbstractDamageablePart minimal = null;
            float lowest = Float.MAX_VALUE;

            for (AbstractDamageablePart part : this) {
               if (part.canCauseDeath) {
                  float partCurrentHealth = part.currentHealth;
                  if (partCurrentHealth < lowest) {
                     minimal = part;
                     lowest = partCurrentHealth;
                  }
               }
            }

            Objects.requireNonNull(minimal);
            yield minimal.currentHealth / minimal.getMaxHealth();
         }
         case AVERAGE_ALL -> {
            for (AbstractDamageablePart part : this) {
               currentHealth += part.currentHealth;
            }

            yield currentHealth / this.getCurrentMaxHealth();
         }
         case CRITICAL_50_PERCENT_OTHER_50_PERCENT -> {
            float currentNormal = 0.0F;
            int maxNormal = 0;
            float currentCritical = 0.0F;
            int maxCritical = 0;

            for (AbstractDamageablePart part : this) {
               if (!part.canCauseDeath) {
                  currentNormal += part.currentHealth;
                  maxNormal += part.getMaxHealth();
               } else {
                  currentCritical += part.currentHealth;
                  maxCritical += part.getMaxHealth();
               }
            }

            float avgNormal = currentNormal / maxNormal;
            float avgCritical = currentCritical / maxCritical;
            yield (avgCritical + avgNormal) / 2.0F;
         }
         default -> throw new RuntimeException("Unknown constant " + mode);
      } * player.getMaxHealth();
      return this.criticalConditionActive && this.unconsciousTicks > 0 && this.hasCriticalPartCollapsed() ? Math.max(1.0F, scaledHealth) : scaledHealth;
   }

   @Override
   public boolean isDead(@Nullable Player player) {
      boolean bleeding = PRCompatManager.getHandler().isBleeding(player);
      if (bleeding) {
         return true;
      } else if (player != null && !player.isAlive()) {
         return true;
      } else if (this.hasNoRemainingBodyHealth()) {
         return true;
      } else if (this.criticalConditionActive && this.unconsciousTicks > 0) {
         return false;
      } else if (!this.noCritical) {
         for (AbstractDamageablePart part : this) {
            if (part.canCauseDeath && part.currentHealth <= 0.0F) {
               return true;
            }
         }

         return false;
      } else {
         boolean dead = true;

         for (AbstractDamageablePart partx : this) {
            if (partx.currentHealth > 0.0F) {
               dead = false;
               break;
            }
         }

         return dead;
      }
   }

   @Override
   public Float getAbsorption() {
      float value = 0.0F;

      for (AbstractDamageablePart part : this) {
         value += part.getAbsorption();
      }

      return value;
   }

   @Override
   public void setAbsorption(float absorption) {
      float newAbsorption = absorption / 8.0F;
      this.forEach(damageablePart -> damageablePart.setAbsorption(newAbsorption));
   }

   @Override
   public int getMaxRenderSize() {
      int max = 0;

      for (AbstractDamageablePart part : this) {
         int newMax;
         if (FirstAidConfig.CLIENT.overlayMode.get() == FirstAidConfig.Client.OverlayMode.NUMBERS) {
            newMax = ClientAccess.getTextWidth(TEXT_FORMAT.format(part.currentHealth) + "/" + part.getMaxHealth()) + 1;
         } else {
            newMax = (int)(((int)(part.getMaxHealth() + part.getAbsorption() + 0.9999F) + 1) / 2.0F * 9.0F);
         }

         max = Math.max(max, newMax);
      }

      return max;
   }

   @Override
   public void sleepHeal(Player player) {
      if (this.sleepBlockTicks <= 0) {
         CommonUtils.healAllPartsByPercentage(FirstAidConfig.SERVER.sleepHealPercentage.get(), this, player);
         this.refreshPainState(player);
         this.sleepBlockTicks = 20;
      }
   }

   @Override
   public int getCurrentMaxHealth() {
      int maxHealth = 0;

      for (AbstractDamageablePart part : this) {
         maxHealth += part.getMaxHealth();
      }

      return maxHealth;
   }

   @Override
   public void revivePlayer(Player player) {
      if (FirstAidConfig.GENERAL.debug.get()) {
         CommonUtils.debugLogStacktrace("Reviving player");
      }

      this.clearStatusEffects();

      for (AbstractDamageablePart part : this) {
         if ((part.canCauseDeath || this.noCritical) && part.currentHealth <= 0.0F) {
            part.currentHealth = 1.0F;
         }
      }

      if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
         FirstAidNetworking.sendDamageModelSync(serverPlayer, this, FirstAidConfig.SERVER.scaleMaxHealth.get());
      }
   }

   @Override
   public void runScaleLogic(Player player) {
      if (FirstAidConfig.SERVER.scaleMaxHealth.get()) {
         float globalFactor = player.getMaxHealth() / 20.0F;
         if (this.prevScaleFactor != globalFactor) {
            if (Math.abs(globalFactor - 1.0F) < 1.0E-6F) {
               for (AbstractDamageablePart part : this) {
                  part.setMaxHealth(part.initialMaxHealth);
               }

               this.prevScaleFactor = globalFactor;
               return;
            }

            if (FirstAidConfig.GENERAL.debug.get()) {
               FirstAid.LOGGER.info("Starting health scaling factor {} -> {} (max health {})", this.prevScaleFactor, globalFactor, player.getMaxHealth());
            }

            int reduced = 0;
            int added = 0;
            float expectedNewMaxHealth = 0.0F;
            int newMaxHealth = 0;

            for (AbstractDamageablePart part : this) {
               float floatResult = part.initialMaxHealth * globalFactor;
               expectedNewMaxHealth += floatResult;
               int result = (int)floatResult;
               if (result % 2 == 1) {
                  int partMaxHealth = part.getMaxHealth();
                  if (part.currentHealth < partMaxHealth && reduced < 4) {
                     result--;
                     reduced++;
                  } else if (part.currentHealth > partMaxHealth && added < 4) {
                     result++;
                     added++;
                  } else if (reduced > added) {
                     result++;
                     added++;
                  } else {
                     result--;
                     reduced++;
                  }
               }

               newMaxHealth += result;
               if (FirstAidConfig.GENERAL.debug.get()) {
                  FirstAid.LOGGER.info("Part {} max health: {} initial; {} old; {} new", part.part.name(), part.initialMaxHealth, part.getMaxHealth(), result);
               }

               part.setMaxHealth(result);
            }

            if (Math.abs(expectedNewMaxHealth - newMaxHealth) >= 2.0F) {
               if (FirstAidConfig.GENERAL.debug.get()) {
                  FirstAid.LOGGER.info("Entering second stage - diff {}", Math.abs(expectedNewMaxHealth - newMaxHealth));
               }

               List<AbstractDamageablePart> prioList = new ArrayList<>();

               for (AbstractDamageablePart part : this) {
                  prioList.add(part);
               }

               prioList.sort(Comparator.comparingInt(AbstractDamageablePart::getMaxHealth));

               for (AbstractDamageablePart part : prioList) {
                  int maxHealth = part.getMaxHealth();
                  if (FirstAidConfig.GENERAL.debug.get()) {
                     FirstAid.LOGGER.info("Part {}: Second stage with total diff {}", part.part.name(), Math.abs(expectedNewMaxHealth - newMaxHealth));
                  }

                  if (expectedNewMaxHealth > newMaxHealth) {
                     part.setMaxHealth(maxHealth + 2);
                     newMaxHealth += part.getMaxHealth() - maxHealth;
                  } else if (expectedNewMaxHealth < newMaxHealth) {
                     part.setMaxHealth(maxHealth - 2);
                     newMaxHealth -= maxHealth - part.getMaxHealth();
                  }

                  if (Math.abs(expectedNewMaxHealth - newMaxHealth) < 2.0F) {
                     break;
                  }
               }
            }
         }

         this.prevScaleFactor = globalFactor;
      }
   }

   @Override
   public void scheduleResync() {
      if (this.resyncTimer == -1 || this.resyncTimer > 3) {
         this.resyncTimer = 3;
      }
   }

   @Override
   public boolean hasNoCritical() {
      return this.noCritical;
   }

   private boolean isPainSuppressed(Player player) {
      return this.morphineTicksLeft > 0 || player.hasEffect(RegistryObjects.MORPHINE_EFFECT) || player.hasEffect(RegistryObjects.PAINKILLER_EFFECT);
   }

   private void tickPendingMedicineActivations(Player player) {
      boolean changed = false;
      if (this.pendingPainkillerTicks > 0) {
         this.pendingPainkillerTicks--;
         if (this.pendingPainkillerTicks == 0) {
            player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT, getPainkillerDuration(), 0, false, false));
            changed = true;
         }
      }

      if (this.pendingMorphineDelayTicks > 0) {
         this.pendingMorphineDelayTicks--;
         if (this.pendingMorphineDelayTicks == 0 && this.pendingMorphineEffectTicks > 0) {
            int duration = this.pendingMorphineEffectTicks;
            this.pendingMorphineEffectTicks = 0;
            player.addEffect(new MobEffectInstance(RegistryObjects.MORPHINE_EFFECT, duration, 0, false, false));
            player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT, duration, 0, false, false));
            changed = true;
         }
      }

      if (changed) {
         this.scheduleResync();
      }
   }

   private void updateMedicalState(Player player) {
      boolean previousUnconsciousState = this.isUnconscious();
      int previousPainLevel = this.painLevel;
      int previousAdrenalineLevel = this.adrenalineLevel;
      int previousAdrenalineTicks = this.adrenalineTicks;
      float previousSuppressionIntensity = this.suppressionIntensity;
      int previousSuppressionHoldTicks = this.suppressionHoldTicks;
      int previousUnconsciousTicks = this.unconsciousTicks;
      boolean previousCriticalCondition = this.criticalConditionActive;
      boolean previousGiveUpState = this.unconsciousAllowsGiveUp;
      boolean previousDeathState = this.unconsciousCausesDeath;
      String previousUnconsciousReasonKey = this.unconsciousReasonKey;
      this.painLevel = this.calculatePainLevel();
      this.tickSuppressionState();
      if (this.criticalConditionActive && !this.hasCriticalPartCollapsed()) {
         this.criticalConditionActive = false;
         if (this.unconsciousCausesDeath) {
            this.unconsciousAllowsGiveUp = false;
            this.unconsciousCausesDeath = false;
            if (this.unconsciousReasonKey.equals("firstaid.gui.critical_condition")) {
               this.unconsciousReasonKey = "firstaid.gui.stabilizing";
            }
         }
      }

      if (this.unconsciousTicks > 0) {
         this.unconsciousTicks--;
      }

      if (this.criticalConditionActive && this.unconsciousTicks <= 0 && this.unconsciousCausesDeath) {
         this.clearUnconsciousPenalties(player);
         CommonUtils.killPlayerDirectly(player, null);
      } else {
         if (this.unconsciousTicks <= 0) {
            this.clearUnconsciousState();
         }

         if (previousPainLevel != this.painLevel
            || previousAdrenalineLevel != this.adrenalineLevel
            || previousAdrenalineTicks != this.adrenalineTicks
            || Float.compare(previousSuppressionIntensity, this.suppressionIntensity) != 0
            || previousSuppressionHoldTicks != this.suppressionHoldTicks
            || previousUnconsciousTicks != this.unconsciousTicks
            || previousCriticalCondition != this.criticalConditionActive
            || previousGiveUpState != this.unconsciousAllowsGiveUp
            || previousDeathState != this.unconsciousCausesDeath
            || !Objects.equals(previousUnconsciousReasonKey, this.unconsciousReasonKey)) {
            this.scheduleResync();
         }

         if (this.painLevel == 0 && this.adrenalineTicks == 0 && this.unconsciousTicks == 0 && !this.isPainSuppressed(player)) {
            this.unconsciousAllowsGiveUp = false;
            this.unconsciousCausesDeath = false;
            this.unconsciousReasonKey = "";
         }

         if (this.collapseAnimationTicks > 0) {
            this.collapseAnimationTicks--;
         }

         if (previousUnconsciousState != this.isUnconscious()) {
            if (this.isUnconscious()) {
               this.collapseAnimationTicks = 12;
               this.collapsePlacementPending = true;
            } else {
               this.collapseAnimationTicks = 0;
               this.collapsePlacementPending = false;
            }

            player.refreshDimensions();
         }
      }
   }

   private int calculatePainLevel() {
      boolean hasInjury = false;
      int fullyLostParts = 0;
      float maxSeverity = 0.0F;
      float weightedSeverity = 0.0F;
      float totalWeight = 0.0F;

      for (AbstractDamageablePart part : this) {
         float missingHealth = part.getMaxHealth() - part.currentHealth;
         if (!(missingHealth <= 0.0F)) {
            hasInjury = true;
            float injuryRatio = missingHealth / part.getMaxHealth();
            if (part.currentHealth <= 0.0F) {
               fullyLostParts++;
               injuryRatio = part.canCauseDeath ? 1.0F : 0.85F;
            }

            if (part.canCauseDeath && injuryRatio >= 0.55F) {
               injuryRatio = Math.min(1.0F, injuryRatio + 0.15F);
            }

            float weight = part.canCauseDeath ? 1.35F : 1.0F;
            maxSeverity = Math.max(maxSeverity, injuryRatio);
            weightedSeverity += injuryRatio * weight;
            totalWeight += weight;
         }
      }

      if (!hasInjury) {
         return 0;
      } else if (!FirstAid.dynamicPainEnabled) {
         return 1;
      } else {
         float averageSeverity = totalWeight <= 0.0F ? 0.0F : weightedSeverity / totalWeight;
         float combinedSeverity = Math.min(1.0F, maxSeverity * 0.65F + averageSeverity * 0.35F);
         int painLevel = Math.max(1, Math.min(5, 1 + (int)Math.floor(combinedSeverity * 4.9999F)));
         return fullyLostParts < 3 && painLevel >= 5 ? 4 : painLevel;
      }
   }

   private int calculateAdrenalineLevel(int ticks) {
      if (ticks >= 140) {
         return 3;
      } else if (ticks >= 70) {
         return 2;
      } else {
         return ticks >= 20 ? 1 : 0;
      }
   }

   private void tickSuppressionState() {
      if (this.suppressionHoldTicks > 0) {
         this.suppressionHoldTicks--;
         this.suppressionDecayTicker = 0;
      } else if (this.suppressionIntensity > 0.0F) {
         this.suppressionDecayTicker++;
         if (this.suppressionDecayTicker >= 4) {
            this.suppressionDecayTicker = 0;
            this.suppressionIntensity = Math.max(0.0F, this.suppressionIntensity - 0.03F);
         }
      } else {
         this.suppressionDecayTicker = 0;
      }

      this.refreshSuppressionSnapshot();
   }

   private void refreshSuppressionSnapshot() {
      this.adrenalineTicks = Math.round(Mth.clamp(this.suppressionIntensity, 0.0F, 1.0F) * 200.0F);
      this.adrenalineLevel = this.calculateAdrenalineLevel(this.adrenalineTicks);
   }

   private void applyUnconsciousPenalties(Player player) {
      player.setSprinting(false);
      player.stopUsingItem();
      player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 5, 0, false, false));
      this.updateUnconsciousAttributes(player, true);
      player.setPose(this.getUnconsciousPose(player));
      if (this.collapsePlacementPending) {
         this.collapsePlacementPending = false;
         this.placePlayerForCollapse(player);
      }
   }

   private void clearUnconsciousPenalties(Player player) {
      this.updateUnconsciousAttributes(player, false);
      player.setPose(Pose.STANDING);
   }

   @Nullable
   private AbstractDamageablePart getFirstCriticalRescueTarget() {
      for (AbstractDamageablePart part : this) {
         if (part.canCauseDeath && part.currentHealth > 0.0F) {
            return part;
         }
      }

      return null;
   }

   private void rescueCriticalParts(float restoredHealth) {
      for (AbstractDamageablePart part : this) {
         if (part.canCauseDeath && part.currentHealth <= 0.0F) {
            part.currentHealth = Math.min((float)part.getMaxHealth(), restoredHealth);
         }
      }
   }

   private void rescueNonCriticalZeroParts(float restoredHealth) {
      for (AbstractDamageablePart part : this) {
         if (!part.canCauseDeath && part.currentHealth <= 0.0F) {
            part.currentHealth = Math.min((float)part.getMaxHealth(), restoredHealth);
         }
      }
   }

   private boolean hasCriticalPartCollapsed() {
      for (AbstractDamageablePart part : this) {
         if (part.canCauseDeath && part.currentHealth <= 0.0F) {
            return true;
         }
      }

      return false;
   }

   private boolean hasNoRemainingBodyHealth() {
      for (AbstractDamageablePart part : this) {
         if (part.currentHealth > 0.0F) {
            return false;
         }
      }

      return true;
   }

   private void setUnconsciousState(int ticks, boolean allowsGiveUp, boolean causesDeath, String reasonKey) {
      this.unconsciousTicks = Math.max(this.unconsciousTicks, ticks);
      this.unconsciousAllowsGiveUp = allowsGiveUp;
      this.unconsciousCausesDeath = causesDeath;
      this.unconsciousReasonKey = reasonKey;
   }

   private void clearUnconsciousState() {
      this.unconsciousTicks = 0;
      this.unconsciousAllowsGiveUp = false;
      this.unconsciousCausesDeath = false;
      this.unconsciousReasonKey = "";
      this.collapseAnimationTicks = 0;
      this.collapsePlacementPending = false;
   }

   private void placePlayerForCollapse(Player player) {
      Vec3 origin = player.position();
      Vec3 adjustedOrigin = this.getRaisedCollapseOrigin(player, origin);
      Vec3 target = this.findCollapsePlacement(player, adjustedOrigin);
      if (target == null && !adjustedOrigin.equals(origin)) {
         target = this.findCollapsePlacement(player, origin);
      }

      if (target != null) {
         player.setPos(target.x, target.y, target.z);
      }
   }

   public static EntityDimensions getUnconsciousDimensions(boolean cramped) {
      return cramped ? CRAMPED_UNCONSCIOUS_DIMENSIONS : UNCONSCIOUS_DIMENSIONS;
   }

   public boolean shouldUseCrampedUnconsciousDimensions(Player player) {
      return this.isUnconscious() && !this.canOccupySpace(player, player.position(), UNCONSCIOUS_DIMENSIONS, false);
   }

   private Vec3 getRaisedCollapseOrigin(Player player, Vec3 origin) {
      if (this.canOccupyCollapseSpace(player, origin, false)) {
         return origin;
      } else {
         Vec3 raisedOrigin = origin.add(0.0, 1.0, 0.0);
         return this.canOccupyCollapseSpace(player, raisedOrigin, true) ? raisedOrigin : origin;
      }
   }

   private Vec3 findCollapsePlacement(Player player, Vec3 origin) {
      Vec3 bestTarget = null;
      double bestDistance = Double.MAX_VALUE;
      int bestManhattan = Integer.MAX_VALUE;

      for (int dz = -2; dz <= 2; dz++) {
         for (int dx = -2; dx <= 2; dx++) {
            Vec3 candidate = origin.add(dx, 0.0, dz);
            if (this.canOccupyCollapseSpace(player, candidate, true)) {
               double distance = dx * dx + dz * dz;
               int manhattan = Math.abs(dx) + Math.abs(dz);
               if (distance < bestDistance
                  || distance == bestDistance && manhattan < bestManhattan
                  || distance == bestDistance && manhattan == bestManhattan && this.isDeterministicallyEarlier(candidate, bestTarget)) {
                  bestTarget = candidate;
                  bestDistance = distance;
                  bestManhattan = manhattan;
               }
            }
         }
      }

      return bestTarget;
   }

   private boolean canOccupyCollapseSpace(Player player, Vec3 position, boolean requireSupport) {
      return this.canOccupySpace(player, position, UNCONSCIOUS_DIMENSIONS, requireSupport);
   }

   private boolean canOccupySpace(Player player, Vec3 position, EntityDimensions dimensions, boolean requireSupport) {
      AABB boundingBox = dimensions.makeBoundingBox(position.x, position.y, position.z);
      return !player.level().noCollision(player, boundingBox) ? false : !requireSupport || this.hasCollapseSupport(player, boundingBox);
   }

   private boolean hasCollapseSupport(Player player, AABB boundingBox) {
      return !player.level().noCollision(player, boundingBox.move(0.0, -0.125, 0.0));
   }

   private boolean isDeterministicallyEarlier(Vec3 candidate, Vec3 currentBest) {
      if (currentBest == null) {
         return true;
      } else {
         return candidate.z != currentBest.z ? candidate.z < currentBest.z : candidate.x < currentBest.x;
      }
   }

   private Pose getUnconsciousPose(Player player) {
      return this.shouldUseCrampedUnconsciousDimensions(player) ? Pose.CROUCHING : Pose.SWIMMING;
   }

   private void updateUnconsciousAttributes(Player player, boolean unconscious) {
      AttributeMap attributeMap = player.getAttributes();
      this.updateUnconsciousModifier(attributeMap, Attributes.MOVEMENT_SPEED, unconscious);
      this.updateUnconsciousModifier(attributeMap, Attributes.JUMP_STRENGTH, unconscious);
      this.updateUnconsciousModifier(attributeMap, Attributes.ATTACK_SPEED, unconscious);
      this.updateUnconsciousModifier(attributeMap, Attributes.BLOCK_BREAK_SPEED, unconscious);
      this.updateUnconsciousModifier(attributeMap, Attributes.BLOCK_INTERACTION_RANGE, unconscious);
   }

   private void updateUnconsciousModifier(AttributeMap map, Holder<Attribute> attribute, boolean unconscious) {
      AttributeInstance instance = map.getInstance(attribute);
      if (instance != null) {
         if (unconscious) {
            if (!instance.hasModifier(ATTR_UNCONSCIOUS)) {
               instance.addTransientModifier(new AttributeModifier(ATTR_UNCONSCIOUS, -1.0, Operation.ADD_MULTIPLIED_TOTAL));
            }
         } else if (instance.hasModifier(ATTR_UNCONSCIOUS)) {
            instance.removeModifier(ATTR_UNCONSCIOUS);
         }
      }
   }
}

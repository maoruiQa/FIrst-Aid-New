/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.common.damagesystem;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.api.enums.EnumDebuffSlot;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.common.CapProvider;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.compat.playerrevive.PRCompatManager;
import ichttt.mods.firstaid.common.damagesystem.debuff.SharedDebuff;
import ichttt.mods.firstaid.common.registries.FirstAidRegistryLookups;
import ichttt.mods.firstaid.common.registries.LookupReloadListener;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class PlayerDamageModel extends AbstractPlayerDamageModel implements LookupReloadListener {
    private static final int MAX_PAIN_LEVEL = 5;
    private static final int MAX_ADRENALINE_LEVEL = 3;
    private static final int MAX_ADRENALINE_TICKS = 200;
    private static final float MAX_SUPPRESSION_INTENSITY = 1.0F;
    private static final float ADRENALINE_ABSORPTION_AMOUNT = 8.0F;
    private static final float ADRENALINE_INJECTION_SUPPRESSION_STRENGTH = 0.35F;
    private static final float SUPPRESSION_GAIN_MULTIPLIER = 0.48F;
    private static final int SUPPRESSION_HOLD_TICKS = 20 * 4;
    private static final float SUPPRESSION_DECAY_STEP = 0.03F;
    private static final int SUPPRESSION_DECAY_INTERVAL = 4;
    private static final int ADRENALINE_DURATION_TICKS = 20 * 35;
    private static final int PAINKILLER_DURATION_TICKS = 20 * 120;
    private static final int PAINKILLER_ACTIVATION_DELAY_TICKS = 20 * 30;
    private static final int MORPHINE_ACTIVATION_DELAY_TICKS = 20 * 10;
    private static final int CRITICAL_UNCONSCIOUS_TICKS = 20 * 150;
    private static final int RESCUE_DURATION_TICKS = 20 * 8;
    private static final int DEFIBRILLATOR_RESCUE_DURATION_TICKS = 20 * 3;
    private static final int EXECUTION_DURATION_TICKS = 20 * 5;
    private static final float CRITICAL_DOWNED_CRITICAL_PART_DAMAGE_MULTIPLIER = 0.1F;
    private static final double RESCUE_RANGE = 3.0D;
    private static final int COLLAPSE_ANIMATION_TICKS = 12;
    private static final int COLLAPSE_SEARCH_RADIUS = 2;
    private static final double COLLAPSE_SUPPORT_PROBE_DEPTH = 0.125D;
    private static final EntityDimensions UNCONSCIOUS_DIMENSIONS = EntityDimensions.scalable(1.4F, 1.0F);
    private static final EntityDimensions CRAMPED_UNCONSCIOUS_DIMENSIONS = EntityDimensions.scalable(0.6F, 1.0F);
    private static final UUID UNCONSCIOUS_MOVEMENT_ID = UUID.fromString("4db2b47e-a88d-4af6-88bf-dd4d1ed6e001");
    private static final UUID UNCONSCIOUS_ATTACK_ID = UUID.fromString("4db2b47e-a88d-4af6-88bf-dd4d1ed6e002");
    private static final String UNCONSCIOUS_REASON_NONE = "";
    private static final String UNCONSCIOUS_REASON_CRITICAL = "firstaid.gui.critical_condition";
    private static final String UNCONSCIOUS_REASON_RECOVERING = "firstaid.gui.stabilizing";

    private final Set<SharedDebuff> sharedDebuffs = new HashSet<>();
    private int morphineTicksLeft = 0;
    private int pendingPainkillerTicks = 0;
    private int pendingMorphineDelayTicks = 0;
    private int pendingMorphineEffectTicks = 0;
    private int sleepBlockTicks = 0;
    private float prevHealthCurrent = -1F;
    private float prevScaleFactor;
    private final boolean noCritical;
    private boolean needsMorphineUpdate = false;
    private int resyncTimer = -1;
    private int painLevel = 0;
    private int adrenalineLevel = 0;
    private int adrenalineTicks = 0;
    private int adrenalineHeartbeatTriggerId = 0;
    private float suppressionIntensity = 0.0F;
    private int suppressionHoldTicks = 0;
    private int suppressionDecayTicker = 0;
    private int unconsciousTicks = 0;
    private boolean criticalConditionActive = false;
    private boolean unconsciousAllowsGiveUp = false;
    private boolean unconsciousCausesDeath = false;
    private String unconsciousReasonKey = UNCONSCIOUS_REASON_NONE;
    private int collapseAnimationTicks = 0;
    private boolean collapsePlacementPending = false;
    private boolean externalRevivePending = false;

    public PlayerDamageModel() {
        super(new DamageablePart(FirstAidConfig.SERVER.maxHealthHead.get(),      FirstAidConfig.SERVER.causeDeathHead.get(),  EnumPlayerPart.HEAD),
              new DamageablePart(FirstAidConfig.SERVER.maxHealthLeftArm.get(),   false,                         EnumPlayerPart.LEFT_ARM),
              new DamageablePart(FirstAidConfig.SERVER.maxHealthLeftLeg.get(),   false,                         EnumPlayerPart.LEFT_LEG),
              new DamageablePart(FirstAidConfig.SERVER.maxHealthLeftFoot.get(),  false,                         EnumPlayerPart.LEFT_FOOT),
              new DamageablePart(FirstAidConfig.SERVER.maxHealthBody.get(),      FirstAidConfig.SERVER.causeDeathBody.get(),  EnumPlayerPart.BODY),
              new DamageablePart(FirstAidConfig.SERVER.maxHealthRightArm.get(),  false,                         EnumPlayerPart.RIGHT_ARM),
              new DamageablePart(FirstAidConfig.SERVER.maxHealthRightLeg.get(),  false,                         EnumPlayerPart.RIGHT_LEG),
              new DamageablePart(FirstAidConfig.SERVER.maxHealthRightFoot.get(), false,                         EnumPlayerPart.RIGHT_FOOT));
        noCritical = !FirstAidConfig.SERVER.causeDeathBody.get() && !FirstAidConfig.SERVER.causeDeathHead.get();
        FirstAidRegistryLookups.registerReloadListener(this);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tagCompound = new CompoundTag();
        tagCompound.put("head", HEAD.serializeNBT());
        tagCompound.put("leftArm", LEFT_ARM.serializeNBT());
        tagCompound.put("leftLeg", LEFT_LEG.serializeNBT());
        tagCompound.put("leftFoot", LEFT_FOOT.serializeNBT());
        tagCompound.put("body", BODY.serializeNBT());
        tagCompound.put("rightArm", RIGHT_ARM.serializeNBT());
        tagCompound.put("rightLeg", RIGHT_LEG.serializeNBT());
        tagCompound.put("rightFoot", RIGHT_FOOT.serializeNBT());
        tagCompound.putBoolean("hasTutorial", hasTutorial);
        tagCompound.putInt("pendingPainkillerTicks", pendingPainkillerTicks);
        tagCompound.putInt("pendingMorphineDelayTicks", pendingMorphineDelayTicks);
        tagCompound.putInt("pendingMorphineEffectTicks", pendingMorphineEffectTicks);
        tagCompound.putInt("painLevel", painLevel);
        tagCompound.putInt("adrenalineLevel", adrenalineLevel);
        tagCompound.putInt("adrenalineTicks", adrenalineTicks);
        tagCompound.putInt("adrenalineHeartbeatTriggerId", adrenalineHeartbeatTriggerId);
        tagCompound.putFloat("suppressionIntensity", suppressionIntensity);
        tagCompound.putInt("suppressionHoldTicks", suppressionHoldTicks);
        tagCompound.putInt("suppressionDecayTicker", suppressionDecayTicker);
        tagCompound.putInt("unconsciousTicks", unconsciousTicks);
        tagCompound.putBoolean("criticalConditionActive", criticalConditionActive);
        tagCompound.putBoolean("unconsciousAllowsGiveUp", unconsciousAllowsGiveUp);
        tagCompound.putBoolean("unconsciousCausesDeath", unconsciousCausesDeath);
        tagCompound.putBoolean("externalRevivePending", externalRevivePending);
        tagCompound.putInt("collapseAnimationTicks", collapseAnimationTicks);
        if (!unconsciousReasonKey.isEmpty()) {
            tagCompound.putString("unconsciousReasonKey", unconsciousReasonKey);
        }
        return tagCompound;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        HEAD.deserializeNBT((CompoundTag) nbt.get("head"));
        LEFT_ARM.deserializeNBT((CompoundTag) nbt.get("leftArm"));
        LEFT_LEG.deserializeNBT((CompoundTag) nbt.get("leftLeg"));
        LEFT_FOOT.deserializeNBT((CompoundTag) nbt.get("leftFoot"));
        BODY.deserializeNBT((CompoundTag) nbt.get("body"));
        RIGHT_ARM.deserializeNBT((CompoundTag) nbt.get("rightArm"));
        RIGHT_LEG.deserializeNBT((CompoundTag) nbt.get("rightLeg"));
        RIGHT_FOOT.deserializeNBT((CompoundTag) nbt.get("rightFoot"));
        if (nbt.contains("morphineTicks")) { //legacy - we still have to read it
            morphineTicksLeft = nbt.getInt("morphineTicks");
            needsMorphineUpdate = true;
        }
        if (nbt.contains("hasTutorial")) {
            hasTutorial = nbt.getBoolean("hasTutorial");
        }
        pendingPainkillerTicks = nbt.getInt("pendingPainkillerTicks");
        pendingMorphineDelayTicks = nbt.getInt("pendingMorphineDelayTicks");
        pendingMorphineEffectTicks = nbt.getInt("pendingMorphineEffectTicks");
        painLevel = nbt.getInt("painLevel");
        adrenalineLevel = nbt.getInt("adrenalineLevel");
        adrenalineTicks = nbt.getInt("adrenalineTicks");
        adrenalineHeartbeatTriggerId = nbt.getInt("adrenalineHeartbeatTriggerId");
        suppressionIntensity = Mth.clamp(nbt.getFloat("suppressionIntensity"), 0.0F, MAX_SUPPRESSION_INTENSITY);
        suppressionHoldTicks = nbt.getInt("suppressionHoldTicks");
        suppressionDecayTicker = nbt.getInt("suppressionDecayTicker");
        unconsciousTicks = nbt.getInt("unconsciousTicks");
        criticalConditionActive = nbt.getBoolean("criticalConditionActive");
        unconsciousAllowsGiveUp = nbt.contains("unconsciousAllowsGiveUp") ? nbt.getBoolean("unconsciousAllowsGiveUp") : criticalConditionActive;
        unconsciousCausesDeath = nbt.contains("unconsciousCausesDeath") ? nbt.getBoolean("unconsciousCausesDeath") : criticalConditionActive;
        externalRevivePending = nbt.getBoolean("externalRevivePending");
        unconsciousReasonKey = nbt.contains("unconsciousReasonKey") ? nbt.getString("unconsciousReasonKey") : (criticalConditionActive ? UNCONSCIOUS_REASON_CRITICAL : UNCONSCIOUS_REASON_NONE);
        collapseAnimationTicks = nbt.getInt("collapseAnimationTicks");
        collapsePlacementPending = false;
        refreshSuppressionSnapshot();
    }

    @Override
    public void onLookupsReloaded() {
        FirstAid.LOGGER.debug("Reloaded lookups");
        sharedDebuffs.clear();
        for (EnumDebuffSlot debuffSlot : EnumDebuffSlot.values()) {
            IDebuff[] debuffs = FirstAidRegistryLookups.getDebuffs(debuffSlot);
            for (EnumPlayerPart playerPart : debuffSlot.playerParts) {
                getFromEnum(playerPart).loadDebuffInfo(debuffs);
            }
            for (IDebuff debuff : debuffs) {
                if (debuff instanceof SharedDebuff sharedDebuff) {
                    sharedDebuffs.add(sharedDebuff);
                }
            }
        }
    }

    @Override
    public void tick(Level world, Player player) {
        if (isDead(player))
            return;
        world.getProfiler().push("FirstAidPlayerModel");
        if (sleepBlockTicks > 0)
            sleepBlockTicks--;
        else if (sleepBlockTicks < 0)
            throw new RuntimeException("Negative sleepBlockTicks " + sleepBlockTicks);

        float newCurrentHealth = calculateNewCurrentHealth(player);
        if (Float.isNaN(newCurrentHealth)) {
            FirstAid.LOGGER.warn("New current health is not a number, setting it to 0!");
            newCurrentHealth = 0F;
        }
        if (newCurrentHealth <= 0F) {
            if (!world.isClientSide && (hasNoRemainingBodyHealth() || hasAllCriticalPartsCollapsed())) {
                clearUnconsciousState();
                clearUnconsciousPenalties(player);
                CommonUtils.killPlayerDirectly(player, null);
            }
            FirstAid.LOGGER.error("Got {} health left, but isn't marked as dead!", newCurrentHealth);
            world.getProfiler().pop();
            return;
        }
        if (!world.isClientSide && resyncTimer != -1) {
            resyncTimer--;
            if (resyncTimer == 0) {
                resyncTimer = -1;
                CommonUtils.syncDamageModel((ServerPlayer) player);
            }
        }

        if (Float.isInfinite(newCurrentHealth)) {
            FirstAid.LOGGER.error("Error calculating current health: Value was infinite"); //Shouldn't happen anymore, but let's be safe
        } else {
            syncVanillaHealth(player, newCurrentHealth);
            prevHealthCurrent = newCurrentHealth;
        }

        if (!this.hasTutorial)
            this.hasTutorial = CapProvider.tutorialDone.contains(player.getName().getString());

        runScaleLogic(player);

        MobEffect morphineEffect = RegistryObjects.MORPHINE_EFFECT.get();
        MobEffect painkillerEffect = RegistryObjects.PAINKILLER_EFFECT.get();
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

        if (!world.isClientSide) {
            tickPendingMedicineActivations(player);
            updateMedicalState(player);
            if (isUnconscious()) {
                applyUnconsciousPenalties(player);
            } else {
                clearUnconsciousPenalties(player);
            }
        }

        boolean painSuppressed = morphine != null || painkiller != null;
        boolean healingStateChanged = false;
        world.getProfiler().push("PartDebuffs");
        for (AbstractDamageablePart part : this) {
            float previousHealth = part.currentHealth;
            boolean hadHealer = part.activeHealer != null;
            if (part instanceof DamageablePart damageablePart) {
                damageablePart.tick(world, player, !painSuppressed, isUnconscious());
            } else {
                part.tick(world, player, !painSuppressed);
            }
            if (!world.isClientSide && (Float.compare(previousHealth, part.currentHealth) != 0 || hadHealer != (part.activeHealer != null))) {
                healingStateChanged = true;
            }
        }
        if (!painSuppressed && !world.isClientSide)
            sharedDebuffs.forEach(sharedDebuff -> sharedDebuff.tick(player));
        if (healingStateChanged && player instanceof ServerPlayer serverPlayer) {
            CommonUtils.syncDamageModel(serverPlayer);
        }
        world.getProfiler().pop();
        world.getProfiler().pop();
    }

    public void syncVanillaHealth(Player player) {
        float newCurrentHealth = calculateNewCurrentHealth(player);
        if (Float.isNaN(newCurrentHealth)) {
            FirstAid.LOGGER.warn("New current health is not a number, setting it to 0!");
            newCurrentHealth = 0F;
        }
        if (Float.isInfinite(newCurrentHealth)) {
            FirstAid.LOGGER.error("Error calculating current health: Value was infinite");
            return;
        }
        syncVanillaHealth(player, newCurrentHealth);
        prevHealthCurrent = newCurrentHealth;
    }

    private void syncVanillaHealth(Player player, float newCurrentHealth) {
        if (newCurrentHealth != prevHealthCurrent) {
            CommonUtils.runWithoutSetHealthInterception(() -> player.setHealth(newCurrentHealth));
        }
    }

    public static int getRandMorphineDuration() { //Tweak tooltip event when changing as well
        return ((EventHandler.RAND.nextInt(5) * 20 * 15) + 20 * 330);
    }

    public static int getMorphineActivationDelay() {
        return FirstAid.scaleMedicalTimingTicks(MORPHINE_ACTIVATION_DELAY_TICKS);
    }

    public static int getPainkillerDuration() {
        return PAINKILLER_DURATION_TICKS * 2;
    }

    public static int getPainkillerActivationDelay() {
        return FirstAid.scaleMedicalTimingTicks(PAINKILLER_ACTIVATION_DELAY_TICKS);
    }

    public static int getAdrenalineDuration() {
        return ADRENALINE_DURATION_TICKS;
    }

    public static float getAdrenalineAbsorptionAmount() {
        return ADRENALINE_ABSORPTION_AMOUNT;
    }

    public static int getRescueDurationTicks() {
        return RESCUE_DURATION_TICKS;
    }

    public static int getDefibrillatorRescueDurationTicks() {
        return DEFIBRILLATOR_RESCUE_DURATION_TICKS;
    }

    public static int getExecutionDurationTicks() {
        return EXECUTION_DURATION_TICKS;
    }

    public static double getRescueRange() {
        return RESCUE_RANGE;
    }

    public void queuePainkillerActivation() {
        pendingPainkillerTicks = Math.max(pendingPainkillerTicks, getPainkillerActivationDelay());
        scheduleResync();
    }

    public void queueMorphineActivation() {
        pendingMorphineDelayTicks = Math.max(pendingMorphineDelayTicks, getMorphineActivationDelay());
        pendingMorphineEffectTicks = Math.max(pendingMorphineEffectTicks, getRandMorphineDuration());
        scheduleResync();
    }

    @Deprecated
    @Override
    public void applyMorphine() {
        morphineTicksLeft = getRandMorphineDuration();
        needsMorphineUpdate = true;
    }

    @Override
    public void applyMorphine(Player player) {
        int duration = getRandMorphineDuration();
        player.addEffect(new MobEffectInstance(RegistryObjects.MORPHINE_EFFECT.get(), duration, 0, false, false));
        player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT.get(), duration, 0, false, false));
    }

    @Deprecated
    @Override
    public int getMorphineTicks() {
        return morphineTicksLeft;
    }

    public void applyAdrenalineInjection(Player player) {
        MobEffectInstance activePainkiller = player.getEffect(RegistryObjects.PAINKILLER_EFFECT.get());
        MobEffectInstance activeAbsorption = player.getEffect(MobEffects.ABSORPTION);
        MobEffectInstance activeHaste = player.getEffect(MobEffects.DIG_SPEED);
        MobEffectInstance activeStrength = player.getEffect(MobEffects.DAMAGE_BOOST);
        MobEffectInstance activeSpeed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        int duration = Math.max(getAdrenalineDuration(), activePainkiller == null ? 0 : activePainkiller.getDuration());
        int absorptionDuration = Math.max(getAdrenalineDuration(), activeAbsorption == null ? 0 : activeAbsorption.getDuration());
        int hasteDuration = Math.max(getAdrenalineDuration(), activeHaste == null ? 0 : activeHaste.getDuration());
        int strengthDuration = Math.max(getAdrenalineDuration(), activeStrength == null ? 0 : activeStrength.getDuration());
        int speedDuration = Math.max(getAdrenalineDuration(), activeSpeed == null ? 0 : activeSpeed.getDuration());
        player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT.get(), duration, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, absorptionDuration, Math.max(1, activeAbsorption == null ? 0 : activeAbsorption.getAmplifier()), false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, hasteDuration, Math.max(0, activeHaste == null ? 0 : activeHaste.getAmplifier()), false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, strengthDuration, Math.max(0, activeStrength == null ? 0 : activeStrength.getAmplifier()), false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, speedDuration, Math.max(0, activeSpeed == null ? 0 : activeSpeed.getAmplifier()), false, false));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 140, 0, false, false));
        registerAdrenalineNearMiss(player, ADRENALINE_INJECTION_SUPPRESSION_STRENGTH, getAdrenalineDuration());
        adrenalineHeartbeatTriggerId++;
        scheduleResync();
        if (player instanceof ServerPlayer serverPlayer) {
            CommonUtils.syncDamageModel(serverPlayer);
        }
    }

    public int getPainLevel() {
        return painLevel;
    }

    public int getAdrenalineLevel() {
        return adrenalineLevel;
    }

    public int getAdrenalineTicks() {
        return adrenalineTicks;
    }

    public int getAdrenalineHeartbeatTriggerId() {
        return adrenalineHeartbeatTriggerId;
    }

    public int getSuppressionLevel() {
        return Mth.clamp(adrenalineLevel, 0, MAX_ADRENALINE_LEVEL);
    }

    public float getSuppressionIntensity() {
        return suppressionIntensity;
    }

    public int getSuppressionHoldTicks() {
        return suppressionHoldTicks;
    }

    public int getUnconsciousTicks() {
        return unconsciousTicks;
    }

    public boolean isCriticalConditionActive() {
        return criticalConditionActive;
    }

    public boolean isUnconscious() {
        return unconsciousTicks > 0;
    }

    public boolean isCriticalDowned() {
        return criticalConditionActive && isUnconscious();
    }

    public boolean canGiveUp() {
        return isUnconscious() && unconsciousAllowsGiveUp;
    }

    public boolean refreshRescueWakeUpState(Player player) {
        if (!isRescueWakeUpRecoveryActive()) {
            return false;
        }

        int delayTicks = FirstAid.rescueWakeUpEnabled ? FirstAid.getRescueWakeUpDelayTicks() : 0;
        if (delayTicks <= 0) {
            clearUnconsciousState();
            clearUnconsciousPenalties(player);
            CommonUtils.runWithoutSetHealthInterception(() -> player.setHealth(Math.max(player.getHealth(), 1.0F)));
        } else {
            unconsciousTicks = delayTicks;
            unconsciousAllowsGiveUp = false;
            unconsciousCausesDeath = false;
            unconsciousReasonKey = UNCONSCIOUS_REASON_RECOVERING;
        }

        scheduleResync();
        return true;
    }

    public boolean canBeRescued() {
        return criticalConditionActive && isUnconscious();
    }

    public float getCollapseAnimationProgress(float partialTick) {
        if (!isUnconscious()) {
            return 1.0F;
        }
        return Mth.clamp(1.0F - ((Math.max(0.0F, collapseAnimationTicks) - Math.max(0.0F, partialTick)) / COLLAPSE_ANIMATION_TICKS), 0.0F, 1.0F);
    }

    public float getCollapseAnimationProgress() {
        return getCollapseAnimationProgress(0.0F);
    }

    public String getUnconsciousReasonKey() {
        return unconsciousReasonKey.isEmpty() ? "firstaid.gui.unconscious" : unconsciousReasonKey;
    }

    public int getUnconsciousSecondsLeft() {
        return Math.max(1, (int) Math.ceil(unconsciousTicks / 20.0D));
    }

    public float getIncomingPartDamageMultiplier(AbstractDamageablePart part) {
        return isCriticalDowned() && part.canCauseDeath ? CRITICAL_DOWNED_CRITICAL_PART_DAMAGE_MULTIPLIER : 1.0F;
    }

    public float getPainVisualStrength() {
        if (painLevel <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, painLevel / (float) MAX_PAIN_LEVEL);
    }

    public float getDeathCountdownDangerProgress() {
        if (!canGiveUp()) {
            return 0.0F;
        }
        float remaining = Math.max(0.0F, unconsciousTicks);
        float progress = 1.0F - (remaining / CRITICAL_UNCONSCIOUS_TICKS);
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    public void registerAdrenalineNearMiss(Player player, float strength) {
        registerAdrenalineNearMiss(player, strength, SUPPRESSION_HOLD_TICKS);
    }

    public void registerAdrenalineNearMiss(Player player, float strength, int holdTicks) {
        float clampedStrength = Mth.clamp(strength, 0.35F, 1.45F);
        float previousIntensity = suppressionIntensity;
        int previousHoldTicks = suppressionHoldTicks;
        int previousAdrenalineTicks = adrenalineTicks;
        float baseIntensity = 0.28F + clampedStrength * 0.24F;
        float addedIntensity = Mth.clamp(baseIntensity * SUPPRESSION_GAIN_MULTIPLIER,
                0.36F * SUPPRESSION_GAIN_MULTIPLIER,
                0.62F * SUPPRESSION_GAIN_MULTIPLIER);
        suppressionIntensity = Mth.clamp(suppressionIntensity + addedIntensity, 0.0F, MAX_SUPPRESSION_INTENSITY);
        suppressionHoldTicks = Math.max(suppressionHoldTicks, holdTicks);
        suppressionDecayTicker = 0;
        MobEffectInstance activePainkiller = player.getEffect(RegistryObjects.PAINKILLER_EFFECT.get());
        int painkillerDuration = Math.max(holdTicks, activePainkiller == null ? 0 : activePainkiller.getDuration());
        player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT.get(), painkillerDuration, 0, false, false));
        refreshSuppressionSnapshot();
        if (Float.compare(previousIntensity, suppressionIntensity) != 0 || previousHoldTicks != suppressionHoldTicks || previousAdrenalineTicks != adrenalineTicks) {
            scheduleResync();
        }
    }

    public void clearStatusEffects() {
        morphineTicksLeft = 0;
        pendingPainkillerTicks = 0;
        pendingMorphineDelayTicks = 0;
        pendingMorphineEffectTicks = 0;
        painLevel = 0;
        adrenalineLevel = 0;
        adrenalineTicks = 0;
        suppressionIntensity = 0.0F;
        suppressionHoldTicks = 0;
        suppressionDecayTicker = 0;
        unconsciousTicks = 0;
        criticalConditionActive = false;
        unconsciousAllowsGiveUp = false;
        unconsciousCausesDeath = false;
        unconsciousReasonKey = UNCONSCIOUS_REASON_NONE;
        collapseAnimationTicks = 0;
        collapsePlacementPending = false;
        externalRevivePending = false;
    }

    public void markExternalRevivePending(Player player) {
        externalRevivePending = true;
        criticalConditionActive = false;
        clearUnconsciousState();
        clearUnconsciousPenalties(player);
        player.refreshDimensions();
        scheduleResync();
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            CommonUtils.syncDamageModel(serverPlayer);
        }
    }

    public void handlePostDamage(Player player) {
        if (hasNoRemainingBodyHealth() || hasAllCriticalPartsCollapsed()) {
            criticalConditionActive = false;
            clearUnconsciousState();
            clearUnconsciousPenalties(player);
            scheduleResync();
            return;
        }
        if (criticalConditionActive || !hasCriticalPartCollapsed()) {
            return;
        }
        criticalConditionActive = true;
        setUnconsciousState(CRITICAL_UNCONSCIOUS_TICKS, true, true, UNCONSCIOUS_REASON_CRITICAL);
        painLevel = Math.max(painLevel, MAX_PAIN_LEVEL);
        CommonUtils.runWithoutSetHealthInterception(() -> player.setHealth(Math.max(player.getHealth(), 1.0F)));
        scheduleResync();
    }

    public boolean rescueFromCriticalState(Player player, @Nullable AbstractPartHealer healer) {
        return rescueFromCriticalState(player, healer, FirstAid.rescueWakeUpEnabled);
    }

    public boolean rescueFromCriticalState(Player player, @Nullable AbstractPartHealer healer, boolean keepWakeUpDelay) {
        return performCriticalRescue(player, healer, keepWakeUpDelay, 0.0F, 1.0F);
    }

    public boolean defibrillatorRescueFromCriticalState(Player player, boolean keepWakeUpDelay) {
        boolean rescued = performCriticalRescue(player, null, keepWakeUpDelay, 2.0F, 0.4F);
        if (rescued) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 20, 0, false, false));
        }
        return rescued;
    }

    public void giveUp(Player player) {
        if (!canGiveUp()) {
            return;
        }
        criticalConditionActive = false;
        clearUnconsciousState();
        clearUnconsciousPenalties(player);
        scheduleResync();
        CommonUtils.killPlayer(this, player, null);
    }

    public void refreshPainState(Player player) {
        int previousPainLevel = painLevel;
        painLevel = calculatePainLevel();
        if (previousPainLevel != painLevel) {
            scheduleResync();
            if (player instanceof ServerPlayer serverPlayer) {
                CommonUtils.syncDamageModel(serverPlayer);
            }
        }
    }

    @Override
    @Nonnull
    public Iterator<AbstractDamageablePart> iterator() {
        return new Iterator<AbstractDamageablePart>() {
            private byte count = 0;
            @Override
            public boolean hasNext() {
                return count < 8;
            }

            @Override
            public AbstractDamageablePart next() {
                if (count >= 8)
                    throw new NoSuchElementException();
                AbstractDamageablePart part = getFromEnum(EnumPlayerPart.VALUES[count]);
                count++;
                return part;
            }
        };
    }

    private float calculateNewCurrentHealth(Player player) {
        float currentHealth = 0;
        FirstAidConfig.Server.VanillaHealthCalculationMode mode = FirstAidConfig.SERVER.vanillaHealthCalculation.get();
        if (noCritical) mode = FirstAidConfig.Server.VanillaHealthCalculationMode.AVERAGE_ALL;
        switch (mode) {
            case AVERAGE_CRITICAL:
                int maxHealth = 0;
                for (AbstractDamageablePart part : this) {
                    if (part.canCauseDeath) {
                        currentHealth += part.currentHealth;
                        maxHealth += part.getMaxHealth();
                    }
                }
                currentHealth = currentHealth / maxHealth;
                break;
            case MIN_CRITICAL:
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
                currentHealth = minimal.currentHealth / minimal.getMaxHealth();
                break;
            case AVERAGE_ALL:
                for (AbstractDamageablePart part : this)
                    currentHealth += part.currentHealth;
                currentHealth = currentHealth / getCurrentMaxHealth();
                break;
            case CRITICAL_50_PERCENT_OTHER_50_PERCENT:
                float currentNormal = 0;
                int maxNormal = 0;
                float currentCritical = 0;
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
                currentHealth = (avgCritical + avgNormal) / 2;
                break;
            default:
                throw new RuntimeException("Unknown constant " + mode);
        }
        float scaledHealth = currentHealth * player.getMaxHealth();
        if (isCriticalDowned() && hasCriticalPartCollapsed() && !hasAllCriticalPartsCollapsed()) {
            return Math.max(1.0F, scaledHealth);
        }
        return scaledHealth;
    }

    @Override
    public boolean isDead(@Nullable Player player) {
        boolean bleeding = PRCompatManager.getHandler().isBleeding(player);
        if (bleeding) {
            return true; //Technically not dead yet, but we should still return true to avoid running ticking and other logic
        }

        if (player != null && !player.isAlive())
            return true;

        if (hasNoRemainingBodyHealth() || hasAllCriticalPartsCollapsed()) {
            return true;
        }

        if (isCriticalDowned()) {
            return false;
        }

        if (this.noCritical) {
            boolean dead = true;
            for (AbstractDamageablePart part : this) {
                if (part.currentHealth > 0) {
                    dead = false;
                    break;
                }
            }
            return dead;
        } else {
            for (AbstractDamageablePart part : this) {
                if (part.canCauseDeath && part.currentHealth <= 0) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public Float getAbsorption() { //Float class because of DataManager
        float value = 0;
        for (AbstractDamageablePart part : this)
                value += part.getAbsorption();
        return value; //Autoboxing FTW
    }

    @Override
    public void setAbsorption(float absorption) {
        final float newAbsorption = absorption / 8F;
        forEach(damageablePart -> damageablePart.setAbsorption(newAbsorption));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public int getMaxRenderSize() {
        int max = 0;
        for (AbstractDamageablePart part : this) {
            int newMax;
            if (FirstAidConfig.CLIENT.overlayMode.get() == FirstAidConfig.Client.OverlayMode.NUMBERS)
                newMax = Minecraft.getInstance().font.width(HealthRenderUtils.TEXT_FORMAT.format(part.currentHealth) + "/" + part.getMaxHealth()) + 1;
            else
                newMax = (int) (((((int) (part.getMaxHealth() + 0.9999F)) + 1) / 2F) * 9F);
            max = Math.max(max, newMax);
        }
        return max;
    }

    @Override
    public void sleepHeal(Player player) {
        if (sleepBlockTicks > 0)
            return;
        CommonUtils.healAllPartsByPercentage(FirstAidConfig.SERVER.sleepHealPercentage.get(), this, player);
        refreshPainState(player);
        sleepBlockTicks = 20;
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
        player.revive();
        clearStatusEffects();
        externalRevivePending = false;
        for (AbstractDamageablePart part : this) {
            if ((part.canCauseDeath || this.noCritical) && part.currentHealth <= 0F) {
                part.currentHealth = 1F; // Set the critical health to a non-zero value
            }
        }
        if (FirstAid.rescueWakeUpEnabled && FirstAid.getRescueWakeUpDelayTicks() > 0) {
            setUnconsciousState(FirstAid.getRescueWakeUpDelayTicks(), false, false, UNCONSCIOUS_REASON_RECOVERING);
        }
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer)
            CommonUtils.syncDamageModel(serverPlayer);
    }

    @Override
    public void runScaleLogic(Player player) {
        if (FirstAidConfig.SERVER.scaleMaxHealth.get()) { //Attempt to calculate the max health of the body parts based on the maxHealth attribute
            player.level().getProfiler().push("healthscaling");
            float globalFactor = player.getMaxHealth() / 20F;
            if (prevScaleFactor != globalFactor) {
                if (FirstAidConfig.GENERAL.debug.get()) {
                    FirstAid.LOGGER.info("Starting health scaling factor {} -> {} (max health {})", prevScaleFactor, globalFactor, player.getMaxHealth());
                }
                player.level().getProfiler().push("distribution");
                int reduced = 0;
                int added = 0;
                float expectedNewMaxHealth = 0F;
                int newMaxHealth = 0;
                for (AbstractDamageablePart part : this) {
                    float floatResult = ((float) part.initialMaxHealth) * globalFactor;
                    expectedNewMaxHealth += floatResult;
                    int result = (int) floatResult;
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
                player.level().getProfiler().popPush("correcting");
                if (Math.abs(expectedNewMaxHealth - newMaxHealth) >= 2F) {
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
                            newMaxHealth += (part.getMaxHealth() - maxHealth);
                        } else if (expectedNewMaxHealth < newMaxHealth) {
                            part.setMaxHealth(maxHealth - 2);
                            newMaxHealth -= (maxHealth - part.getMaxHealth());
                        }
                        if (Math.abs(expectedNewMaxHealth - newMaxHealth) < 2F) {
                            break;
                        }
                    }
                }
                player.level().getProfiler().pop();
            }
            prevScaleFactor = globalFactor;
            player.level().getProfiler().pop();
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

    private boolean performCriticalRescue(Player player, @Nullable AbstractPartHealer healer, boolean keepWakeUpDelay, float extraCriticalHealth, float wakeUpDelayMultiplier) {
        if (!canBeRescued()) {
            return false;
        }
        rescueCriticalParts(keepWakeUpDelay ? 1.0F : 2.0F);
        if (extraCriticalHealth > 0.0F) {
            restoreDamagedCriticalParts(extraCriticalHealth);
        }
        if (!keepWakeUpDelay) {
            rescueNonCriticalZeroParts(1.0F);
        }
        criticalConditionActive = false;
        painLevel = Math.max(2, painLevel);
        if (keepWakeUpDelay) {
            AbstractDamageablePart rescueTarget = getFirstCriticalRescueTarget();
            if (healer != null && rescueTarget != null && rescueTarget.activeHealer == null) {
                rescueTarget.activeHealer = healer;
            }
            setUnconsciousState(getScaledRescueWakeUpDelayTicks(wakeUpDelayMultiplier), false, false, UNCONSCIOUS_REASON_RECOVERING);
        } else {
            clearUnconsciousState();
            clearUnconsciousPenalties(player);
            CommonUtils.runWithoutSetHealthInterception(() -> player.setHealth(Math.max(player.getHealth(), 1.0F)));
        }
        scheduleResync();
        if (player instanceof ServerPlayer serverPlayer) {
            CommonUtils.syncDamageModel(serverPlayer);
        }
        return true;
    }

    private void tickPendingMedicineActivations(Player player) {
        boolean changed = false;
        if (pendingPainkillerTicks > 0) {
            pendingPainkillerTicks--;
            if (pendingPainkillerTicks == 0) {
                player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT.get(), getPainkillerDuration(), 0, false, false));
                changed = true;
            }
        }
        if (pendingMorphineDelayTicks > 0) {
            pendingMorphineDelayTicks--;
            if (pendingMorphineDelayTicks == 0 && pendingMorphineEffectTicks > 0) {
                int duration = pendingMorphineEffectTicks;
                pendingMorphineEffectTicks = 0;
                player.addEffect(new MobEffectInstance(RegistryObjects.MORPHINE_EFFECT.get(), duration, 0, false, false));
                player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT.get(), duration, 0, false, false));
                changed = true;
            }
        }
        if (changed) {
            scheduleResync();
        }
    }

    private void updateMedicalState(Player player) {
        boolean previousUnconsciousState = isUnconscious();
        int previousPainLevel = painLevel;
        int previousAdrenalineLevel = adrenalineLevel;
        int previousAdrenalineTicks = adrenalineTicks;
        float previousSuppressionIntensity = suppressionIntensity;
        int previousSuppressionHoldTicks = suppressionHoldTicks;
        int previousUnconsciousTicks = unconsciousTicks;
        boolean previousCriticalCondition = criticalConditionActive;
        boolean previousGiveUpState = unconsciousAllowsGiveUp;
        boolean previousDeathState = unconsciousCausesDeath;
        String previousUnconsciousReasonKey = unconsciousReasonKey;
        if (resolveExternalReviveState(player)) {
            return;
        }

        painLevel = calculatePainLevel();
        tickSuppressionState();

        if (hasAllCriticalPartsCollapsed()) {
            criticalConditionActive = false;
            clearUnconsciousState();
            clearUnconsciousPenalties(player);
            CommonUtils.killPlayerDirectly(player, null);
            return;
        }

        if (criticalConditionActive && !hasCriticalPartCollapsed()) {
            criticalConditionActive = false;
            if (unconsciousCausesDeath) {
                unconsciousAllowsGiveUp = false;
                unconsciousCausesDeath = false;
                if (UNCONSCIOUS_REASON_CRITICAL.equals(unconsciousReasonKey)) {
                    unconsciousReasonKey = UNCONSCIOUS_REASON_RECOVERING;
                }
            }
        }

        if (unconsciousTicks > 0) {
            unconsciousTicks--;
        }

        if (criticalConditionActive && unconsciousTicks <= 0 && unconsciousCausesDeath) {
            clearUnconsciousPenalties(player);
            CommonUtils.killPlayerDirectly(player, null);
            return;
        }

        if (unconsciousTicks <= 0) {
            clearUnconsciousState();
        }

        if (previousPainLevel != painLevel
                || previousAdrenalineLevel != adrenalineLevel
                || previousAdrenalineTicks != adrenalineTicks
                || Float.compare(previousSuppressionIntensity, suppressionIntensity) != 0
                || previousSuppressionHoldTicks != suppressionHoldTicks
                || previousUnconsciousTicks != unconsciousTicks
                || previousCriticalCondition != criticalConditionActive
                || previousGiveUpState != unconsciousAllowsGiveUp
                || previousDeathState != unconsciousCausesDeath
                || !Objects.equals(previousUnconsciousReasonKey, unconsciousReasonKey)) {
            scheduleResync();
        }

        if (painLevel == 0 && adrenalineTicks == 0 && unconsciousTicks == 0 && !isPainSuppressed(player)) {
            unconsciousAllowsGiveUp = false;
            unconsciousCausesDeath = false;
            unconsciousReasonKey = UNCONSCIOUS_REASON_NONE;
        }

        if (collapseAnimationTicks > 0) {
            collapseAnimationTicks--;
        }

        if (previousUnconsciousState != isUnconscious()) {
            if (isUnconscious()) {
                collapseAnimationTicks = COLLAPSE_ANIMATION_TICKS;
                collapsePlacementPending = true;
            } else {
                collapseAnimationTicks = 0;
                collapsePlacementPending = false;
            }
            player.refreshDimensions();
        }
    }

    private boolean resolveExternalReviveState(Player player) {
        if (!externalRevivePending || PRCompatManager.getHandler().isBleeding(player)) {
            return false;
        }

        externalRevivePending = false;
        if (player.isAlive() && player.getHealth() > 0.0F) {
            revivePlayer(player);
        } else {
            clearStatusEffects();
            clearUnconsciousPenalties(player);
            player.refreshDimensions();
            scheduleResync();
            if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                CommonUtils.syncDamageModel(serverPlayer);
            }
        }

        return true;
    }

    private boolean isPainSuppressed(Player player) {
        return morphineTicksLeft > 0
                || player.hasEffect(RegistryObjects.MORPHINE_EFFECT.get())
                || player.hasEffect(RegistryObjects.PAINKILLER_EFFECT.get());
    }

    private int calculatePainLevel() {
        boolean hasInjury = false;
        int fullyLostParts = 0;
        float maxSeverity = 0.0F;
        float weightedSeverity = 0.0F;
        float totalWeight = 0.0F;
        for (AbstractDamageablePart part : this) {
            float missingHealth = Math.max(0.0F, part.getMaxHealth() - part.currentHealth);
            if (missingHealth <= 0F) {
                continue;
            }
            hasInjury = true;
            float injuryRatio = missingHealth / part.getMaxHealth();
            if (part.currentHealth <= 0F) {
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
        if (!hasInjury) {
            return 0;
        }
        if (!FirstAid.dynamicPainEnabled) {
            return 1;
        }
        float averageSeverity = totalWeight <= 0.0F ? 0.0F : weightedSeverity / totalWeight;
        float combinedSeverity = Math.min(1.0F, maxSeverity * 0.65F + averageSeverity * 0.35F);
        int nextPainLevel = Math.max(1, Math.min(MAX_PAIN_LEVEL, 1 + (int) Math.floor(combinedSeverity * (MAX_PAIN_LEVEL - 0.0001F))));
        if (fullyLostParts < 3 && nextPainLevel >= MAX_PAIN_LEVEL) {
            return MAX_PAIN_LEVEL - 1;
        }
        return nextPainLevel;
    }

    private int calculateAdrenalineLevel(int ticks) {
        if (ticks >= 140) {
            return 3;
        }
        if (ticks >= 70) {
            return 2;
        }
        if (ticks >= 20) {
            return 1;
        }
        return 0;
    }

    private void tickSuppressionState() {
        if (suppressionHoldTicks > 0) {
            suppressionHoldTicks--;
            suppressionDecayTicker = 0;
        } else if (suppressionIntensity > 0.0F) {
            suppressionDecayTicker++;
            if (suppressionDecayTicker >= SUPPRESSION_DECAY_INTERVAL) {
                suppressionDecayTicker = 0;
                suppressionIntensity = Math.max(0.0F, suppressionIntensity - SUPPRESSION_DECAY_STEP);
            }
        } else {
            suppressionDecayTicker = 0;
        }
        refreshSuppressionSnapshot();
    }

    private void refreshSuppressionSnapshot() {
        adrenalineTicks = Math.round(Mth.clamp(suppressionIntensity, 0.0F, MAX_SUPPRESSION_INTENSITY) * MAX_ADRENALINE_TICKS);
        adrenalineLevel = calculateAdrenalineLevel(adrenalineTicks);
    }

    private void applyUnconsciousPenalties(Player player) {
        player.setSprinting(false);
        player.stopUsingItem();
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 5, 0, false, false));
        updateUnconsciousAttributes(player, true);
        player.setForcedPose(getUnconsciousPose(player));
        if (collapsePlacementPending) {
            collapsePlacementPending = false;
            placePlayerForCollapse(player);
        }
    }

    private void clearUnconsciousPenalties(Player player) {
        updateUnconsciousAttributes(player, false);
        player.setForcedPose(null);
    }

    public void refreshClientUnconsciousPose(Player player) {
        player.setForcedPose(isUnconscious() ? getClientUnconsciousPose() : null);
    }

    private void rescueCriticalParts(float restoredHealth) {
        for (AbstractDamageablePart part : this) {
            if (part.canCauseDeath && part.currentHealth <= 0.0F) {
                part.currentHealth = Math.min(part.getMaxHealth(), restoredHealth);
            }
        }
    }

    private void rescueNonCriticalZeroParts(float restoredHealth) {
        for (AbstractDamageablePart part : this) {
            if (!part.canCauseDeath && part.currentHealth <= 0.0F) {
                part.currentHealth = Math.min(part.getMaxHealth(), restoredHealth);
            }
        }
    }

    private void restoreDamagedCriticalParts(float restoredHealth) {
        for (AbstractDamageablePart part : this) {
            if (part.canCauseDeath && part.currentHealth < part.getMaxHealth()) {
                part.currentHealth = Math.min(part.getMaxHealth(), part.currentHealth + restoredHealth);
            }
        }
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

    private boolean hasCriticalPartCollapsed() {
        for (AbstractDamageablePart part : this) {
            if (part.canCauseDeath && part.currentHealth <= 0.0F) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAllCriticalPartsCollapsed() {
        boolean hasCriticalPart = false;
        for (AbstractDamageablePart part : this) {
            if (!part.canCauseDeath) {
                continue;
            }
            hasCriticalPart = true;
            if (part.currentHealth > 0.0F) {
                return false;
            }
        }
        return hasCriticalPart;
    }

    private boolean hasNoRemainingBodyHealth() {
        for (AbstractDamageablePart part : this) {
            if (part.currentHealth > 0.0F) {
                return false;
            }
        }
        return true;
    }

    private int getScaledRescueWakeUpDelayTicks(float multiplier) {
        int delayTicks = FirstAid.getRescueWakeUpDelayTicks();
        if (delayTicks <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(delayTicks * multiplier));
    }

    private void setUnconsciousState(int ticks, boolean allowsGiveUp, boolean causesDeath, String reasonKey) {
        unconsciousTicks = ticks;
        unconsciousAllowsGiveUp = allowsGiveUp;
        unconsciousCausesDeath = causesDeath;
        unconsciousReasonKey = reasonKey;
    }

    private boolean isRescueWakeUpRecoveryActive() {
        return isUnconscious() && !criticalConditionActive && UNCONSCIOUS_REASON_RECOVERING.equals(unconsciousReasonKey);
    }

    private void clearUnconsciousState() {
        unconsciousTicks = 0;
        unconsciousAllowsGiveUp = false;
        unconsciousCausesDeath = false;
        unconsciousReasonKey = UNCONSCIOUS_REASON_NONE;
        collapseAnimationTicks = 0;
        collapsePlacementPending = false;
    }

    private void placePlayerForCollapse(Player player) {
        Vec3 origin = player.position();
        Vec3 adjustedOrigin = getRaisedCollapseOrigin(player, origin);
        Vec3 target = findCollapsePlacement(player, adjustedOrigin);
        if (target == null && !adjustedOrigin.equals(origin)) {
            target = findCollapsePlacement(player, origin);
        }
        if (target != null) {
            player.setPos(target.x, target.y, target.z);
        }
    }

    public static EntityDimensions getUnconsciousDimensions(boolean cramped) {
        return cramped ? CRAMPED_UNCONSCIOUS_DIMENSIONS : UNCONSCIOUS_DIMENSIONS;
    }

    public boolean shouldUseCrampedUnconsciousDimensions(Player player) {
        return isUnconscious() && !canOccupySpace(player, player.position(), UNCONSCIOUS_DIMENSIONS, false);
    }

    private Vec3 getRaisedCollapseOrigin(Player player, Vec3 origin) {
        if (canOccupyCollapseSpace(player, origin, false)) {
            return origin;
        }

        Vec3 raisedOrigin = origin.add(0.0D, 1.0D, 0.0D);
        return canOccupyCollapseSpace(player, raisedOrigin, true) ? raisedOrigin : origin;
    }

    private Vec3 findCollapsePlacement(Player player, Vec3 origin) {
        Vec3 bestTarget = null;
        double bestDistance = Double.MAX_VALUE;
        int bestManhattan = Integer.MAX_VALUE;

        for (int dz = -COLLAPSE_SEARCH_RADIUS; dz <= COLLAPSE_SEARCH_RADIUS; dz++) {
            for (int dx = -COLLAPSE_SEARCH_RADIUS; dx <= COLLAPSE_SEARCH_RADIUS; dx++) {
                Vec3 candidate = origin.add(dx, 0.0D, dz);
                if (!canOccupyCollapseSpace(player, candidate, true)) {
                    continue;
                }

                double distance = dx * dx + dz * dz;
                int manhattan = Math.abs(dx) + Math.abs(dz);
                if (distance < bestDistance
                        || (distance == bestDistance && manhattan < bestManhattan)
                        || (distance == bestDistance && manhattan == bestManhattan && isDeterministicallyEarlier(candidate, bestTarget))) {
                    bestTarget = candidate;
                    bestDistance = distance;
                    bestManhattan = manhattan;
                }
            }
        }

        return bestTarget;
    }

    private boolean canOccupyCollapseSpace(Player player, Vec3 position, boolean requireSupport) {
        return canOccupySpace(player, position, UNCONSCIOUS_DIMENSIONS, requireSupport);
    }

    private Pose getUnconsciousPose(Player player) {
        return shouldUseCrampedUnconsciousDimensions(player) ? Pose.CROUCHING : Pose.SWIMMING;
    }

    private Pose getClientUnconsciousPose() {
        return Pose.SWIMMING;
    }

    private boolean canOccupySpace(Player player, Vec3 position, EntityDimensions dimensions, boolean requireSupport) {
        AABB boundingBox = dimensions.makeBoundingBox(position.x, position.y, position.z);
        if (!player.level().noCollision(player, boundingBox)) {
            return false;
        }
        return !requireSupport || hasCollapseSupport(player, boundingBox);
    }

    private boolean hasCollapseSupport(Player player, AABB boundingBox) {
        return !player.level().noCollision(player, boundingBox.move(0.0D, -COLLAPSE_SUPPORT_PROBE_DEPTH, 0.0D));
    }

    private boolean isDeterministicallyEarlier(Vec3 candidate, @Nullable Vec3 currentBest) {
        if (currentBest == null) {
            return true;
        }
        if (candidate.z != currentBest.z) {
            return candidate.z < currentBest.z;
        }
        return candidate.x < currentBest.x;
    }

    private void updateUnconsciousAttributes(Player player, boolean unconscious) {
        updateUnconsciousModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), UNCONSCIOUS_MOVEMENT_ID, unconscious);
        updateUnconsciousModifier(player.getAttribute(Attributes.ATTACK_SPEED), UNCONSCIOUS_ATTACK_ID, unconscious);
    }

    private void updateUnconsciousModifier(@Nullable AttributeInstance instance, UUID modifierId, boolean unconscious) {
        if (instance == null) {
            return;
        }
        AttributeModifier modifier = new AttributeModifier(modifierId, "firstaid_unconscious", -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
        if (unconscious) {
            if (instance.getModifier(modifierId) == null) {
                instance.addTransientModifier(modifier);
            }
        } else if (instance.getModifier(modifierId) != null) {
            instance.removeModifier(modifierId);
        }
    }
}

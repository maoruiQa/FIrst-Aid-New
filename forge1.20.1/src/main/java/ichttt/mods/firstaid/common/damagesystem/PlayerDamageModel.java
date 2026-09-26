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

import java.util.UUID;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.api.enums.EnumDebuffSlot;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.CapProvider;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.compat.playerrevive.PRCompatManager;
import ichttt.mods.firstaid.common.damagesystem.DamageablePart;
import ichttt.mods.firstaid.common.damagesystem.debuff.SharedDebuff;
import ichttt.mods.firstaid.common.potion.MilkImmuneMobEffectInstance;
import ichttt.mods.firstaid.common.registries.FirstAidRegistryLookups;
import ichttt.mods.firstaid.common.registries.LookupReloadListener;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PlayerDamageModel
extends AbstractPlayerDamageModel
implements LookupReloadListener {
    private static final DecimalFormat TEXT_FORMAT = new DecimalFormat("0.0");
    private static final int MAX_PAIN_LEVEL = 5;
    /** Normal full chronic pain reference (visual strength 1.0). */
    public static final float CHRONIC_SOFT_CAP = 1.0f;
    /** Brief near-blind ceiling for acute + chronic. */
    public static final float PAIN_HARD_CAP = 1.85f;
    private static final float ACUTE_GAIN_PER_MISSING_HP = 0.22f;
    private static final float ACUTE_GAIN_MAX_PER_HIT = 0.95f;
    private static final float ACUTE_DECAY_PER_TICK = 0.015f;
    private static final float ACUTE_FAST_DECAY_MULTIPLIER = 1.65f;
    private static final float CHRONIC_POWER = 1.55f;
    private static final float CHRONIC_SCALE = 0.92f;
    /** Fraction of acute pain that still shows under opioids. */
    public static final float ACUTE_BREAKTHROUGH = 0.25f;
    private static final float ACUTE_HITCH_THRESHOLD = 0.70f;
    private static final int ACUTE_HITCH_DURATION_TICKS = 10;
    private static final int ACUTE_HITCH_COOLDOWN_TICKS = 40;
    private static final float SPRINT_FLARE_CHRONIC_MIN = 0.36f;
    private static final float SPRINT_FLARE_ACUTE = 0.08f;
    private static final int MORPHINE_RUSH_TICKS = 200;
    private static final float MORPHINE_SAT_DECAY_K = 1.65f;
    private static final float MORPHINE_SAT_END_FADE = 0.08f;
    private static final int MAX_ADRENALINE_LEVEL = 3;
    private static final int MAX_ADRENALINE_TICKS = 200;
    private static final float MAX_SUPPRESSION_INTENSITY = 1.0f;
    private static final float ADRENALINE_ABSORPTION_AMOUNT = 8.0f;
    private static final int ADRENALINE_ABSORPTION_AMPLIFIER = 1;
    private static final int ADRENALINE_HASTE_AMPLIFIER = 0;
    private static final int ADRENALINE_STRENGTH_AMPLIFIER = 0;
    private static final int ADRENALINE_SPEED_AMPLIFIER = 0;
    private static final float ADRENALINE_INJECTION_SUPPRESSION_STRENGTH = 0.35f;

    private static final int SUPPRESSION_HOLD_TICKS = 80;
    private static final float SUPPRESSION_DECAY_STEP = 0.03f;
    private static final int SUPPRESSION_DECAY_INTERVAL = 4;
    private static final int ADRENALINE_DURATION_TICKS = 700;
    private static final int PAINKILLER_ACTIVATION_DELAY_TICKS = 600;
    private static final int MORPHINE_ACTIVATION_DELAY_TICKS = 200;
    private static final int CRITICAL_UNCONSCIOUS_TICKS = 3000;
    private static final int RESCUE_DURATION_TICKS = 160;
    private static final int DEFIBRILLATOR_RESCUE_DURATION_TICKS = 60;
    private static final int EXECUTION_DURATION_TICKS = 100;
    private static final float CRITICAL_DOWNED_CRITICAL_PART_DAMAGE_MULTIPLIER = 0.1f;
    private static final double RESCUE_RANGE = 3.0;
    private static final int COLLAPSE_ANIMATION_TICKS = 12;
    private static final int COLLAPSE_SEARCH_RADIUS = 2;
    private static final double COLLAPSE_SUPPORT_PROBE_DEPTH = 0.125;
    private static final EntityDimensions UNCONSCIOUS_DIMENSIONS = EntityDimensions.scalable((float)1.4f, (float)1.0f);
    private static final EntityDimensions CRAMPED_UNCONSCIOUS_DIMENSIONS = EntityDimensions.scalable((float)0.6f, (float)1.0f);
    private static final UUID UNCONSCIOUS_MOVEMENT_ID = UUID.fromString("4db2b47e-a88d-4af6-88bf-dd4d1ed6e001");
    private static final UUID UNCONSCIOUS_ATTACK_ID = UUID.fromString("4db2b47e-a88d-4af6-88bf-dd4d1ed6e002");
    private static final String UNCONSCIOUS_REASON_NONE = "";
    private static final String UNCONSCIOUS_REASON_CRITICAL = "firstaid.gui.critical_condition";
    private static final String UNCONSCIOUS_REASON_RECOVERING = "firstaid.gui.stabilizing";
    public static final float MORPHINE_INJECTOR_DURATION_MULTIPLIER = 2.5f;
    public static final float MORPHINE_INJECTOR_EFFECT_DURATION_MULTIPLIER = 0.85f;
    public static final int MORPHINE_ORAL_REGEN_TICKS = 500;
    public static final int MORPHINE_INJECTOR_REGEN_TICKS = 2520;
    /** Peak suppression after morphine injector: hold max briefly, then allow normal decay. */
    private static final int FORCE_MAX_SUPPRESSION_HOLD_TICKS = 20 * 15;
    private static final float CRAWL_WINDOW_FRACTION = 0.6f;
    private static final float CRAWL_SPEED_FACTOR = 0.28f;
    private static final float CRAWL_MOVE_ATTRIBUTE = -0.72f;
    private static final float ADDICTION_MAX = 100.0f;
    private static final float ADDICTION_ORAL_GAIN = 6.0f;
    private static final float ADDICTION_INJECTION_GAIN = 16.0f;
    private static final float ADDICTION_MEDICAL_MULT = 0.55f;
    private static final float ADDICTION_RECREATIONAL_MULT = 1.35f;
    private static final float ADDICTION_MILD_THRESHOLD = 0.25f;
    private static final float ADDICTION_MEDIUM_THRESHOLD = 0.5f;
    private static final float ADDICTION_SEVERE_THRESHOLD = 0.75f;
    private static final int ADDICTION_DECAY_DELAY_TICKS = 2400;
    private static final float ADDICTION_DECAY_PER_SECOND = 0.06f;
    private static final float ADDICTION_DECAY_PER_SECOND_LOW = 0.015f;
    private static final float ADDICTION_LOW_DECAY_THRESHOLD = 30.0f;
    private static final int ADDICTION_EPISODE_CHECK_INTERVAL_MILD = 280;
    private static final int ADDICTION_EPISODE_CHECK_INTERVAL_SEVERE = 60;
    private static final int ADDICTION_EPISODE_COOLDOWN_MILD = 440;
    private static final int ADDICTION_EPISODE_COOLDOWN_SEVERE = 160;
    private static final float ADDICTION_EPISODE_BASE_CHANCE = 0.05f;
    private static final float ADDICTION_EPISODE_CHANCE_SCALE = 0.8f;
    private static final float ADDICTION_EPISODE_MAX_CHANCE = 0.88f;
    private static final int ADDICTION_PULSE_INCREASE_TICKS = 300;
    private static final int ADDICTION_PULSE_DECREASE_TICKS = 160;
    private static final int ADDICTION_EPISODE_DURATION_MILD_SECONDS = 28;
    private static final int ADDICTION_EPISODE_DURATION_SEVERE_SECONDS = 110;
    public static final int EPISODE_NONE = 0;
    public static final int EPISODE_PAIN = 1;
    public static final int EPISODE_UNEXPLAINED_PAIN = 1;
    public static final int EPISODE_DARKNESS = 2;
    public static final int EPISODE_NAUSEA = 4;
    public static final int EPISODE_WEAKNESS = 8;
    public static final int EPISODE_SLOWNESS = 16;
    public static final int PULSE_NONE = 0;
    public static final int PULSE_INCREASE = 1;
    public static final int PULSE_ULTRA_INCREASE = 2;
    public static final int PULSE_DECREASE = 3;
    private final Set<SharedDebuff> sharedDebuffs = new HashSet<SharedDebuff>();
    private int morphineTicksLeft = 0;
    /** Peak morphine duration for this dose; used for remaining-ratio visual fade. */
    private int morphineMaxTicks = 0;
    /** Client rush SFX window after morphine activates (10s). */
    private int morphineRushTicks = 0;
    private int pendingPainkillerTicks = 0;
    private int pendingMorphineDelayTicks = 0;
    private int pendingMorphineEffectTicks = 0;
    private boolean pendingMorphineMedicalUse = false;
    private int sleepBlockTicks = 0;
    private float prevHealthCurrent = -1.0f;
    private float prevScaleFactor;
    private final boolean noCritical;
    private boolean needsMorphineUpdate = false;
    private int resyncTimer = -1;
    private int painLevel = 0;
    private float chronicPainIntensity = 0.0f;
    private float acutePainIntensity = 0.0f;
    private float lastTrackedMissingHealth = -1.0f;
    private int acuteHitchCooldownTicks = 0;
    private int adrenalineLevel = 0;
    private int adrenalineTicks = 0;
    private int adrenalineHeartbeatTriggerId = 0;
    private float suppressionIntensity = 0.0f;
    private int suppressionHoldTicks = 0;
    private int suppressionDecayTicker = 0;
    private int suppressionPainReliefTicks = 0;
    private int adrenalineExposureTicks = 0;
    private boolean adrenalineFatiguePending = false;
    private boolean adrenalineFatigueTriggered = false;
    private int adrenalineFatigueTicksLeft = 0;
    private final Map<String, OwnedInjuryEffect> ownedInjuryEffects = new HashMap<>();
    private boolean applyingInjuryEffect;
    private static final class OwnedInjuryEffect {
        int amplifier;
        int duration;
        long appliedAt;
        boolean ambiguous;

        OwnedInjuryEffect(int amplifier, int duration, long appliedAt, boolean ambiguous) {
            this.amplifier = amplifier;
            this.duration = duration;
            this.appliedAt = appliedAt;
            this.ambiguous = ambiguous;
        }
    }
    private int unconsciousTicks = 0;
    private int unconsciousMaxTicks = 0;
    private boolean criticalConditionActive = false;
    private boolean unconsciousAllowsGiveUp = false;
    private boolean unconsciousCausesDeath = false;
    private String unconsciousReasonKey = "";
    private int collapseAnimationTicks = 0;
    private boolean collapsePlacementPending = false;
    private boolean externalRevivePending = false;
    private float addictionValue = 0.0f;
    private int ticksSinceLastOpioid = 0;
    private int withdrawalEpisodeType = 0;
    private int withdrawalEpisodeTicksLeft = 0;
    private int withdrawalCooldownTicks = 0;
    private int withdrawalCheckTicks = 0;
    private int addictionPulseType = 0;
    private int addictionPulseTicks = 0;
    /** Monotonic cue id so clients play tinnitus once per event. */
    private int tinnitusCueId = 0;
    private float tinnitusCueSeverity = 0.0f;
    /** After rescue/respawn, suppress feedback audio until this reaches 0. */
    private int audioMuteTicks = 0;
    private boolean poisonEffectActive = false;
    private boolean witherEffectActive = false;

    public PlayerDamageModel() {
        super(new DamageablePart((Integer)FirstAidConfig.SERVER.maxHealthHead.get(), (Boolean)FirstAidConfig.SERVER.causeDeathHead.get(), EnumPlayerPart.HEAD), new DamageablePart((Integer)FirstAidConfig.SERVER.maxHealthLeftArm.get(), false, EnumPlayerPart.LEFT_ARM), new DamageablePart((Integer)FirstAidConfig.SERVER.maxHealthLeftLeg.get(), false, EnumPlayerPart.LEFT_LEG), new DamageablePart((Integer)FirstAidConfig.SERVER.maxHealthLeftFoot.get(), false, EnumPlayerPart.LEFT_FOOT), new DamageablePart((Integer)FirstAidConfig.SERVER.maxHealthBody.get(), (Boolean)FirstAidConfig.SERVER.causeDeathBody.get(), EnumPlayerPart.BODY), new DamageablePart((Integer)FirstAidConfig.SERVER.maxHealthRightArm.get(), false, EnumPlayerPart.RIGHT_ARM), new DamageablePart((Integer)FirstAidConfig.SERVER.maxHealthRightLeg.get(), false, EnumPlayerPart.RIGHT_LEG), new DamageablePart((Integer)FirstAidConfig.SERVER.maxHealthRightFoot.get(), false, EnumPlayerPart.RIGHT_FOOT));
        this.noCritical = (Boolean)FirstAidConfig.SERVER.causeDeathBody.get() == false && (Boolean)FirstAidConfig.SERVER.causeDeathHead.get() == false;
        FirstAidRegistryLookups.registerReloadListener(this);
    }

    @Override
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
        tagCompound.putInt("morphineTicks", this.morphineTicksLeft);
        tagCompound.putInt("morphineMaxTicks", this.morphineMaxTicks);
        tagCompound.putInt("morphineRushTicks", this.morphineRushTicks);
        tagCompound.putInt("pendingPainkillerTicks", this.pendingPainkillerTicks);
        tagCompound.putInt("pendingMorphineDelayTicks", this.pendingMorphineDelayTicks);
        tagCompound.putInt("pendingMorphineEffectTicks", this.pendingMorphineEffectTicks);
        tagCompound.putBoolean("pendingMorphineMedicalUse", this.pendingMorphineMedicalUse);
        tagCompound.putInt("painLevel", this.painLevel);
        tagCompound.putFloat("chronicPainIntensity", this.chronicPainIntensity);
        tagCompound.putFloat("acutePainIntensity", this.acutePainIntensity);
        tagCompound.putInt("adrenalineLevel", this.adrenalineLevel);
        tagCompound.putInt("adrenalineTicks", this.adrenalineTicks);
        tagCompound.putInt("adrenalineHeartbeatTriggerId", this.adrenalineHeartbeatTriggerId);
        tagCompound.putFloat("suppressionIntensity", this.suppressionIntensity);
        tagCompound.putInt("suppressionHoldTicks", this.suppressionHoldTicks);
        tagCompound.putInt("suppressionDecayTicker", this.suppressionDecayTicker);
        tagCompound.putInt("suppressionPainReliefTicks", this.suppressionPainReliefTicks);
        tagCompound.putInt("adrenalineExposureTicks", this.adrenalineExposureTicks);
        tagCompound.putBoolean("adrenalineFatiguePending", this.adrenalineFatiguePending);
        tagCompound.putBoolean("adrenalineFatigueTriggered", this.adrenalineFatigueTriggered);
        tagCompound.putInt("adrenalineFatigueTicksLeft", this.adrenalineFatigueTicksLeft);
        CompoundTag ownedTag = new CompoundTag();
        this.ownedInjuryEffects.forEach((id, owned) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("amplifier", owned.amplifier);
            entry.putInt("duration", owned.duration);
            entry.putLong("appliedAt", owned.appliedAt);
            entry.putBoolean("ambiguous", owned.ambiguous);
            ownedTag.put(id, entry);
        });
        tagCompound.put("ownedInjuryEffects", ownedTag);
        tagCompound.putInt("unconsciousTicks", this.unconsciousTicks);
        tagCompound.putInt("unconsciousMaxTicks", this.unconsciousMaxTicks);
        tagCompound.putBoolean("criticalConditionActive", this.criticalConditionActive);
        tagCompound.putBoolean("unconsciousAllowsGiveUp", this.unconsciousAllowsGiveUp);
        tagCompound.putBoolean("unconsciousCausesDeath", this.unconsciousCausesDeath);
        tagCompound.putBoolean("externalRevivePending", this.externalRevivePending);
        tagCompound.putInt("collapseAnimationTicks", this.collapseAnimationTicks);
        tagCompound.putFloat("addictionValue", this.addictionValue);
        tagCompound.putInt("ticksSinceLastOpioid", this.ticksSinceLastOpioid);
        tagCompound.putInt("withdrawalEpisodeType", this.withdrawalEpisodeType);
        tagCompound.putInt("withdrawalEpisodeTicksLeft", this.withdrawalEpisodeTicksLeft);
        tagCompound.putInt("withdrawalCooldownTicks", this.withdrawalCooldownTicks);
        tagCompound.putInt("withdrawalCheckTicks", this.withdrawalCheckTicks);
        tagCompound.putInt("addictionPulseType", this.addictionPulseType);
        tagCompound.putInt("addictionPulseTicks", this.addictionPulseTicks);
        tagCompound.putInt("tinnitusCueId", this.tinnitusCueId);
        tagCompound.putFloat("tinnitusCueSeverity", this.tinnitusCueSeverity);
        tagCompound.putInt("audioMuteTicks", this.audioMuteTicks);
        if (!this.unconsciousReasonKey.isEmpty()) {
            tagCompound.putString("unconsciousReasonKey", this.unconsciousReasonKey);
        }
        return tagCompound;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.HEAD.deserializeNBT(nbt.getCompound("head"));
        this.LEFT_ARM.deserializeNBT(nbt.getCompound("leftArm"));
        this.LEFT_LEG.deserializeNBT(nbt.getCompound("leftLeg"));
        this.LEFT_FOOT.deserializeNBT(nbt.getCompound("leftFoot"));
        this.BODY.deserializeNBT(nbt.getCompound("body"));
        this.RIGHT_ARM.deserializeNBT(nbt.getCompound("rightArm"));
        this.RIGHT_LEG.deserializeNBT(nbt.getCompound("rightLeg"));
        this.RIGHT_FOOT.deserializeNBT(nbt.getCompound("rightFoot"));
        if (nbt.contains("morphineTicks")) {
            this.morphineTicksLeft = nbt.getInt("morphineTicks");
            this.needsMorphineUpdate = true;
        }
        this.morphineMaxTicks = nbt.contains("morphineMaxTicks") ? nbt.getInt("morphineMaxTicks") : Math.max(this.morphineMaxTicks, this.morphineTicksLeft);
        this.morphineRushTicks = nbt.contains("morphineRushTicks") ? Math.max(0, nbt.getInt("morphineRushTicks")) : 0;
        this.pendingPainkillerTicks = nbt.getInt("pendingPainkillerTicks");
        this.pendingMorphineDelayTicks = nbt.getInt("pendingMorphineDelayTicks");
        this.pendingMorphineEffectTicks = nbt.getInt("pendingMorphineEffectTicks");
        this.pendingMorphineMedicalUse = nbt.getBoolean("pendingMorphineMedicalUse");
        if (nbt.contains("hasTutorial")) {
            this.hasTutorial = nbt.getBoolean("hasTutorial");
        }
        this.painLevel = nbt.getInt("painLevel");
        this.chronicPainIntensity = nbt.contains("chronicPainIntensity")
            ? Mth.clamp(nbt.getFloat("chronicPainIntensity"), 0.0f, CHRONIC_SOFT_CAP)
            : 0.0f;
        this.acutePainIntensity = nbt.contains("acutePainIntensity")
            ? Mth.clamp(nbt.getFloat("acutePainIntensity"), 0.0f, PAIN_HARD_CAP)
            : 0.0f;
        this.adrenalineLevel = nbt.getInt("adrenalineLevel");
        this.adrenalineTicks = nbt.getInt("adrenalineTicks");
        this.adrenalineHeartbeatTriggerId = nbt.getInt("adrenalineHeartbeatTriggerId");
        this.suppressionIntensity = nbt.contains("suppressionIntensity") ? Mth.clamp((float)nbt.getFloat("suppressionIntensity"), (float)0.0f, (float)1.0f) : Mth.clamp((float)((float)this.adrenalineTicks / 200.0f), (float)0.0f, (float)1.0f);
        this.suppressionHoldTicks = Math.min(nbt.getInt("suppressionHoldTicks"), FORCE_MAX_SUPPRESSION_HOLD_TICKS);
        this.suppressionDecayTicker = nbt.getInt("suppressionDecayTicker");
        this.suppressionPainReliefTicks = Math.max(0, nbt.getInt("suppressionPainReliefTicks"));
        this.adrenalineExposureTicks = Math.max(0, nbt.getInt("adrenalineExposureTicks"));
        this.adrenalineFatiguePending = nbt.getBoolean("adrenalineFatiguePending");
        this.adrenalineFatigueTriggered = nbt.getBoolean("adrenalineFatigueTriggered");
        this.adrenalineFatigueTicksLeft = Math.max(0, Math.min(600, nbt.getInt("adrenalineFatigueTicksLeft")));
        this.ownedInjuryEffects.clear();
        CompoundTag ownedTag = nbt.getCompound("ownedInjuryEffects");
        for (String id : ownedTag.getAllKeys()) {
            CompoundTag entry = ownedTag.getCompound(id);
            this.ownedInjuryEffects.put(id, new OwnedInjuryEffect(entry.getInt("amplifier"), entry.getInt("duration"), entry.getLong("appliedAt"), entry.getBoolean("ambiguous")));
        }
        this.unconsciousTicks = nbt.getInt("unconsciousTicks");
        this.unconsciousMaxTicks = nbt.contains("unconsciousMaxTicks") ? nbt.getInt("unconsciousMaxTicks") : this.unconsciousTicks;
        this.criticalConditionActive = nbt.getBoolean("criticalConditionActive");
        this.unconsciousAllowsGiveUp = nbt.contains("unconsciousAllowsGiveUp") ? nbt.getBoolean("unconsciousAllowsGiveUp") : this.criticalConditionActive;
        this.unconsciousCausesDeath = nbt.contains("unconsciousCausesDeath") ? nbt.getBoolean("unconsciousCausesDeath") : this.criticalConditionActive;
        this.externalRevivePending = nbt.getBoolean("externalRevivePending");
        this.unconsciousReasonKey = nbt.contains("unconsciousReasonKey") ? nbt.getString("unconsciousReasonKey") : (this.criticalConditionActive ? UNCONSCIOUS_REASON_CRITICAL : UNCONSCIOUS_REASON_NONE);
        this.collapseAnimationTicks = nbt.getInt("collapseAnimationTicks");
        this.addictionValue = nbt.contains("addictionValue") ? Mth.clamp((float)nbt.getFloat("addictionValue"), (float)0.0f, (float)100.0f) : 0.0f;
        this.ticksSinceLastOpioid = nbt.getInt("ticksSinceLastOpioid");
        this.withdrawalEpisodeType = nbt.getInt("withdrawalEpisodeType");
        this.withdrawalEpisodeTicksLeft = nbt.getInt("withdrawalEpisodeTicksLeft");
        this.withdrawalCooldownTicks = nbt.getInt("withdrawalCooldownTicks");
        int n = this.withdrawalCheckTicks = nbt.contains("withdrawalCheckTicks") ? nbt.getInt("withdrawalCheckTicks") : 0;
        if (this.withdrawalEpisodeType == 2) {
            this.withdrawalEpisodeType = 3;
        } else if (this.withdrawalEpisodeType == 3) {
            this.withdrawalEpisodeType = 5;
        } else if (this.withdrawalEpisodeType == 1) {
            this.withdrawalEpisodeType = 1;
        }
        this.addictionPulseType = nbt.getInt("addictionPulseType");
        this.addictionPulseTicks = nbt.getInt("addictionPulseTicks");
        this.tinnitusCueId = nbt.contains("tinnitusCueId") ? Math.max(0, nbt.getInt("tinnitusCueId")) : 0;
        this.tinnitusCueSeverity = nbt.contains("tinnitusCueSeverity")
                ? Mth.clamp(nbt.getFloat("tinnitusCueSeverity"), 0.0f, 1.0f)
                : 0.0f;
        this.audioMuteTicks = nbt.contains("audioMuteTicks") ? Math.max(0, nbt.getInt("audioMuteTicks")) : 0;
        this.collapsePlacementPending = false;
        this.refreshSuppressionSnapshot();
    }

    @Override
    public void onLookupsReloaded() {
        FirstAid.LOGGER.debug("Reloaded lookups");
        this.sharedDebuffs.clear();
        for (EnumDebuffSlot debuffSlot : EnumDebuffSlot.values()) {
            IDebuff[] debuffs = FirstAidRegistryLookups.getDebuffs(debuffSlot);
            for (EnumPlayerPart enumPlayerPart : debuffSlot.playerParts) {
                this.getFromEnum(enumPlayerPart).loadDebuffInfo(debuffs);
            }
            for (IDebuff iDebuff : debuffs) {
                if (!(iDebuff instanceof SharedDebuff)) continue;
                SharedDebuff sharedDebuff = (SharedDebuff)iDebuff;
                this.sharedDebuffs.add(sharedDebuff);
            }
        }
    }

    @Override
    public void tick(Level world, Player player) {
        if (this.isDead(player)) {
            return;
        }
        if (this.sleepBlockTicks > 0) {
            --this.sleepBlockTicks;
        } else if (this.sleepBlockTicks < 0) {
            throw new RuntimeException("Negative sleepBlockTicks " + this.sleepBlockTicks);
        }
        float newCurrentHealth = this.calculateNewCurrentHealth(player);
        if (Float.isNaN(newCurrentHealth)) {
            FirstAid.LOGGER.warn("New current health is not a number, setting it to 0!");
            newCurrentHealth = 0.0f;
        }
        if (newCurrentHealth <= 0.0f) {
            FirstAid.LOGGER.error("Got {} health left, but isn't marked as dead!", (Object)Float.valueOf(newCurrentHealth));
            return;
        }
        if (!world.isClientSide && this.resyncTimer != -1) {
            --this.resyncTimer;
            if (this.resyncTimer == 0) {
                this.resyncTimer = -1;
                CommonUtils.syncDamageModel((ServerPlayer)player);
            }
        }
        if (Float.isInfinite(newCurrentHealth)) {
            FirstAid.LOGGER.error("Error calculating current health: Value was infinite");
        } else {
            this.syncVanillaHealth(player, newCurrentHealth);
            this.prevHealthCurrent = newCurrentHealth;
        }
        if (!this.hasTutorial) {
            this.hasTutorial = CapProvider.tutorialDone.contains(player.getName().getString());
        }
        this.runScaleLogic(player);
        MobEffect painkillerEffect = RegistryObjects.PAINKILLER_EFFECT.get();
        MobEffect morphineEffect = RegistryObjects.MORPHINE_EFFECT.get();
        // Never re-apply a zero/expired morphine dose (prevents sticky 00:00 after milk/clear).
        if (this.needsMorphineUpdate && this.morphineTicksLeft > 0) {
            player.addEffect(new MobEffectInstance(morphineEffect, this.morphineTicksLeft, 0, false, false));
        }
        MobEffectInstance morphine = player.getEffect(morphineEffect);
        MobEffectInstance painkiller = player.getEffect(painkillerEffect);
        if (!this.needsMorphineUpdate) {
            this.morphineTicksLeft = morphine == null ? 0 : Math.max(0, morphine.getDuration());
        }
        if (this.morphineTicksLeft <= 0) {
            this.morphineTicksLeft = 0;
            this.morphineMaxTicks = 0;
            this.morphineRushTicks = 0;
        } else {
            this.trackMorphineMaxDuration();
        }
        this.needsMorphineUpdate = false;
        if (!world.isClientSide) {
            this.tickPendingMedicineActivations(player);
            this.updateMedicalState(player);
        }
        // Pose/attributes must run on client too �?forced pose is not automatically synced.
        if (this.unconsciousTicks > 0) {
            this.applyUnconsciousPenalties(player);
        } else {
            this.clearUnconsciousPenalties(player);
        }
        // Client: predictive acute/rush decay between syncs; keep higher of synced vs local pain level.
        if (world.isClientSide) {
            if (this.acutePainIntensity > 0.0f) {
                float decay = ACUTE_DECAY_PER_TICK;
                if (this.acutePainIntensity > CHRONIC_SOFT_CAP) {
                    decay *= ACUTE_FAST_DECAY_MULTIPLIER;
                }
                this.acutePainIntensity = Math.max(0.0f, this.acutePainIntensity - decay);
            }
            if (this.morphineRushTicks > 0) {
                --this.morphineRushTicks;
            }
            int localPain = this.calculatePainLevel();
            if (this.isWithdrawalEpisodeActive()) {
                localPain = Math.max(localPain, this.getAddictionPainLevel());
            }
            this.painLevel = Math.max(this.painLevel, localPain);
        }
        boolean painSuppressed = this.isPainSuppressed(player);
                if (painSuppressed && !world.isClientSide()) {
                    if (!this.ownedInjuryEffects.isEmpty()) this.clearOwnedInjuryEffects(player);
                    this.sharedDebuffs.forEach(SharedDebuff::clearPending);
                }
        boolean healingStateChanged = false;
        for (AbstractDamageablePart part : this) {
            float previousHealth = part.currentHealth;
            boolean hadHealer = part.activeHealer != null;
            if (part instanceof DamageablePart damageablePart) {
                damageablePart.tick(world, player, !painSuppressed, this.isUnconscious());
            } else {
                part.tick(world, player, !painSuppressed);
            }
            if (world.isClientSide || Float.compare(previousHealth, part.currentHealth) == 0 && hadHealer == (part.activeHealer != null)) continue;
            healingStateChanged = true;
        }
        if (!painSuppressed && !world.isClientSide) {
            this.sharedDebuffs.forEach(sharedDebuff -> sharedDebuff.tick(player));
        }
        if (healingStateChanged && player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer)player;
            this.painLevel = this.calculatePainLevel();
            CommonUtils.syncDamageModel(serverPlayer);
        }
    }

    public void syncVanillaHealth(Player player) {
        float newCurrentHealth = this.calculateNewCurrentHealth(player);
        if (Float.isNaN(newCurrentHealth)) {
            FirstAid.LOGGER.warn("New current health is not a number, setting it to 0!");
            newCurrentHealth = 0.0f;
        }
        if (Float.isInfinite(newCurrentHealth)) {
            FirstAid.LOGGER.error("Error calculating current health: Value was infinite");
            return;
        }
        this.syncVanillaHealth(player, newCurrentHealth);
        this.prevHealthCurrent = newCurrentHealth;
    }

    private void syncVanillaHealth(Player player, float newCurrentHealth) {
        if (newCurrentHealth != this.prevHealthCurrent) {
            float syncedHealth = newCurrentHealth;
            CommonUtils.runWithoutSetHealthInterception(() -> player.setHealth(syncedHealth));
        }
    }

    public static int getRandMorphineDuration() {
        return EventHandler.RAND.nextInt(5) * 20 * 15 + 9000;
    }

    public int computeMorphineEffectDuration() {
        return this.computeMorphineEffectDuration(PlayerDamageModel.getRandMorphineDuration());
    }
    /**
     * Morphine effect duration is derived from the painkiller duration this use actually applies,
     * then shortened by addiction. Always <= that painkiller window.
     */
    public int computeMorphineEffectDuration(int painkillerDurationFromThisUse) {
        int pain = Math.max(20 * 60, painkillerDurationFromThisUse);
        float factor = 1.0F - this.getAddictionNormalized() * 0.60F;
        return Math.max(20 * 90, Math.min(pain, Math.round(pain * factor)));
    }


    public static int getMorphineActivationDelay() {
        return FirstAid.getMorphineActivationDelayTicks();
    }

    public static int getPainkillerDuration() {
        return 4800;
    }

    public static int getAdrenalineDuration() {
        return 700;
    }

    public static float getAdrenalineAbsorptionAmount() {
        return 8.0f;
    }

    public static int getPainkillerActivationDelay() {
        return FirstAid.getPainkillerActivationDelayTicks();
    }

    public void queuePainkillerActivation() {
        this.pendingPainkillerTicks = Math.max(this.pendingPainkillerTicks, PlayerDamageModel.getPainkillerActivationDelay());
        this.scheduleResync();
    }

    public void queueMorphineActivation() {
        this.pendingMorphineMedicalUse = this.isMedicalOpioidUse();
        this.pendingMorphineDelayTicks = Math.max(this.pendingMorphineDelayTicks, PlayerDamageModel.getMorphineActivationDelay());
        this.pendingMorphineEffectTicks = Math.max(this.pendingMorphineEffectTicks, PlayerDamageModel.getRandMorphineDuration());
        this.scheduleResync();
    }

    @Override
    @Deprecated
    public void applyMorphine() {
        int base = PlayerDamageModel.getRandMorphineDuration();
        this.morphineTicksLeft = this.computeMorphineEffectDuration(base);
        this.needsMorphineUpdate = true;
    }

    @Override
    public void applyMorphine(Player player) {
        this.applyOralMorphineEffects(player, PlayerDamageModel.getRandMorphineDuration(), this.isMedicalOpioidUse());
    }

    public void applyMorphineInjection(Player player) {
        int basePainRelief = PlayerDamageModel.getRandMorphineDuration();
        int painDuration = Math.round(basePainRelief * MORPHINE_INJECTOR_DURATION_MULTIPLIER * MORPHINE_INJECTOR_EFFECT_DURATION_MULTIPLIER);
        MobEffectInstance activePainkiller = player.getEffect(RegistryObjects.PAINKILLER_EFFECT.get());
        painDuration = Math.max(painDuration, activePainkiller == null ? 0 : activePainkiller.getDuration());
        int morphineDuration = Math.round(this.computeMorphineEffectDuration(basePainRelief) * MORPHINE_INJECTOR_EFFECT_DURATION_MULTIPLIER); // not painDuration — sat must not track 2.5x painkiller
        MobEffectInstance activeMorphine = player.getEffect(RegistryObjects.MORPHINE_EFFECT.get());
        morphineDuration = Math.max(morphineDuration, activeMorphine == null ? 0 : activeMorphine.getDuration());
        player.addEffect(new MobEffectInstance(RegistryObjects.MORPHINE_EFFECT.get(), morphineDuration, 0, false, false));
        player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT.get(), painDuration, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MORPHINE_INJECTOR_REGEN_TICKS, 0, false, false));
        this.setMorphineDuration(morphineDuration);
        this.startMorphineRush();
        this.needsMorphineUpdate = false;
        this.forceMaxSuppression(player);
        this.registerOpioidUse(player, true, this.isMedicalOpioidUse());
        this.scheduleResync();
        if (!player.level().isClientSide && player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer)player;
            CommonUtils.syncDamageModel(serverPlayer);
        }
    }

    private void applyOralMorphineEffects(Player player, int basePainReliefTicks, boolean medicalUse) {
        int painDuration = Math.max(1200, basePainReliefTicks);
        int morphineDuration = this.computeMorphineEffectDuration(painDuration);
        morphineDuration = Math.min(morphineDuration, painDuration);
        player.addEffect(new MobEffectInstance(RegistryObjects.MORPHINE_EFFECT.get(), morphineDuration, 0, false, false));
        player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT.get(), painDuration, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 500, 0, false, false));
        this.setMorphineDuration(morphineDuration);
        this.startMorphineRush();
        this.needsMorphineUpdate = false;
        this.registerOpioidUse(player, false, medicalUse);
    }
    private void forceMaxSuppression(Player player) {
        this.forceMaxSuppression(player, FORCE_MAX_SUPPRESSION_HOLD_TICKS);
    }

    private void forceMaxSuppression(Player player, int holdTicks) {
        float previousIntensity = this.suppressionIntensity;
        int previousHoldTicks = this.suppressionHoldTicks;
        int previousAdrenalineTicks = this.adrenalineTicks;
        int effectiveHold = Mth.clamp(holdTicks, 1, FORCE_MAX_SUPPRESSION_HOLD_TICKS);
        this.suppressionIntensity = MAX_SUPPRESSION_INTENSITY;
        this.suppressionHoldTicks = Math.max(this.suppressionHoldTicks, effectiveHold);
        this.suppressionHoldTicks = Math.min(this.suppressionHoldTicks, FORCE_MAX_SUPPRESSION_HOLD_TICKS);
        this.suppressionDecayTicker = 0;
        this.startSuppressionPainRelief(player);
        this.refreshSuppressionSnapshot();
        if (Float.compare(previousIntensity, this.suppressionIntensity) != 0 || previousHoldTicks != this.suppressionHoldTicks || previousAdrenalineTicks != this.adrenalineTicks) {
            this.scheduleResync();
        }
    }

    @Override
    @Deprecated
    public int getMorphineTicks() {
        return this.morphineTicksLeft;
    }

    public int getMorphineMaxTicks() {
        return this.morphineMaxTicks;
    }

    public float getMorphineRemainingRatio() {
        if (this.morphineTicksLeft <= 0 || this.morphineMaxTicks <= 0) {
            return 0.0F;
        }
        return Mth.clamp(this.morphineTicksLeft / (float) this.morphineMaxTicks, 0.0F, 1.0F);
    }

    private void setMorphineDuration(int duration) {
        this.morphineTicksLeft = Math.max(0, duration);
        if (this.morphineTicksLeft <= 0) {
            this.morphineMaxTicks = 0;
        } else {
            this.morphineMaxTicks = Math.max(this.morphineMaxTicks, this.morphineTicksLeft);
        }
    }

    private void trackMorphineMaxDuration() {
        if (this.morphineTicksLeft <= 0) {
            this.morphineMaxTicks = 0;
        } else if (this.morphineTicksLeft > this.morphineMaxTicks) {
            this.morphineMaxTicks = this.morphineTicksLeft;
        }
    }


    @Override
    public Float getAbsorption() {
        float value = 0;
        for (AbstractDamageablePart part : this) {
            value += part.getAbsorption();
        }
        return value;
    }

    @Override
    public void setAbsorption(float absorption) {
        float newAbsorption = absorption / 8.0F;
        forEach(damageablePart -> damageablePart.setAbsorption(newAbsorption));
    }

    public int getPainLevel() {
        return this.painLevel;
    }

    public int getAdrenalineLevel() {
        return this.adrenalineLevel;
    }

    public int getAdrenalineTicks() {
        return this.adrenalineTicks;
    }

    public int getAdrenalineHeartbeatTriggerId() {
        return this.adrenalineHeartbeatTriggerId;
    }

    public int getSuppressionLevel() {
        return this.adrenalineLevel;
    }

    public float getSuppressionIntensity() {
        return this.suppressionIntensity;
    }

    public int getSuppressionPainReliefTicks() {
        return this.suppressionPainReliefTicks;
    }

    public void applyTrackedInjuryEffect(Player player, String effectId, MobEffectInstance effect) {
        if (this.isPainSuppressed(player)) {
            return;
        }
        OwnedInjuryEffect owned = this.ownedInjuryEffects.get(effectId);
        if (owned != null && owned.ambiguous || owned == null && player.hasEffect(effect.getEffect())) {
            return;
        }
        this.applyingInjuryEffect = true;
        try {
            player.addEffect(effect);
        } finally {
            this.applyingInjuryEffect = false;
        }
        MobEffectInstance active = player.getEffect(effect.getEffect());
        if (active != null) {
            this.ownedInjuryEffects.put(effectId, new OwnedInjuryEffect(active.getAmplifier(), active.getDuration(), player.level().getGameTime(), false));
        }
    }

    public void onEffectAdded(Player player, MobEffectInstance effect) {
        if (effect.getEffect().equals(RegistryObjects.PAINKILLER_EFFECT.get()) || effect.getEffect().equals(RegistryObjects.MORPHINE_EFFECT.get())) {
            this.clearOwnedInjuryEffects(player);
        } else if (!this.applyingInjuryEffect) {
            OwnedInjuryEffect owned = this.ownedInjuryEffects.get(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect()).toString());
            if (owned != null) {
                owned.ambiguous = true;
            }
        }
    }

    private void startSuppressionPainRelief(Player player) {
        this.suppressionPainReliefTicks = 20;
        this.clearOwnedInjuryEffects(player);
    }

    private void clearOwnedInjuryEffects(Player player) {
        long now = player.level().getGameTime();
        for (MobEffectInstance active : new ArrayList<>(player.getActiveEffects())) {
            String id = BuiltInRegistries.MOB_EFFECT.getKey(active.getEffect()).toString();
            OwnedInjuryEffect owned = this.ownedInjuryEffects.get(id);
            if (owned != null && !owned.ambiguous && active.getAmplifier() == owned.amplifier
                    && Math.abs(active.getDuration() - Math.max(0, owned.duration - (now - owned.appliedAt))) <= 2) {
                player.removeEffect(active.getEffect());
            }
        }
        this.ownedInjuryEffects.clear();
        this.sharedDebuffs.forEach(SharedDebuff::clearPending);
    }



    public int getSuppressionHoldTicks() {
        return this.suppressionHoldTicks;
    }

    public void applyAdrenalineInjection(Player player) {
        MobEffectInstance activePainkiller = player.getEffect(RegistryObjects.PAINKILLER_EFFECT.get());
        MobEffectInstance activeAbsorption = player.getEffect(MobEffects.ABSORPTION);
        MobEffectInstance activeHaste = player.getEffect(MobEffects.DIG_SPEED);
        MobEffectInstance activeStrength = player.getEffect(MobEffects.DAMAGE_BOOST);
        MobEffectInstance activeSpeed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        int duration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activePainkiller == null ? 0 : activePainkiller.getDuration());
        int absorptionDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeAbsorption == null ? 0 : activeAbsorption.getDuration());
        int absorptionAmplifier = Math.max(1, activeAbsorption == null ? 0 : activeAbsorption.getAmplifier());
        int hasteDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeHaste == null ? 0 : activeHaste.getDuration());
        int hasteAmplifier = Math.max(0, activeHaste == null ? 0 : activeHaste.getAmplifier());
        int strengthDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeStrength == null ? 0 : activeStrength.getDuration());
        int strengthAmplifier = Math.max(0, activeStrength == null ? 0 : activeStrength.getAmplifier());
        int speedDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeSpeed == null ? 0 : activeSpeed.getDuration());
        int speedAmplifier = Math.max(0, activeSpeed == null ? 0 : activeSpeed.getAmplifier());
        player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT.get(), duration, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, absorptionDuration, absorptionAmplifier, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, hasteDuration, hasteAmplifier, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, strengthDuration, strengthAmplifier, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, speedDuration, speedAmplifier, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 140, 0, false, false));
        this.registerAdrenalineNearMiss(player, 0.35f, PlayerDamageModel.getAdrenalineDuration());
        this.forceMaxSuppression(player);
        ++this.adrenalineHeartbeatTriggerId;
        this.scheduleResync();
        if (!player.level().isClientSide && player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer)player;
            CommonUtils.syncDamageModel(serverPlayer);
        }
    }

    public int getUnconsciousTicks() {
        return this.unconsciousTicks;
    }

    public boolean isCriticalConditionActive() {
        return this.criticalConditionActive;
    }

    public boolean isUnconscious() {
        return this.unconsciousTicks > 0;
    }

    public boolean isCriticalDowned() {
        return this.criticalConditionActive && this.unconsciousTicks > 0;
    }

    public float getIncomingPartDamageMultiplier(AbstractDamageablePart part) {
        return this.isCriticalDowned() && part.canCauseDeath ? 0.1f : 1.0f;
    }

    public boolean canGiveUp() {
        return this.isUnconscious() && this.unconsciousAllowsGiveUp;
    }

    public boolean refreshRescueWakeUpState(Player player) {
        if (!this.isRescueWakeUpRecoveryActive()) {
            return false;
        }
        int delayTicks = FirstAid.rescueWakeUpEnabled ? FirstAid.getRescueWakeUpDelayTicks() : 0;
        int n = delayTicks;
        if (delayTicks <= 0) {
            this.clearUnconsciousState();
            this.clearUnconsciousPenalties(player);
            CommonUtils.runWithoutSetHealthInterception(() -> player.setHealth(Math.max(player.getHealth(), 1.0f)));
        } else {
            this.unconsciousTicks = delayTicks;
            this.unconsciousAllowsGiveUp = false;
            this.unconsciousCausesDeath = false;
            this.unconsciousReasonKey = UNCONSCIOUS_REASON_RECOVERING;
        }
        this.scheduleResync();
        return true;
    }

    public float getCollapseAnimationProgress(float partialTick) {
        if (!this.isUnconscious()) {
            return 1.0f;
        }
        return Mth.clamp((float)(1.0f - (Math.max(0.0f, (float)this.collapseAnimationTicks) - Math.max(0.0f, partialTick)) / 12.0f), (float)0.0f, (float)1.0f);
    }

    public float getCollapseAnimationProgress() {
        return this.getCollapseAnimationProgress(0.0f);
    }

    public String getUnconsciousReasonKey() {
        return this.unconsciousReasonKey.isEmpty() ? "firstaid.gui.unconscious" : this.unconsciousReasonKey;
    }

    public int getUnconsciousSecondsLeft() {
        return Math.max(1, (int)Math.ceil((double)this.unconsciousTicks / 20.0));
    }

    public static int getRescueDurationTicks() {
        return 160;
    }

    public static int getDefibrillatorRescueDurationTicks() {
        return 60;
    }

    public static int getExecutionDurationTicks() {
        return 100;
    }

    public static double getRescueRange() {
        return 3.0;
    }

    public float getChronicPainIntensity() {
        return this.chronicPainIntensity;
    }

    public float getAcutePainIntensity() {
        return this.acutePainIntensity;
    }

    public float getEffectivePainIntensity() {
        return Math.min(PAIN_HARD_CAP, this.chronicPainIntensity + this.acutePainIntensity);
    }

    /**
     * Continuous pain strength for client FX. Can exceed 1.0 during acute spikes (hard cap {@link #PAIN_HARD_CAP}).
     */
    public float getPainVisualStrength() {
        return this.getPainVisualStrength(false);
    }

    /**
     * @param painSuppressed when true, chronic contribution is muted and only acute breakthrough remains
     */
    public float getPainVisualStrength(boolean painSuppressed) {
        float effective = painSuppressed
            ? this.acutePainIntensity * ACUTE_BREAKTHROUGH
            : this.getEffectivePainIntensity();
        if (effective <= 0.0f && this.painLevel <= 0) {
            return 0.0f;
        }
        return Math.min(PAIN_HARD_CAP, effective) / CHRONIC_SOFT_CAP;
    }

    public int getMorphineRushTicks() {
        return this.morphineRushTicks;
    }

    /**
     * Morphine color-grade factor 0..1 using decelerating decay over the dose, with a rush floor for the first 10s.
     * <p>
     * Important: never treat a missing {@code morphineMaxTicks} as full-strength forever — that made classic
     * continuous post FX look stuck at peak on some loaders.
     */
    public float getMorphineSaturationFactor() {
        if (this.morphineTicksLeft <= 0) {
            return 0.0f;
        }
        // Recover peak if NBT/sync lagged (client often saw max=0 → ratio 0 → old code forced peak).
        if (this.morphineMaxTicks < this.morphineTicksLeft) {
            this.morphineMaxTicks = this.morphineTicksLeft;
        }
        float ratio = this.morphineMaxTicks <= 0
            ? 1.0f
            : Mth.clamp(this.morphineTicksLeft / (float) this.morphineMaxTicks, 0.0f, 1.0f);
        float progress = 1.0f - ratio;
        // Decelerating descent: steep early, flatter late (exp).
        float base = (float) Math.exp(-MORPHINE_SAT_DECAY_K * (double) progress);
        // Extra continuous-grade shaping so mid-dose is clearly below onset on live uniforms
        // (tiered 26.2 already quantizes; classic composite needs a stronger mid drop).
        float continuousShape = ratio * ratio; // remaining²
        float shaped = base * (0.35f + 0.65f * continuousShape);
        float endT = Mth.clamp(ratio / MORPHINE_SAT_END_FADE, 0.0f, 1.0f);
        float endFade = endT * endT * (3.0f - 2.0f * endT);
        float curve = shaped * endFade;
        if (this.morphineRushTicks > 0) {
            float rushT = 1.0f - this.morphineRushTicks / (float) MORPHINE_RUSH_TICKS;
            // Hold near peak only during the 10s rush, then follow the decay curve.
            float rushEnv = 1.0f - 0.20f * rushT;
            curve = Math.max(curve, 0.90f * rushEnv);
        }
        return Mth.clamp(curve, 0.0f, 1.0f);
    }

    private void startMorphineRush() {
        this.morphineRushTicks = MORPHINE_RUSH_TICKS;
    }

    public float getDeathCountdownDangerProgress() {
        if (!this.canGiveUp()) {
            return 0.0f;
        }
        float remaining = Math.max(0.0f, (float)this.unconsciousTicks);
        float progress = 1.0f - remaining / 3000.0f;
        return Math.max(0.0f, Math.min(1.0f, progress));
    }
    public void ensureEncounterAdrenaline(Player player, float floor) {
        float previous = this.suppressionIntensity;
        this.suppressionIntensity = Math.max(previous, Mth.clamp(floor, 0.0f, 1.0f));
        if (this.suppressionIntensity <= 0.0f) return;
        this.suppressionHoldTicks = Math.max(this.suppressionHoldTicks, 40);
        this.suppressionDecayTicker = 0;
        if (previous < this.suppressionIntensity) {
            this.startSuppressionPainRelief(player);
            this.refreshSuppressionSnapshot();
            this.scheduleResync();
        }
    }



    public void registerAdrenalineNearMiss(Player player, float strength) {
        this.registerAdrenalineNearMiss(player, strength, 80);
    }

    public void registerAdrenalineNearMiss(Player player, float strength, int holdTicks) {
        float clampedStrength = Mth.clamp((float)strength, (float)0.35f, (float)1.45f);
        float previousIntensity = this.suppressionIntensity;
        int previousHoldTicks = this.suppressionHoldTicks;
        int previousAdrenalineTicks = this.adrenalineTicks;
        float baseIntensity = 0.28f + clampedStrength * 0.24f;
        float addedIntensity = Mth.clamp((float)(baseIntensity * FirstAid.suppressionGainMultiplier), (float)(0.36f * FirstAid.suppressionGainMultiplier), (float)(0.62f * FirstAid.suppressionGainMultiplier));
        this.suppressionIntensity = Mth.clamp((float)(this.suppressionIntensity + addedIntensity), (float)0.0f, (float)1.0f);
        this.suppressionHoldTicks = Math.max(this.suppressionHoldTicks, holdTicks);
        this.suppressionDecayTicker = 0;

        this.startSuppressionPainRelief(player);
        this.refreshSuppressionSnapshot();
        if (previousIntensity != this.suppressionIntensity || previousHoldTicks != this.suppressionHoldTicks || previousAdrenalineTicks != this.adrenalineTicks) {
            this.scheduleResync();
        }
    }

    /**
     * Clears morphine/painkiller model state and effects.
     * Used when milk is drunk or an admin/debug clear is needed so pain can be tested again.
     * Always clears both the vanilla effects and FirstAid pending/ticks so milk cannot leave a sticky 00:00 or half-state.
     */
    public void clearPainSuppressants(Player player) {
        this.morphineTicksLeft = 0;
        this.morphineMaxTicks = 0;
        this.morphineRushTicks = 0;
        this.needsMorphineUpdate = false;
        this.pendingMorphineDelayTicks = 0;
        this.pendingMorphineEffectTicks = 0;
        this.pendingMorphineMedicalUse = false;
        this.pendingPainkillerTicks = 0;
        this.suppressionPainReliefTicks = 0;
        if (player != null) {
            player.removeEffect(RegistryObjects.MORPHINE_EFFECT.get());
            player.removeEffect(RegistryObjects.PAINKILLER_EFFECT.get());
            // Milk ends the opioid cover; drop addiction icons that would otherwise re-apply.
            this.clearAddictionPulseEffects(player);
        }
        this.scheduleResync();
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            CommonUtils.syncDamageModel(serverPlayer);
        }
    }

    public void clearStatusEffects() {
        this.morphineTicksLeft = 0;
        this.morphineMaxTicks = 0;
        this.morphineRushTicks = 0;
        this.needsMorphineUpdate = false;
        this.pendingPainkillerTicks = 0;
        this.pendingMorphineDelayTicks = 0;
        this.pendingMorphineEffectTicks = 0;
        this.pendingMorphineMedicalUse = false;
        this.painLevel = 0;
        this.chronicPainIntensity = 0.0f;
        this.acutePainIntensity = 0.0f;
        this.lastTrackedMissingHealth = -1.0f;
        this.acuteHitchCooldownTicks = 0;
        this.adrenalineLevel = 0;
        this.adrenalineTicks = 0;
        this.suppressionIntensity = 0.0f;
        this.suppressionHoldTicks = 0;
        this.suppressionDecayTicker = 0;
        this.suppressionPainReliefTicks = 0;
        this.adrenalineExposureTicks = 0;
        this.adrenalineFatiguePending = false;
      this.adrenalineFatigueTriggered = false;
        this.adrenalineFatigueTicksLeft = 0;
        this.ownedInjuryEffects.clear();
        this.unconsciousTicks = 0;
        this.unconsciousMaxTicks = 0;
        this.criticalConditionActive = false;
        this.unconsciousAllowsGiveUp = false;
        this.unconsciousCausesDeath = false;
        this.unconsciousReasonKey = UNCONSCIOUS_REASON_NONE;
        this.externalRevivePending = false;
        this.withdrawalEpisodeType = 0;
        this.withdrawalEpisodeTicksLeft = 0;
        this.withdrawalCooldownTicks = 0;
        this.addictionPulseType = 0;
        this.addictionPulseTicks = 0;
        this.tinnitusCueId = 0;
        this.tinnitusCueSeverity = 0.0f;
        this.audioMuteTicks = 0;
    }

    public void markExternalRevivePending(Player player) {
        this.externalRevivePending = true;
        this.criticalConditionActive = false;
        this.clearUnconsciousState();
        this.clearUnconsciousPenalties(player);
        player.refreshDimensions();
        this.scheduleResync();
        if (!player.level().isClientSide && player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer)player;
            CommonUtils.syncDamageModel(serverPlayer);
        }
    }

    public void handlePostDamage(Player player, @Nullable DamageSource source) {
        if (this.hasNoRemainingBodyHealth() || this.hasAllCriticalPartsCollapsed()) {
            this.criticalConditionActive = false;
            this.clearUnconsciousState();
            this.clearUnconsciousPenalties(player);
            this.scheduleResync();
            return;
        }
        if (this.criticalConditionActive || !this.hasCriticalPartCollapsed()) {
            return;
        }
        if (CommonUtils.tryUseTotem(this, player, source)) {
            return;
        }
        this.criticalConditionActive = true;
        this.setUnconsciousState(3000, true, true, UNCONSCIOUS_REASON_CRITICAL);
        this.acutePainIntensity = Math.min(PAIN_HARD_CAP, Math.max(this.acutePainIntensity, 1.25f));
        this.painLevel = Math.max(this.painLevel, 5);
        CommonUtils.runWithoutSetHealthInterception(() -> player.setHealth(Math.max(player.getHealth(), 1.0f)));
        this.scheduleResync();
    }

    public boolean canBeRescued() {
        return this.criticalConditionActive && this.isUnconscious();
    }

    public boolean rescueFromCriticalState(Player player, @Nullable AbstractPartHealer healer) {
        return this.rescueFromCriticalState(player, healer, FirstAid.rescueWakeUpEnabled);
    }

    public boolean rescueFromCriticalState(Player player, @Nullable AbstractPartHealer healer, boolean keepWakeUpDelay) {
        return this.performCriticalRescue(player, healer, keepWakeUpDelay, 0.0f, 1.0f, keepWakeUpDelay ? 1.0f : 2.0f);
    }

    public boolean defibrillatorRescueFromCriticalState(Player player, boolean keepWakeUpDelay) {
        boolean rescued = this.performCriticalRescue(player, null, keepWakeUpDelay, 2.0f, 0.4f, keepWakeUpDelay ? 1.0f : 2.0f);
        if (rescued) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 0, false, false));
        }
        return rescued;
    }
    public boolean adrenalineRescueFromCriticalState(Player player, boolean keepWakeUpDelay) {
        boolean rescued = this.performCriticalRescue(player, null, keepWakeUpDelay, 0.0f, 0.4f, 1.0f);
        if (rescued) this.applyAdrenalineInjection(player);
        return rescued;
    }



    private boolean performCriticalRescue(Player player, @Nullable AbstractPartHealer healer, boolean keepWakeUpDelay, float extraCriticalHealth, float wakeUpDelayMultiplier, float restoredCriticalHealth) {
        if (!this.canBeRescued()) {
            return false;
        }
        this.rescueCriticalParts(restoredCriticalHealth);
        if (extraCriticalHealth > 0.0f) {
            this.restoreDamagedCriticalParts(extraCriticalHealth);
        }
        if (!keepWakeUpDelay) {
            this.rescueNonCriticalZeroParts(1.0f);
        }
        this.criticalConditionActive = false;
        this.painLevel = Math.max(2, this.painLevel);
        this.chronicPainIntensity = Math.max(this.chronicPainIntensity, 0.22f);
        // Clear sticky feedback so revival does not keep ringing ears / heartbeat.
        this.clearStickyFeedbackAfterRescue();
        if (keepWakeUpDelay) {
            AbstractDamageablePart rescueTarget = this.getFirstCriticalRescueTarget();
            if (healer != null && rescueTarget != null && rescueTarget.activeHealer == null) {
                rescueTarget.activeHealer = healer;
            }
            this.setUnconsciousState(this.getScaledRescueWakeUpDelayTicks(wakeUpDelayMultiplier), false, false, UNCONSCIOUS_REASON_RECOVERING);
        } else {
            this.clearUnconsciousState();
            this.clearUnconsciousPenalties(player);
            CommonUtils.runWithoutSetHealthInterception(() -> player.setHealth(Math.max(player.getHealth(), 1.0f)));
        }
        this.scheduleResync();
        if (player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer)player;
            CommonUtils.syncDamageModel(serverPlayer);
        }
        return true;
    }


    /**
     * Clears residual suppression/acute audio drivers and starts a short mute window after rescue.
     */
    private void clearStickyFeedbackAfterRescue() {
        this.suppressionIntensity = 0.0f;
        this.suppressionHoldTicks = 0;
        this.suppressionDecayTicker = 0;
        this.suppressionPainReliefTicks = 0;
        this.refreshSuppressionSnapshot();
        this.acutePainIntensity = Math.min(this.acutePainIntensity, 0.35f);
        this.tinnitusCueSeverity = 0.0f;
        this.beginAudioMute(60);
    }

    public void registerTinnitusCue(float severity) {
        float clamped = Mth.clamp(severity, 0.0f, 1.0f);
        if (clamped <= 0.01f || this.audioMuteTicks > 0) {
            return;
        }
        ++this.tinnitusCueId;
        this.tinnitusCueSeverity = Math.max(this.tinnitusCueSeverity, clamped);
        this.scheduleResync();
    }

    public int getTinnitusCueId() {
        return this.tinnitusCueId;
    }

    public float getTinnitusCueSeverity() {
        return this.tinnitusCueSeverity;
    }

    public void beginAudioMute(int ticks) {
        this.audioMuteTicks = Math.max(this.audioMuteTicks, Math.max(0, ticks));
    }

    public int getAudioMuteTicks() {
        return this.audioMuteTicks;
    }

    public boolean isAudioMuted() {
        return this.audioMuteTicks > 0;
    }

    /**
     * After damage is applied, register tinnitus only for explosions, head trauma, or very strong shocks.
     */
    public void registerDamageFeedback(Player player, DamageSource source, AbstractPlayerDamageModel before) {
        if (player.level().isClientSide || before == null || this.audioMuteTicks > 0) {
            return;
        }
        float headLost = Math.max(0.0f, before.HEAD.currentHealth - this.HEAD.currentHealth);
        float totalLost = 0.0f;
        for (AbstractDamageablePart part : this) {
            AbstractDamageablePart previous = before.getFromEnum(part.part);
            if (previous != null) {
                totalLost += Math.max(0.0f, previous.currentHealth - part.currentHealth);
            }
        }
        if (totalLost <= 0.05f) {
            return;
        }
        boolean explosion = source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
        if (explosion) {
            this.registerTinnitusCue(Mth.clamp(0.55f + totalLost * 0.10f, 0.55f, 1.0f));
            return;
        }
        // Meaningful direct head trauma (at least ~0.5 hearts).
        if (headLost >= 1.0f) {
            this.registerTinnitusCue(Mth.clamp(0.48f + headLost * 0.12f, 0.48f, 1.0f));
            return;
        }
        // Sudden strong full-body shock (~3+ hearts in one hit).
        if (totalLost >= 6.0f) {
            this.registerTinnitusCue(Mth.clamp(0.52f + (totalLost - 6.0f) * 0.04f, 0.52f, 1.0f));
        }
    }

    private int getScaledRescueWakeUpDelayTicks(float multiplier) {
        int delayTicks = FirstAid.getRescueWakeUpDelayTicks();
        if (delayTicks <= 0) {
            return 0;
        }
        return Math.max(1, Math.round((float)delayTicks * multiplier));
    }

    public void giveUp(Player player) {
        if (!this.canGiveUp()) {
            return;
        }
        this.criticalConditionActive = false;
        this.clearUnconsciousState();
        this.clearUnconsciousPenalties(player);
        this.scheduleResync();
        CommonUtils.killPlayer(this, player, null);
    }

    public void refreshPainState(Player player) {
        int previousPainLevel = this.painLevel;
        float previousAcute = this.acutePainIntensity;
        if (!player.level().isClientSide) {
            this.trackInjuryDeltaForAcutePain(player);
        }
        this.painLevel = this.calculatePainLevel();
        if (previousPainLevel != this.painLevel || Float.compare(previousAcute, this.acutePainIntensity) != 0) {
            this.scheduleResync();
            if (!player.level().isClientSide && player instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer)player;
                CommonUtils.syncDamageModel(serverPlayer);
            }
        }
    }

    private float sumVisibleMissingHealth() {
        float total = 0.0f;
        for (AbstractDamageablePart part : this) {
            total += CommonUtils.getVisibleMissingHealth(part);
        }
        return total;
    }

    private void trackInjuryDeltaForAcutePain(Player player) {
        float missing = this.sumVisibleMissingHealth();
        if (this.lastTrackedMissingHealth < 0.0f) {
            this.lastTrackedMissingHealth = missing;
            return;
        }
        float delta = missing - this.lastTrackedMissingHealth;
        this.lastTrackedMissingHealth = missing;
        if (delta > 0.04f) {
            this.registerAcutePainFromInjury(player, delta);
        }
    }

    public void registerAcutePainFromInjury(@Nullable Player player, float deltaMissingHealth) {
        if (deltaMissingHealth <= 0.0f) {
            return;
        }
        float previousAcute = this.acutePainIntensity;
        float gain = Math.min(ACUTE_GAIN_MAX_PER_HIT, deltaMissingHealth * ACUTE_GAIN_PER_MISSING_HP);
        this.acutePainIntensity = Math.min(PAIN_HARD_CAP, this.acutePainIntensity + gain);
        if (player != null && !player.level().isClientSide) {
            this.tryApplyAcuteHitch(player, previousAcute);
        }
    }

    private void tryApplyAcuteHitch(Player player, float previousAcute) {
        if (this.acuteHitchCooldownTicks > 0) {
            return;
        }
        if (previousAcute >= ACUTE_HITCH_THRESHOLD || this.acutePainIntensity < ACUTE_HITCH_THRESHOLD) {
            return;
        }
        if (this.isPainSuppressed(player)) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ACUTE_HITCH_DURATION_TICKS, 0, false, false, true));
        this.acuteHitchCooldownTicks = ACUTE_HITCH_COOLDOWN_TICKS;
    }

    private void tickAcutePainState(Player player) {
        if (this.acuteHitchCooldownTicks > 0) {
            --this.acuteHitchCooldownTicks;
        }
        if (this.acutePainIntensity > 0.0f) {
            float decay = ACUTE_DECAY_PER_TICK;
            if (this.acutePainIntensity > CHRONIC_SOFT_CAP) {
                decay *= ACUTE_FAST_DECAY_MULTIPLIER;
            }
            this.acutePainIntensity = Math.max(0.0f, this.acutePainIntensity - decay);
        }
        // Light action flare while sprinting with severe+ chronic injury.
        if (player.isSprinting()
            && this.chronicPainIntensity >= SPRINT_FLARE_CHRONIC_MIN
            && !this.isPainSuppressed(player)
            && player.tickCount % 20 == 0) {
            this.acutePainIntensity = Math.min(PAIN_HARD_CAP, this.acutePainIntensity + SPRINT_FLARE_ACUTE);
        }
    }

    private void tickMorphineRush() {
        if (this.morphineRushTicks > 0) {
            --this.morphineRushTicks;
        }
    }


    @Override
    @Nonnull
    public Iterator<AbstractDamageablePart> iterator() {
        return new Iterator<AbstractDamageablePart>(){
            private byte count = 0;

            @Override
            public boolean hasNext() {
                return this.count < 8;
            }

            @Override
            public AbstractDamageablePart next() {
                if (this.count >= 8) {
                    throw new NoSuchElementException();
                }
                AbstractDamageablePart part = PlayerDamageModel.this.getFromEnum(EnumPlayerPart.VALUES[this.count]);
                this.count = (byte)(this.count + 1);
                return part;
            }
        };
    }

    private float calculateNewCurrentHealth(Player player) {
        float currentHealth = 0.0f;
        FirstAidConfig.Server.VanillaHealthCalculationMode mode = (FirstAidConfig.Server.VanillaHealthCalculationMode)((Object)FirstAidConfig.SERVER.vanillaHealthCalculation.get());
        if (this.noCritical) {
            mode = FirstAidConfig.Server.VanillaHealthCalculationMode.AVERAGE_ALL;
        }
        switch (mode) {
            case AVERAGE_CRITICAL: {
                int maxHealth = 0;
                for (AbstractDamageablePart part : this) {
                    if (!part.canCauseDeath) continue;
                    currentHealth += part.currentHealth;
                    maxHealth += part.getMaxHealth();
                }
                currentHealth /= (float)maxHealth;
                break;
            }
            case MIN_CRITICAL: {
                AbstractDamageablePart minimal = null;
                float lowest = Float.MAX_VALUE;
                for (AbstractDamageablePart part : this) {
                    float partCurrentHealth;
                    if (!part.canCauseDeath || !((partCurrentHealth = part.currentHealth) < lowest)) continue;
                    minimal = part;
                    lowest = partCurrentHealth;
                }
                Objects.requireNonNull(minimal);
                currentHealth = minimal.currentHealth / (float)minimal.getMaxHealth();
                break;
            }
            case AVERAGE_ALL: {
                for (AbstractDamageablePart part : this) {
                    currentHealth += part.currentHealth;
                }
                currentHealth /= (float)this.getCurrentMaxHealth();
                break;
            }
            case CRITICAL_50_PERCENT_OTHER_50_PERCENT: {
                float currentNormal = 0.0f;
                int maxNormal = 0;
                float currentCritical = 0.0f;
                int maxCritical = 0;
                for (AbstractDamageablePart part : this) {
                    if (!part.canCauseDeath) {
                        currentNormal += part.currentHealth;
                        maxNormal += part.getMaxHealth();
                        continue;
                    }
                    currentCritical += part.currentHealth;
                    maxCritical += part.getMaxHealth();
                }
                float avgNormal = currentNormal / (float)maxNormal;
                float avgCritical = currentCritical / (float)maxCritical;
                currentHealth = (avgCritical + avgNormal) / 2.0f;
                break;
            }
            default: {
                throw new RuntimeException("Unknown constant " + String.valueOf((Object)mode));
            }
        }
        float scaledHealth = currentHealth * player.getMaxHealth();
        if (this.isCriticalDowned() && this.hasCriticalPartCollapsed() && !this.hasAllCriticalPartsCollapsed()) {
            return Math.max(1.0f, scaledHealth);
        }
        return scaledHealth;
    }

    @Override
    public boolean isDead(@Nullable Player player) {
        boolean bleeding = PRCompatManager.getHandler().isBleeding(player);
        if (bleeding) {
            return true;
        }
        if (player != null && !player.isAlive()) {
            return true;
        }
        if (this.hasNoRemainingBodyHealth()) {
            return true;
        }
        if (this.hasAllCriticalPartsCollapsed()) {
            return true;
        }
        if (this.isCriticalDowned()) {
            return false;
        }
        if (this.noCritical) {
            boolean dead = true;
            for (AbstractDamageablePart part : this) {
                if (!(part.currentHealth > 0.0f)) continue;
                dead = false;
                break;
            }
            return dead;
        }
        for (AbstractDamageablePart part : this) {
            if (!part.canCauseDeath || !(part.currentHealth <= 0.0f)) continue;
            return true;
        }
        return false;
    }

    @Override
    public int getMaxRenderSize() {
        int max = 0;
        for (AbstractDamageablePart part : this) {
            int newMax = FirstAidConfig.CLIENT.overlayMode.get() == FirstAidConfig.Client.OverlayMode.NUMBERS ? Minecraft.getInstance().font.width(TEXT_FORMAT.format(part.currentHealth) + "/" + part.getMaxHealth()) + 1 : (int)((float)((int)((float)part.getMaxHealth() + 0.9999f) + 1) / 2.0f * 9.0f);
            max = Math.max(max, newMax);
        }
        return max;
    }

    @Override
    public void sleepHeal(Player player) {
        if (this.sleepBlockTicks > 0) {
            return;
        }
        CommonUtils.healAllPartsByPercentage((Double)FirstAidConfig.SERVER.sleepHealPercentage.get(), this, player);
        this.refreshPainState(player);
        this.sleepBlockTicks = 20;
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
        if (((Boolean)FirstAidConfig.GENERAL.debug.get()).booleanValue()) {
            CommonUtils.debugLogStacktrace("Reviving player");
        }
        player.revive();
        this.clearStatusEffects();
        this.externalRevivePending = false;
        for (AbstractDamageablePart part : this) {
            if (!part.canCauseDeath && !this.noCritical || !(part.currentHealth <= 0.0f)) continue;
            part.currentHealth = 1.0f;
        }
        if (FirstAid.rescueWakeUpEnabled && FirstAid.getRescueWakeUpDelayTicks() > 0) {
            this.setUnconsciousState(FirstAid.getRescueWakeUpDelayTicks(), false, false, UNCONSCIOUS_REASON_RECOVERING);
        }
        if (!player.level().isClientSide && player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer)player;
            CommonUtils.syncDamageModel(serverPlayer);
        }
    }

    @Override
    public void runScaleLogic(Player player) {
        if (((Boolean)FirstAidConfig.SERVER.scaleMaxHealth.get()).booleanValue()) {
            float globalFactor = player.getMaxHealth() / 20.0f;
            if (this.prevScaleFactor != globalFactor) {
                if (Math.abs(globalFactor - 1.0f) < 1.0E-6f) {
                    for (AbstractDamageablePart part : this) {
                        part.setMaxHealth(part.initialMaxHealth);
                    }
                    this.prevScaleFactor = globalFactor;
                    return;
                }
                if (((Boolean)FirstAidConfig.GENERAL.debug.get()).booleanValue()) {
                    FirstAid.LOGGER.info("Starting health scaling factor {} -> {} (max health {})", (Object)Float.valueOf(this.prevScaleFactor), (Object)Float.valueOf(globalFactor), (Object)Float.valueOf(player.getMaxHealth()));
                }
                int reduced = 0;
                int added = 0;
                float expectedNewMaxHealth = 0.0f;
                int newMaxHealth = 0;
                for (AbstractDamageablePart part : this) {
                    float floatResult = (float)part.initialMaxHealth * globalFactor;
                    expectedNewMaxHealth += floatResult;
                    int result = (int)floatResult;
                    if (result % 2 == 1) {
                        int partMaxHealth = part.getMaxHealth();
                        if (part.currentHealth < (float)partMaxHealth && reduced < 4) {
                            --result;
                            ++reduced;
                        } else if (part.currentHealth > (float)partMaxHealth && added < 4) {
                            ++result;
                            ++added;
                        } else if (reduced > added) {
                            ++result;
                            ++added;
                        } else {
                            --result;
                            ++reduced;
                        }
                    }
                    newMaxHealth += result;
                    if (((Boolean)FirstAidConfig.GENERAL.debug.get()).booleanValue()) {
                        FirstAid.LOGGER.info("Part {} max health: {} initial; {} old; {} new", (Object)part.part.name(), (Object)part.initialMaxHealth, (Object)part.getMaxHealth(), (Object)result);
                    }
                    part.setMaxHealth(result);
                }
                if (Math.abs(expectedNewMaxHealth - (float)newMaxHealth) >= 2.0f) {
                    if (((Boolean)FirstAidConfig.GENERAL.debug.get()).booleanValue()) {
                        FirstAid.LOGGER.info("Entering second stage - diff {}", (Object)Float.valueOf(Math.abs(expectedNewMaxHealth - (float)newMaxHealth)));
                    }
                    ArrayList<AbstractDamageablePart> prioList = new ArrayList<AbstractDamageablePart>();
                    for (AbstractDamageablePart part : this) {
                        prioList.add(part);
                    }
                    prioList.sort(Comparator.comparingInt(AbstractDamageablePart::getMaxHealth));
                    for (AbstractDamageablePart part : prioList) {
                        int maxHealth = part.getMaxHealth();
                        if (((Boolean)FirstAidConfig.GENERAL.debug.get()).booleanValue()) {
                            FirstAid.LOGGER.info("Part {}: Second stage with total diff {}", (Object)part.part.name(), (Object)Float.valueOf(Math.abs(expectedNewMaxHealth - (float)newMaxHealth)));
                        }
                        if (expectedNewMaxHealth > (float)newMaxHealth) {
                            part.setMaxHealth(maxHealth + 2);
                            newMaxHealth += part.getMaxHealth() - maxHealth;
                        } else if (expectedNewMaxHealth < (float)newMaxHealth) {
                            part.setMaxHealth(maxHealth - 2);
                            newMaxHealth -= maxHealth - part.getMaxHealth();
                        }
                        if (!(Math.abs(expectedNewMaxHealth - (float)newMaxHealth) < 2.0f)) continue;
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

    public boolean isPainSuppressed(Player player) {
        return this.suppressionPainReliefTicks > 0 || this.morphineTicksLeft > 0 || player.hasEffect(RegistryObjects.MORPHINE_EFFECT.get()) || player.hasEffect(RegistryObjects.PAINKILLER_EFFECT.get());
    }

    private boolean isMorphineActive(Player player) {
        return this.morphineTicksLeft > 0 || player.hasEffect(RegistryObjects.MORPHINE_EFFECT.get());
    }

    /**
     * True while morphine is active or still in activation delay.
     * Blocks withdrawal episodes and addiction debuff icons so treatment does not look like a drug crash.
     */
    private boolean isUnderOpioidCover(Player player) {
        return this.isMorphineActive(player) || this.pendingMorphineDelayTicks > 0;
    }

    private void tickPendingMedicineActivations(Player player) {
        boolean changed = false;
        if (this.pendingPainkillerTicks > 0) {
            --this.pendingPainkillerTicks;
            if (this.pendingPainkillerTicks == 0) {
                player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT.get(), PlayerDamageModel.getPainkillerDuration(), 0, false, false));
                changed = true;
            }
        }
        if (this.pendingMorphineDelayTicks > 0) {
            --this.pendingMorphineDelayTicks;
            if (this.pendingMorphineDelayTicks == 0 && this.pendingMorphineEffectTicks > 0) {
                int duration = this.pendingMorphineEffectTicks;
                boolean medicalUse = this.pendingMorphineMedicalUse;
                this.pendingMorphineEffectTicks = 0;
                this.pendingMorphineMedicalUse = false;
                this.applyOralMorphineEffects(player, duration, medicalUse);
                changed = true;
            }
        }
        if (changed) {
            this.scheduleResync();
        }
    }

    private void tickDotTinnitusState(Player player) {
        boolean poisoned = player.hasEffect(MobEffects.POISON);
        if (!poisoned) {
            this.poisonEffectActive = false;
        } else if (!this.poisonEffectActive && this.audioMuteTicks <= 0) {
            this.poisonEffectActive = true;
            this.registerTinnitusCue(0.45f);
        }

        boolean withering = player.hasEffect(MobEffects.WITHER);
        if (!withering) {
            this.witherEffectActive = false;
        } else if (!this.witherEffectActive && this.audioMuteTicks <= 0) {
            this.witherEffectActive = true;
            this.registerTinnitusCue(0.45f);
        }
    }

    private void updateMedicalState(Player player) {
        if (!player.level().isClientSide()) {
            this.tickDotTinnitusState(player);
        }
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
        if (this.resolveExternalReviveState(player)) {
            return;
        }
        float previousAcute = this.acutePainIntensity;
        int previousRush = this.morphineRushTicks;
        int previousAudioMute = this.audioMuteTicks;
        this.trackInjuryDeltaForAcutePain(player);
        this.tickAcutePainState(player);
        this.tickMorphineRush();
        if (this.audioMuteTicks > 0) {
            --this.audioMuteTicks;
        }
        this.painLevel = this.calculatePainLevel();
        this.tickAddictionState(player);
        if (this.isWithdrawalEpisodeActive()) {
            this.painLevel = Math.max(this.painLevel, this.getAddictionPainLevel());
        }
        int previousPainReliefTicks = this.suppressionPainReliefTicks;
        this.tickSuppressionState();
        this.tickAdrenalineFatigue(player);
        this.tickAdrenalineCombatEffects(player);
        if (this.hasAllCriticalPartsCollapsed()) {
            this.criticalConditionActive = false;
            this.clearUnconsciousState();
            this.clearUnconsciousPenalties(player);
            CommonUtils.killPlayerDirectly(player, null);
            return;
        }
        if (this.criticalConditionActive && !this.hasCriticalPartCollapsed()) {
            this.criticalConditionActive = false;
            if (this.unconsciousCausesDeath) {
                this.unconsciousAllowsGiveUp = false;
                this.unconsciousCausesDeath = false;
                if (this.unconsciousReasonKey.equals(UNCONSCIOUS_REASON_CRITICAL)) {
                    this.unconsciousReasonKey = UNCONSCIOUS_REASON_RECOVERING;
                    this.refreshRescueWakeUpState(player);
                }
            }
        }
        if (this.unconsciousTicks > 0) {
            --this.unconsciousTicks;
        }
        if (this.criticalConditionActive && this.unconsciousTicks <= 0 && this.unconsciousCausesDeath) {
            this.clearUnconsciousPenalties(player);
            CommonUtils.killPlayerDirectly(player, null);
            return;
        }
        if (this.unconsciousTicks <= 0) {
            this.clearUnconsciousState();
        }
        if (previousPainLevel != this.painLevel
            || previousAdrenalineLevel != this.adrenalineLevel
            || previousAdrenalineTicks != this.adrenalineTicks
            || Float.compare(previousSuppressionIntensity, this.suppressionIntensity) != 0
            || previousSuppressionHoldTicks != this.suppressionHoldTicks
            || previousPainReliefTicks > 0 && this.suppressionPainReliefTicks == 0
            || previousUnconsciousTicks != this.unconsciousTicks
            || previousCriticalCondition != this.criticalConditionActive
            || previousGiveUpState != this.unconsciousAllowsGiveUp
            || previousDeathState != this.unconsciousCausesDeath
            || !Objects.equals(previousUnconsciousReasonKey, this.unconsciousReasonKey)
            || Float.compare(previousAcute, this.acutePainIntensity) != 0
            || previousRush != this.morphineRushTicks
            || previousAudioMute != this.audioMuteTicks) {
            this.scheduleResync();
        }
        if (this.painLevel == 0 && this.adrenalineTicks == 0 && this.unconsciousTicks == 0 && !this.isPainSuppressed(player)) {
            this.unconsciousAllowsGiveUp = false;
            this.unconsciousCausesDeath = false;
            this.unconsciousReasonKey = UNCONSCIOUS_REASON_NONE;
        }
        if (this.collapseAnimationTicks > 0) {
            --this.collapseAnimationTicks;
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

    private boolean resolveExternalReviveState(Player player) {
        if (!this.externalRevivePending || PRCompatManager.getHandler().isBleeding(player)) {
            return false;
        }
        this.externalRevivePending = false;
        if (player.isAlive() && player.getHealth() > 0.0f) {
            this.revivePlayer(player);
        } else {
            this.clearStatusEffects();
            this.clearUnconsciousPenalties(player);
            player.refreshDimensions();
            this.scheduleResync();
            if (!player.level().isClientSide && player instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer)player;
                CommonUtils.syncDamageModel(serverPlayer);
            }
        }
        return true;
    }

    private int calculatePainLevel() {
        int fullyLostParts = this.countFullyLostParts();
        this.chronicPainIntensity = this.calculateChronicIntensity();
        float effective = Math.min(PAIN_HARD_CAP, this.chronicPainIntensity + this.acutePainIntensity);
        return this.mapEffectiveToPainLevel(effective, fullyLostParts);
    }

    private int countFullyLostParts() {
        int fullyLostParts = 0;
        for (AbstractDamageablePart part : this) {
            if (CommonUtils.getVisualHealth(part) <= 0.0f && CommonUtils.getVisibleMissingHealth(part) > 0.0f) {
                ++fullyLostParts;
            }
        }
        return fullyLostParts;
    }

    private float calculateChronicIntensity() {
        boolean hasInjury = false;
        float maxSeverity = 0.0f;
        float weightedSeverity = 0.0f;
        float totalWeight = 0.0f;
        for (AbstractDamageablePart part : this) {
            float visualHealth = CommonUtils.getVisualHealth(part);
            float missingHealth = CommonUtils.getVisibleMissingHealth(part);
            if (missingHealth <= 0.0f) continue;
            hasInjury = true;
            float injuryRatio = missingHealth / (float) part.getMaxHealth();
            if (visualHealth <= 0.0f) {
                injuryRatio = part.canCauseDeath ? 1.0f : 0.85f;
            }
            if (part.canCauseDeath && injuryRatio >= 0.55f) {
                injuryRatio = Math.min(1.0f, injuryRatio + 0.15f);
            }
            float weight = part.canCauseDeath ? 1.35f : 1.0f;
            maxSeverity = Math.max(maxSeverity, injuryRatio);
            weightedSeverity += injuryRatio * weight;
            totalWeight += weight;
        }
        if (!hasInjury) {
            return 0.0f;
        }
        float averageSeverity = totalWeight <= 0.0f ? 0.0f : weightedSeverity / totalWeight;
        // Soften low-end injury; keep multi-part critical near soft cap.
        float combinedSeverity = Math.min(1.0f, maxSeverity * 0.55f + averageSeverity * 0.45f);
        return Mth.clamp((float) Math.pow(combinedSeverity, CHRONIC_POWER) * CHRONIC_SCALE, 0.0f, CHRONIC_SOFT_CAP);
    }

    private int mapEffectiveToPainLevel(float effective, int fullyLostParts) {
        if (effective <= 0.0f && fullyLostParts <= 0 && this.chronicPainIntensity <= 0.0f && this.acutePainIntensity <= 0.0f) {
            return 0;
        }
        if (fullyLostParts >= 3 || effective > 0.82f) {
            return 5;
        }
        if (effective > 0.58f) {
            return 4;
        }
        if (effective > 0.36f) {
            return 3;
        }
        if (effective > 0.18f) {
            return 2;
        }
        if (effective > 0.0f || this.chronicPainIntensity > 0.0f || this.acutePainIntensity > 0.0f) {
            return 1;
        }
        return 0;
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
        float previousIntensity = this.suppressionIntensity;
        if (FirstAidConfig.SERVER.adrenalineFatigueEnabled.get()) {
            if (previousIntensity > 0.0F && !this.adrenalineFatigueTriggered) {
                if (this.adrenalineExposureTicks < Integer.MAX_VALUE) ++this.adrenalineExposureTicks;
                if (this.adrenalineExposureTicks >= FirstAidConfig.SERVER.adrenalineFatigueThresholdSeconds.get() * 20) {
                    this.adrenalineFatiguePending = true;
                }
            }
        } else {
            this.adrenalineExposureTicks = 0;
            this.adrenalineFatiguePending = false;
            this.adrenalineFatigueTriggered = false;
        }
        if (this.suppressionHoldTicks > FORCE_MAX_SUPPRESSION_HOLD_TICKS) {
            this.suppressionHoldTicks = FORCE_MAX_SUPPRESSION_HOLD_TICKS;
        }
        if (this.suppressionHoldTicks > 0) {
            --this.suppressionHoldTicks;
            this.suppressionDecayTicker = 0;
        } else if (this.suppressionIntensity > 0.0f) {
            ++this.suppressionDecayTicker;
            if (this.suppressionDecayTicker >= 4) {
                this.suppressionDecayTicker = 0;
                this.suppressionIntensity = Math.max(0.0f, this.suppressionIntensity - 0.015f);
            }
        } else {
            this.suppressionDecayTicker = 0;
        }
        if (this.suppressionIntensity > 0.0F || previousIntensity > 0.0F) {
            this.suppressionPainReliefTicks = 20;
        } else if (this.suppressionPainReliefTicks > 0) {
            --this.suppressionPainReliefTicks;
        }
        if (this.suppressionIntensity <= 0.0F
            && (this.adrenalineFatigueTriggered
                || !this.adrenalineFatiguePending)) {
            this.adrenalineExposureTicks = 0;
            this.adrenalineFatiguePending = false;
            this.adrenalineFatigueTriggered = false;
        }
        this.refreshSuppressionSnapshot();
    }
    public boolean isAdrenalineFatigueReady() {
        return FirstAidConfig.SERVER.adrenalineFatigueEnabled.get()
            && !this.adrenalineFatigueTriggered
            && this.adrenalineFatiguePending;
    }

    public void applyAdrenalineFatigue(Player player) {
        if (!this.isAdrenalineFatigueReady()) return;
        this.adrenalineFatiguePending = false;
        this.adrenalineFatigueTriggered = true;
        int duration = (int)Math.min(600L, Math.round(this.adrenalineExposureTicks
            * FirstAidConfig.SERVER.adrenalineFatigueDurationRatio.get()));
        if (this.suppressionIntensity <= 0.0F) this.adrenalineExposureTicks = 0;
        if (duration > 0) {
            this.adrenalineFatigueTicksLeft = Math.max(this.adrenalineFatigueTicksLeft, duration);
            this.ensureAdrenalineFatigueEffects(player);
        }
        this.scheduleResync();
    }

    private void tickAdrenalineFatigue(Player player) {
        if (this.adrenalineFatigueTicksLeft <= 0) return;
        this.ensureAdrenalineFatigueEffects(player);
        --this.adrenalineFatigueTicksLeft;
    }

    private void ensureAdrenalineFatigueEffects(Player player) {
        for (var effect : java.util.List.of(MobEffects.DARKNESS, MobEffects.CONFUSION,
            MobEffects.DIG_SLOWDOWN, MobEffects.WEAKNESS)) {
            MobEffectInstance active = player.getEffect(effect);
            if (active == null || active.getAmplifier() == 0
                && active.getDuration() < this.adrenalineFatigueTicksLeft - 1) {
                player.addEffect(new MobEffectInstance(effect, this.adrenalineFatigueTicksLeft, 0, false, false));
            }
        }
    }

    private void tickAdrenalineCombatEffects(Player player) {
        if (this.adrenalineLevel >= 2) {
            MobEffectInstance haste = player.getEffect(MobEffects.DIG_SPEED);
            if (haste == null || haste.getAmplifier() == 0 && haste.getDuration() <= 20) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 0, false, false));
            }
        }
        if (this.adrenalineLevel >= 3) {
            MobEffectInstance strength = player.getEffect(MobEffects.DAMAGE_BOOST);
            if (strength == null || strength.getAmplifier() == 0 && strength.getDuration() <= 20) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, false));
            }
        }
    }



    private void refreshSuppressionSnapshot() {
        this.adrenalineTicks = Math.round(Mth.clamp((float)this.suppressionIntensity, (float)0.0f, (float)1.0f) * 200.0f);
        this.adrenalineLevel = this.calculateAdrenalineLevel(this.adrenalineTicks);
    }

    private void applyUnconsciousPenalties(Player player) {
        player.setSprinting(false);
        player.stopUsingItem();
        this.updateUnconsciousAttributes(player, true);
        player.setForcedPose(this.getUnconsciousPose(player));
        if (!player.level().isClientSide && this.collapsePlacementPending) {
            this.collapsePlacementPending = false;
            this.placePlayerForCollapse(player);
        }
    }

    private void clearUnconsciousPenalties(Player player) {
        this.updateUnconsciousAttributes(player, false);
        player.setForcedPose(null);
    }

    @Nullable
    private AbstractDamageablePart getFirstCriticalRescueTarget() {
        for (AbstractDamageablePart part : this) {
            if (!part.canCauseDeath || !(part.currentHealth > 0.0f)) continue;
            return part;
        }
        return null;
    }

    private void rescueCriticalParts(float restoredHealth) {
        for (AbstractDamageablePart part : this) {
            if (!part.canCauseDeath || !(part.currentHealth <= 0.0f)) continue;
            part.currentHealth = Math.min((float)part.getMaxHealth(), restoredHealth);
        }
    }

    private void rescueNonCriticalZeroParts(float restoredHealth) {
        for (AbstractDamageablePart part : this) {
            if (part.canCauseDeath || !(part.currentHealth <= 0.0f)) continue;
            part.currentHealth = Math.min((float)part.getMaxHealth(), restoredHealth);
        }
    }

    private void restoreDamagedCriticalParts(float restoredHealth) {
        for (AbstractDamageablePart part : this) {
            if (!part.canCauseDeath || !(part.currentHealth < (float)part.getMaxHealth())) continue;
            part.currentHealth = Math.min((float)part.getMaxHealth(), part.currentHealth + restoredHealth);
        }
    }

    private boolean hasCriticalPartCollapsed() {
        for (AbstractDamageablePart part : this) {
            if (!part.canCauseDeath || !(part.currentHealth <= 0.0f)) continue;
            return true;
        }
        return false;
    }

    private boolean hasAllCriticalPartsCollapsed() {
        boolean hasCriticalPart = false;
        for (AbstractDamageablePart part : this) {
            if (!part.canCauseDeath) continue;
            hasCriticalPart = true;
            if (!(part.currentHealth > 0.0f)) continue;
            return false;
        }
        return hasCriticalPart;
    }

    private boolean hasNoRemainingBodyHealth() {
        for (AbstractDamageablePart part : this) {
            if (!(part.currentHealth > 0.0f)) continue;
            return false;
        }
        return true;
    }

    private void setUnconsciousState(int ticks, boolean allowsGiveUp, boolean causesDeath, String reasonKey) {
        this.unconsciousTicks = ticks;
        this.unconsciousMaxTicks = Math.max(1, ticks);
        this.unconsciousAllowsGiveUp = allowsGiveUp;
        this.unconsciousCausesDeath = causesDeath;
        this.unconsciousReasonKey = reasonKey;
    }

    private boolean isRescueWakeUpRecoveryActive() {
        return this.isUnconscious() && !this.criticalConditionActive && UNCONSCIOUS_REASON_RECOVERING.equals(this.unconsciousReasonKey);
    }

    private void clearUnconsciousState() {
        this.unconsciousTicks = 0;
        this.unconsciousMaxTicks = 0;
        this.unconsciousAllowsGiveUp = false;
        this.unconsciousCausesDeath = false;
        this.unconsciousReasonKey = UNCONSCIOUS_REASON_NONE;
        this.collapseAnimationTicks = 0;
        this.collapsePlacementPending = false;
    }

    public boolean canCrawlWhileDowned() {
        if (!((Boolean)FirstAidConfig.SERVER.criticalCrawlEnabled.get()).booleanValue() || !this.isCriticalDowned() || this.unconsciousMaxTicks <= 0) {
            return false;
        }
        float remainingRatio = (float)this.unconsciousTicks / (float)this.unconsciousMaxTicks;
        return remainingRatio > 0.39999998f;
    }

    public float getCrawlSpeedFactor() {
        return this.canCrawlWhileDowned() ? CRAWL_SPEED_FACTOR : 0.0f;
    }

    public float getAddictionValue() {
        return this.addictionValue;
    }

    public void setAddictionValue(float value) {
        this.addictionValue = Mth.clamp((float)value, (float)0.0f, (float)100.0f);
        this.scheduleResync();
    }

    public float getAddictionNormalized() {
        return Mth.clamp((float)(this.addictionValue / 100.0f), (float)0.0f, (float)1.0f);
    }

    public int getWithdrawalEpisodeType() {
        return this.withdrawalEpisodeType;
    }

    public int getWithdrawalEpisodeTicksLeft() {
        return this.withdrawalEpisodeTicksLeft;
    }

    public int getAddictionPulseType() {
        return this.addictionPulseTicks > 0 ? this.addictionPulseType : 0;
    }

    public boolean isUnexplainedPainEpisodeActive() {
        return this.isWithdrawalEpisodeActive();
    }

    public boolean isWithdrawalEpisodeActive() {
        return this.withdrawalEpisodeTicksLeft > 0 && this.withdrawalEpisodeType != 0;
    }

    public boolean hasWithdrawalFlag(int flag) {
        return (this.withdrawalEpisodeType & flag) != 0;
    }

    private boolean isMedicalOpioidUse() {
        if (this.painLevel > 0) {
            return true;
        }
        for (AbstractDamageablePart part : this) {
            if (!(CommonUtils.getVisibleMissingHealth(part) > 0.0f)) continue;
            return true;
        }
        return false;
    }

    private void registerOpioidUse(Player player, boolean injection, boolean medicalUse) {
        if (!((Boolean)FirstAidConfig.SERVER.addictionEnabled.get()).booleanValue()) {
            return;
        }
        float base = injection ? FirstAidConfig.SERVER.morphineInjectorAddictionGain.get().floatValue()
                : FirstAidConfig.SERVER.morphineAddictionGain.get().floatValue();
        float mult = medicalUse ? 0.55f : 1.35f;
        float gain = base * mult;
        this.addictionValue = Mth.clamp((float)(this.addictionValue + gain), (float)0.0f, (float)100.0f);
        this.ticksSinceLastOpioid = 0;
        if (this.withdrawalEpisodeTicksLeft > 0) {
            this.clearWithdrawalEpisode(player);
        }
        // Addiction value still rises, but do not attach harmful "addiction rising" icons
        // while the player is under morphine / activation delay (that looked like a drug debuff).
        if (this.isUnderOpioidCover(player)) {
            this.clearAddictionPulseEffects(player);
        } else {
            this.addictionPulseType = injection || !medicalUse || gain >= 12.0f ? 2 : 1;
            this.addictionPulseTicks = 100;
        }
        this.scheduleResync();
    }

    private void tickAddictionState(Player player) {
        if (this.addictionPulseTicks > 0) {
            --this.addictionPulseTicks;
            if (this.addictionPulseTicks == 0) {
                this.addictionPulseType = 0;
            }
        }
        if (!((Boolean)FirstAidConfig.SERVER.addictionEnabled.get()).booleanValue()) {
            if (this.withdrawalEpisodeTicksLeft > 0) {
                this.clearWithdrawalEpisode(player);
            }
            this.syncAddictionVisualEffects(player);
            return;
        }
        if (this.isUnderOpioidCover(player)) {
            this.ticksSinceLastOpioid = 0;
            if (this.withdrawalEpisodeTicksLeft > 0) {
                this.clearWithdrawalEpisode(player);
            }
            // Keep treatment clean: no addiction gain/withdrawal icons on top of morphine.
            this.clearAddictionPulseEffects(player);
            return;
        }
        ++this.ticksSinceLastOpioid;
        if (this.addictionValue > 0.0f && this.ticksSinceLastOpioid > 2400 && this.ticksSinceLastOpioid % 20 == 0) {
            float previous = this.addictionValue;
            float decay = this.addictionValue < 30.0f ? 0.015f : 0.06f;
            this.addictionValue = Math.max(0.0f, this.addictionValue - decay);
            if (this.addictionValue < previous) {
                this.addictionPulseType = 3;
                this.addictionPulseTicks = 160;
                this.scheduleResync();
            }
        }
        if (this.withdrawalEpisodeTicksLeft > 0) {
            --this.withdrawalEpisodeTicksLeft;
            this.applyWithdrawalEpisodeEffects(player);
            if (this.withdrawalEpisodeTicksLeft <= 0) {
                this.withdrawalEpisodeType = 0;
                this.withdrawalCooldownTicks = this.getEpisodeCooldownTicks(this.getAddictionNormalized());
                this.scheduleResync();
            }
            this.syncAddictionVisualEffects(player);
            return;
        }
        if (this.withdrawalCooldownTicks > 0) {
            --this.withdrawalCooldownTicks;
            this.syncAddictionVisualEffects(player);
            return;
        }
        float normalized = this.getAddictionNormalized();
        if (normalized < 0.25f) {
            this.withdrawalCheckTicks = 0;
            this.syncAddictionVisualEffects(player);
            return;
        }
        if (this.withdrawalCheckTicks > 0) {
            --this.withdrawalCheckTicks;
            this.syncAddictionVisualEffects(player);
            return;
        }
        float chance = this.getEpisodeChance(normalized);
        this.withdrawalCheckTicks = this.getEpisodeCheckInterval(normalized);
        if (EventHandler.RAND.nextFloat() > chance) {
            this.syncAddictionVisualEffects(player);
            return;
        }
        this.startWithdrawalEpisode(player, normalized);
        this.syncAddictionVisualEffects(player);
    }

    private int getEpisodeCheckInterval(float normalized) {
        float t = Mth.clamp((float)((normalized - 0.25f) / 0.75f), (float)0.0f, (float)1.0f);
        float curved = t * t;
        return Math.round(Mth.lerp((float)curved, (float)280.0f, (float)60.0f));
    }

    private int getEpisodeCooldownTicks(float normalized) {
        float t = Mth.clamp((float)((normalized - 0.25f) / 0.75f), (float)0.0f, (float)1.0f);
        return Math.round(Mth.lerp((float)(t * t), (float)440.0f, (float)160.0f));
    }

    private float getEpisodeChance(float normalized) {
        float t = Mth.clamp((float)normalized, (float)0.0f, (float)1.0f);
        return Mth.clamp((float)(0.05f + t * t * 0.8f), (float)0.0f, (float)0.88f);
    }

    private void syncAddictionVisualEffects(Player player) {
        if (player.level().isClientSide) {
            return;
        }
        if (this.addictionPulseTicks > 0) {
            MobEffect pulseEffect = switch (this.addictionPulseType) {
                case 1 -> RegistryObjects.ADDICTION_INCREASE_EFFECT.get();
                case 2 -> RegistryObjects.ADDICTION_ULTRA_INCREASE_EFFECT.get();
                case 3 -> RegistryObjects.ADDICTION_DECREASE_EFFECT.get();
                default -> null;
            };
            if (pulseEffect != null) {
                MilkImmuneMobEffectInstance.ensure(player, pulseEffect, this.addictionPulseTicks);
            }
            if (this.addictionPulseType != 1) {
                player.removeEffect(RegistryObjects.ADDICTION_INCREASE_EFFECT.get());
            }
            if (this.addictionPulseType != 2) {
                player.removeEffect(RegistryObjects.ADDICTION_ULTRA_INCREASE_EFFECT.get());
            }
            if (this.addictionPulseType != 3) {
                player.removeEffect(RegistryObjects.ADDICTION_DECREASE_EFFECT.get());
            }
        } else {
            player.removeEffect(RegistryObjects.ADDICTION_INCREASE_EFFECT.get());
            player.removeEffect(RegistryObjects.ADDICTION_ULTRA_INCREASE_EFFECT.get());
            player.removeEffect(RegistryObjects.ADDICTION_DECREASE_EFFECT.get());
        }
        if (this.isWithdrawalEpisodeActive()) {
            MilkImmuneMobEffectInstance.ensure(player, RegistryObjects.ADDICTION_WITHDRAWAL_EFFECT.get(), this.withdrawalEpisodeTicksLeft);
            this.applyWithdrawalEpisodeEffects(player);
        } else {
            player.removeEffect(RegistryObjects.ADDICTION_WITHDRAWAL_EFFECT.get());
        }
    }

    private void startWithdrawalEpisode(Player player, float normalized) {
        int flags = this.rollWithdrawalEpisodeFlags(normalized);
        float durationSeconds = Mth.lerp((float)normalized, (float)28.0f, (float)110.0f);
        int durationTicks = Math.max(240, Math.round((durationSeconds *= 0.88f + EventHandler.RAND.nextFloat() * 0.24f) * 20.0f));
        this.withdrawalEpisodeType = flags;
        this.withdrawalEpisodeTicksLeft = durationTicks;
        this.applyWithdrawalEpisodeEffects(player);
        this.scheduleResync();
    }

    private int rollWithdrawalEpisodeFlags(float normalized) {
        int i;
        int flags = 1;
        int extraCount = this.rollExtraSymptomCount(normalized);
        int[] pool = new int[]{2, 4, 8, 16};
        for (i = pool.length - 1; i > 0; --i) {
            int j = EventHandler.RAND.nextInt(i + 1);
            int tmp = pool[i];
            pool[i] = pool[j];
            pool[j] = tmp;
        }
        for (i = 0; i < extraCount && i < pool.length; ++i) {
            flags |= pool[i];
        }
        return flags;
    }

    private int rollExtraSymptomCount(float normalized) {
        if (normalized < 0.5f) {
            float mildProgress = Mth.clamp((float)((normalized - 0.25f) / 0.25f), (float)0.0f, (float)1.0f);
            return EventHandler.RAND.nextFloat() < 0.25f + mildProgress * 0.55f ? 1 : 0;
        }
        if (normalized < 0.75f) {
            float midProgress = Mth.clamp((float)((normalized - 0.5f) / 0.25f), (float)0.0f, (float)1.0f);
            return EventHandler.RAND.nextFloat() < 0.45f + midProgress * 0.4f ? 2 : 1;
        }
        float severeProgress = Mth.clamp((float)((normalized - 0.75f) / 0.25f), (float)0.0f, (float)1.0f);
        int count = 2;
        if (EventHandler.RAND.nextFloat() < 0.55f + severeProgress * 0.35f) {
            ++count;
        }
        if (EventHandler.RAND.nextFloat() < severeProgress * 0.85f) {
            ++count;
        }
        return Math.min(4, count);
    }

    private int getAddictionPainLevel() {
        float normalized = this.getAddictionNormalized();
        if (normalized < 0.25f) {
            return 0;
        }
        if (normalized < 0.5f) {
            return normalized < 0.375f ? 1 : 2;
        }
        if (normalized < 0.75f) {
            return normalized < 0.625f ? 2 : 3;
        }
        if (normalized < 0.9f) {
            return 4;
        }
        return 5;
    }

    private void applyWithdrawalEpisodeEffects(Player player) {
        int amplifier;
        int remaining = Math.max(2, this.withdrawalEpisodeTicksLeft);
        MilkImmuneMobEffectInstance.ensure(player, RegistryObjects.ADDICTION_WITHDRAWAL_EFFECT.get(), remaining);
        if (this.hasWithdrawalFlag(2)) {
            MilkImmuneMobEffectInstance.ensure(player, MobEffects.DARKNESS, remaining);
        } else {
            player.removeEffect(MobEffects.DARKNESS);
        }
        if (this.hasWithdrawalFlag(4)) {
            MilkImmuneMobEffectInstance.ensure(player, MobEffects.CONFUSION, remaining);
        } else {
            player.removeEffect(MobEffects.CONFUSION);
        }
        if (this.hasWithdrawalFlag(8)) {
            amplifier = this.getAddictionNormalized() >= 0.75f ? 1 : 0;
            MilkImmuneMobEffectInstance.ensure(player, MobEffects.WEAKNESS, remaining, amplifier, true);
        } else {
            player.removeEffect(MobEffects.WEAKNESS);
        }
        if (this.hasWithdrawalFlag(16)) {
            amplifier = this.getAddictionNormalized() >= 0.75f ? 1 : 0;
            MilkImmuneMobEffectInstance.ensure(player, MobEffects.MOVEMENT_SLOWDOWN, remaining, amplifier, true);
        } else {
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
    }

    private void clearWithdrawalEpisode(Player player) {
        this.withdrawalEpisodeType = 0;
        this.withdrawalEpisodeTicksLeft = 0;
        player.removeEffect(RegistryObjects.ADDICTION_WITHDRAWAL_EFFECT.get());
        player.removeEffect(MobEffects.DARKNESS);
        player.removeEffect(MobEffects.CONFUSION);
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        this.scheduleResync();
    }

    /** Removes addiction pulse icons without changing the addiction value itself. */
    private void clearAddictionPulseEffects(Player player) {
        this.addictionPulseType = 0;
        this.addictionPulseTicks = 0;
        if (player == null || player.level().isClientSide) {
            return;
        }
        player.removeEffect(RegistryObjects.ADDICTION_INCREASE_EFFECT.get());
        player.removeEffect(RegistryObjects.ADDICTION_ULTRA_INCREASE_EFFECT.get());
        player.removeEffect(RegistryObjects.ADDICTION_DECREASE_EFFECT.get());
        player.removeEffect(RegistryObjects.ADDICTION_WITHDRAWAL_EFFECT.get());
    }

    public void refreshClientUnconsciousPose(Player player) {
        if (player.level().isClientSide) {
            if (isUnconscious()) {
                applyUnconsciousPenalties(player);
            } else {
                clearUnconsciousPenalties(player);
            }
        }
    }

    private void placePlayerForCollapse(Player player) {
        Vec3 origin = player.position();
        Vec3 adjustedOrigin = this.getRaisedCollapseOrigin(player, origin);
        Vec3 target = this.findCollapsePlacement(player, adjustedOrigin);
        if (target == null && !adjustedOrigin.equals((Object)origin)) {
            target = this.findCollapsePlacement(player, origin);
        }
        if (target == null) {
            return;
        }
        player.setPos(target.x, target.y, target.z);
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
        }
        Vec3 raisedOrigin = origin.add(0.0, 1.0, 0.0);
        return this.canOccupyCollapseSpace(player, raisedOrigin, true) ? raisedOrigin : origin;
    }

    private Vec3 findCollapsePlacement(Player player, Vec3 origin) {
        Vec3 bestTarget = null;
        double bestDistance = Double.MAX_VALUE;
        int bestManhattan = Integer.MAX_VALUE;
        for (int dz = -2; dz <= 2; ++dz) {
            for (int dx = -2; dx <= 2; ++dx) {
                Vec3 candidate = origin.add((double)dx, 0.0, (double)dz);
                if (!this.canOccupyCollapseSpace(player, candidate, true)) continue;
                double distance = dx * dx + dz * dz;
                int manhattan = Math.abs(dx) + Math.abs(dz);
                if (!(distance < bestDistance || distance == bestDistance && manhattan < bestManhattan || distance == bestDistance && manhattan == bestManhattan && this.isDeterministicallyEarlier(candidate, bestTarget))) continue;
                bestTarget = candidate;
                bestDistance = distance;
                bestManhattan = manhattan;
            }
        }
        return bestTarget;
    }

    private boolean canOccupyCollapseSpace(Player player, Vec3 position, boolean requireSupport) {
        return this.canOccupySpace(player, position, UNCONSCIOUS_DIMENSIONS, requireSupport);
    }

    private boolean canOccupySpace(Player player, Vec3 position, EntityDimensions dimensions, boolean requireSupport) {
        AABB boundingBox = dimensions.makeBoundingBox(position.x, position.y, position.z);
        if (!player.level().noCollision((Entity)player, boundingBox)) {
            return false;
        }
        return !requireSupport || this.hasCollapseSupport(player, boundingBox);
    }

    private boolean hasCollapseSupport(Player player, AABB boundingBox) {
        return !player.level().noCollision((Entity)player, boundingBox.move(0.0, -0.125, 0.0));
    }

    private boolean isDeterministicallyEarlier(Vec3 candidate, Vec3 currentBest) {
        if (currentBest == null) {
            return true;
        }
        if (candidate.z != currentBest.z) {
            return candidate.z < currentBest.z;
        }
        return candidate.x < currentBest.x;
    }

    private Pose getUnconsciousPose(Player player) {
        return this.shouldUseCrampedUnconsciousDimensions(player) ? Pose.CROUCHING : Pose.SWIMMING;
    }

    private void updateUnconsciousAttributes(Player player, boolean unconscious) {
        // While crawl window is open, keep ~15% move speed so input can produce crawl motion.
        // Outside that window (or when crawl is disabled), fully pin movement.
        double moveAmount = unconscious ? (this.canCrawlWhileDowned() ? CRAWL_MOVE_ATTRIBUTE : -1.0D) : 0.0D;
        updateUnconsciousModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), UNCONSCIOUS_MOVEMENT_ID, unconscious, moveAmount);
        updateUnconsciousModifier(player.getAttribute(Attributes.ATTACK_SPEED), UNCONSCIOUS_ATTACK_ID, unconscious, -1.0D);
    }

    private void updateUnconsciousModifier(@Nullable AttributeInstance instance, UUID modifierId, boolean unconscious, double amount) {
        if (instance == null) {
            return;
        }
        if (unconscious) {
            AttributeModifier existing = instance.getModifier(modifierId);
            if (existing == null || Double.compare(existing.getAmount(), amount) != 0) {
                instance.removeModifier(modifierId);
                instance.addTransientModifier(new AttributeModifier(modifierId, "firstaid_unconscious", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        } else if (instance.getModifier(modifierId) != null) {
            instance.removeModifier(modifierId);
        }
    }

}

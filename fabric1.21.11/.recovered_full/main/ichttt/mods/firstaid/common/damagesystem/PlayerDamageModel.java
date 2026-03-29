/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 *  javax.annotation.Nullable
 *  net.minecraft.core.Holder
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.nbt.Tag
 *  net.minecraft.resources.Identifier
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.util.Mth
 *  net.minecraft.world.effect.MobEffect
 *  net.minecraft.world.effect.MobEffectInstance
 *  net.minecraft.world.effect.MobEffects
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityDimensions
 *  net.minecraft.world.entity.Pose
 *  net.minecraft.world.entity.ai.attributes.Attribute
 *  net.minecraft.world.entity.ai.attributes.AttributeInstance
 *  net.minecraft.world.entity.ai.attributes.AttributeMap
 *  net.minecraft.world.entity.ai.attributes.AttributeModifier
 *  net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation
 *  net.minecraft.world.entity.ai.attributes.Attributes
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
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
import ichttt.mods.firstaid.common.CapProvider;
import ichttt.mods.firstaid.common.ClientAccess;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.compat.playerrevive.PRCompatManager;
import ichttt.mods.firstaid.common.damagesystem.DamageablePart;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
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
    private static final int MAX_ADRENALINE_LEVEL = 3;
    private static final int MAX_ADRENALINE_TICKS = 200;
    private static final float MAX_SUPPRESSION_INTENSITY = 1.0f;
    private static final float SUPPRESSION_GAIN_MULTIPLIER = 0.48f;
    private static final int SUPPRESSION_HOLD_TICKS = 80;
    private static final float SUPPRESSION_DECAY_STEP = 0.03f;
    private static final int SUPPRESSION_DECAY_INTERVAL = 4;
    private static final int PAINKILLER_ACTIVATION_DELAY_TICKS = 600;
    private static final int MORPHINE_ACTIVATION_DELAY_TICKS = 200;
    private static final int CRITICAL_UNCONSCIOUS_TICKS = 3000;
    private static final int RESCUE_WAKE_UP_DELAY = Math.max(1, Math.round(30.000002f));
    private static final int RESCUE_DURATION_TICKS = 160;
    private static final double RESCUE_RANGE = 3.0;
    private static final int COLLAPSE_ANIMATION_TICKS = 12;
    private static final int COLLAPSE_SEARCH_RADIUS = 2;
    private static final double COLLAPSE_SUPPORT_PROBE_DEPTH = 0.125;
    private static final EntityDimensions UNCONSCIOUS_DIMENSIONS = EntityDimensions.scalable((float)1.4f, (float)1.0f);
    private static final EntityDimensions CRAMPED_UNCONSCIOUS_DIMENSIONS = EntityDimensions.scalable((float)0.6f, (float)1.0f);
    private static final Identifier ATTR_UNCONSCIOUS = Identifier.fromNamespaceAndPath((String)"firstaid", (String)"unconscious");
    private static final String UNCONSCIOUS_REASON_NONE = "";
    private static final String UNCONSCIOUS_REASON_CRITICAL = "firstaid.gui.critical_condition";
    private static final String UNCONSCIOUS_REASON_RECOVERING = "firstaid.gui.stabilizing";
    private final Set<SharedDebuff> sharedDebuffs = new HashSet<SharedDebuff>();
    private int morphineTicksLeft = 0;
    private int pendingPainkillerTicks = 0;
    private int pendingMorphineDelayTicks = 0;
    private int pendingMorphineEffectTicks = 0;
    private int sleepBlockTicks = 0;
    private float prevHealthCurrent = -1.0f;
    private float prevScaleFactor;
    private final boolean noCritical;
    private boolean needsMorphineUpdate = false;
    private int resyncTimer = -1;
    private int painLevel = 0;
    private int adrenalineLevel = 0;
    private int adrenalineTicks = 0;
    private float suppressionIntensity = 0.0f;
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
        super(new DamageablePart(FirstAidConfig.SERVER.maxHealthHead.get(), FirstAidConfig.SERVER.causeDeathHead.get(), EnumPlayerPart.HEAD), new DamageablePart(FirstAidConfig.SERVER.maxHealthLeftArm.get(), false, EnumPlayerPart.LEFT_ARM), new DamageablePart(FirstAidConfig.SERVER.maxHealthLeftLeg.get(), false, EnumPlayerPart.LEFT_LEG), new DamageablePart(FirstAidConfig.SERVER.maxHealthLeftFoot.get(), false, EnumPlayerPart.LEFT_FOOT), new DamageablePart(FirstAidConfig.SERVER.maxHealthBody.get(), FirstAidConfig.SERVER.causeDeathBody.get(), EnumPlayerPart.BODY), new DamageablePart(FirstAidConfig.SERVER.maxHealthRightArm.get(), false, EnumPlayerPart.RIGHT_ARM), new DamageablePart(FirstAidConfig.SERVER.maxHealthRightLeg.get(), false, EnumPlayerPart.RIGHT_LEG), new DamageablePart(FirstAidConfig.SERVER.maxHealthRightFoot.get(), false, EnumPlayerPart.RIGHT_FOOT));
        this.noCritical = FirstAidConfig.SERVER.causeDeathBody.get() == false && FirstAidConfig.SERVER.causeDeathHead.get() == false;
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

    @Override
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
        this.suppressionIntensity = nbt.contains("suppressionIntensity") ? Mth.clamp((float)nbt.getFloatOr("suppressionIntensity", 0.0f), (float)0.0f, (float)1.0f) : Mth.clamp((float)((float)this.adrenalineTicks / 200.0f), (float)0.0f, (float)1.0f);
        this.suppressionHoldTicks = nbt.getIntOr("suppressionHoldTicks", 0);
        this.suppressionDecayTicker = nbt.getIntOr("suppressionDecayTicker", 0);
        this.unconsciousTicks = nbt.getIntOr("unconsciousTicks", 0);
        this.criticalConditionActive = nbt.getBooleanOr("criticalConditionActive", false);
        this.unconsciousAllowsGiveUp = nbt.getBooleanOr("unconsciousAllowsGiveUp", this.criticalConditionActive);
        this.unconsciousCausesDeath = nbt.getBooleanOr("unconsciousCausesDeath", this.criticalConditionActive);
        this.unconsciousReasonKey = nbt.getStringOr("unconsciousReasonKey", this.criticalConditionActive ? UNCONSCIOUS_REASON_CRITICAL : UNCONSCIOUS_REASON_NONE);
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
        if (!world.isClientSide() && this.resyncTimer != -1) {
            --this.resyncTimer;
            if (this.resyncTimer == 0) {
                this.resyncTimer = -1;
                if (player instanceof ServerPlayer) {
                    ServerPlayer serverPlayer = (ServerPlayer)player;
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
            if (world.isClientSide() || Float.compare(previousHealth, part.currentHealth) == 0 && hadHealer == (part.activeHealer != null)) continue;
            healingStateChanged = true;
        }
        if (!painSuppressed && !world.isClientSide()) {
            this.sharedDebuffs.forEach(sharedDebuff -> sharedDebuff.tick(player));
        }
        if (healingStateChanged && player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer)player;
            FirstAidNetworking.sendDamageModelSync(serverPlayer, this, FirstAidConfig.SERVER.scaleMaxHealth.get());
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
        this.pendingPainkillerTicks = Math.max(this.pendingPainkillerTicks, PlayerDamageModel.getPainkillerActivationDelay());
        this.scheduleResync();
    }

    public void queueMorphineActivation() {
        this.pendingMorphineDelayTicks = Math.max(this.pendingMorphineDelayTicks, PlayerDamageModel.getMorphineActivationDelay());
        this.pendingMorphineEffectTicks = Math.max(this.pendingMorphineEffectTicks, PlayerDamageModel.getRandMorphineDuration());
        this.scheduleResync();
    }

    @Override
    @Deprecated
    public void applyMorphine() {
        this.morphineTicksLeft = PlayerDamageModel.getRandMorphineDuration();
        this.needsMorphineUpdate = true;
    }

    @Override
    public void applyMorphine(Player player) {
        int duration = PlayerDamageModel.getRandMorphineDuration();
        player.addEffect(new MobEffectInstance(RegistryObjects.MORPHINE_EFFECT, duration, 0, false, false));
        player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT, duration, 0, false, false));
    }

    @Override
    @Deprecated
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

    public static double getRescueRange() {
        return 3.0;
    }

    public float getPainVisualStrength() {
        if (this.painLevel <= 0) {
            return 0.0f;
        }
        return Math.min(1.0f, (float)this.painLevel / 5.0f);
    }

    public float getDeathCountdownDangerProgress() {
        if (!this.canGiveUp()) {
            return 0.0f;
        }
        float remaining = Math.max(0.0f, (float)this.unconsciousTicks);
        float progress = 1.0f - remaining / 3000.0f;
        return Math.max(0.0f, Math.min(1.0f, progress));
    }

    public void registerAdrenalineNearMiss(float strength) {
        float clampedStrength = Mth.clamp((float)strength, (float)0.35f, (float)1.45f);
        float previousIntensity = this.suppressionIntensity;
        int previousHoldTicks = this.suppressionHoldTicks;
        int previousAdrenalineTicks = this.adrenalineTicks;
        float baseIntensity = 0.28f + clampedStrength * 0.24f;
        float addedIntensity = Mth.clamp((float)(baseIntensity * 0.48f), (float)0.1728f, (float)0.2976f);
        this.suppressionIntensity = Mth.clamp((float)(this.suppressionIntensity + addedIntensity), (float)0.0f, (float)1.0f);
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
        this.suppressionIntensity = 0.0f;
        this.suppressionHoldTicks = 0;
        this.suppressionDecayTicker = 0;
        this.unconsciousTicks = 0;
        this.criticalConditionActive = false;
        this.unconsciousAllowsGiveUp = false;
        this.unconsciousCausesDeath = false;
        this.unconsciousReasonKey = UNCONSCIOUS_REASON_NONE;
    }

    public void handlePostDamage(Player player) {
        if (this.hasNoRemainingBodyHealth()) {
            this.criticalConditionActive = false;
            this.clearUnconsciousState();
            this.clearUnconsciousPenalties(player);
            this.scheduleResync();
            return;
        }
        if (this.criticalConditionActive || !this.hasCriticalPartCollapsed()) {
            return;
        }
        this.criticalConditionActive = true;
        this.setUnconsciousState(3000, true, true, UNCONSCIOUS_REASON_CRITICAL);
        this.painLevel = Math.max(this.painLevel, 5);
        player.setHealth(Math.max(player.getHealth(), 1.0f));
        this.scheduleResync();
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
        }
        this.rescueCriticalParts(keepWakeUpDelay ? 1.0f : 2.0f);
        if (!keepWakeUpDelay) {
            this.rescueNonCriticalZeroParts(1.0f);
        }
        this.criticalConditionActive = false;
        this.painLevel = Math.max(2, this.painLevel);
        if (keepWakeUpDelay) {
            AbstractDamageablePart rescueTarget = this.getFirstCriticalRescueTarget();
            if (healer != null && rescueTarget != null && rescueTarget.activeHealer == null) {
                rescueTarget.activeHealer = healer;
            }
            this.setUnconsciousState(Math.max(this.unconsciousTicks, RESCUE_WAKE_UP_DELAY), false, false, UNCONSCIOUS_REASON_RECOVERING);
        } else {
            this.clearUnconsciousState();
            this.clearUnconsciousPenalties(player);
            player.setHealth(Math.max(player.getHealth(), 1.0f));
        }
        this.scheduleResync();
        if (player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer)player;
            FirstAidNetworking.sendDamageModelSync(serverPlayer, this, FirstAidConfig.SERVER.scaleMaxHealth.get());
        }
        return true;
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
        this.painLevel = this.calculatePainLevel();
        if (previousPainLevel != this.painLevel) {
            this.scheduleResync();
            if (!player.level().isClientSide() && player instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer)player;
                FirstAidNetworking.sendDamageModelSync(serverPlayer, this, FirstAidConfig.SERVER.scaleMaxHealth.get());
            }
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
        FirstAidConfig.Server.VanillaHealthCalculationMode mode = FirstAidConfig.SERVER.vanillaHealthCalculation.get();
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
        if (this.criticalConditionActive && this.unconsciousTicks > 0 && this.hasCriticalPartCollapsed()) {
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
        if (this.criticalConditionActive && this.unconsciousTicks > 0) {
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
    public Float getAbsorption() {
        float value = 0.0f;
        for (AbstractDamageablePart part : this) {
            value += part.getAbsorption();
        }
        return Float.valueOf(value);
    }

    @Override
    public void setAbsorption(float absorption) {
        float newAbsorption = absorption / 8.0f;
        this.forEach(damageablePart -> damageablePart.setAbsorption(newAbsorption));
    }

    @Override
    public int getMaxRenderSize() {
        int max = 0;
        for (AbstractDamageablePart part : this) {
            int newMax = FirstAidConfig.CLIENT.overlayMode.get() == FirstAidConfig.Client.OverlayMode.NUMBERS ? ClientAccess.getTextWidth(TEXT_FORMAT.format(part.currentHealth) + "/" + part.getMaxHealth()) + 1 : (int)((float)((int)((float)part.getMaxHealth() + part.getAbsorption() + 0.9999f) + 1) / 2.0f * 9.0f);
            max = Math.max(max, newMax);
        }
        return max;
    }

    @Override
    public void sleepHeal(Player player) {
        if (this.sleepBlockTicks > 0) {
            return;
        }
        CommonUtils.healAllPartsByPercentage(FirstAidConfig.SERVER.sleepHealPercentage.get(), this, player);
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
        if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
            CommonUtils.debugLogStacktrace("Reviving player");
        }
        this.clearStatusEffects();
        for (AbstractDamageablePart part : this) {
            if (!part.canCauseDeath && !this.noCritical || !(part.currentHealth <= 0.0f)) continue;
            part.currentHealth = 1.0f;
        }
        if (!player.level().isClientSide() && player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer)player;
            FirstAidNetworking.sendDamageModelSync(serverPlayer, this, FirstAidConfig.SERVER.scaleMaxHealth.get());
        }
    }

    @Override
    public void runScaleLogic(Player player) {
        if (FirstAidConfig.SERVER.scaleMaxHealth.get().booleanValue()) {
            float globalFactor = player.getMaxHealth() / 20.0f;
            if (this.prevScaleFactor != globalFactor) {
                if (Math.abs(globalFactor - 1.0f) < 1.0E-6f) {
                    for (AbstractDamageablePart part : this) {
                        part.setMaxHealth(part.initialMaxHealth);
                    }
                    this.prevScaleFactor = globalFactor;
                    return;
                }
                if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
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
                    if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
                        FirstAid.LOGGER.info("Part {} max health: {} initial; {} old; {} new", (Object)part.part.name(), (Object)part.initialMaxHealth, (Object)part.getMaxHealth(), (Object)result);
                    }
                    part.setMaxHealth(result);
                }
                if (Math.abs(expectedNewMaxHealth - (float)newMaxHealth) >= 2.0f) {
                    if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
                        FirstAid.LOGGER.info("Entering second stage - diff {}", (Object)Float.valueOf(Math.abs(expectedNewMaxHealth - (float)newMaxHealth)));
                    }
                    ArrayList<AbstractDamageablePart> prioList = new ArrayList<AbstractDamageablePart>();
                    for (AbstractDamageablePart part : this) {
                        prioList.add(part);
                    }
                    prioList.sort(Comparator.comparingInt(AbstractDamageablePart::getMaxHealth));
                    for (AbstractDamageablePart part : prioList) {
                        int maxHealth = part.getMaxHealth();
                        if (FirstAidConfig.GENERAL.debug.get().booleanValue()) {
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
                        break;
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
            --this.pendingPainkillerTicks;
            if (this.pendingPainkillerTicks == 0) {
                player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT, PlayerDamageModel.getPainkillerDuration(), 0, false, false));
                changed = true;
            }
        }
        if (this.pendingMorphineDelayTicks > 0) {
            --this.pendingMorphineDelayTicks;
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
                if (this.unconsciousReasonKey.equals(UNCONSCIOUS_REASON_CRITICAL)) {
                    this.unconsciousReasonKey = UNCONSCIOUS_REASON_RECOVERING;
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
        if (previousPainLevel != this.painLevel || previousAdrenalineLevel != this.adrenalineLevel || previousAdrenalineTicks != this.adrenalineTicks || Float.compare(previousSuppressionIntensity, this.suppressionIntensity) != 0 || previousSuppressionHoldTicks != this.suppressionHoldTicks || previousUnconsciousTicks != this.unconsciousTicks || previousCriticalCondition != this.criticalConditionActive || previousGiveUpState != this.unconsciousAllowsGiveUp || previousDeathState != this.unconsciousCausesDeath || !Objects.equals(previousUnconsciousReasonKey, this.unconsciousReasonKey)) {
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

    private int calculatePainLevel() {
        boolean hasInjury = false;
        int fullyLostParts = 0;
        float maxSeverity = 0.0f;
        float weightedSeverity = 0.0f;
        float totalWeight = 0.0f;
        for (AbstractDamageablePart part : this) {
            float missingHealth = (float)part.getMaxHealth() - part.currentHealth;
            if (missingHealth <= 0.0f) continue;
            hasInjury = true;
            float injuryRatio = missingHealth / (float)part.getMaxHealth();
            if (part.currentHealth <= 0.0f) {
                ++fullyLostParts;
                float f = injuryRatio = part.canCauseDeath ? 1.0f : 0.85f;
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
            return 0;
        }
        if (!FirstAid.dynamicPainEnabled) {
            return 1;
        }
        float averageSeverity = totalWeight <= 0.0f ? 0.0f : weightedSeverity / totalWeight;
        float combinedSeverity = Math.min(1.0f, maxSeverity * 0.65f + averageSeverity * 0.35f);
        int painLevel = Math.max(1, Math.min(5, 1 + (int)Math.floor(combinedSeverity * 4.9999f)));
        if (fullyLostParts < 3 && painLevel >= 5) {
            return 4;
        }
        return painLevel;
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
        if (this.suppressionHoldTicks > 0) {
            --this.suppressionHoldTicks;
            this.suppressionDecayTicker = 0;
        } else if (this.suppressionIntensity > 0.0f) {
            ++this.suppressionDecayTicker;
            if (this.suppressionDecayTicker >= 4) {
                this.suppressionDecayTicker = 0;
                this.suppressionIntensity = Math.max(0.0f, this.suppressionIntensity - 0.03f);
            }
        } else {
            this.suppressionDecayTicker = 0;
        }
        this.refreshSuppressionSnapshot();
    }

    private void refreshSuppressionSnapshot() {
        this.adrenalineTicks = Math.round(Mth.clamp((float)this.suppressionIntensity, (float)0.0f, (float)1.0f) * 200.0f);
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

    private boolean hasCriticalPartCollapsed() {
        for (AbstractDamageablePart part : this) {
            if (!part.canCauseDeath || !(part.currentHealth <= 0.0f)) continue;
            return true;
        }
        return false;
    }

    private boolean hasNoRemainingBodyHealth() {
        for (AbstractDamageablePart part : this) {
            if (!(part.currentHealth > 0.0f)) continue;
            return false;
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
        this.unconsciousReasonKey = UNCONSCIOUS_REASON_NONE;
        this.collapseAnimationTicks = 0;
        this.collapsePlacementPending = false;
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
                if (!(distance < bestDistance || distance == bestDistance && manhattan < bestManhattan) && (distance != bestDistance || manhattan != bestManhattan || !this.isDeterministicallyEarlier(candidate, bestTarget))) continue;
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
        AttributeMap attributeMap = player.getAttributes();
        this.updateUnconsciousModifier(attributeMap, (Holder<Attribute>)Attributes.MOVEMENT_SPEED, unconscious);
        this.updateUnconsciousModifier(attributeMap, (Holder<Attribute>)Attributes.JUMP_STRENGTH, unconscious);
        this.updateUnconsciousModifier(attributeMap, (Holder<Attribute>)Attributes.ATTACK_SPEED, unconscious);
        this.updateUnconsciousModifier(attributeMap, (Holder<Attribute>)Attributes.BLOCK_BREAK_SPEED, unconscious);
        this.updateUnconsciousModifier(attributeMap, (Holder<Attribute>)Attributes.BLOCK_INTERACTION_RANGE, unconscious);
    }

    private void updateUnconsciousModifier(AttributeMap map, Holder<Attribute> attribute, boolean unconscious) {
        AttributeInstance instance = map.getInstance(attribute);
        if (instance == null) {
            return;
        }
        if (unconscious) {
            if (!instance.hasModifier(ATTR_UNCONSCIOUS)) {
                instance.addTransientModifier(new AttributeModifier(ATTR_UNCONSCIOUS, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        } else if (instance.hasModifier(ATTR_UNCONSCIOUS)) {
            instance.removeModifier(ATTR_UNCONSCIOUS);
        }
    }
}


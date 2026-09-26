package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * 26.2 visuals:
 * - hit-frequency red pulse vignette
 * - radial pain blur (tiered)
 * - morphine saturation tiers (remaining/total)
 * - suppression desat + blur multi-pass tiers
 * - continuous gray-white suppression rim overlay
 */
public final class PainVisualEffectsController {
    private static final Identifier PAIN_LOW = Identifier.fromNamespaceAndPath(FirstAid.MODID, "pain_low");
    private static final Identifier PAIN_MID = Identifier.fromNamespaceAndPath(FirstAid.MODID, "pain_mid");
    private static final Identifier PAIN_HIGH = Identifier.fromNamespaceAndPath(FirstAid.MODID, "pain");

    private static final Identifier MORPHINE_T1 = Identifier.fromNamespaceAndPath(FirstAid.MODID, "morphine_saturation_t1");
    private static final Identifier MORPHINE_T2 = Identifier.fromNamespaceAndPath(FirstAid.MODID, "morphine_saturation_t2");
    private static final Identifier MORPHINE_T3 = Identifier.fromNamespaceAndPath(FirstAid.MODID, "morphine_saturation_t3");
    private static final Identifier MORPHINE_T4 = Identifier.fromNamespaceAndPath(FirstAid.MODID, "morphine_saturation_t4");
    private static final Identifier MORPHINE_T5 = Identifier.fromNamespaceAndPath(FirstAid.MODID, "morphine_saturation_t5");

    private static final Identifier DESAT_T1 = Identifier.fromNamespaceAndPath(FirstAid.MODID, "suppression_desat_t1");
    private static final Identifier DESAT_T2 = Identifier.fromNamespaceAndPath(FirstAid.MODID, "suppression_desat_t2");
    private static final Identifier DESAT_T3 = Identifier.fromNamespaceAndPath(FirstAid.MODID, "suppression_desat_t3");
    private static final Identifier DESAT_T4 = Identifier.fromNamespaceAndPath(FirstAid.MODID, "suppression_desat_t4");
    private static final Identifier DESAT_T5 = Identifier.fromNamespaceAndPath(FirstAid.MODID, "suppression_desat_t5");

    private static final float HIT_PULSE_MAX = 1.55F;
    private static final float HIT_PULSE_DECAY = 0.014F;
    private static final float HIT_PULSE_SUPPRESSED_DECAY = 0.06F;
    private static final float PAIN_APPROACH_UP = 0.22F;
    private static final float PAIN_APPROACH_DOWN = 0.028F;
    private static final float MORPHINE_APPROACH_UP = 0.10F;
    private static final float MORPHINE_APPROACH_DOWN = 0.022F;
    /** Pain level 2 ("moderate") → 2/5 blur strength; adrenaline rush uses this. */
    private static final float ADRENALINE_MODERATE_BLUR = 0.40F;
    private static final float MORPHINE_SAT_PEAK = 0.80F;
    private static final float SUPPRESSION_DESAT_PEAK = 1.05F;

    private enum ActiveEffect {
        NONE,
        PAIN_LOW,
        PAIN_MID,
        PAIN_HIGH,
        MORPHINE_T1,
        MORPHINE_T2,
        MORPHINE_T3,
        MORPHINE_T4,
        MORPHINE_T5,
        DESAT_T1,
        DESAT_T2,
        DESAT_T3,
        DESAT_T4,
        DESAT_T5
    }

    private float painStrength;
    private float morphineStrength;
    private float suppressionStrength;
    private float hitPulse;
    private float lastMissingHealth = -1.0F;
    private int lastHurtTime = -1;
    /** Client peak of current firstaid:morphine effect (not painkiller). */
    private int morphineEffectPeakTicks;
    private int lastMorphineEffectTicks;

    public void tick(Minecraft client) {
        Player player = client.player;
        if (player == null || !player.isAlive() || !FirstAid.isSynced) {
            painStrength = approach(painStrength, 0.0F, 0.035F);
            morphineStrength = approach(morphineStrength, 0.0F, 0.03F);
            suppressionStrength = approach(suppressionStrength, 0.0F, 0.025F);
            hitPulse = Math.max(0.0F, hitPulse - HIT_PULSE_DECAY * 2.0F);
            lastMissingHealth = -1.0F;
            return;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        PlayerDamageModel model = damageModel instanceof PlayerDamageModel m ? m : null;

        // Saturation ONLY while firstaid:morphine is active — never painkiller / model lag.
        boolean hasMorphineEffect = player.hasEffect(RegistryObjects.MORPHINE_EFFECT);
        boolean hasPainkiller = player.hasEffect(RegistryObjects.PAINKILLER_EFFECT);
        boolean painSuppressed = model != null && model.isPainSuppressed(player);

        tickHitPulse(player, model, painSuppressed);

        float targetPain = 0.0F;
        if (model != null && FirstAid.enablePainBlur) {
            float visual = model.getPainVisualStrength(painSuppressed);
            if (visual > 0.01F || (!painSuppressed && (model.getPainLevel() > 0 || hasVisibleInjury(model)))) {
                targetPain = Mth.clamp(visual, 0.0F, PlayerDamageModel.PAIN_HARD_CAP);
            }
            if (!painSuppressed) {
                if (model.isUnconscious()) {
                    targetPain = Math.max(targetPain, 0.55F);
                }
                if (model.isWithdrawalEpisodeActive()) {
                    targetPain = Math.max(targetPain, 0.75F);
                }
                targetPain = Math.max(targetPain, Mth.clamp(hitPulse * 0.85F, 0.0F, 1.0F));
            } else {
                targetPain = Math.max(targetPain, Mth.clamp(hitPulse * 0.35F * PlayerDamageModel.ACUTE_BREAKTHROUGH, 0.0F, 0.55F));
            }
        }
        if (FirstAid.enablePainBlur && isAdrenalineRushActive(player)) {
            targetPain = Math.max(targetPain, ADRENALINE_MODERATE_BLUR);
        }

        float targetMorphine = computeMorphineSatFromEffect(player);
        if (!hasMorphineEffect) {
            targetMorphine = 0.0F;
            morphineStrength = 0.0F;
            morphineEffectPeakTicks = 0;
            lastMorphineEffectTicks = 0;
        }

        float modelSuppression = model == null ? 0.0F : model.getSuppressionIntensity();
        float feedbackSuppression = ClientEventHandler.getSuppressionFeedbackController().getVisualStrength();
        float suppressionScale = FirstAid.lowSuppressionEnabled ? FirstAid.lowSuppressionMultiplier : 1.0F;
        float targetSuppression = FirstAid.suppressionDisplayCurve(Math.max(modelSuppression, feedbackSuppression) * suppressionScale);

        painStrength = approach(painStrength, targetPain, targetPain > painStrength ? PAIN_APPROACH_UP : PAIN_APPROACH_DOWN);
        if (hasMorphineEffect) {
            float morphineDown = Math.max(MORPHINE_APPROACH_DOWN, 0.06F);
            morphineStrength = approach(
                    morphineStrength,
                    targetMorphine,
                    targetMorphine > morphineStrength ? MORPHINE_APPROACH_UP : morphineDown
            );
        }
        suppressionStrength = approach(suppressionStrength, targetSuppression, targetSuppression > suppressionStrength ? 0.08F : 0.018F);
    }

    /** Sat 0..1 from firstaid:morphine remaining duration only. */
    private float computeMorphineSatFromEffect(Player player) {
        var effect = player.getEffect(RegistryObjects.MORPHINE_EFFECT);
        if (effect == null) {
            morphineEffectPeakTicks = 0;
            lastMorphineEffectTicks = 0;
            return 0.0F;
        }
        int dur = Math.max(0, effect.getDuration());
        if (dur <= 0) {
            morphineEffectPeakTicks = 0;
            lastMorphineEffectTicks = 0;
            return 0.0F;
        }
        if (morphineEffectPeakTicks <= 0 || dur > lastMorphineEffectTicks + 15) {
            morphineEffectPeakTicks = Math.max(morphineEffectPeakTicks, dur);
        }
        if (dur > morphineEffectPeakTicks) {
            morphineEffectPeakTicks = dur;
        }
        lastMorphineEffectTicks = dur;
        float ratio = Mth.clamp(dur / (float) Math.max(1, morphineEffectPeakTicks), 0.0F, 1.0F);
        float progress = 1.0F - ratio;
        float base = (float) Math.exp(-1.65D * (double) progress);
        float continuousShape = ratio * ratio;
        float shaped = base * (0.30F + 0.70F * continuousShape);
        float endT = Mth.clamp(ratio / 0.08F, 0.0F, 1.0F);
        float endFade = endT * endT * (3.0F - 2.0F * endT);
        float curve = shaped * endFade;
        AbstractPlayerDamageModel dm = CommonUtils.getDamageModel(player);
        if (dm instanceof PlayerDamageModel pdm && pdm.getMorphineRushTicks() > 0) {
            float rushT = 1.0F - pdm.getMorphineRushTicks() / 200.0F;
            curve = Math.max(curve, 0.90F * (1.0F - 0.20F * rushT));
        }
        return Mth.clamp(curve, 0.0F, 1.0F);
    }

    public void clear() {
        painStrength = 0.0F;
        morphineStrength = 0.0F;
        suppressionStrength = 0.0F;
        hitPulse = 0.0F;
        lastMissingHealth = -1.0F;
        lastHurtTime = -1;
        morphineEffectPeakTicks = 0;
        lastMorphineEffectTicks = 0;
    }

    public float getPainStrength() {
        return painStrength;
    }

    public float getMorphineStrength() {
        return morphineStrength;
    }

    public float getSuppressionStrength() {
        return suppressionStrength;
    }

    public float getHitPulse() {
        return hitPulse;
    }

    public float getNetColorAmount() {
        return MORPHINE_SAT_PEAK * morphineStrength - SUPPRESSION_DESAT_PEAK * suppressionStrength;
    }

    public void renderOverlay(GuiGraphicsExtractor guiGraphics, int width, int height, float partialTick, float pulseTime) {
        if (FirstAid.enablePainVignette && hitPulse > 0.02F) {
            float wave = 0.72F + 0.28F * Mth.sin(pulseTime * 0.42F);
            float intensity = Mth.clamp(hitPulse * wave, 0.0F, 1.35F);
            renderRedPulseVignette(guiGraphics, width, height, intensity);
        }
        if (suppressionStrength > 0.02F) {
            renderSuppressionGrayRim(guiGraphics, width, height, suppressionStrength, pulseTime);
        }
    }

    private void tickHitPulse(Player player, PlayerDamageModel model, boolean painSuppressed) {
        float missing = model == null ? 0.0F : sumMissingHealth(model);
        boolean tookHit = false;

        if (lastMissingHealth < 0.0F) {
            lastMissingHealth = missing;
        } else if (missing > lastMissingHealth + 0.04F) {
            float delta = missing - lastMissingHealth;
            float add = Mth.clamp(0.10F + delta * 0.14F, 0.12F, 0.70F);
            hitPulse = Math.min(HIT_PULSE_MAX, hitPulse + add);
            tookHit = true;
        }
        lastMissingHealth = missing;

        int hurtTime = player.hurtTime;
        if (!tookHit && hurtTime > 0 && hurtTime != lastHurtTime && hurtTime >= player.hurtDuration - 1) {
            hitPulse = Math.min(HIT_PULSE_MAX, hitPulse + 0.14F);
            tookHit = true;
        }
        lastHurtTime = hurtTime;

        if (!tookHit) {
            float decay = painSuppressed ? HIT_PULSE_SUPPRESSED_DECAY : HIT_PULSE_DECAY;
            hitPulse = Math.max(0.0F, hitPulse - decay);
        }
        if (painSuppressed) {
            hitPulse = Math.max(0.0F, hitPulse - HIT_PULSE_SUPPRESSED_DECAY * 0.5F);
        }
    }

    public void applyPostEffect(Minecraft client) {
        if (client.gameRenderer == null) {
            return;
        }
        List<Identifier> requested = client.gameRenderer.gameRenderState().requestedPostEffects;
        Identifier id = effectId(resolveDesiredEffect());
        if (id != null && !requested.contains(id)) {
            requested.add(id);
        }
    }

    private ActiveEffect resolveDesiredEffect() {
        float net = getNetColorAmount();

        // Morphine remaining tiers (positive net)
        if (net > 0.58F) {
            return ActiveEffect.MORPHINE_T5;
        }
        if (net > 0.42F) {
            return ActiveEffect.MORPHINE_T4;
        }
        if (net > 0.28F) {
            return ActiveEffect.MORPHINE_T3;
        }
        if (net > 0.14F) {
            return ActiveEffect.MORPHINE_T2;
        }
        if (net > 0.04F) {
            return ActiveEffect.MORPHINE_T1;
        }

        // Suppression desat+blur tiers (negative net or raw suppression)
        // Prefer desat+blur whenever suppression is meaningfully active.
        float s = suppressionStrength;
        if (net < -0.55F || s > 0.78F) {
            return ActiveEffect.DESAT_T5;
        }
        if (net < -0.38F || s > 0.58F) {
            return ActiveEffect.DESAT_T4;
        }
        if (net < -0.22F || s > 0.38F) {
            return ActiveEffect.DESAT_T3;
        }
        if (net < -0.10F || s > 0.20F) {
            return ActiveEffect.DESAT_T2;
        }
        if (net < -0.03F || s > 0.06F) {
            return ActiveEffect.DESAT_T1;
        }

        if (painStrength > 0.70F) {
            return ActiveEffect.PAIN_HIGH;
        }
        if (painStrength > 0.40F) {
            return ActiveEffect.PAIN_MID;
        }
        if (painStrength > 0.04F) {
            return ActiveEffect.PAIN_LOW;
        }
        return ActiveEffect.NONE;
    }

    private static Identifier effectId(ActiveEffect effect) {
        return switch (effect) {
            case PAIN_LOW -> PAIN_LOW;
            case PAIN_MID -> PAIN_MID;
            case PAIN_HIGH -> PAIN_HIGH;
            case MORPHINE_T1 -> MORPHINE_T1;
            case MORPHINE_T2 -> MORPHINE_T2;
            case MORPHINE_T3 -> MORPHINE_T3;
            case MORPHINE_T4 -> MORPHINE_T4;
            case MORPHINE_T5 -> MORPHINE_T5;
            case DESAT_T1 -> DESAT_T1;
            case DESAT_T2 -> DESAT_T2;
            case DESAT_T3 -> DESAT_T3;
            case DESAT_T4 -> DESAT_T4;
            case DESAT_T5 -> DESAT_T5;
            case NONE -> null;
        };
    }

    private static float sumMissingHealth(PlayerDamageModel model) {
        float total = 0.0F;
        for (AbstractDamageablePart part : model) {
            total += CommonUtils.getVisibleMissingHealth(part);
        }
        return total;
    }

    private static boolean hasVisibleInjury(PlayerDamageModel model) {
        return sumMissingHealth(model) > 0.0F;
    }

    private static void renderRedPulseVignette(GuiGraphicsExtractor guiGraphics, int width, int height, float intensity) {
        int layers = 8;
        int baseThickness = 18 + Math.round(10.0F * Math.min(1.0F, intensity));
        for (int layer = 0; layer < layers; layer++) {
            float progress = (layer + 1) / (float) layers;
            float falloff = 1.0F - progress;
            int thickness = Math.max(3, Math.round(baseThickness * (0.30F + progress * (1.20F + intensity * 0.90F))));
            int alpha = Math.round((10.0F + 92.0F * intensity) * falloff * falloff);
            if (alpha > 0) {
                fillEdge(guiGraphics, width, height, color(alpha, 170, 12, 12), thickness);
            }
        }
        int wash = Math.round(4.0F + 28.0F * Math.min(1.0F, intensity));
        if (wash > 0) {
            guiGraphics.fill(0, 0, width, height, color(wash, 140, 8, 8));
        }
    }

    private static void renderSuppressionGrayRim(GuiGraphicsExtractor guiGraphics, int width, int height, float strength, float pulseTime) {
        float s = Mth.clamp(strength, 0.0F, 1.0F);
        float pulse = 0.90F + 0.10F * Mth.sin(pulseTime * 0.38F + 0.6F);
        float intensity = s * pulse;
        int layers = 10;
        int baseThickness = 16 + Math.round(34.0F * s);
        for (int layer = 0; layer < layers; layer++) {
            float progress = (layer + 1) / (float) layers;
            float falloff = 1.0F - progress;
            int thickness = Math.max(4, Math.round(baseThickness * (0.28F + progress * (1.15F + intensity * 1.05F))));
            int alpha = Math.round((144.0F * intensity) * falloff * falloff);
            if (alpha > 0) {
                int r = 188 + Math.round(28.0F * s);
                int g = 192 + Math.round(30.0F * s);
                int b = 200 + Math.round(28.0F * s);
                fillEdge(guiGraphics, width, height, color(Math.min(220, alpha), r, g, b), thickness);
            }
        }
        int wash = Math.round(48.0F * s);
        if (wash > 0) {
            guiGraphics.fill(0, 0, width, height, color(wash, 200, 204, 210));
        }
    }

    private static void fillEdge(GuiGraphicsExtractor guiGraphics, int width, int height, int color, int thickness) {
        guiGraphics.fill(0, 0, width, thickness, color);
        guiGraphics.fill(0, height - thickness, width, height, color);
        guiGraphics.fill(0, thickness, thickness, height - thickness, color);
        guiGraphics.fill(width - thickness, thickness, width, height - thickness, color);
    }

    private static int color(int alpha, int red, int green, int blue) {
        return (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | blue & 255;
    }

    private static float approach(float current, float target, float delta) {
        return current < target ? Math.min(target, current + delta) : Math.max(target, current - delta);
    }

    private static float smoothstep(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private static boolean isAdrenalineRushActive(Player player) {
        var absorption = player.getEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
        if (absorption == null || absorption.getAmplifier() < 1) {
            return false;
        }
        return player.hasEffect(net.minecraft.world.effect.MobEffects.HASTE)
                && player.hasEffect(net.minecraft.world.effect.MobEffects.STRENGTH)
                && player.hasEffect(net.minecraft.world.effect.MobEffects.SPEED);
    }
}

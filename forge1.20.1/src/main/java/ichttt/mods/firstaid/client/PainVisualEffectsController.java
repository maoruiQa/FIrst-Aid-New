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

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.mixin.client.GameRendererPostAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Classic visuals: tiered composite post for morphine sat (baked Amount, like 26.2),
 * live Strength for pain blur, hit red pulse, gray-white suppression rim.
 * <p>
 * Saturation uses baked tiers because live Amount uniforms are unreliable on some
 * Fabric 1.21.1 setups (stuck peak + no clear after morphine ends).
 */
public final class PainVisualEffectsController {
    private static final ResourceLocation[] COMPOSITE_SAT_TIERS = {
            new ResourceLocation(FirstAid.MODID, "shaders/post/composite_sat_t0.json"),
            new ResourceLocation(FirstAid.MODID, "shaders/post/composite_sat_t1.json"),
            new ResourceLocation(FirstAid.MODID, "shaders/post/composite_sat_t2.json"),
            new ResourceLocation(FirstAid.MODID, "shaders/post/composite_sat_t3.json"),
            new ResourceLocation(FirstAid.MODID, "shaders/post/composite_sat_t4.json"),
            new ResourceLocation(FirstAid.MODID, "shaders/post/composite_sat_t5.json")
    };
    /** Baked Amount values — keep in sync with composite_sat_t*.json */
    private static final float[] BAKED_SAT_AMOUNT = {0.0F, 0.16F, 0.30F, 0.45F, 0.60F, 0.76F};

    private static final float PAIN_SHADER_PEAK = 0.18F;
    private static final float SUPPRESSION_BLUR_PEAK = 0.11F;
    private static final float ADRENALINE_MODERATE_BLUR = 0.40F;
    private static final float MORPHINE_SAT_PEAK = 0.78F;
    private static final float SUPPRESSION_DESAT_PEAK = 1.05F;
    private static final float HIT_PULSE_MAX = 1.55F;
    private static final float HIT_PULSE_DECAY = 0.014F;
    private static final float HIT_PULSE_SUPPRESSED_DECAY = 0.06F;
    private static final float PAIN_APPROACH_UP = 0.22F;
    private static final float PAIN_APPROACH_DOWN = 0.028F;
    private static final float MORPHINE_APPROACH_UP = 0.12F;
    private static final float MORPHINE_APPROACH_DOWN = 0.06F;

    private float painStrength;
    private float morphineStrength;
    private float suppressionStrength;
    private float hitPulse;
    private float lastMissingHealth = -1.0F;
    private int lastHurtTime = -1;
    private boolean compositeActive;
    private int activeSatTier = -1;
    private int loadFailCooldown;
    private int loadFailStreak;
    /** Client-only peak of current morphine effect duration for sat curve (not painkiller). */
    private int morphineEffectPeakTicks;
    private int lastMorphineEffectTicks;

    public void tick(Minecraft client) {
        Player player = client.player;
        if (player == null || !player.isAlive() || !FirstAid.isSynced) {
            painStrength = approach(painStrength, 0.0F, 0.035F);
            morphineStrength = 0.0F;
            suppressionStrength = approach(suppressionStrength, 0.0F, 0.025F);
            hitPulse = Math.max(0.0F, hitPulse - HIT_PULSE_DECAY * 2.0F);
            lastMissingHealth = -1.0F;
            updateEffect(client);
            return;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        PlayerDamageModel model = damageModel instanceof PlayerDamageModel m ? m : null;

        // Saturation is bound ONLY to firstaid:morphine — never painkiller.
        boolean hasMorphineEffect = player.hasEffect(RegistryObjects.MORPHINE_EFFECT.get());
        boolean hasPainkiller = player.hasEffect(RegistryObjects.PAINKILLER_EFFECT.get());
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

        // Pure client curve from the morphine mob-effect duration (ignores painkiller / model lag).
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
            morphineStrength = approach(
                    morphineStrength,
                    targetMorphine,
                    targetMorphine > morphineStrength ? MORPHINE_APPROACH_UP : MORPHINE_APPROACH_DOWN
            );
        }
        suppressionStrength = approach(suppressionStrength, targetSuppression, targetSuppression > suppressionStrength ? 0.08F : 0.018F);
        updateEffect(client);
    }

    /**
     * Saturation 0..1 from {@code firstaid:morphine} remaining duration only.
     * Painkiller must never feed this path.
     */
    private float computeMorphineSatFromEffect(Player player) {
        var effect = player.getEffect(RegistryObjects.MORPHINE_EFFECT.get());
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
        // New dose / refresh: duration jumped up (or first observation).
        if (morphineEffectPeakTicks <= 0 || dur > lastMorphineEffectTicks + 15) {
            morphineEffectPeakTicks = Math.max(morphineEffectPeakTicks, dur);
        }
        if (dur > morphineEffectPeakTicks) {
            morphineEffectPeakTicks = dur;
        }
        lastMorphineEffectTicks = dur;

        float ratio = Mth.clamp(dur / (float) Math.max(1, morphineEffectPeakTicks), 0.0F, 1.0F);
        float progress = 1.0F - ratio;
        // Decelerating descent: steep early, flatter late.
        float base = (float) Math.exp(-1.65D * (double) progress);
        float continuousShape = ratio * ratio;
        float shaped = base * (0.30F + 0.70F * continuousShape);
        float endT = Mth.clamp(ratio / 0.08F, 0.0F, 1.0F);
        float endFade = endT * endT * (3.0F - 2.0F * endT);
        float curve = shaped * endFade;

        // Rush floor only while model still reports rush window (optional).
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel instanceof PlayerDamageModel pdm && pdm.getMorphineRushTicks() > 0) {
            float rushT = 1.0F - pdm.getMorphineRushTicks() / 200.0F;
            float rushEnv = 1.0F - 0.20F * rushT;
            curve = Math.max(curve, 0.90F * rushEnv);
        }
        return Mth.clamp(curve, 0.0F, 1.0F);
    }

    public void clear(Minecraft client) {
        painStrength = 0.0F;
        morphineStrength = 0.0F;
        suppressionStrength = 0.0F;
        hitPulse = 0.0F;
        lastMissingHealth = -1.0F;
        lastHurtTime = -1;
        morphineEffectPeakTicks = 0;
        lastMorphineEffectTicks = 0;
        forceShutdown(client);
        loadFailCooldown = 0;
        loadFailStreak = 0;
    }

    public float getPainStrength() { return painStrength; }
    public float getMorphineStrength() { return morphineStrength; }
    public float getSuppressionStrength() { return suppressionStrength; }
    public float getHitPulse() { return hitPulse; }

    public float getNetColorAmount() {
        return MORPHINE_SAT_PEAK * morphineStrength - SUPPRESSION_DESAT_PEAK * suppressionStrength;
    }

    public void renderOverlay(GuiGraphics guiGraphics, int width, int height, float pulseTime) {
        if (FirstAid.enablePainVignette && hitPulse > 0.02F) {
            float wave = 0.72F + 0.28F * Mth.sin(pulseTime * 0.42F);
            float intensity = Mth.clamp(hitPulse * wave, 0.0F, 1.35F);
            renderRedPulseVignette(guiGraphics, width, height, intensity);
        }
        if (suppressionStrength > 0.02F) {
            renderSuppressionGrayRim(guiGraphics, width, height, suppressionStrength, pulseTime);
        }
    }

    public void processFrame(Minecraft client, float partialTick) {
        updateEffect(client);
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
            hitPulse = Math.max(0.0F, hitPulse - (painSuppressed ? HIT_PULSE_SUPPRESSED_DECAY : HIT_PULSE_DECAY));
        }
        if (painSuppressed) {
            hitPulse = Math.max(0.0F, hitPulse - HIT_PULSE_SUPPRESSED_DECAY * 0.5F);
        }
    }

    private void updateEffect(Minecraft client) {
        if (client.gameRenderer == null) {
            return;
        }
        if (loadFailCooldown > 0) {
            loadFailCooldown--;
        }

        // Sat tier is driven solely by morphineStrength (morphine effect curve).
        int satTier = resolveSatTier(morphineStrength);
        boolean needBlur = FirstAid.enablePainBlur
                && (painStrength > 0.03F || suppressionStrength > 0.04F);
        // Never keep the composite alive for painkiller alone.
        boolean want = satTier > 0 || needBlur;

        if (!want) {
            forceShutdown(client);
            return;
        }

        ResourceLocation desired = COMPOSITE_SAT_TIERS[satTier];
        PostChain chain = getPostEffect(client);
        boolean ours = isOurComposite(chain);
        boolean wrongTier = !ours || activeSatTier != satTier;

        if (wrongTier) {
            if (loadFailCooldown > 0) {
                return;
            }
            try {
                // Always reload when tier changes so baked Amount cannot stick at peak.
                client.gameRenderer.loadEffect(desired);
                chain = getPostEffect(client);
                ours = isOurComposite(chain);
                if (!ours) {
                    loadFailStreak = Math.min(loadFailStreak + 1, 8);
                    loadFailCooldown = 20 * loadFailStreak;
                    FirstAid.LOGGER.warn(
                            "FirstAid composite sat tier {} not active after load (streak={}); will retry",
                            satTier,
                            loadFailStreak
                    );
                    compositeActive = false;
                    activeSatTier = -1;
                    return;
                }
                activeSatTier = satTier;
                compositeActive = true;
                loadFailStreak = 0;
                loadFailCooldown = 0;
            } catch (Exception e) {
                compositeActive = false;
                activeSatTier = -1;
                loadFailStreak = Math.min(loadFailStreak + 1, 8);
                loadFailCooldown = 20 * loadFailStreak;
                FirstAid.LOGGER.error("Failed to load firstaid composite sat tier {}", satTier, e);
                return;
            }
        } else {
            compositeActive = true;
        }

        applyUniforms(chain, satTier);
    }

    private void applyUniforms(PostChain chain, int satTier) {
        if (chain == null) {
            return;
        }
        // Baked Amount is authoritative for sat (Fabric live uniforms were unreliable).
        float amount = BAKED_SAT_AMOUNT[Mth.clamp(satTier, 0, BAKED_SAT_AMOUNT.length - 1)];
        PostChainUniforms.setFloat(chain, "Amount", amount);

        float blur = 0.0F;
        if (FirstAid.enablePainBlur) {
            if (painStrength > 0.02F) {
                float spike = Mth.clamp(painStrength, 0.0F, PlayerDamageModel.PAIN_HARD_CAP);
                float norm = Mth.clamp(0.22F + spike * 0.70F, 0.22F, 1.45F);
                blur = Math.max(blur, PAIN_SHADER_PEAK * norm);
            }
            if (suppressionStrength > 0.03F) {
                float s = Mth.clamp(suppressionStrength, 0.0F, 1.0F);
                blur = Math.max(blur, SUPPRESSION_BLUR_PEAK * s);
            }
        }
        PostChainUniforms.setFloat(chain, "Strength", blur);
    }

    /**
     * Map continuous morphine strength (0..1 after curve) onto discrete sat tiers.
     */
    private static int resolveSatTier(float morphineStrength) {
        float m = Mth.clamp(morphineStrength, 0.0F, 1.0F);
        if (m <= 0.04F) {
            return 0;
        }
        if (m <= 0.18F) {
            return 1;
        }
        if (m <= 0.34F) {
            return 2;
        }
        if (m <= 0.52F) {
            return 3;
        }
        if (m <= 0.72F) {
            return 4;
        }
        return 5;
    }

    private static PostChain getPostEffect(Minecraft client) {
        if (client.gameRenderer == null) {
            return null;
        }
        return ((GameRendererPostAccessor) client.gameRenderer).firstaid$getPostEffect();
    }

    private static boolean isOurComposite(PostChain chain) {
        if (chain == null) {
            return false;
        }
        String name = chain.getName();
        if (name == null || name.isEmpty()) {
            return false;
        }
        return name.contains("firstaid") && (name.contains("composite") || name.contains("composite_sat"));
    }

    private void forceShutdown(Minecraft client) {
        if (client.gameRenderer == null) {
            compositeActive = false;
            activeSatTier = -1;
            return;
        }
        try {
            PostChain chain = getPostEffect(client);
            if (isOurComposite(chain)) {
                // Zero before tear-down so a failed shutdown never leaves peak sat.
                PostChainUniforms.setFloat(chain, "Amount", 0.0F);
                PostChainUniforms.setFloat(chain, "Strength", 0.0F);
                client.gameRenderer.shutdownEffect();
            }
        } catch (Exception ignored) {
        }
        compositeActive = false;
        activeSatTier = -1;
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

    private static void renderRedPulseVignette(GuiGraphics guiGraphics, int width, int height, float intensity) {
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

    private static void renderSuppressionGrayRim(GuiGraphics guiGraphics, int width, int height, float strength, float pulseTime) {
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

    private static void fillEdge(GuiGraphics guiGraphics, int width, int height, int color, int thickness) {
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

    private static boolean isAdrenalineRushActive(Player player) {
        var absorption = player.getEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
        if (absorption == null || absorption.getAmplifier() < 1) {
            return false;
        }
        return player.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED)
                && player.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST)
                && player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
    }
}

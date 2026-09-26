package ichttt.mods.firstaid.common.potion;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Applies status effects that milk (and other cure sources) cannot permanently clear.
 * <p>
 * Strategy:
 * <ul>
 *   <li>Strip curative-item / EffectCure data when the runtime API exposes it</li>
 *   <li>Callers re-apply every tick while model state is active, so vanilla milk
 *       {@code clear_all_effects} only blanks the icon for at most one tick</li>
 * </ul>
 */
public final class MilkImmuneMobEffectInstance {
    private static final Method GET_CURATIVE_ITEMS;
    private static final Method SET_CURATIVE_ITEMS;
    private static final Method GET_CURES;
    private static final Method SET_CURES;

    static {
        Method getCurative = null;
        Method setCurative = null;
        Method getCures = null;
        Method setCures = null;
        try {
            getCurative = MobEffectInstance.class.getMethod("getCurativeItems");
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            setCurative = MobEffectInstance.class.getMethod("setCurativeItems", Collection.class);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            getCures = MobEffectInstance.class.getMethod("getCures");
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            setCures = MobEffectInstance.class.getMethod("setCures", Set.class);
        } catch (ReflectiveOperationException ignored) {
        }
        GET_CURATIVE_ITEMS = getCurative;
        SET_CURATIVE_ITEMS = setCurative;
        GET_CURES = getCures;
        SET_CURES = setCures;
    }

    private MilkImmuneMobEffectInstance() {
    }

    public static MobEffectInstance create(Holder<MobEffect> effect, int durationTicks) {
        return create(effect, durationTicks, 0, true);
    }

    public static MobEffectInstance create(Holder<MobEffect> effect, int durationTicks, int amplifier, boolean showIcon) {
        MobEffectInstance instance = new MobEffectInstance(effect, Math.max(2, durationTicks), amplifier, false, true, showIcon);
        stripCures(instance);
        return instance;
    }

    public static void apply(LivingEntity entity, Holder<MobEffect> effect, int durationTicks) {
        apply(entity, effect, durationTicks, 0, true);
    }

    public static void apply(LivingEntity entity, Holder<MobEffect> effect, int durationTicks, int amplifier, boolean showIcon) {
        entity.addEffect(create(effect, durationTicks, amplifier, showIcon));
    }

    /**
     * Ensures the effect is present for the remaining duration; re-applies after milk clears it.
     */
    public static void ensure(LivingEntity entity, Holder<MobEffect> effect, int durationTicks) {
        ensure(entity, effect, durationTicks, 0, true);
    }

    public static void ensure(LivingEntity entity, Holder<MobEffect> effect, int durationTicks, int amplifier, boolean showIcon) {
        int remaining = Math.max(2, durationTicks);
        MobEffectInstance active = entity.getEffect(effect);
        if (active == null || active.getDuration() < remaining - 1 || active.getAmplifier() != amplifier) {
            apply(entity, effect, remaining, amplifier, showIcon);
        } else {
            // Keep cure list empty if milk recreated a default instance somehow.
            stripCures(active);
        }
    }

    public static void stripCures(MobEffectInstance instance) {
        if (instance == null) {
            return;
        }
        try {
            if (SET_CURES != null) {
                SET_CURES.invoke(instance, Collections.emptySet());
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            if (SET_CURATIVE_ITEMS != null) {
                SET_CURATIVE_ITEMS.invoke(instance, Collections.emptyList());
            } else if (GET_CURATIVE_ITEMS != null) {
                Object curative = GET_CURATIVE_ITEMS.invoke(instance);
                if (curative instanceof Collection<?> collection) {
                    collection.clear();
                }
            }
        } catch (ReflectiveOperationException | UnsupportedOperationException ignored) {
        }
        try {
            if (GET_CURES != null) {
                Object cures = GET_CURES.invoke(instance);
                if (cures instanceof Collection<?> collection) {
                    collection.clear();
                }
            }
        } catch (ReflectiveOperationException | UnsupportedOperationException ignored) {
        }
    }
}

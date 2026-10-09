package ichttt.mods.firstaid.common.damagesystem.debuff;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

public record OnHitDebuffEntry(float damageTakenThreshold, int effectDuration, float healthFractionThreshold) {
   public static final Codec<OnHitDebuffEntry> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.FLOAT.fieldOf("damageTakenThreshold").forGetter(o -> o.damageTakenThreshold),
            ExtraCodecs.intRange(1, 32767).fieldOf("effectDuration").forGetter(o -> o.effectDuration),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("healthFractionThreshold", 0.0F).forGetter(o -> o.healthFractionThreshold)
         )
         .apply(instance, OnHitDebuffEntry::new)
   );
    public OnHitDebuffEntry(float damageTakenThreshold, int effectDuration) {
        this(damageTakenThreshold, effectDuration, 0.0F);
    }
}

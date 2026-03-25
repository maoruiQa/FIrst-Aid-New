package ichttt.mods.firstaid.common.damagesystem.debuff.builder;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.api.debuff.IDebuffBuilder;
import ichttt.mods.firstaid.api.enums.EnumDebuffSlot;
import ichttt.mods.firstaid.common.damagesystem.debuff.OnHitDebuff;
import ichttt.mods.firstaid.common.damagesystem.debuff.OnHitDebuffEntry;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

public class OnHitDebuffBuilder implements IDebuffBuilder {
   public static final MapCodec<OnHitDebuffBuilder> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            StringRepresentable.fromEnum(EnumDebuffSlot::values).fieldOf("debuffSlot").forGetter(o -> o.debuffSlot),
            Identifier.CODEC.fieldOf("potionName").forGetter(o -> o.effect),
            OnHitDebuffEntry.CODEC.listOf().fieldOf("timeBoundaries").forGetter(o -> o.timeBoundaries),
            Identifier.CODEC.optionalFieldOf("soundEvent").forGetter(o -> o.sound == null ? Optional.empty() : Optional.of(o.sound))
         )
         .apply(instance, OnHitDebuffBuilder::new)
   );
   private final EnumDebuffSlot debuffSlot;
   private final Identifier effect;
   private final List<OnHitDebuffEntry> timeBoundaries;
   private final Identifier sound;

   public OnHitDebuffBuilder(EnumDebuffSlot debuffSlot, Identifier effect, List<OnHitDebuffEntry> timeBoundaries, Optional<Identifier> sound) {
      this.debuffSlot = debuffSlot;
      this.effect = effect;
      this.timeBoundaries = timeBoundaries;
      this.sound = sound.orElse(null);
   }

   @Override
   public MapCodec<? extends IDebuffBuilder> codec() {
      return CODEC;
   }

   @Override
   public EnumDebuffSlot affectedSlot() {
      return this.debuffSlot;
   }

   @Override
   public IDebuff build() {
      return new OnHitDebuff(this.effect, this.timeBoundaries, this.sound);
   }
}

package ichttt.mods.firstaid.common.damagesystem.debuff.builder;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.api.debuff.IDebuffBuilder;
import ichttt.mods.firstaid.api.enums.EnumDebuffSlot;
import ichttt.mods.firstaid.common.damagesystem.debuff.ConstantDebuff;
import ichttt.mods.firstaid.common.damagesystem.debuff.ConstantDebuffEntry;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

public class ConstantDebuffBuilder implements IDebuffBuilder {
   public static final MapCodec<ConstantDebuffBuilder> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            StringRepresentable.fromEnum(EnumDebuffSlot::values).fieldOf("debuffSlot").forGetter(o -> o.debuffSlot),
            Identifier.CODEC.fieldOf("potionName").forGetter(o -> o.effect),
            ConstantDebuffEntry.CODEC.listOf().fieldOf("amplifierBoundaries").forGetter(o -> o.amplifierBoundaries)
         )
         .apply(instance, ConstantDebuffBuilder::new)
   );
   private final EnumDebuffSlot debuffSlot;
   private final Identifier effect;
   private final List<ConstantDebuffEntry> amplifierBoundaries;

   public ConstantDebuffBuilder(EnumDebuffSlot debuffSlot, Identifier effect, List<ConstantDebuffEntry> amplifierBoundaries) {
      this.debuffSlot = debuffSlot;
      this.effect = effect;
      this.amplifierBoundaries = amplifierBoundaries;
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
      return new ConstantDebuff(this.effect, this.amplifierBoundaries);
   }
}

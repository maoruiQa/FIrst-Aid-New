package ichttt.mods.firstaid.common.damagesystem.distribution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.util.CommonUtils;
import javax.annotation.Nonnull;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

public class DirectDamageDistributionAlgorithm implements IDamageDistributionAlgorithm {
   public static final MapCodec<DirectDamageDistributionAlgorithm> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            StringRepresentable.fromEnum(() -> EnumPlayerPart.VALUES).fieldOf("part").forGetter(o -> o.part),
            Codec.BOOL.fieldOf("debuff").forGetter(o -> o.debuff)
         )
         .apply(instance, DirectDamageDistributionAlgorithm::new)
   );
   private final EnumPlayerPart part;
   private final boolean debuff;

   public DirectDamageDistributionAlgorithm(EnumPlayerPart part, boolean debuff) {
      this.part = part;
      this.debuff = debuff;
   }

   @Override
   public float distributeDamage(float damage, @Nonnull Player player, @Nonnull DamageSource source, boolean addStat) {
      AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
      return damageModel == null ? 0.0F : damageModel.getFromEnum(this.part).damage(damage, player, this.debuff);
   }

   @Override
   public MapCodec<DirectDamageDistributionAlgorithm> codec() {
      return CODEC;
   }
}

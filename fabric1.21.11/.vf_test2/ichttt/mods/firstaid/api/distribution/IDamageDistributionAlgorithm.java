package ichttt.mods.firstaid.api.distribution;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nonnull;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

public interface IDamageDistributionAlgorithm {
   float distributeDamage(float var1, @Nonnull Player var2, @Nonnull DamageSource var3, boolean var4);

   default boolean skipGlobalPotionModifiers() {
      return false;
   }

   MapCodec<? extends IDamageDistributionAlgorithm> codec();
}

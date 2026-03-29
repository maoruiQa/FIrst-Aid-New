package ichttt.mods.firstaid.api.distribution;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.world.damagesource.DamageType;

public interface IDamageDistributionTarget {
   IDamageDistributionAlgorithm getAlgorithm();

   List<DamageType> buildApplyList(RegistryLookup<DamageType> var1);

   boolean isDynamic();

   MapCodec<? extends IDamageDistributionTarget> codec();
}

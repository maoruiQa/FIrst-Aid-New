package ichttt.mods.firstaid.common.damagesystem.distribution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.annotation.Nonnull;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.tuple.Pair;

public class RandomDamageDistributionAlgorithm extends DamageDistribution {
   public static final MapCodec<RandomDamageDistributionAlgorithm> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(Codec.BOOL.fieldOf("nearestFirst").forGetter(o -> o.nearestFirst), Codec.BOOL.fieldOf("tryNoKill").forGetter(o -> o.tryNoKill))
         .apply(instance, RandomDamageDistributionAlgorithm::pick)
   );
   public static final RandomDamageDistributionAlgorithm NEAREST_NOKILL = new RandomDamageDistributionAlgorithm(true, true);
   public static final RandomDamageDistributionAlgorithm NEAREST_KILL = new RandomDamageDistributionAlgorithm(true, false);
   public static final RandomDamageDistributionAlgorithm ANY_NOKILL = new RandomDamageDistributionAlgorithm(false, true);
   public static final RandomDamageDistributionAlgorithm ANY_KILL = new RandomDamageDistributionAlgorithm(false, false);
   private static final Random RANDOM = new Random();
   private final boolean nearestFirst;
   private final boolean tryNoKill;

   public static RandomDamageDistributionAlgorithm getDefault() {
      return FirstAidConfig.SERVER.useFriendlyRandomDistribution.get() ? NEAREST_NOKILL : NEAREST_KILL;
   }

   public static RandomDamageDistributionAlgorithm pick(boolean nearestFirst, boolean tryNoKill) {
      if (nearestFirst) {
         return tryNoKill ? NEAREST_NOKILL : NEAREST_KILL;
      } else {
         return tryNoKill ? ANY_NOKILL : ANY_KILL;
      }
   }

   protected RandomDamageDistributionAlgorithm(boolean nearestFirst, boolean tryNoKill) {
      this.nearestFirst = nearestFirst;
      this.tryNoKill = tryNoKill;
   }

   @Override
   protected float minHealth(@Nonnull Player player, @Nonnull AbstractDamageablePart playerPart) {
      return this.tryNoKill && playerPart.canCauseDeath ? 1.0F : 0.0F;
   }

   @Nonnull
   @Override
   protected List<Pair<EquipmentSlot, EnumPlayerPart[]>> getPartList() {
      if (this.nearestFirst) {
         int startValue = RANDOM.nextInt(4);
         return addAllRandom(startValue, RANDOM.nextBoolean());
      } else {
         List<Pair<EquipmentSlot, EnumPlayerPart[]>> partList = new ArrayList<>();
         List<EquipmentSlot> slots = Arrays.asList(EquipmentSlot.values());
         Collections.shuffle(slots, RANDOM);

         for (EquipmentSlot slot : slots) {
            if (CommonUtils.isValidArmorSlot(slot)) {
               List<EnumPlayerPart> parts = CommonUtils.getPartListForSlot(slot);
               if (!parts.isEmpty()) {
                  Collections.shuffle(parts);
                  partList.add(Pair.of(slot, parts.toArray(new EnumPlayerPart[0])));
               }
            }
         }

         return partList;
      }
   }

   public static List<Pair<EquipmentSlot, EnumPlayerPart[]>> addAllRandom(int startValue, boolean up) {
      List<Pair<EquipmentSlot, EnumPlayerPart[]>> partList = new ArrayList<>();

      for (int i = 0; i < CommonUtils.ARMOR_SLOTS.length; i++) {
         int posInArray = Math.abs(i - (up ? 0 : 3)) + startValue;
         if (posInArray > 3) {
            posInArray -= 4;
         }

         EquipmentSlot slot = CommonUtils.ARMOR_SLOTS[posInArray];
         List<EnumPlayerPart> parts = CommonUtils.getPartListForSlot(slot);
         if (!parts.isEmpty()) {
            Collections.shuffle(parts);
            partList.add(Pair.of(slot, parts.toArray(new EnumPlayerPart[0])));
         }
      }

      return partList;
   }

   @Override
   public MapCodec<RandomDamageDistributionAlgorithm> codec() {
      return CODEC;
   }
}

package ichttt.mods.firstaid.common.damagesystem.distribution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.tuple.Pair;

public class StandardDamageDistributionAlgorithm extends DamageDistribution {
   public static final MapCodec<StandardDamageDistributionAlgorithm> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            Codec.unboundedMap(EquipmentSlot.CODEC, StringRepresentable.fromEnum(() -> EnumPlayerPart.VALUES).listOf())
               .fieldOf("partMap")
               .forGetter(o -> o.builtList.stream().collect(Collectors.toMap(Pair::getLeft, pair -> Arrays.asList((EnumPlayerPart[])pair.getRight())))),
            Codec.BOOL.optionalFieldOf("shuffle", false).forGetter(o -> o.shuffle),
            Codec.BOOL.optionalFieldOf("doNeighbours", true).forGetter(o -> o.doNeighbours)
         )
         .apply(instance, StandardDamageDistributionAlgorithm::new)
   );
   private final boolean shuffle;
   private final boolean doNeighbours;
   private final EnumSet<EnumPlayerPart> blockedParts;
   private final List<Pair<EquipmentSlot, EnumPlayerPart[]>> builtList;

   public StandardDamageDistributionAlgorithm(Map<EquipmentSlot, List<EnumPlayerPart>> partList, boolean shuffle, boolean doNeighbours) {
      this.builtList = new ArrayList<>(partList.size());

      for (Entry<EquipmentSlot, List<EnumPlayerPart>> entry : partList.entrySet()) {
         EquipmentSlot slot = entry.getKey();
         List<EnumPlayerPart> parts = entry.getValue();

         for (EnumPlayerPart part : parts) {
            if (part.slot != slot) {
               throw new RuntimeException(part + " is not a member of " + slot);
            }
         }

         this.builtList.add(Pair.of(slot, parts.toArray(new EnumPlayerPart[0])));
      }

      this.shuffle = shuffle;
      this.doNeighbours = doNeighbours;
      this.blockedParts = EnumSet.noneOf(EnumPlayerPart.class);
   }

   private StandardDamageDistributionAlgorithm(
      List<Pair<EquipmentSlot, EnumPlayerPart[]>> partList, boolean shuffle, boolean doNeighbours, EnumSet<EnumPlayerPart> blockedParts
   ) {
      this.builtList = partList;
      this.shuffle = shuffle;
      this.doNeighbours = doNeighbours;
      this.blockedParts = blockedParts;
   }

   @Nonnull
   @Override
   protected List<Pair<EquipmentSlot, EnumPlayerPart[]>> getPartList() {
      if (this.shuffle) {
         Collections.shuffle(this.builtList);
      }

      return this.builtList;
   }

   @Override
   public float distributeDamage(float damage, @Nonnull Player player, @Nonnull DamageSource source, boolean addStat) {
      float rest = super.distributeDamage(damage, player, source, addStat);
      EnumSet<EnumPlayerPart> exhaustedParts = this.blockedParts;
      if (rest > 0.0F && this.doNeighbours) {
         EnumSet<EnumPlayerPart> blockedParts = EnumSet.copyOf(this.blockedParts);
         exhaustedParts = blockedParts;

         for (Pair<EquipmentSlot, EnumPlayerPart[]> pair : this.builtList) {
            blockedParts.addAll(Arrays.asList((EnumPlayerPart[])pair.getRight()));
         }

         for (int i = this.builtList.size() - 1; i >= 0; i--) {
            EnumPlayerPart[] parts = (EnumPlayerPart[])this.builtList.get(i).getRight();
            List<EnumPlayerPart> neighbours = collectOverflowTargets(parts, blockedParts);
            if (!neighbours.isEmpty()) {
               float overflowTransfer = getOverflowTransferFactor(parts);
               if (overflowTransfer <= 0.0F) {
                  continue;
               }

               Map<EquipmentSlot, List<EnumPlayerPart>> neighbourMapping = new LinkedHashMap<>();

               for (EnumPlayerPart neighbour : neighbours) {
                  neighbourMapping.computeIfAbsent(neighbour.slot, type -> new ArrayList<>(3)).add(neighbour);
               }

               List<Pair<EquipmentSlot, EnumPlayerPart[]>> neighbourDistributions = new ArrayList<>();

               for (Entry<EquipmentSlot, List<EnumPlayerPart>> entry : neighbourMapping.entrySet()) {
                  neighbourDistributions.add(Pair.of(entry.getKey(), entry.getValue().toArray(new EnumPlayerPart[0])));
               }

               StandardDamageDistributionAlgorithm remainingDistribution = new StandardDamageDistributionAlgorithm(
                  neighbourDistributions, false, true, blockedParts
               );
               rest = remainingDistribution.distributeDamage(rest * overflowTransfer, player, source, addStat);
               if (rest <= 0.0F) {
                  break;
               }

               blockedParts = remainingDistribution.blockedParts;
               exhaustedParts = blockedParts;
            }
         }
      }

      return rest > 0.0F && exhaustedParts.contains(EnumPlayerPart.BODY) && exhaustedParts.contains(EnumPlayerPart.HEAD) ? 0.0F : rest;
   }

   @Override
   public MapCodec<StandardDamageDistributionAlgorithm> codec() {
      return CODEC;
   }

   private static float getOverflowTransferFactor(EnumPlayerPart[] parts) {
      float total = 0.0F;
      int count = 0;

      for (EnumPlayerPart part : parts) {
         float factor = part.getOverflowTransferFactor();
         if (factor > 0.0F) {
            total += factor;
            count++;
         }
      }

      return count == 0 ? 0.0F : total / count;
   }

   private static List<EnumPlayerPart> collectOverflowTargets(EnumPlayerPart[] parts, EnumSet<EnumPlayerPart> blockedParts) {
      List<EnumPlayerPart> neighbours = new ArrayList<>();
      EnumSet<EnumPlayerPart> seenParts = EnumSet.noneOf(EnumPlayerPart.class);

      for (EnumPlayerPart part : parts) {
         for (EnumPlayerPart overflowTarget : part.getOverflowTargets()) {
            if (!blockedParts.contains(overflowTarget) && seenParts.add(overflowTarget)) {
               neighbours.add(overflowTarget);
            }
         }
      }

      return neighbours;
   }
}

package ichttt.mods.firstaid.api.enums;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;

public enum EnumPlayerPart implements StringRepresentable {
   HEAD(EquipmentSlot.HEAD),
   LEFT_ARM(EquipmentSlot.CHEST),
   LEFT_LEG(EquipmentSlot.LEGS),
   LEFT_FOOT(EquipmentSlot.FEET),
   BODY(EquipmentSlot.CHEST),
   RIGHT_ARM(EquipmentSlot.CHEST),
   RIGHT_LEG(EquipmentSlot.LEGS),
   RIGHT_FOOT(EquipmentSlot.FEET);

   public static final EnumPlayerPart[] VALUES = values();
   private ImmutableList<EnumPlayerPart> neighbours;
   private final String serializedName;
   public final EquipmentSlot slot;

   private EnumPlayerPart(EquipmentSlot slot) {
      this.slot = slot;
      this.serializedName = this.name().toLowerCase(Locale.ROOT);
   }

   public ImmutableList<EnumPlayerPart> getNeighbours() {
      if (this.neighbours == null) {
         synchronized (this) {
            if (this.neighbours == null) {
               Builder<EnumPlayerPart> builder = ImmutableList.builder();
               builder.addAll(this.getNeighboursDown());
               builder.addAll(this.getNeighboursUp());
               builder.addAll(this.getNeighboursLeft());
               builder.addAll(this.getNeighboursRight());
               ImmutableList<EnumPlayerPart> builtList = builder.build();
               this.neighbours = builtList;
               return builtList;
            }
         }
      }

      return this.neighbours;
   }

   public List<EnumPlayerPart> getOverflowTargets() {
      return switch (this) {
         case HEAD, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG -> Collections.singletonList(BODY);
         case LEFT_FOOT -> Collections.singletonList(LEFT_LEG);
         case RIGHT_FOOT -> Collections.singletonList(RIGHT_LEG);
         case BODY -> Arrays.asList(LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, LEFT_FOOT, RIGHT_FOOT, HEAD);
      };
   }

   public float getOverflowTransferFactor() {
      return switch (this) {
         case LEFT_FOOT, RIGHT_FOOT -> 0.8F;
         case LEFT_LEG, RIGHT_LEG -> 0.65F;
         case LEFT_ARM, RIGHT_ARM -> 0.55F;
         case HEAD -> 0.35F;
         case BODY -> 0.45F;
      };
   }

   @Nonnull
   private List<EnumPlayerPart> getNeighboursUp() {
      switch (this) {
         case LEFT_LEG:
         case RIGHT_LEG:
            return Collections.singletonList(BODY);
         case LEFT_FOOT:
            return Collections.singletonList(LEFT_LEG);
         case BODY:
            return Collections.singletonList(HEAD);
         case RIGHT_ARM:
         default:
            return Collections.emptyList();
         case RIGHT_FOOT:
            return Collections.singletonList(RIGHT_LEG);
      }
   }

   @Nonnull
   private List<EnumPlayerPart> getNeighboursDown() {
      switch (this) {
         case HEAD:
            return Collections.singletonList(BODY);
         case LEFT_ARM:
         case LEFT_FOOT:
         case RIGHT_ARM:
         default:
            return Collections.emptyList();
         case LEFT_LEG:
            return Collections.singletonList(LEFT_FOOT);
         case BODY:
            return Arrays.asList(LEFT_LEG, RIGHT_LEG);
         case RIGHT_LEG:
            return Collections.singletonList(RIGHT_FOOT);
      }
   }

   @Nonnull
   private List<EnumPlayerPart> getNeighboursLeft() {
      switch (this) {
         case BODY:
            return Collections.singletonList(LEFT_ARM);
         case RIGHT_ARM:
            return Collections.singletonList(BODY);
         case RIGHT_LEG:
            return Collections.singletonList(LEFT_LEG);
         case RIGHT_FOOT:
            return Collections.singletonList(LEFT_FOOT);
         default:
            return Collections.emptyList();
      }
   }

   @Nonnull
   private List<EnumPlayerPart> getNeighboursRight() {
      switch (this) {
         case LEFT_ARM:
            return Collections.singletonList(BODY);
         case LEFT_LEG:
            return Collections.singletonList(RIGHT_LEG);
         case LEFT_FOOT:
            return Collections.singletonList(RIGHT_FOOT);
         case BODY:
            return Collections.singletonList(RIGHT_ARM);
         default:
            return Collections.emptyList();
      }
   }

   public String getSerializedName() {
      return this.serializedName;
   }

   static {
      for (EnumPlayerPart value : VALUES) {
         List<EnumPlayerPart> neighbours = value.getNeighbours();
         if (neighbours.contains(value)) {
            throw new RuntimeException(value + " contains itself as a neighbour!");
         }

         if (neighbours.isEmpty()) {
            throw new RuntimeException(value + " does not have any neighbours!");
         }

         if (EnumSet.copyOf(neighbours).size() != neighbours.size()) {
            throw new RuntimeException(value + " neighbours contain the same part multiple times!");
         }

         Set<EnumPlayerPart> hopefullyAllParts = EnumSet.copyOf(neighbours);
         int oldSize = -1;

         while (oldSize != hopefullyAllParts.size()) {
            oldSize = hopefullyAllParts.size();
            Set<EnumPlayerPart> neighboursOfNeighbours = EnumSet.noneOf(EnumPlayerPart.class);

            for (EnumPlayerPart part : hopefullyAllParts) {
               neighboursOfNeighbours.addAll(part.getNeighbours());
            }

            hopefullyAllParts.addAll(neighboursOfNeighbours);
         }

         if (hopefullyAllParts.size() != VALUES.length) {
            throw new RuntimeException(value + " could not read all player parts " + Arrays.toString(hopefullyAllParts.toArray(new EnumPlayerPart[0])));
         }
      }
   }
}

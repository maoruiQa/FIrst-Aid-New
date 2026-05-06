package ichttt.mods.firstaid.api.damagesystem;

import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.util.INBTSerializable;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public abstract class AbstractPlayerDamageModel implements Iterable<AbstractDamageablePart>, INBTSerializable<CompoundTag> {
   public final AbstractDamageablePart HEAD;
   public final AbstractDamageablePart LEFT_ARM;
   public final AbstractDamageablePart LEFT_LEG;
   public final AbstractDamageablePart LEFT_FOOT;
   public final AbstractDamageablePart BODY;
   public final AbstractDamageablePart RIGHT_ARM;
   public final AbstractDamageablePart RIGHT_LEG;
   public final AbstractDamageablePart RIGHT_FOOT;
   public boolean hasTutorial;

   public AbstractPlayerDamageModel(
      AbstractDamageablePart head,
      AbstractDamageablePart leftArm,
      AbstractDamageablePart leftLeg,
      AbstractDamageablePart leftFoot,
      AbstractDamageablePart body,
      AbstractDamageablePart rightArm,
      AbstractDamageablePart rightLeg,
      AbstractDamageablePart rightFoot
   ) {
      this.HEAD = head;
      this.LEFT_ARM = leftArm;
      this.LEFT_LEG = leftLeg;
      this.LEFT_FOOT = leftFoot;
      this.BODY = body;
      this.RIGHT_ARM = rightArm;
      this.RIGHT_LEG = rightLeg;
      this.RIGHT_FOOT = rightFoot;
   }

   public AbstractDamageablePart getFromEnum(EnumPlayerPart part) {
      switch (part) {
         case HEAD:
            return this.HEAD;
         case LEFT_ARM:
            return this.LEFT_ARM;
         case LEFT_LEG:
            return this.LEFT_LEG;
         case BODY:
            return this.BODY;
         case RIGHT_ARM:
            return this.RIGHT_ARM;
         case RIGHT_LEG:
            return this.RIGHT_LEG;
         case LEFT_FOOT:
            return this.LEFT_FOOT;
         case RIGHT_FOOT:
            return this.RIGHT_FOOT;
         default:
            throw new RuntimeException("Unknown enum " + part);
      }
   }

   public abstract void tick(Level var1, Player var2);

   @Deprecated
   public abstract void applyMorphine();

   public abstract void applyMorphine(Player var1);

   @Deprecated
   public abstract int getMorphineTicks();

   public abstract int getPainLevel();

   public abstract int getAdrenalineLevel();

   public abstract int getAdrenalineTicks();

   public abstract int getUnconsciousTicks();

   public abstract boolean isCriticalConditionActive();

   public abstract boolean isDead(@Nullable Player var1);

   public abstract Float getAbsorption();

   public abstract void setAbsorption(float var1);

   public abstract int getCurrentMaxHealth();

   public abstract int getMaxRenderSize();

   public abstract void sleepHeal(Player var1);

   public abstract void revivePlayer(Player var1);

   public abstract void runScaleLogic(Player var1);

   public abstract void scheduleResync();

   public abstract boolean hasNoCritical();
}

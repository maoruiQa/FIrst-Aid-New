package ichttt.mods.firstaid.api.event;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

public final class FirstAidLivingDamageEvent {
   public static final Event<FirstAidLivingDamageEvent.Callback> EVENT = EventFactory.createArrayBacked(
      FirstAidLivingDamageEvent.Callback.class, callbacks -> event -> {
         for (FirstAidLivingDamageEvent.Callback callback : callbacks) {
            callback.onDamage(event);
         }
      }
   );
   private final Player player;
   private final AbstractPlayerDamageModel afterDamageDone;
   private final AbstractPlayerDamageModel beforeDamageDone;
   private final DamageSource source;
   private final float undistributedDamage;
   private boolean canceled;

   public FirstAidLivingDamageEvent(
      Player player, AbstractPlayerDamageModel afterDamageDone, AbstractPlayerDamageModel beforeDamageDone, DamageSource source, float undistributedDamage
   ) {
      this.player = player;
      this.afterDamageDone = afterDamageDone;
      this.beforeDamageDone = beforeDamageDone;
      this.source = source;
      this.undistributedDamage = undistributedDamage;
   }

   public Player getPlayer() {
      return this.player;
   }

   public AbstractPlayerDamageModel getAfterDamage() {
      return this.afterDamageDone;
   }

   public AbstractPlayerDamageModel getBeforeDamage() {
      return this.beforeDamageDone;
   }

   public DamageSource getSource() {
      return this.source;
   }

   public float getUndistributedDamage() {
      return this.undistributedDamage;
   }

   public boolean isCanceled() {
      return this.canceled;
   }

   public void setCanceled(boolean canceled) {
      this.canceled = canceled;
   }

   public interface Callback {
      void onDamage(FirstAidLivingDamageEvent var1);
   }
}

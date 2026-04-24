package ichttt.mods.firstaid.common.apiimpl;

import ichttt.mods.firstaid.api.healing.HealingItemApiHelper;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.common.ClientAccess;
import javax.annotation.Nonnull;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class HealingItemApiHelperImpl extends HealingItemApiHelper {
   private static final HealingItemApiHelperImpl INSTANCE = new HealingItemApiHelperImpl();

   public static void init() {
      HealingItemApiHelper.setImpl(INSTANCE);
   }

   @Nonnull
   @Override
   public InteractionResult onItemRightClick(ItemHealing itemHealing, Level world, Player player, InteractionHand hand) {
      if (world.isClientSide()) {
         boolean consumed = ClientAccess.beginApplyHealthUse(hand) || ClientAccess.showApplyHealth(hand);
         return consumed ? InteractionResult.SUCCESS : InteractionResult.FAIL;
      }

      return InteractionResult.SUCCESS;
   }
}

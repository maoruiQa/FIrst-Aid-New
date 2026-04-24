package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.medicine.ItemMedicine;
import ichttt.mods.firstaid.api.medicine.MedicineStatusContext;
import ichttt.mods.firstaid.api.medicine.MedicineStatusDisplay;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.common.util.CommonUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

public final class MedicineStatusClientHelper {
   private static int cachedItemCount = -1;
   private static List<ItemMedicine> cachedMedicines = List.of();

   private MedicineStatusClientHelper() {
   }

   public static List<MedicineStatusDisplay> collect(Player player) {
      AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
      MedicineStatusContext context = new MedicineStatusContext(player, player.level(), damageModel);
      Map<Object, MedicineStatusDisplay> displays = new LinkedHashMap<>();

      for (ItemMedicine medicine : getMedicines()) {
         MedicineStatusDisplay display = medicine.getActiveStatus(context);
         if (display != null) {
            displays.putIfAbsent(display.getStatusId(), display);
         }
      }

      return new ArrayList<>(displays.values());
   }

   public static int drawStatusLine(GuiGraphicsExtractor guiGraphics, Font font, MedicineStatusDisplay display, int x, int y) {
      int textX = x;
      if (display.getIconTexture() != null) {
         HealthRenderUtils.blit(guiGraphics, display.getIconTexture(), 8, 8, x, y, 0, 0, 8, 8);
         textX += 10;
      }

      guiGraphics.text(font, display.getText(), textX, y + 1, display.getColor());
      return y + 10;
   }

   private static List<ItemMedicine> getMedicines() {
      int itemCount = BuiltInRegistries.ITEM.size();
      if (itemCount != cachedItemCount) {
         List<ItemMedicine> medicines = new ArrayList<>();

         for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof ItemMedicine medicine) {
               medicines.add(medicine);
            }
         }

         cachedMedicines = List.copyOf(medicines);
         cachedItemCount = itemCount;
      }

      return cachedMedicines;
   }
}

package ichttt.mods.firstaid.client.gui;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.util.HeartSpriteHelper;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class FirstaidIngameGui {
   private static int lastHealth = -1;
   private static int blinkUntilTick;
   private static Player lastPlayer;
   private static Level lastLevel;
   private static final RandomSource RANDOM = RandomSource.create();

   private FirstaidIngameGui() {
   }

   public static void renderHealth(int width, int height, GuiGraphicsExtractor guiGraphics) {
      Minecraft minecraft = Minecraft.getInstance();
      Player player = minecraft.player;
      if (player == null) {
         return;
      }

      AbstractPlayerDamageModel damageModel = CommonUtils.getOptionalDamageModel(minecraft.player).orElse(null);
      int criticalHalfHearts = 0;
      if (damageModel != null) {
         float criticalHealth = Float.MAX_VALUE;

         for (AbstractDamageablePart part : damageModel) {
            if (part.canCauseDeath) {
               criticalHealth = Math.min(criticalHealth, part.currentHealth);
            }
         }

         criticalHealth = criticalHealth / damageModel.getCurrentMaxHealth() * minecraft.player.getMaxHealth();
         criticalHalfHearts = Mth.ceil(criticalHealth);
      }

      int health = Mth.ceil(getModelDisplayHealth(player, damageModel));
      boolean healthBlink = updateHealthBlink(player, health);
      AttributeInstance attrMaxHealth = player.getAttribute(Attributes.MAX_HEALTH);
      float healthMax = Math.max((float)attrMaxHealth.getValue(), (float)health);
      int absorption = Mth.ceil(player.getAbsorptionAmount());
      int healthRows = Mth.ceil((healthMax + absorption) / 2.0F / 10.0F);
      int rowHeight = Math.max(10 - (healthRows - 2), 3);
      int left = width / 2 - 91;
      int top = height - 39;
      int regen = player.hasEffect(MobEffects.REGENERATION) ? player.tickCount % Mth.ceil(healthMax + 5.0F) : -1;
      RANDOM.setSeed((long)player.tickCount * 312871L);
      float absorptionRemaining = absorption;

      for (int i = Mth.ceil((healthMax + absorption) / 2.0F) - 1; i >= 0; --i) {
         boolean criticalHalf = i * 2 + 1 == criticalHalfHearts;
         boolean criticalBlink = i * 2 < criticalHalfHearts && !criticalHalf;
         boolean spriteBlink = criticalBlink || healthBlink;
         int row = Mth.ceil((float)(i + 1) / 10.0F) - 1;
         int x = left + i % 10 * 8;
         int y = top - row * rowHeight;
         if (health <= 4) {
            y += RANDOM.nextInt(2);
         }
         if (i == regen) {
            y -= 2;
         }

         guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HeartSpriteHelper.container(player, spriteBlink), x, y, 9, 9);
         if (absorptionRemaining > 0.0F) {
            boolean halfAbsorption = absorptionRemaining == absorption && absorption % 2 == 1;
            guiGraphics.blitSprite(
               RenderPipelines.GUI_TEXTURED,
               halfAbsorption
                  ? HeartSpriteHelper.heart(player, true, true, spriteBlink)
                  : HeartSpriteHelper.heart(player, true, false, spriteBlink),
               x,
               y,
               9,
               9
            );
            absorptionRemaining -= absorptionRemaining == absorption && absorption % 2 == 1 ? 1.0F : 2.0F;
            continue;
         }

         if (criticalHalf) {
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HeartSpriteHelper.heart(player, false, true, true), x, y, 9, 9);
         }

         if (i * 2 + 1 < health) {
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HeartSpriteHelper.heart(player, false, false, spriteBlink), x, y, 9, 9);
         } else if (i * 2 + 1 == health && !criticalHalf) {
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HeartSpriteHelper.heart(player, false, true, spriteBlink), x, y, 9, 9);
         }
      }
   }

   private static boolean updateHealthBlink(Player player, int health) {
      int tick = player.tickCount;
      if (lastPlayer != player || lastLevel != player.level() || lastHealth < 0 || !player.isAlive()) {
         lastPlayer = player;
         lastLevel = player.level();
         lastHealth = health;
         blinkUntilTick = 0;
         return false;
      }
      if (health < lastHealth) {
         blinkUntilTick = tick + 20;
      } else if (health > lastHealth) {
         blinkUntilTick = tick + 10;
      }
      lastHealth = health;
      return blinkUntilTick > tick && (blinkUntilTick - tick) / 3 % 2 == 1;
   }

   private static float getModelDisplayHealth(Player player, AbstractPlayerDamageModel damageModel) {
      if (damageModel == null) {
         return player.getHealth();
      }

      float currentHealth = 0.0F;
      FirstAidConfig.Server.VanillaHealthCalculationMode mode = (FirstAidConfig.Server.VanillaHealthCalculationMode)FirstAidConfig.SERVER.vanillaHealthCalculation.get();
      if (damageModel.hasNoCritical()) {
         mode = FirstAidConfig.Server.VanillaHealthCalculationMode.AVERAGE_ALL;
      }

      float ratio = switch (mode) {
         case AVERAGE_CRITICAL -> {
            int maxHealth = 0;

            for (AbstractDamageablePart part : damageModel) {
               if (part.canCauseDeath) {
                  currentHealth += part.currentHealth;
                  maxHealth += part.getMaxHealth();
               }
            }

            yield maxHealth <= 0 ? 0.0F : currentHealth / maxHealth;
         }
         case MIN_CRITICAL -> {
            AbstractDamageablePart minimal = null;
            float lowest = Float.MAX_VALUE;

            for (AbstractDamageablePart part : damageModel) {
               if (part.canCauseDeath && part.currentHealth < lowest) {
                  minimal = part;
                  lowest = part.currentHealth;
               }
            }

            yield minimal == null || minimal.getMaxHealth() <= 0 ? 0.0F : minimal.currentHealth / minimal.getMaxHealth();
         }
         case AVERAGE_ALL -> {
            for (AbstractDamageablePart part : damageModel) {
               currentHealth += part.currentHealth;
            }

            int maxHealth = damageModel.getCurrentMaxHealth();
            yield maxHealth <= 0 ? 0.0F : currentHealth / maxHealth;
         }
         case CRITICAL_50_PERCENT_OTHER_50_PERCENT -> {
            float currentNormal = 0.0F;
            int maxNormal = 0;
            float currentCritical = 0.0F;
            int maxCritical = 0;

            for (AbstractDamageablePart part : damageModel) {
               if (part.canCauseDeath) {
                  currentCritical += part.currentHealth;
                  maxCritical += part.getMaxHealth();
               } else {
                  currentNormal += part.currentHealth;
                  maxNormal += part.getMaxHealth();
               }
            }

            float avgNormal = maxNormal <= 0 ? 0.0F : currentNormal / maxNormal;
            float avgCritical = maxCritical <= 0 ? 0.0F : currentCritical / maxCritical;
            yield (avgCritical + avgNormal) / 2.0F;
         }
      };

      float displayHealth = ratio * player.getMaxHealth();
      return displayHealth <= 0.0F && player.isAlive() && !damageModel.isDead(player) ? 1.0F : displayHealth;
   }

}

package ichttt.mods.firstaid.client.gui;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public final class FirstaidIngameGui {
   private static final Identifier HEART_CONTAINER_SPRITE = Identifier.withDefaultNamespace("hud/heart/container");
   private static final Identifier HEART_CONTAINER_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/container_blinking");
   private static final Identifier HEART_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/full");
   private static final Identifier HEART_FULL_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/full_blinking");
   private static final Identifier HEART_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/half");
   private static final Identifier HEART_HALF_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/half_blinking");
   private static final Identifier HEART_POISONED_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/poisoned_full");
   private static final Identifier HEART_POISONED_FULL_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/poisoned_full_blinking");
   private static final Identifier HEART_POISONED_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/poisoned_half");
   private static final Identifier HEART_POISONED_HALF_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/poisoned_half_blinking");
   private static final Identifier HEART_WITHERED_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/withered_full");
   private static final Identifier HEART_WITHERED_FULL_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/withered_full_blinking");
   private static final Identifier HEART_WITHERED_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/withered_half");
   private static final Identifier HEART_WITHERED_HALF_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/withered_half_blinking");
   private static final Identifier HEART_ABSORBING_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/absorbing_full");
   private static final Identifier HEART_ABSORBING_FULL_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/absorbing_full_blinking");
   private static final Identifier HEART_ABSORBING_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/absorbing_half");
   private static final Identifier HEART_ABSORBING_HALF_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/absorbing_half_blinking");

   private FirstaidIngameGui() {
   }

   public static void renderHealth(Gui gui, int width, int height, GuiGraphics guiGraphics) {
      Minecraft minecraft = Minecraft.getInstance();
      Player player = minecraft.player;
      if (player != null) {
         AbstractPlayerDamageModel damageModel = (AbstractPlayerDamageModel)CommonUtils.getOptionalDamageModel(minecraft.player).orElse(null);
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
         AttributeInstance attrMaxHealth = player.getAttribute(Attributes.MAX_HEALTH);
         float healthMax = Math.max((float)attrMaxHealth.getValue(), (float)health);
         int absorption = Mth.ceil(player.getAbsorptionAmount());
         int healthRows = Mth.ceil((healthMax + absorption) / 2.0F / 10.0F);
         int rowHeight = Math.max(10 - (healthRows - 2), 3);
         int left = width / 2 - 91;
         int top = height - 39;
         boolean poisoned = player.hasEffect(MobEffects.POISON);
         boolean withered = !poisoned && player.hasEffect(MobEffects.WITHER);
         float absorptionRemaining = absorption;

         for (int i = Mth.ceil((healthMax + absorption) / 2.0F) - 1; i >= 0; i--) {
            boolean criticalHalf = i * 2 + 1 == criticalHalfHearts;
            boolean criticalBlink = i * 2 < criticalHalfHearts && !criticalHalf;
            int row = Mth.ceil((i + 1) / 10.0F) - 1;
            int x = left + i % 10 * 8;
            int y = top - row * rowHeight;
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, criticalBlink ? HEART_CONTAINER_BLINKING_SPRITE : HEART_CONTAINER_SPRITE, x, y, 9, 9);
            if (absorptionRemaining > 0.0F) {
               boolean halfAbsorption = absorptionRemaining == absorption && absorption % 2 == 1;
               guiGraphics.blitSprite(
                  RenderPipelines.GUI_TEXTURED,
                  halfAbsorption
                     ? (criticalBlink ? HEART_ABSORBING_HALF_BLINKING_SPRITE : HEART_ABSORBING_HALF_SPRITE)
                     : (criticalBlink ? HEART_ABSORBING_FULL_BLINKING_SPRITE : HEART_ABSORBING_FULL_SPRITE),
                  x,
                  y,
                  9,
                  9
               );
               absorptionRemaining -= absorptionRemaining == absorption && absorption % 2 == 1 ? 1.0F : 2.0F;
            } else {
               if (criticalHalf) {
                  guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, getHeartSprite(poisoned, withered, true, true), x, y, 9, 9);
               }

               if (i * 2 + 1 < health) {
                  guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, getHeartSprite(poisoned, withered, false, criticalBlink), x, y, 9, 9);
               } else if (i * 2 + 1 == health && !criticalHalf) {
                  guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, getHeartSprite(poisoned, withered, true, criticalBlink), x, y, 9, 9);
               }
            }
         }
      }
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

   private static Identifier getHeartSprite(boolean poisoned, boolean withered, boolean halfHeart, boolean blinking) {
      if (poisoned) {
         return halfHeart
            ? (blinking ? HEART_POISONED_HALF_BLINKING_SPRITE : HEART_POISONED_HALF_SPRITE)
            : (blinking ? HEART_POISONED_FULL_BLINKING_SPRITE : HEART_POISONED_FULL_SPRITE);
      } else if (withered) {
         return halfHeart
            ? (blinking ? HEART_WITHERED_HALF_BLINKING_SPRITE : HEART_WITHERED_HALF_SPRITE)
            : (blinking ? HEART_WITHERED_FULL_BLINKING_SPRITE : HEART_WITHERED_FULL_SPRITE);
      } else {
         return halfHeart ? (blinking ? HEART_HALF_BLINKING_SPRITE : HEART_HALF_SPRITE) : (blinking ? HEART_FULL_BLINKING_SPRITE : HEART_FULL_SPRITE);
      }
   }
}

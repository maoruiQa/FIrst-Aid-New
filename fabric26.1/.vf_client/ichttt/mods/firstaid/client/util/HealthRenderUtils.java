package ichttt.mods.firstaid.client.util;

import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.client.gui.FlashStateManager;
import ichttt.mods.firstaid.common.EventHandler;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.text.DecimalFormat;
import java.util.EnumMap;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffects;
import org.joml.Matrix3x2fStack;

public final class HealthRenderUtils {
   public static final Identifier SHOW_WOUNDS_LOCATION = Identifier.fromNamespaceAndPath("firstaid", "textures/gui/show_wounds.png");
   public static final DecimalFormat TEXT_FORMAT = new DecimalFormat("0.0");
   private static final Identifier HEART_CONTAINER_SPRITE = Identifier.withDefaultNamespace("hud/heart/container");
   private static final Identifier HEART_CONTAINER_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/container_blinking");
   private static final Identifier HEART_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/full");
   private static final Identifier HEART_FULL_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/full_blinking");
   private static final Identifier HEART_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/half");
   private static final Identifier HEART_HALF_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/half_blinking");
   private static final Identifier HEART_ABSORBING_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/absorbing_full");
   private static final Identifier HEART_ABSORBING_FULL_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/absorbing_full_blinking");
   private static final Identifier HEART_ABSORBING_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/absorbing_half");
   private static final Identifier HEART_ABSORBING_HALF_BLINKING_SPRITE = Identifier.withDefaultNamespace("hud/heart/absorbing_half_blinking");
   private static final Object2IntOpenHashMap<EnumPlayerPart> PREV_HEALTH = new Object2IntOpenHashMap();
   private static final EnumMap<EnumPlayerPart, FlashStateManager> FLASH_STATES = new EnumMap<>(EnumPlayerPart.class);

   private HealthRenderUtils() {
   }

   public static void blit(GuiGraphics guiGraphics, Identifier texture, int textureWidth, int textureHeight, int x, int y, int u, int v, int width, int height) {
      guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
   }

   public static void blit(
      GuiGraphics guiGraphics, Identifier texture, int textureWidth, int textureHeight, int x, int y, int u, int v, int width, int height, int color
   ) {
      guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight, color);
   }

   public static void drawHealthString(
      GuiGraphics guiGraphics, Font font, AbstractDamageablePart damageablePart, int xTranslation, int yTranslation, boolean allowSecondLine
   ) {
      float absorption = damageablePart.getAbsorption();
      String text = TEXT_FORMAT.format(damageablePart.currentHealth) + "/" + damageablePart.getMaxHealth();
      if (absorption > 0.0F) {
         String line2 = "+ " + TEXT_FORMAT.format(absorption);
         if (allowSecondLine) {
            guiGraphics.drawString(font, line2, xTranslation, yTranslation + 5, 16777215);
            yTranslation -= 5;
         } else {
            text = text + " " + line2;
         }
      }

      guiGraphics.drawString(font, text, xTranslation, yTranslation, 16777215);
   }

   public static boolean healthChanged(AbstractDamageablePart damageablePart, boolean playerDead) {
      int current = (int)Math.ceil(damageablePart.currentHealth);
      FlashStateManager activeFlashState = Objects.requireNonNull(FLASH_STATES.get(damageablePart.part));
      if (PREV_HEALTH.containsKey(damageablePart.part)) {
         int prev = PREV_HEALTH.getInt(damageablePart.part);
         updatePrev(damageablePart.part, current, playerDead);
         if (prev != current) {
            activeFlashState.setActive(Util.getMillis());
            return true;
         } else {
            return false;
         }
      } else {
         activeFlashState.setActive(Util.getMillis());
         updatePrev(damageablePart.part, current, playerDead);
         return true;
      }
   }

   private static void updatePrev(EnumPlayerPart part, int current, boolean playerDead) {
      if (playerDead) {
         PREV_HEALTH.clear();
      } else {
         PREV_HEALTH.put(part, current);
      }
   }

   public static boolean drawAsString(AbstractDamageablePart damageablePart, boolean allowSecondLine) {
      int maxHealth = getMaxHearts(damageablePart.getMaxHealth());
      int maxExtraHealth = getMaxHearts(damageablePart.getAbsorption());
      return maxHealth + maxExtraHealth > 8 && allowSecondLine || maxHealth + maxExtraHealth > 12;
   }

   public static void drawHealth(
      GuiGraphics guiGraphics, Font font, AbstractDamageablePart damageablePart, int xTranslation, int yTranslation, boolean allowSecondLine
   ) {
      int maxHealth = getMaxHearts(damageablePart.getMaxHealth());
      int maxExtraHealth = getMaxHearts(damageablePart.getAbsorption());
      int current = (int)Math.ceil(damageablePart.currentHealth);
      int absorption = (int)Math.ceil(damageablePart.getAbsorption());
      if (drawAsString(damageablePart, allowSecondLine)) {
         drawHealthString(guiGraphics, font, damageablePart, xTranslation, yTranslation, allowSecondLine);
      } else {
         FlashStateManager activeFlashState = Objects.requireNonNull(FLASH_STATES.get(damageablePart.part));
         boolean highlight = activeFlashState.update(Util.getMillis());
         boolean low = current + absorption < 1.25F;
         Minecraft minecraft = Minecraft.getInstance();
         int regen = -1;
         if (minecraft.player != null && (Boolean)FirstAidConfig.SERVER.allowOtherHealingItems.get() && minecraft.player.hasEffect(MobEffects.REGENERATION)) {
            regen = minecraft.gui.getGuiTicks() / 2 % 15;
         }

         Matrix3x2fStack stack = guiGraphics.pose();
         stack.pushMatrix();
         stack.translate(xTranslation, yTranslation);
         boolean drawSecondLine = allowSecondLine && maxHealth + maxExtraHealth > 4;
         if (drawSecondLine) {
            int maxHealth2 = Math.max(0, maxHealth - 4);
            int maxExtraHealth2 = Math.max(0, maxExtraHealth - (4 - Math.min(4, maxHealth)));
            int current2 = Math.max(0, current - 8);
            int absorption2 = Math.max(0, absorption - maxExtraHealth * 2);
            maxHealth = Math.min(4, maxHealth);
            maxExtraHealth -= maxExtraHealth2;
            current = Math.min(8, current);
            absorption -= absorption2;
            stack.pushMatrix();
            stack.translate(0.0F, 5.0F);
            renderLine(stack, regen, low, maxHealth2, maxExtraHealth2, current2, absorption2, guiGraphics, highlight);
            stack.popMatrix();
         }

         renderLine(stack, regen, low, maxHealth, maxExtraHealth, current, absorption, guiGraphics, highlight);
         stack.popMatrix();
      }
   }

   private static void renderLine(
      Matrix3x2fStack stack, int regen, boolean low, int maxHealth, int maxExtraHearts, int current, int absorption, GuiGraphics guiGraphics, boolean highlight
   ) {
      int[] lowOffsets = new int[maxHealth + maxExtraHearts];
      if (low) {
         for (int i = 0; i < lowOffsets.length; i++) {
            lowOffsets[i] = EventHandler.RAND.nextInt(2);
         }
      }

      stack.pushMatrix();
      renderMax(regen, lowOffsets, maxHealth, guiGraphics, highlight);
      if (maxExtraHearts > 0) {
         if (maxHealth != 0) {
            stack.translate(2 + 9 * maxHealth, 0.0F);
         }

         renderMax(regen - maxHealth, lowOffsets, maxExtraHearts, guiGraphics, false);
      }

      stack.popMatrix();
      renderCurrentHealth(regen, lowOffsets, current, guiGraphics, highlight);
      if (absorption > 0) {
         stack.pushMatrix();
         stack.translate(maxHealth * 9 + (maxHealth == 0 ? 0 : 2), 0.0F);
         renderAbsorption(regen - maxHealth, lowOffsets, absorption, guiGraphics, highlight);
         stack.popMatrix();
      }
   }

   public static int getMaxHearts(float value) {
      int maxCurrentHearts = Mth.ceil(value);
      if (maxCurrentHearts % 2 != 0) {
         maxCurrentHearts++;
      }

      return maxCurrentHearts >> 1;
   }

   private static void renderMax(int regen, int[] lowOffsets, int max, GuiGraphics guiGraphics, boolean highlight) {
      renderHeartSprites(
         regen,
         lowOffsets,
         max,
         false,
         guiGraphics,
         highlight ? HEART_CONTAINER_BLINKING_SPRITE : HEART_CONTAINER_SPRITE,
         highlight ? HEART_CONTAINER_BLINKING_SPRITE : HEART_CONTAINER_SPRITE
      );
   }

   private static void renderCurrentHealth(int regen, int[] lowOffsets, int current, GuiGraphics guiGraphics, boolean highlight) {
      boolean renderLastHalf = current % 2 != 0;
      int render = current / 2 + (renderLastHalf ? 1 : 0);
      renderHeartSprites(
         regen,
         lowOffsets,
         render,
         renderLastHalf,
         guiGraphics,
         highlight ? HEART_HALF_BLINKING_SPRITE : HEART_HALF_SPRITE,
         highlight ? HEART_FULL_BLINKING_SPRITE : HEART_FULL_SPRITE
      );
   }

   private static void renderAbsorption(int regen, int[] lowOffsets, int absorption, GuiGraphics guiGraphics, boolean highlight) {
      boolean renderLastHalf = absorption % 2 != 0;
      int render = absorption / 2 + (renderLastHalf ? 1 : 0);
      if (render > 0) {
         renderHeartSprites(
            regen,
            lowOffsets,
            render,
            renderLastHalf,
            guiGraphics,
            highlight ? HEART_ABSORBING_HALF_BLINKING_SPRITE : HEART_ABSORBING_HALF_SPRITE,
            highlight ? HEART_ABSORBING_FULL_BLINKING_SPRITE : HEART_ABSORBING_FULL_SPRITE
         );
      }
   }

   private static void renderHeartSprites(
      int regen, int[] lowOffsets, int toDraw, boolean lastOneHalf, GuiGraphics guiGraphics, Identifier halfSprite, Identifier fullSprite
   ) {
      if (toDraw > 0) {
         for (int i = 0; i < toDraw; i++) {
            boolean renderHalf = lastOneHalf && i + 1 == toDraw;
            guiGraphics.blitSprite(
               RenderPipelines.GUI_TEXTURED, renderHalf ? halfSprite : fullSprite, (int)(9.0F * i), (i == regen ? -2 : 0) - lowOffsets[i], 9, 9
            );
         }
      }
   }

   static {
      for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
         FLASH_STATES.put(part, new FlashStateManager());
      }
   }
}

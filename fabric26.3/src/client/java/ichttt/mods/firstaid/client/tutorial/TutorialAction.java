package ichttt.mods.firstaid.client.tutorial;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;

public class TutorialAction {
   private final List<Object> queue = new ArrayList<>();
   private final GuiTutorial guiContext;
   private int pos = 0;
   private TextWrapper activeWrapper;
   private String s1;
   private String s2;

   public TutorialAction(GuiTutorial guiContext) {
      this.guiContext = guiContext;
   }

   public void draw(GuiGraphicsExtractor guiGraphics) {
      if (this.s2 != null) {
         this.guiContext.drawOffsetString(guiGraphics, this.s1, 4);
         this.guiContext.drawOffsetString(guiGraphics, this.s2, 16);
      } else if (this.s1 != null) {
         this.guiContext.drawOffsetString(guiGraphics, this.s1, 10);
      }
   }

   public void next() {
      if (this.activeWrapper != null) {
         this.writeFromActiveWrapper();
      } else {
         Object obj = this.queue.get(this.pos);
         if (obj instanceof TextWrapper) {
            this.activeWrapper = (TextWrapper)obj;
            this.writeFromActiveWrapper();
            this.pos++;
         } else {
            if (!(obj instanceof Consumer<?> consumer)) {
               throw new RuntimeException("Found invalid object " + obj.toString());
            }

            @SuppressWarnings("unchecked")
            Consumer<GuiTutorial> tutorialConsumer = (Consumer<GuiTutorial>)consumer;
            tutorialConsumer.accept(this.guiContext);
            this.pos++;
            if (this.hasNext()) {
               this.next();
            }
         }
      }
   }

   public boolean hasNext() {
      return this.pos < this.queue.size() || this.activeWrapper != null;
   }

   private void writeFromActiveWrapper() {
      this.s1 = this.activeWrapper.nextLine();
      if (this.activeWrapper.getRemainingLines() >= 1) {
         this.s2 = this.activeWrapper.nextLine();
      } else {
         this.s2 = null;
      }

      if (this.activeWrapper.getRemainingLines() < 1) {
         this.activeWrapper = null;
      }
   }

   public void addTextWrapper(String i18nKey, String... format) {
      this.queue.add(new TextWrapper(I18n.get(i18nKey, (Object[])format)));
   }

   public void addActionCallable(Consumer<GuiTutorial> callable) {
      this.queue.add(callable);
   }
}

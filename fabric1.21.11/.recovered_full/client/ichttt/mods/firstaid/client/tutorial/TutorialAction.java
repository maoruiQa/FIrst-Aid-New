/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.resources.language.I18n
 */
package ichttt.mods.firstaid.client.tutorial;

import ichttt.mods.firstaid.client.tutorial.GuiTutorial;
import ichttt.mods.firstaid.client.tutorial.TextWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

public class TutorialAction {
    private final List<Object> queue = new ArrayList<Object>();
    private final GuiTutorial guiContext;
    private int pos = 0;
    private TextWrapper activeWrapper;
    private String s1;
    private String s2;

    public TutorialAction(GuiTutorial guiContext) {
        this.guiContext = guiContext;
    }

    public void draw(GuiGraphics guiGraphics) {
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
            return;
        }
        Object obj = this.queue.get(this.pos);
        if (obj instanceof TextWrapper) {
            this.activeWrapper = (TextWrapper)obj;
            this.writeFromActiveWrapper();
            ++this.pos;
        } else if (obj instanceof Consumer) {
            Consumer consumer = (Consumer)obj;
            consumer.accept(this.guiContext);
            ++this.pos;
            if (this.hasNext()) {
                this.next();
            }
        } else {
            throw new RuntimeException("Found invalid object " + obj.toString());
        }
    }

    public boolean hasNext() {
        return this.pos < this.queue.size() || this.activeWrapper != null;
    }

    private void writeFromActiveWrapper() {
        this.s1 = this.activeWrapper.nextLine();
        this.s2 = this.activeWrapper.getRemainingLines() >= 1 ? this.activeWrapper.nextLine() : null;
        if (this.activeWrapper.getRemainingLines() < 1) {
            this.activeWrapper = null;
        }
    }

    public void addTextWrapper(String i18nKey, String ... format) {
        this.queue.add(new TextWrapper(I18n.get((String)i18nKey, (Object[])format)));
    }

    public void addActionCallable(Consumer<GuiTutorial> callable) {
        this.queue.add(callable);
    }
}


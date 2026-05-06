/*
 * Decompiled with CFR 0.152.
 */
package ichttt.mods.firstaid.client.tutorial;

import java.util.ArrayList;
import java.util.List;

public class TextWrapper {
    private static final int maxChars = 35;
    private static final int minimumChars = 29;
    private int currentLine = 0;
    private final List<String> lines = new ArrayList<String>();

    public TextWrapper(String text) {
        char[] chars = text.toCharArray();
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (char c : chars) {
            if (count < 29) {
                builder.append(c);
            } else if (c == ' ') {
                this.lines.add(builder.toString());
                builder = new StringBuilder();
                count = 0;
            } else if (count >= 35) {
                builder.append('-');
                this.lines.add(builder.toString());
                builder = new StringBuilder();
                builder.append(c);
                count = 0;
            } else {
                builder.append(c);
            }
            ++count;
        }
        String last = builder.toString();
        if (!last.equals("")) {
            this.lines.add(last);
        }
    }

    public int getRemainingLines() {
        return this.lines.size() - this.currentLine;
    }

    public String nextLine() {
        String s = this.lines.get(this.currentLine);
        ++this.currentLine;
        return s;
    }
}


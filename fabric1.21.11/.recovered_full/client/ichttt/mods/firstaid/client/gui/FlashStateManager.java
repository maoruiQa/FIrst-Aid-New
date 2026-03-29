/*
 * Decompiled with CFR 0.152.
 */
package ichttt.mods.firstaid.client.gui;

public class FlashStateManager {
    private long startTime;
    private int currentState = 0;

    public void setActive(long startTime) {
        this.startTime = startTime;
        this.currentState = 1;
    }

    public boolean update(long worldTime) {
        if (this.isPaused()) {
            return false;
        }
        this.currentState = (int)((worldTime - this.startTime) / 150L) + 1;
        if (this.currentState >= 8) {
            this.currentState = 0;
        }
        if (this.isPaused()) {
            return false;
        }
        return this.currentState % 2 == 0;
    }

    public boolean isPaused() {
        return this.currentState == 0;
    }
}


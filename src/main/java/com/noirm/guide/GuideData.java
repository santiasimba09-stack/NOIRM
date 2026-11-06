package com.noirm.guide;

import net.minecraft.nbt.CompoundTag;

/**
 * A player's place in the new-player guide (part of {@code ShinobiData}): whether they got their
 * Shinobi Handbook, which first step they're on, and whether they turned the tips off. Server-only
 * (nothing here is shown by the client), so it isn't synced.
 */
public final class GuideData {
    private boolean handbookGiven;
    private boolean tipsOff;
    private int step;
    private int reminders;
    private long nextTip;
    private boolean openedHandbook;
    private boolean triedCombat;
    private boolean usedBoard;

    public boolean handbookGiven() {
        return handbookGiven;
    }

    public void setHandbookGiven(boolean value) {
        handbookGiven = value;
    }

    public boolean tipsOff() {
        return tipsOff;
    }

    public void setTipsOff(boolean value) {
        tipsOff = value;
    }

    /** The first step they're on (see {@link FirstSteps.Step}); past the last one, the tips are done. */
    public int step() {
        return step;
    }

    public void setStep(int value) {
        step = value;
        reminders = 0;
    }

    public int reminders() {
        return reminders;
    }

    public void addReminder() {
        reminders++;
    }

    /** Game time of the next tip. */
    public long nextTip() {
        return nextTip;
    }

    public void setNextTip(long time) {
        nextTip = time;
    }

    public boolean openedHandbook() {
        return openedHandbook;
    }

    public void setOpenedHandbook(boolean value) {
        openedHandbook = value;
    }

    public boolean triedCombat() {
        return triedCombat;
    }

    public void setTriedCombat(boolean value) {
        triedCombat = value;
    }

    public boolean usedBoard() {
        return usedBoard;
    }

    public void setUsedBoard(boolean value) {
        usedBoard = value;
    }

    /** Back to a brand-new player (commands). */
    public void reset() {
        handbookGiven = false;
        tipsOff = false;
        step = 0;
        reminders = 0;
        nextTip = 0;
        openedHandbook = false;
        triedCombat = false;
        usedBoard = false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("HandbookGiven", handbookGiven);
        tag.putBoolean("TipsOff", tipsOff);
        tag.putInt("Step", step);
        tag.putInt("Reminders", reminders);
        tag.putBoolean("Opened", openedHandbook);
        tag.putBoolean("Combat", triedCombat);
        tag.putBoolean("Board", usedBoard);
        return tag;
    }

    public void load(CompoundTag tag) {
        handbookGiven = tag.getBoolean("HandbookGiven");
        tipsOff = tag.getBoolean("TipsOff");
        step = tag.getInt("Step");
        reminders = tag.getInt("Reminders");
        openedHandbook = tag.getBoolean("Opened");
        triedCombat = tag.getBoolean("Combat");
        usedBoard = tag.getBoolean("Board");
        nextTip = 0; // game time isn't kept across sessions: the next tip comes soon after joining
    }
}

package com.noirm.ally;

/** A crew mate's personality: how they fight and how quickly they come to respect you. */
public enum Trait {
    /** Fights to the end; respects strength. */
    BRAVE(0.10f, 1.0f),
    /** Backs off early to recover; slow to trust. */
    CAUTIOUS(0.40f, 0.8f),
    /** Sticks with you through anything; trust comes fast and stays. */
    LOYAL(0.25f, 1.4f),
    /** Hard to impress; only real strength and victories count. */
    PROUD(0.20f, 0.6f);

    /** Below this fraction of their health they retreat to recover. */
    public final float retreatAt;
    /** How strongly good (and bad) moments change their respect. */
    public final float respectGain;

    Trait(float retreatAt, float respectGain) {
        this.retreatAt = retreatAt;
        this.respectGain = respectGain;
    }

    public String key() {
        return "screen.noirm.ally.trait." + name().toLowerCase(java.util.Locale.ROOT);
    }
}

package com.noirm.ally;

/** What a crew mate is doing when there's no fight. */
public enum Order {
    /** Stay close to the leader. */
    FOLLOW,
    /** Hold this spot (still defends itself). */
    WAIT,
    /** Spar the nearest training dummy (taijutsu, up to the drill cap). */
    TRAIN,
    /** Sit still, recover and build chakra reserves (up to the drill cap). */
    MEDITATE;

    public String key() {
        return "screen.noirm.ally.order." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Order byIndex(int index) {
        Order[] values = values();
        return index >= 0 && index < values.length ? values[index] : FOLLOW;
    }
}

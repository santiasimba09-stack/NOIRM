package com.noirm.ally;

/**
 * What a crew mate is doing when there's no fight. (New orders go at the end: the screen and
 * saves use the names, but older code may still pass ordinals.)
 */
public enum Order {
    /** Stay close to the leader. */
    FOLLOW,
    /** Hold this spot (still defends itself). */
    WAIT,
    /** Spar the nearest training dummy (taijutsu, up to the drill cap). */
    TRAIN,
    /** Sit still, recover and build chakra reserves (up to the drill cap) and nature energy. */
    MEDITATE,
    /** Hold a post (where you stood when you gave the order) and fight anything hostile near it. */
    GUARD,
    /** Walk the ground around a post, fighting anything hostile that comes near. */
    PATROL;

    /** The orders in the order the screens show them. */
    public static final Order[] SHOWN = {FOLLOW, WAIT, GUARD, PATROL, TRAIN, MEDITATE};

    public String key() {
        return "screen.noirm.ally.order." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Whether this order keeps them around a post rather than with you. */
    public boolean usesPost() {
        return this == GUARD || this == PATROL;
    }
}

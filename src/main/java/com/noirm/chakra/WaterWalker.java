package com.noirm.chakra;

/**
 * An NPC that can walk on water with chakra, like players do (see {@link WaterWalking}).
 * Players don't implement this: their rule reads their chakra.
 */
public interface WaterWalker {
    /** Whether the water surface holds this entity up right now. */
    boolean canWaterWalk();
}

package com.noirm.gore;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** One fighter's gore state on the server: four mark levels, what was last sent, missing parts. */
final class GoreMarks {
    static final int[] ZERO = new int[4];

    final LivingEntity entity;
    /** Bruise, dirt, soot, cut: 0–1 each (see {@link Gore.Mark}). */
    final float[] level = new float[4];
    /** The levels clients last heard, in 1/255 steps. */
    final int[] sent = new int[4];
    /** Severed parts ({@link SeveredLimb.Part} bits); only ever set on a dying body. */
    int severed;
    float lastHealth;
    /** The last hit this tick: what kind of blow, when, and where it came from (for severing). */
    Gore.Blow pendingBlow = Gore.Blow.NONE;
    long pendingTick = -1;
    Vec3 pendingFrom;

    GoreMarks(LivingEntity entity) {
        this.entity = entity;
        this.lastHealth = entity.getHealth();
    }
}

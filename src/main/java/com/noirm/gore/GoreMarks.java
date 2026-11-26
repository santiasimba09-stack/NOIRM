package com.noirm.gore;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** One fighter's gore state on the server: five mark levels, how fresh the blood is, missing parts. */
final class GoreMarks {
    static final int KINDS = Gore.Mark.values().length;
    static final int[] ZERO = new int[KINDS];

    final LivingEntity entity;
    /** Bruise, dirt, soot, cut, blood: 0–1 each (see {@link Gore.Mark}). */
    final float[] level = new float[KINDS];
    /** The levels clients last heard, in 1/255 steps. */
    final int[] sent = new int[KINDS];
    /** How fresh (wet) the blood is: 1 right after a bleeding hit, drying to 0. */
    float fresh;
    int sentFresh;
    /** Severed parts ({@link SeveredLimb.Part} bits); only ever set on a dying body. */
    int severed;
    float lastHealth;
    /** Where it stood at the last bleeding check (for the blood trail of the badly hurt). */
    double lastX;
    double lastZ;
    /** The last hit this tick: what kind of blow, when, and where it came from (for severing). */
    Gore.Blow pendingBlow = Gore.Blow.NONE;
    long pendingTick = -1;
    Vec3 pendingFrom;

    GoreMarks(LivingEntity entity) {
        this.entity = entity;
        this.lastHealth = entity.getHealth();
        this.lastX = entity.getX();
        this.lastZ = entity.getZ();
    }

    /** Blood level (low byte) and freshness (next byte), as sent to clients. */
    int packedBlood() {
        return (sent[Gore.Mark.BLOOD.ordinal()] & 0xFF) | (sentFresh & 0xFF) << 8;
    }
}

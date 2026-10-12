package com.noirm.vfx;

import net.minecraft.core.particles.ParticleType;

/** The particle shapes NOIRM draws (each has its own textures in assets/noirm/particles). */
public enum VfxKind {
    /** A soft round light: cores, auras, chakra. Glows (additive, full-bright). */
    GLOW(true),
    /** A sharp little star: sparks, lightning, impacts. Glows. */
    SPARK(true),
    /** A puff that grows and fades: smoke, steam, dust, mist. */
    SMOKE(false),
    /** A flat ring on the ground that spreads out: shockwaves. Glows. */
    RING(true),
    /** A spinning fragment: debris, ice, sand. */
    SHARD(false);

    private final boolean glows;

    VfxKind(boolean glows) {
        this.glows = glows;
    }

    public boolean glows() {
        return glows;
    }

    public ParticleType<VfxOptions> type() {
        return NoirmParticles.type(this);
    }

    static VfxKind of(ParticleType<?> type) {
        for (VfxKind kind : values()) {
            if (kind.type() == type) {
                return kind;
            }
        }
        return GLOW;
    }
}

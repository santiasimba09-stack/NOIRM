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
    SHARD(false),
    /** A cellular flame mass (yellow-white veins, orange cells, smoky rim). Full-bright, not additive, so it reads in daylight. */
    FLAME(false, true),
    /** A curling flame tongue licking upward. Full-bright, not additive. */
    TONGUE(false, true),
    /** A lightning spark burst: sharp shards around a hot centre (contact points). Glows. */
    ZAP(true),
    /** An ash flake that drifts and flutters down. */
    ASH(false),
    /** A soft glow lying flat on the ground: light pooling under lightning and fire. Glows. */
    GROUND_GLOW(true);

    private final boolean glows;
    private final boolean fullBright;

    VfxKind(boolean glows) {
        this(glows, glows);
    }

    VfxKind(boolean glows, boolean fullBright) {
        this.glows = glows;
        this.fullBright = fullBright;
    }

    public boolean glows() {
        return glows;
    }

    /** Lit by itself (fire, light) rather than by the world around it. */
    public boolean fullBright() {
        return fullBright;
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

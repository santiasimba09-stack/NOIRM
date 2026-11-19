package com.noirm.ally;

import com.noirm.entity.SparringNinja;

import java.util.Locale;

/**
 * How a crew mate fights, set from the Orders tab of their screen: how far from the enemy they
 * like to stay, and how freely they spend chakra on jutsu. {@code NinjaFightGoal} honours both;
 * ninjas outside a crew always fight close and use jutsu freely.
 */
public final class Tactics {
    /** Mid range: the band they hold between combos (blocks). */
    public static final double MID_MIN = 4.0;
    public static final double MID_MAX = 8.0;
    /** Far range: the band they always hold (they still swing if someone gets in their face). */
    public static final double FAR_MIN = 8.0;
    public static final double FAR_MAX = 14.0;
    /** Conserving chakra: jutsu never take them below this share of their max chakra… */
    public static final float CONSERVE_RESERVE = 0.5f;
    /** …and they rest this many times longer between jutsu. */
    public static final int CONSERVE_REST_FACTOR = 2;

    private Tactics() {}

    public enum Range {
        /** In their face (the default): close in and throw combos. */
        CLOSE,
        /** Hit and run: step in for a combo, back off to mid range while it recovers. */
        MID,
        /** Hang back: keep 8–14 blocks away, jutsu and throws. */
        FAR;

        public String key() {
            return "screen.noirm.ally.range." + name().toLowerCase(Locale.ROOT);
        }
    }

    public enum JutsuUse {
        /** Cast whatever suits the moment (the default). */
        FREE,
        /** Keep half their chakra back and cast half as often. */
        CONSERVE,
        /** Fists and weapons only (a Substitution to escape a hit is still a reflex). */
        NONE;

        public String key() {
            return "screen.noirm.ally.jutsu_use." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** The range a ninja fights at: their leader's setting, or close for anyone outside a crew. */
    public static Range range(SparringNinja ninja) {
        return ninja.leaderId() == null ? Range.CLOSE : ninja.allyState().range();
    }

    public static JutsuUse jutsuUse(SparringNinja ninja) {
        return ninja.leaderId() == null ? JutsuUse.FREE : ninja.allyState().jutsuUse();
    }
}

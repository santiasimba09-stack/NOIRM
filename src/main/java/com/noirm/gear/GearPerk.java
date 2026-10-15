package com.noirm.gear;

/**
 * The small bonus a piece of gear can carry (one per piece, numbers in {@link GearPerks}).
 * Ninja gear is light: it trades armour for these.
 */
public enum GearPerk {
    NONE,
    /** Masks: your name tag isn't shown to others. */
    HIDE_NAME,
    /** Cloaks: faster while sprinting ({@link GearPerks#SWIFT_SPEED}). */
    SWIFT,
    /** Hand wraps and arm guards: harder hits (the piece adds attack damage). */
    FISTS,
    /** Tabi and straw sandals: soft landings ({@link GearPerks#SOFT_STEP_BLOCKS} fewer blocks of fall). */
    SOFT_STEP;

    /** Translation key of its tooltip line, e.g. {@code tooltip.noirm.perk.hide_name}. */
    public String tooltipKey() {
        return "tooltip.noirm.perk." + name().toLowerCase(java.util.Locale.ROOT);
    }
}

package com.noirm.mission;

/** What a mission asks you to do. See {@link Missions} for how each one plays. */
public enum MissionKind {
    /** Find and defeat a group of rogues at a marked area (beaten into yielding counts too). */
    HUNT,
    /** Walk a client to a marked place; they follow you; rogues may ambush you on the way. */
    ESCORT,
    /** Carry a sealed parcel to a recipient waiting at a marked place, often against the clock. */
    DELIVER,
    /** Visit a chain of marked points; a lookout may be waiting at the last one. */
    SCOUT,
    /** Find a camp, take the scroll from its chest (past its guards) and bring it back. */
    RETRIEVE,
    /** Raiders come at the village in waves; beat every wave without leaving. */
    DEFEND;

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static MissionKind byKey(String key) {
        for (MissionKind kind : values()) {
            if (kind.key().equalsIgnoreCase(key)) {
                return kind;
            }
        }
        return null;
    }

    public static MissionKind byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : HUNT;
    }
}

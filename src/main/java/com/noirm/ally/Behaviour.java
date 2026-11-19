package com.noirm.ally;

import java.util.Locale;

/** How willing a crew mate is to start a fight (see {@link CrewBrain}). */
public enum Behaviour {
    /** Attacks any hostile creature that comes near you. */
    AGGRESSIVE,
    /** Fights what you fight and whoever attacks you or them (the default). */
    DEFENSIVE,
    /** Never starts a fight: only hits back at whoever hurts them, and backs off to you when hurt. */
    PASSIVE;

    public String key() {
        return "screen.noirm.ally.behaviour." + name().toLowerCase(Locale.ROOT);
    }
}

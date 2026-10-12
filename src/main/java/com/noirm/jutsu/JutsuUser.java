package com.noirm.jutsu;

import com.noirm.clan.Nature;

/** Implemented by NPCs that cast jutsu (players use their {@code ShinobiData}). */
public interface JutsuUser {
    /** 0 if the NPC doesn't have the nature. */
    float jutsuAffinity(Nature nature);

    /** Active Sharingan tomoe, 0 if none. */
    default int activeSharinganStage() {
        return 0;
    }
}

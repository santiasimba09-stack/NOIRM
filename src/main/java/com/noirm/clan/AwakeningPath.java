package com.noirm.clan;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** What a near-death awakening unlocks, depending on who you are. */
public enum AwakeningPath implements StringRepresentable {
    /** Generic surge of will: the locked-in boost only. */
    WILL("will"),
    /** Uchiha: awaken the Sharingan, or gain a tomoe. */
    SHARINGAN("sharingan"),
    /** Clans tied to their nature: nature jutsu are free and stronger while locked in. */
    NATURE_SURGE("nature_surge"),
    /** Jinchūriki (overrides the clan's path): the beast takes over. */
    BEAST("beast");

    public static final Codec<AwakeningPath> CODEC = StringRepresentable.fromEnum(AwakeningPath::values);

    private final String name;

    AwakeningPath(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

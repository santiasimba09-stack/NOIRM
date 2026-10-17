package com.noirm.jutsu;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** How a jutsu's key behaves. */
public enum CastType implements StringRepresentable {
    /** Press: it happens. */
    TAP("tap"),
    /** Hold to gather chakra, release to unleash. Longer charge = stronger. */
    CHARGE("charge"),
    /** Hold to keep it going (drains chakra every second); release to stop. */
    CHANNEL("channel");

    public static final Codec<CastType> CODEC = StringRepresentable.fromEnum(CastType::values);

    private final String name;

    CastType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

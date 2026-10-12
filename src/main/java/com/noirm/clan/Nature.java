package com.noirm.clan;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The five basic chakra natures. */
public enum Nature implements StringRepresentable {
    FIRE("fire", "Katon", 0xFF6A1F),
    WATER("water", "Suiton", 0x2F8BFF),
    WIND("wind", "Fūton", 0x9FE7B0),
    EARTH("earth", "Doton", 0x9C7A4A),
    LIGHTNING("lightning", "Raiton", 0xBFE3FF);

    public static final Codec<Nature> CODEC = StringRepresentable.fromEnum(Nature::values);

    private final String name;
    private final String japanese;
    private final int color;

    Nature(String name, String japanese, int color) {
        this.name = name;
        this.japanese = japanese;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String japanese() {
        return japanese;
    }

    /** RGB color for particles and HUD. */
    public int color() {
        return color;
    }

    public String translationKey() {
        return "nature.noirm." + name;
    }
}

package com.noirm.clan;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Chakra natures. The five <b>basic</b> natures, then the <b>combined</b> ones: a kekkei genkai
 * mixes two basic natures (Wood = Earth + Water…), a kekkei tōta mixes three (Dust). You can
 * unlock a combined nature once you have all of its components and enough mastery with them
 * (see {@code NatureSystem}); a few clans carry one in their blood.
 */
public enum Nature implements StringRepresentable {
    FIRE("fire", "Katon", "火", 0xFF6A1F),
    WATER("water", "Suiton", "水", 0x2F8BFF),
    WIND("wind", "Fūton", "風", 0x9FE7B0),
    EARTH("earth", "Doton", "土", 0x9C7A4A),
    LIGHTNING("lightning", "Raiton", "雷", 0xFFE14A),

    WOOD("wood", "Mokuton", "木", 0x6FA84B, EARTH, WATER),
    ICE("ice", "Hyōton", "氷", 0xA8F0FF, WIND, WATER),
    LAVA("lava", "Yōton", "熔", 0xFF4A1A, FIRE, EARTH),
    STORM("storm", "Ranton", "嵐", 0x6FC8FF, WATER, LIGHTNING),
    BOIL("boil", "Futton", "沸", 0xF0E8E0, FIRE, WATER),
    EXPLOSION("explosion", "Bakuton", "爆", 0xFFB347, EARTH, LIGHTNING),
    MAGNET("magnet", "Jiton", "磁", 0x6A6A80, WIND, EARTH),
    SCORCH("scorch", "Shakuton", "灼", 0xFF8C3A, FIRE, WIND),
    DUST("dust", "Jinton", "塵", 0xF5F5F5, FIRE, WIND, EARTH);

    public static final Codec<Nature> CODEC = StringRepresentable.fromEnum(Nature::values);
    /** Only the five basic natures (what clans roll and jutsu of the basic tree use). */
    public static final List<Nature> BASIC = List.of(FIRE, WATER, WIND, EARTH, LIGHTNING);

    private final String name;
    private final String japanese;
    private final String kanji;
    private final int color;
    private final Nature[] components;

    Nature(String name, String japanese, String kanji, int color, Nature... components) {
        this.name = name;
        this.japanese = japanese;
        this.kanji = kanji;
        this.color = color;
        this.components = components;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String japanese() {
        return japanese;
    }

    /** The character shown on its node in the nature tree. */
    public String kanji() {
        return kanji;
    }

    /** RGB color for particles and HUD. */
    public int color() {
        return color;
    }

    public boolean isBasic() {
        return components.length == 0;
    }

    /** The basic natures this one combines (empty for basic natures). */
    public Set<Nature> components() {
        return Set.copyOf(Arrays.asList(components));
    }

    /** 1 = basic, 2 = kekkei genkai, 3 = kekkei tōta. */
    public int tier() {
        return Math.max(1, components.length);
    }

    public String translationKey() {
        return "nature.noirm." + name;
    }

    public static List<Nature> combined() {
        return Arrays.stream(values()).filter(n -> !n.isBasic()).toList();
    }
}

package com.noirm.clan;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Inherited clan traits. Stat traits apply when you join the clan; the others are checked when
 * they matter (see where each constant is used).
 */
public enum ClanTrait implements StringRepresentable {
    /** +30 max chakra. */
    DEEP_RESERVES("deep_reserves"),
    /** +30 max stamina. */
    ENDURANCE("endurance"),
    /** +3 speed. */
    SWIFT("swift"),
    /** +3 taijutsu. */
    FIERCE("fierce"),
    /** +30 max posture. */
    IRON_BODY("iron_body"),
    /** Perception ×1.1. */
    KEEN_EYES("keen_eyes"),
    /** Resolve builds 50% faster; +10% awakening chance. */
    IRON_WILL("iron_will"),
    /** Jutsu cost 15% less chakra. */
    CHAKRA_FLOW("chakra_flow"),
    /** Stamina regenerates 30% faster. */
    QUICK_RECOVERY("quick_recovery"),
    /** Jutsu mastery grows 50% faster. */
    JUTSU_PRODIGY("jutsu_prodigy");

    public static final Codec<ClanTrait> CODEC = StringRepresentable.fromEnum(ClanTrait::values);

    private final String name;

    ClanTrait(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String translationKey() {
        return "trait.noirm." + name;
    }
}

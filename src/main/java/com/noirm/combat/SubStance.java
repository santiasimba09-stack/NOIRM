package com.noirm.combat;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * A style's sub-stance, picked automatically from what the fighter is doing (see
 * {@link CombatSystem#pickSubStance}). It changes the idle pose (the style JSON's {@code stances})
 * and which variant of a move comes out (a move's {@code variants}). A style without a given
 * sub-stance stays in {@link #NEUTRAL}.
 */
public enum SubStance implements StringRepresentable {
    /** Nothing special going on: the style's base guard. */
    NEUTRAL("neutral"),
    /** Pressing: mid-combo or just landed a hit. */
    AGGRESSIVE("aggressive"),
    /** Guarding, or just got hit. */
    DEFENSIVE("defensive"),
    /** Crouching. */
    LOW("low"),
    /** In the air (jumping, launched, falling). */
    AIR("air");

    public static final Codec<SubStance> CODEC = StringRepresentable.fromEnum(SubStance::values);

    private final String name;

    SubStance(String name) {
        this.name = name;
    }

    /** {@code hud.noirm.stance.<name>}: the HUD label. */
    public String translationKey() {
        return "hud.noirm.stance." + name;
    }

    public static SubStance byId(int id) {
        SubStance[] all = values();
        return id >= 0 && id < all.length ? all[id] : NEUTRAL;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

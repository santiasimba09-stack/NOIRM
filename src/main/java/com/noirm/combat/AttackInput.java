package com.noirm.combat;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The attack buttons. LAUNCHER is a heavy attack pressed while crouching; the FORWARD ones are
 * pressed while sprinting (a dash attack), the BACK ones while holding back. GRAB starts a clinch.
 */
public enum AttackInput implements StringRepresentable {
    LIGHT("light"),
    HEAVY("heavy"),
    LAUNCHER("launcher"),
    FORWARD_LIGHT("forward_light"),
    FORWARD_HEAVY("forward_heavy"),
    BACK_LIGHT("back_light"),
    BACK_HEAVY("back_heavy"),
    GRAB("grab");

    public static final Codec<AttackInput> CODEC = StringRepresentable.fromEnum(AttackInput::values);

    private final String name;

    AttackInput(String name) {
        this.name = name;
    }

    /** The plain input a directional one falls back to when a style or route has nothing for it. */
    public AttackInput fallback() {
        return switch (this) {
            case FORWARD_LIGHT, BACK_LIGHT -> LIGHT;
            case FORWARD_HEAVY, BACK_HEAVY -> HEAVY;
            default -> null;
        };
    }

    /** The light, heavy and launcher buttons every style must answer. */
    public boolean isBasic() {
        return this == LIGHT || this == HEAVY || this == LAUNCHER;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

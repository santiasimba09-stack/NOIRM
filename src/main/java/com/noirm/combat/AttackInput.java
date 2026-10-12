package com.noirm.combat;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The attack buttons. LAUNCHER is a heavy attack pressed while crouching. */
public enum AttackInput implements StringRepresentable {
    LIGHT("light"),
    HEAVY("heavy"),
    LAUNCHER("launcher");

    public static final Codec<AttackInput> CODEC = StringRepresentable.fromEnum(AttackInput::values);

    private final String name;

    AttackInput(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}

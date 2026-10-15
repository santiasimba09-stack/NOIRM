package com.noirm.weapon;

import com.noirm.Noirm;
import net.minecraft.resources.ResourceLocation;

/**
 * The weapon movesets. Holding one of these weapons in the main hand swaps the fighter's style
 * for the weapon's ({@code data/noirm/combat/styles/weapon_<name>.json}, not playable as a style)
 * and its stance ({@code noirm:stance_<name>}).
 */
public enum Moveset {
    /** Kunai and chakra blades: fast, short cuts and stabs. */
    KUNAI("kunai"),
    /** Bō staff: wide sweeps, reach, knockback. */
    STAFF("staff"),
    /** Ninjatō and every vanilla sword: solid slashes. */
    SWORD("sword");

    private final String name;
    private final ResourceLocation style;
    private final ResourceLocation stance;

    Moveset(String name) {
        this.name = name;
        this.style = Noirm.id("weapon_" + name);
        this.stance = Noirm.id("stance_" + name);
    }

    /** Short name, e.g. {@code kunai}. */
    public String key() {
        return name;
    }

    /** The combat style it fights with, e.g. {@code noirm:weapon_kunai}. */
    public ResourceLocation style() {
        return style;
    }

    /** The looping stance animation, e.g. {@code noirm:stance_kunai}. */
    public ResourceLocation stance() {
        return stance;
    }
}

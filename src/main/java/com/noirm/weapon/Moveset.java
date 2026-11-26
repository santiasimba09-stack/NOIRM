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
    KUNAI("kunai", true),
    /** Bō staff: wide sweeps, reach, knockback. */
    STAFF("staff", false),
    /** Ninjatō and every vanilla sword: solid slashes. */
    SWORD("sword", true),
    // The v0.13 blades (see NinjaBlades and docs/WEAPONS.md).
    /** Tantō: very fast, short cuts; the heavy is a stab that loves an unguarded back. */
    TANTO("tanto", true),
    /** Katana: two-handed, slower, long clean cuts and a drawing cut that slips through guards. */
    KATANA("katana", true),
    /** Nodachi: a huge two-hander, slow and wide sweeps, an overhead cleave that floors. */
    GREATSWORD("greatsword", true),
    /** Tonfa: both arms, quick alternating blunt strikes that crush a guard. */
    TONFA("tonfa", false),
    /** Yari: long thrusts and shaft sweeps; fights from further away than anything else. */
    SPEAR("spear", true),
    /** Tekko-kagi: fast rakes that make the target bleed. */
    CLAWS("claws", true),
    /** Tessen (iron war fan): quick snapping strikes, a gust that shoves, swats thrown things away. */
    FAN("fan", false);

    private final String name;
    private final boolean cuts;
    private final ResourceLocation style;
    private final ResourceLocation stance;

    Moveset(String name, boolean cuts) {
        this.name = name;
        this.cuts = cuts;
        this.style = Noirm.id("weapon_" + name);
        this.stance = Noirm.id("stance_" + name);
    }

    /** Short name, e.g. {@code kunai}. */
    public String key() {
        return name;
    }

    /** True for edged weapons (they cut and bleed, see {@code Gore}); false for blunt ones (staff, tonfa, fan). */
    public boolean cuts() {
        return cuts;
    }

    /** The moveset called {@code key} ({@code "katana"}…), or null. */
    public static Moveset byKey(String key) {
        for (Moveset moveset : values()) {
            if (moveset.name.equals(key)) {
                return moveset;
            }
        }
        return null;
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

package com.noirm.arts;

import com.noirm.jutsu.JutsuDefinition;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.Set;

/**
 * The chakra shapes (shape transformation): what form your chakra takes, apart from its nature.
 * Each is trained alone in three stages ({@link ShapeTraining}); owning one makes the canon jutsu of
 * that shape stronger, and the jutsu workshop builds new jutsu on the shapes you've trained.
 *
 * <p>Each shape names the <b>focus item</b> you hold while you meditate to train it, the canon
 * behaviours it boosts, and the behaviour and base numbers the workshop builds on. Forms (armour,
 * chakra beasts you wear, avatars) are not shapes: the workshop can never make them.
 *
 * @param behavior   the existing behaviour a workshop jutsu of this shape is cast with
 * @param damage     workshop base damage (power 3)
 * @param radius     workshop base size (size 3)
 * @param range      workshop base range (range 3)
 * @param speed      workshop base projectile speed (speed 3)
 * @param canon      canon behaviours of this shape (owning the shape boosts them)
 */
public enum ChakraShape {
    /** A sphere held in the palm and driven in (the Rasengan's shape). */
    BALL("ball", "球", 0x7FC8FF, "rasengan", 8f, 2f, 4f, 1f,
            Set.of("rasengan", "heat_orbs")),
    /** A thin edge that cuts (Wind Blade, chakra flow on steel). */
    BLADE("blade", "刃", 0xC8D8E8, "wind_crescent", 5.5f, 1.6f, 8f, 1.1f,
            Set.of("wind_crescent", "dash_strike", "scalpel_strike")),
    /** A long, piercing line (False Darkness, Light Fang). */
    SPEAR("spear", "槍", 0xFFE8A0, "beam", 6.5f, 0.5f, 16f, 1f,
            Set.of("beam", "laser", "bending_laser", "needle_rain")),
    /** A rolling wall of chakra that sweeps forward (Great Fire Annihilation). */
    WAVE("wave", "波", 0x6FB3E8, "wave", 5f, 3f, 16f, 0.8f,
            Set.of("wave", "stream", "storm_tide", "sand_tide")),
    /** A barrier around you (Earth Wall, Gale Wall). */
    WALL("wall", "壁", 0xB89A70, "ward_shell", 2f, 1.8f, 0f, 1f,
            Set.of("wall", "dome", "ward_shell", "wood_dome", "rampart")),
    /** A living shape that hunts: a dragon, a hound, a bird (Fire Dragon, Chakra Hawks). */
    BEAST("beast", "獣", 0xFF8C5A, "projectile", 7f, 2.5f, 24f, 0.6f,
            Set.of("hawks", "rock_hounds", "wood_dragon", "clay_bomb")),
    /** Chakra spread around you in all directions at once (Chidori Current). */
    FIELD("field", "陣", 0xB89AE8, "nova", 4.5f, 3.5f, 0f, 1f,
            Set.of("nova", "cloud", "rain", "frost_field", "healing_field", "ash_cloud", "poison_mist")),
    /** Chakra that grips: chains, roots, shadows (Wood Binding, Shadow Imitation). */
    BIND("bind", "縛", 0x9AD8A0, "bind", 3f, 1f, 10f, 1f,
            Set.of("bind", "shadow_bind", "wood_roots", "shadow_neck_bind", "shadow_stitch"));

    /**
     * Forms are never shapes: names the workshop refuses outright, whatever is asked (armour, a
     * chakra beast you wear, an avatar like the Susanoo, a mode).
     */
    public static final Set<String> FORMS = Set.of("armor", "armour", "cloak", "avatar", "form", "beast_form", "chakra_beast",
            "susanoo", "mode", "sage", "sage_mode", "gate", "gates", "shell_form");
    /** Behaviours that are forms: no shape ever casts with one. */
    public static final Set<String> FORM_BEHAVIORS = Set.of("mode", "susanoo", "sage_mode", "buff", "steam_charge", "wood_colossus");

    private final String key;
    private final String kanji;
    private final int color;
    private final String behavior;
    private final float damage;
    private final float radius;
    private final float range;
    private final float speed;
    private final Set<String> canon;

    ChakraShape(String key, String kanji, int color, String behavior, float damage, float radius, float range, float speed, Set<String> canon) {
        this.key = key;
        this.kanji = kanji;
        this.color = color;
        this.behavior = behavior;
        this.damage = damage;
        this.radius = radius;
        this.range = range;
        this.speed = speed;
        this.canon = canon;
    }

    public String key() {
        return key;
    }

    public String kanji() {
        return kanji;
    }

    /** RGB, for the screens. */
    public int color() {
        return color;
    }

    public String behavior() {
        return behavior;
    }

    public float damage() {
        return damage;
    }

    public float radius() {
        return radius;
    }

    public float range() {
        return range;
    }

    public float speed() {
        return speed;
    }

    public String translationKey() {
        return "shape.noirm." + key;
    }

    /** What you hold while you meditate to train this shape. */
    public Item focus() {
        return switch (this) {
            case BALL -> Items.SLIME_BALL;      // a rubber ball
            case BLADE -> Items.FLINT;          // an edge to follow
            case SPEAR -> Items.ARROW;          // a point
            case WAVE -> Items.WATER_BUCKET;    // water that rolls
            case WALL -> Items.BRICK;           // something to stand behind
            case BEAST -> Items.BONE;           // a beast's bone
            case FIELD -> Items.AMETHYST_SHARD; // a ringing crystal
            case BIND -> Items.STRING;          // a cord
        };
    }

    /** Whether a canon jutsu is of this shape (its behaviour, or a dragon-shaped projectile for the beast). */
    public boolean matches(JutsuDefinition def) {
        if (def == null) {
            return false;
        }
        if (this == BEAST && "projectile".equals(def.behavior()) && "dragon".equals(def.params().look())) {
            return true;
        }
        if (this == BALL && "projectile".equals(def.behavior()) && !"dragon".equals(def.params().look()) && def.params().count() <= 1
                && def.params().radius() >= 1.5f) {
            return true; // a single big ball thrown (the Great Fireball)
        }
        return canon.contains(def.behavior());
    }

    /** The shape of a canon jutsu, or null. */
    public static ChakraShape of(JutsuDefinition def) {
        for (ChakraShape shape : values()) {
            if (shape.matches(def)) {
                return shape;
            }
        }
        return null;
    }

    public static ChakraShape byKey(String key) {
        if (key == null) {
            return null;
        }
        String k = key.toLowerCase(Locale.ROOT);
        for (ChakraShape shape : values()) {
            if (shape.key.equals(k)) {
                return shape;
            }
        }
        return null;
    }

    /** Whether this name is a form (armour, cloak, avatar…): the workshop refuses it. */
    public static boolean isForm(String name) {
        return name != null && FORMS.contains(name.toLowerCase(Locale.ROOT));
    }
}

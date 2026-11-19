package com.noirm.genjutsu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * What a genjutsu shows inside its victim's mind: one file per scene in
 * {@code data/noirm/genjutsu/<id>.json} (see {@code docs/GENJUTSU.md}). The server picks the scene
 * for the jutsu that caught someone ({@link #jutsu}) and sends the whole definition to the victim's
 * client, which draws it ({@code client/GenjutsuSceneRenderer}). Nothing here touches real blocks.
 *
 * @param jutsu       the jutsu that open this scene
 * @param duration    ticks, for {@code /noirm genjutsu test} (a real catch uses the jutsu's own length)
 * @param sky         the false sky and how far the real world fades into fog
 * @param ground      what the ground becomes around the victim
 * @param tint        a colour wash over the whole view ({@code #RRGGBB}) and its strength 0–1
 * @param figures     what stands, flies or grows around the victim
 * @param sounds      what the victim hears (and how the real world's sounds are dulled)
 * @param hudFlicker  0–1: how often the victim's own HUD stutters out
 * @param timeScale   below 1, the scene's motion slows and a counter shows the real time left (Tsukuyomi)
 * @param post        an optional post-processing shader ({@code noirm:shaders/post/<name>.json}), "" for none
 * @param mob         how a creature caught in it behaves: {@code freeze}, {@code wander}, {@code attack_phantoms},
 *                    {@code turn_on_allies}
 * @param swapAllies  the victim's allies look like enemies (Kotoamatsukami)
 * @param hideCaster  the real caster is hidden from the victim (they see the phantom instead)
 * @param shatter     the colour of the glass when it breaks
 */
public record GenjutsuScene(List<ResourceLocation> jutsu, int duration, Sky sky, Ground ground, String tint, float tintAlpha,
                            List<Figure> figures, Sounds sounds, float hudFlicker, float timeScale, String post, String mob,
                            boolean swapAllies, boolean hideCaster, String shatter) {

    public static final Codec<GenjutsuScene> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("jutsu", List.of()).forGetter(GenjutsuScene::jutsu),
            Codec.intRange(1, 2400).optionalFieldOf("duration", 120).forGetter(GenjutsuScene::duration),
            Sky.CODEC.optionalFieldOf("sky", Sky.DEFAULT).forGetter(GenjutsuScene::sky),
            Ground.CODEC.optionalFieldOf("ground", Ground.NONE).forGetter(GenjutsuScene::ground),
            Codec.STRING.optionalFieldOf("tint", "#000000").forGetter(GenjutsuScene::tint),
            Codec.floatRange(0, 1).optionalFieldOf("tint_alpha", 0f).forGetter(GenjutsuScene::tintAlpha),
            Figure.CODEC.listOf().optionalFieldOf("figures", List.of()).forGetter(GenjutsuScene::figures),
            Sounds.CODEC.optionalFieldOf("sounds", Sounds.DEFAULT).forGetter(GenjutsuScene::sounds),
            Codec.floatRange(0, 1).optionalFieldOf("hud_flicker", 0f).forGetter(GenjutsuScene::hudFlicker),
            Codec.floatRange(0.05f, 1).optionalFieldOf("time_scale", 1f).forGetter(GenjutsuScene::timeScale),
            Codec.STRING.optionalFieldOf("post", "").forGetter(GenjutsuScene::post),
            Codec.STRING.optionalFieldOf("mob", "freeze").forGetter(GenjutsuScene::mob),
            Codec.BOOL.optionalFieldOf("swap_allies", false).forGetter(GenjutsuScene::swapAllies),
            Codec.BOOL.optionalFieldOf("hide_caster", false).forGetter(GenjutsuScene::hideCaster),
            Codec.STRING.optionalFieldOf("shatter", "#E8DCFF").forGetter(GenjutsuScene::shatter)
    ).apply(i, GenjutsuScene::new));

    /** {@code #RRGGBB} (or {@code RRGGBB}) to an int; white if it can't be read. */
    public static int color(String hex) {
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    /** How a creature caught in this scene behaves. */
    public Reaction reaction() {
        return Reaction.of(mob);
    }

    public enum Reaction {
        /** Rooted in terror (or bound): it stops, trembles and looks wildly around. */
        FREEZE,
        /** Lost: it walks off in random directions, looking for a way out. */
        WANDER,
        /** It fights things that aren't there: swings at phantoms in the air (or at the false image). */
        ATTACK_PHANTOMS,
        /** It turns on its own side. */
        TURN_ON_ALLIES;

        public static Reaction of(String name) {
            return switch (name) {
                case "wander" -> WANDER;
                case "attack_phantoms" -> ATTACK_PHANTOMS;
                case "turn_on_allies" -> TURN_ON_ALLIES;
                default -> FREEZE;
            };
        }
    }

    /**
     * The false sky. The fog pulls in to {@code fogEnd} blocks in the fog colour, so the real
     * surroundings fade away; the dome above blends from {@code top} to {@code horizon}. {@code "none"}
     * keeps the real sky (or the real fog): a subtle illusion.
     *
     * @param moon     a disc in the sky ({@code ""} for none); {@code moonSize} in degrees
     * @param stripes  black bands across the moon (the inverted Tsukuyomi moon)
     */
    public record Sky(String top, String horizon, String fog, float fogStart, float fogEnd, String moon, float moonSize, boolean stripes) {
        public static final Sky DEFAULT = new Sky("none", "none", "none", 4f, 24f, "", 0f, false);
        public static final Codec<Sky> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("top", "none").forGetter(Sky::top),
                Codec.STRING.optionalFieldOf("horizon", "none").forGetter(Sky::horizon),
                Codec.STRING.optionalFieldOf("fog", "none").forGetter(Sky::fog),
                Codec.FLOAT.optionalFieldOf("fog_start", 4f).forGetter(Sky::fogStart),
                Codec.FLOAT.optionalFieldOf("fog_end", 24f).forGetter(Sky::fogEnd),
                Codec.STRING.optionalFieldOf("moon", "").forGetter(Sky::moon),
                Codec.FLOAT.optionalFieldOf("moon_size", 0f).forGetter(Sky::moonSize),
                Codec.BOOL.optionalFieldOf("stripes", false).forGetter(Sky::stripes)
        ).apply(i, Sky::new));
    }

    /**
     * The ground: {@code ink} (a black pool that ripples and drips), {@code water} (a still, pale
     * mirror), {@code blood} (dark red, the Tsukuyomi) or {@code none}.
     */
    public record Ground(String kind, String color, String rim, float radius) {
        public static final Ground NONE = new Ground("none", "#000000", "#000000", 0f);
        public static final Codec<Ground> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("kind", "none").forGetter(Ground::kind),
                Codec.STRING.optionalFieldOf("color", "#000000").forGetter(Ground::color),
                Codec.STRING.optionalFieldOf("rim", "#000000").forGetter(Ground::rim),
                Codec.floatRange(0, 64).optionalFieldOf("radius", 24f).forGetter(Ground::radius)
        ).apply(i, Ground::new));
    }

    /**
     * Something in the scene.
     *
     * @param type    {@code crow}, {@code caster} (copies of the caster), {@code phantom} (ink shapes of
     *                the caster), {@code crew} (phantoms of your own crew, turned against you), {@code tree}
     *                (a tree growing round you), {@code tendril} (ink hands rising to bind), {@code post}
     *                (bare wooden posts), {@code eye} (a huge eye opening in the sky)
     * @param count   how many
     * @param radius  how far from the victim (blocks)
     * @param height  how high (blocks above the feet)
     * @param motion  {@code circle}, {@code swarm}, {@code close_in}, {@code stand}, {@code grow}
     * @param color   main colour
     */
    public record Figure(String type, int count, float radius, float height, String motion, String color) {
        public static final Codec<Figure> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("type").forGetter(Figure::type),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Figure::count),
                Codec.floatRange(0, 48).optionalFieldOf("radius", 4f).forGetter(Figure::radius),
                Codec.floatRange(-8, 48).optionalFieldOf("height", 0f).forGetter(Figure::height),
                Codec.STRING.optionalFieldOf("motion", "stand").forGetter(Figure::motion),
                Codec.STRING.optionalFieldOf("color", "#0A060E").forGetter(Figure::color)
        ).apply(i, Figure::new));
    }

    /**
     * What the victim hears. Periods are in ticks (0 = never).
     *
     * @param drone     the low drone of the false world, looping
     * @param heartbeat their own heartbeat, too loud and too slow
     * @param whispers  wordless whispers
     * @param crows     crows calling
     * @param muffle    the real world's sounds play at this volume (1 = untouched)
     * @param pitch     and at this pitch (below 1: slowed, deeper)
     */
    public record Sounds(boolean drone, int heartbeat, int whispers, int crows, float muffle, float pitch) {
        public static final Sounds DEFAULT = new Sounds(true, 0, 0, 0, 0.5f, 1f);
        public static final Codec<Sounds> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("drone", true).forGetter(Sounds::drone),
                Codec.intRange(0, 1200).optionalFieldOf("heartbeat", 0).forGetter(Sounds::heartbeat),
                Codec.intRange(0, 1200).optionalFieldOf("whispers", 0).forGetter(Sounds::whispers),
                Codec.intRange(0, 1200).optionalFieldOf("crows", 0).forGetter(Sounds::crows),
                Codec.floatRange(0, 1).optionalFieldOf("muffle", 0.5f).forGetter(Sounds::muffle),
                Codec.floatRange(0.25f, 2f).optionalFieldOf("pitch", 1f).forGetter(Sounds::pitch)
        ).apply(i, Sounds::new));
    }
}

package com.noirm.creator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * How a player looks, chosen on the character creation screen: one option id per {@link Slot}
 * (eyes, nose, hair style…), one colour per {@link Tone} and a height. It is saved as short ids
 * ("eyes": "sharp") so adding options later never breaks old saves, and sent as one small
 * packet (a byte per slot, an int per colour) so everyone sees each other's look.
 *
 * <p>The client turns it into a skin by stacking part images ({@code client/PlayerSkins}); the
 * part images are drawn by {@code tools/skingen/gen_player_parts.py} and {@code gen_parts.py}.
 */
public final class PlayerLook {
    public static final float MIN_HEIGHT = 0.85f;
    public static final float MAX_HEIGHT = 1.15f;
    /** The first this-many hair swatches are natural shades (black to white, browns, auburn). */
    public static final int NATURAL_HAIR = 20;

    /** A choice with a fixed list of options. The first option is the default. */
    public enum Slot {
        // New options only ever go at the end of a list (saves keep ids, the look packet keeps indexes).
        EYES("eyes", "round", "sharp", "wide", "narrow", "pale", "fox", "hawk", "hooded", "drooping", "keen", "slit", "bold", "glare"),
        NOSE("nose", "straight", "button", "broad", "hooked", "flat"),
        JAW("jaw", "soft", "square", "sharp", "round"),
        MOUTH("mouth", "neutral", "smile", "frown", "wide", "thin"),
        BROWS("brows", "thin", "thick", "fierce", "soft", "arched", "straight", "angled", "short", "bushy", "dots", "split", "none"),
        MUSCLE("muscle", "lean", "defined", "heavy", "none", "toned", "ripped"),
        SCAR("scar", "none", "eye", "nose", "cheek", "chest", "arm", "back", "lip", "jaw", "cross", "brow", "claws", "burn",
                "chest_claws", "shoulder", "belly"),
        WEATHERING("weathering", "none", "light", "worn", "battered"),
        MARKING("marking", "none", "whiskers", "fangs", "swirls", "tear_lines", "diamond", "war_paint", "freckles", "stripes",
                "mole", "heavy_freckles", "birthmark", "blush"),
        UNDERWEAR("underwear", "boxers", "briefs", "fundoshi", "shorts", "bindings"),
        // Characters start in just their underwear: clothes are gear items worn on top. These three
        // slots stay (with one option) so saves and the look packet keep their shape; an old save's
        // "tee" or "sandals" is unknown now and loads as the first option, i.e. nothing.
        TOP("top", "none"),
        BOTTOM("bottom", "none"),
        FEET("feet", "bare"),
        WRAPS("wraps", "none", "legs", "arms", "both"),
        // 3D hair styles (assets/noirm/hair/styles/<id>.json, by tools/skingen/gen_hair.py); "none" last
        HAIR("hair", "spiky", "messy", "short", "swept", "bowl", "buzz", "topknot", "duck", "bun", "hime", "ponytail", "long",
                "high_ponytail", "braid", "side_bangs", "shoulder_messy", "twin_buns", "wild_mane", "undercut", "mohawk",
                "slick_back", "curly", "twin_tails", "none",
                // the creator wave's styles (after "none", so the older ones keep their numbers)
                "drill_spikes", "flame_spikes", "porcupine", "windswept", "crown_spikes", "bedhead", "messy_layers", "shaggy",
                "wolf_cut", "mullet", "parted", "crop", "side_shave", "faux_hawk", "tall_mohawk", "shaved_knot", "twin_braids",
                "side_braid", "mushroom", "curtain", "veil_bangs", "drill_tails", "high_twin_tails", "side_ponytail",
                "spiky_tail", "half_up", "bun_tails", "horn_buns", "puff", "locs", "locs_tied", "ronin", "spiky_fringe",
                "rat_tail", "crescent", "swept_long", "spiky_long", "wave"),
        HAIR_LENGTH("hair_length", "as_styled", "nape", "shoulder", "waist"),
        HAIR_VOLUME("hair_volume", "flat", "full", "thick", "wild"),
        /** Build: shading plus a small shoulder and arm width change (client/BodyShape). */
        BODY("body", "average", "slim", "athletic", "broad", "lean", "wiry", "stocky", "heavy", "hulking"),
        /** The feminine frame changes the chest's shading and adds a black top to the underwear. */
        FRAME("frame", "masculine", "feminine"),
        // ---- the creator wave (2026-11-19): new slots go at the end (protocol 34)
        /** How the second hair colour ({@link Tone#HAIR2}) is used: dyed tips, roots, streaks, a split… */
        HAIR_DYE("hair_dye", "plain", "tips", "roots", "ombre", "highlights", "streaks", "split", "bangs"),
        /** The pupil drawn in the high-detail eye (client/EyeDecalLayer). */
        PUPIL("pupil", "round", "slit", "pinprick", "wide", "hollow", "ringed"),
        /** Around the eyes: tired bags, dark circles, lines, a red liner under the eye… */
        EYE_DETAIL("eye_detail", "none", "bags", "dark_circles", "crows_feet", "red_liner", "tear_mark", "veins"),
        /** Face paint in the paint colour ({@link Tone#PAINT}): clan-style markings of our own design. */
        FACE_PAINT("face_paint", "none", "eye_band", "cheek_fangs", "jaw_lines", "chin_stripe", "forehead_dot", "crescent",
                "cheek_bars", "tear_trails", "lightning", "spiral", "brow_flames", "half_mask"),
        /** Body tattoos in the paint colour. */
        TATTOO("tattoo", "none", "shoulder_seal", "arm_bands", "chest_seal", "back_crest", "flame_sleeve", "spine_marks",
                "rib_slashes", "leg_bands");

        private final String key;
        private final String[] options;

        Slot(String key, String... options) {
            this.key = key;
            this.options = options;
        }

        public String key() {
            return key;
        }

        public int size() {
            return options.length;
        }

        public String option(int index) {
            return options[Mth.clamp(index, 0, options.length - 1)];
        }

        /** The index of an option id, or -1. */
        public int indexOf(String id) {
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(id)) {
                    return i;
                }
            }
            return -1;
        }

        public String translationKey(int index) {
            return "creator.noirm.option." + key + "." + option(index);
        }
    }

    /** A colour the player picks, with a palette of suggestions (any RGB colour is allowed). */
    public enum Tone {
        SKIN("skin", 0xF1CDB0, 0xE8BC98, 0xE0AC84, 0xC98E66, 0xB07650, 0x8A5A3C, 0x6A4330, 0x4E3024, 0xF3D9C4, 0xD9A27A, 0xA36B47, 0x5C3A28,
                // the creator wave: more tones, warm and cool, very pale to very deep, and two unusual ones
                0xFBE6D6, 0xEFC9A8, 0xE6B98E, 0xD4A373, 0xC08458, 0xA97448, 0x94603A, 0x7A4A2E, 0x5E3A26, 0x3E261A,
                0xD8B48A, 0xB89068, 0x9AA2AA, 0xB8C4B4),
        EYE("eye", 0x1E1A1F, 0x3A2A22, 0x5A3C24, 0x2E4A6B, 0x3F6E9C, 0x3E6B45, 0x6C7A3A, 0x7A7F86, 0xB9B6C8, 0x8E2B2B, 0xC9A445, 0x4B3A6E,
                // the creator wave: brighter and stranger irises
                0x6FA8DC, 0x2E7D8C, 0x49A07A, 0x8FB04A, 0xD8C25A, 0xE08A2E, 0xC0402E, 0xD84A6E, 0x9A5AC8, 0x5A4AA8,
                0xA8AEB8, 0xE6E2D8, 0x7A2A1A, 0x2A3A2A, 0x4A6AA0, 0x8A6A4A),
        // 40 swatches of our own palette: the first NATURAL_HAIR are natural shades (random looks mostly use those)
        HAIR("hair", 0x141418, 0x1B1C22, 0x1E2230, 0x2A2224, 0x2B1D16, 0x3A2618, 0x4A3020, 0x5A3A24, 0x6A4A2A, 0x8A5A30,
                0xA8743A, 0xC08A48, 0xE3C26A, 0xE6CF8E, 0xF2E6BE, 0xD8D4CC, 0xB8B6B0, 0x8E9096, 0x5A5C62, 0xF2F0EA,
                0x8A1E1E, 0xB8261F, 0xD8352A, 0xE85A2A, 0xE07A2E, 0xF0A030, 0xB4462E,
                0x2F5A3A, 0x4E6B3A, 0x6E9A4A, 0x2B3A6E, 0x2F3F6A, 0x6B88B8, 0x9EC4E8, 0x5A3A6A, 0x8A5AB0,
                0xC86A9E, 0xD98AA8, 0xF0B8C8, 0x3AA8A0,
                // the creator wave: more naturals and more vivid dyes
                0x3B2B22, 0x7A5638, 0xB89058, 0xD8B07A, 0x9A9A9E, 0x6A2A24, 0x9A3A1E,
                0x1E5A5A, 0x38D0C0, 0x4AE07A, 0xA8E04A, 0xF0E04A, 0xF07AB0, 0xB8A0F0, 0x6A3AF0, 0xE84AE0, 0x2A2A6A, 0xFFFFFF),
        // black first: new characters start in black boxers (owner, round 15)
        UNDERWEAR("underwear", 0x1A1A1E, 0x1C2233, 0x24262E, 0xD9D4C8, 0x3A3F48, 0x5B2B2B, 0x2E3A2A, 0x6B5A3A, 0x2B3A55, 0x8A8F96, 0x4B2E4E, 0xB8A27A),
        // TOP and BOTTOM: no longer used (clothes are gear), kept so the look packet keeps its shape.
        TOP("top", 0x1C2233, 0x1A1A20, 0x23293D, 0x2E3038, 0x3E7A2E, 0x5B2B2B, 0xD9D4C8, 0x6B5A3A, 0x2B3A55, 0x8A8F96, 0xC2702E, 0x3A2E4E),
        BOTTOM("bottom", 0x24262E, 0x1F2433, 0x2E3038, 0xD9D4C8, 0x3E7A2E, 0x3A2E24, 0x4B4F58, 0x2B3A55, 0x101014, 0x6B5A3A, 0x5B2B2B, 0x8A8F96),
        // ---- the creator wave (2026-11-19): new colours go at the end (protocol 34)
        /** The second hair colour, for {@link Slot#HAIR_DYE} (tips, streaks, a split…). */
        HAIR2("hair2", 0xD8352A, 0xF2E6BE, 0x6B88B8, 0x141418, 0xF0A030, 0x8A5AB0, 0x3AA8A0, 0xF07AB0, 0xE85A2A, 0xD8D4CC,
                0x4AE07A, 0x2B3A6E, 0xC08A48, 0xB8261F, 0xFFFFFF, 0x5A3A6A),
        /** Face paint and tattoos. */
        PAINT("paint", 0xB8261F, 0x8A1E1E, 0x5A2A8A, 0x2B3A6E, 0x1B1C22, 0xF2F0EA, 0xE0A030, 0x2F5A3A, 0x3AA8A0, 0xC86A9E,
                0x6A4A2A, 0xD8352A);

        private final String key;
        private final int[] palette;

        Tone(String key, int... palette) {
            this.key = key;
            this.palette = palette;
        }

        public String key() {
            return key;
        }

        public int[] palette() {
            return palette.clone();
        }

        public int defaultColor() {
            return palette[0];
        }
    }

    private final int[] choices = new int[Slot.values().length];
    private final int[] colors = new int[Tone.values().length];
    private float height = 1f;

    public PlayerLook() {
        for (Tone tone : Tone.values()) {
            colors[tone.ordinal()] = tone.defaultColor();
        }
    }

    public PlayerLook copy() {
        PlayerLook copy = new PlayerLook();
        System.arraycopy(choices, 0, copy.choices, 0, choices.length);
        System.arraycopy(colors, 0, copy.colors, 0, colors.length);
        copy.height = height;
        return copy;
    }

    // ------------------------------------------------------------ values

    public int get(Slot slot) {
        return choices[slot.ordinal()];
    }

    public String id(Slot slot) {
        return slot.option(get(slot));
    }

    public PlayerLook set(Slot slot, int index) {
        choices[slot.ordinal()] = Math.floorMod(index, slot.size());
        return this;
    }

    public int color(Tone tone) {
        return colors[tone.ordinal()];
    }

    public PlayerLook setColor(Tone tone, int rgb) {
        colors[tone.ordinal()] = rgb & 0xFFFFFF;
        return this;
    }

    public float height() {
        return height;
    }

    public PlayerLook setHeight(float value) {
        height = Float.isFinite(value) ? Mth.clamp(value, MIN_HEIGHT, MAX_HEIGHT) : 1f;
        return this;
    }

    /** How much bigger each lock of the 3D hair is drawn (flat 0.9 .. wild 1.24). */
    public float hairVolumeScale() {
        return volumeScale(get(Slot.HAIR_VOLUME));
    }

    public static float volumeScale(int volume) {
        return switch (volume) {
            case 0 -> 0.9f;
            case 2 -> 1.12f;
            case 3 -> 1.24f;
            default -> 1f;
        };
    }

    /** A short text naming every choice: the client's skin cache key. */
    public String key() {
        StringBuilder text = new StringBuilder();
        for (int choice : choices) {
            if (choice >= 36) {
                text.append('~').append(Character.forDigit(choice / 36, 36));
            }
            text.append(Character.forDigit(choice % 36, 36));
        }
        for (int color : colors) {
            text.append('_').append(Integer.toHexString(color));
        }
        return text.toString();
    }

    /** A random look: every part from its list, colours from the palettes; scars and marks are rarer. */
    public static PlayerLook random(RandomSource random) {
        PlayerLook look = new PlayerLook();
        for (Slot slot : Slot.values()) {
            look.set(slot, random.nextInt(slot.size()));
        }
        for (Slot rare : new Slot[]{Slot.SCAR, Slot.MARKING, Slot.WEATHERING, Slot.EYE_DETAIL, Slot.FACE_PAINT, Slot.TATTOO}) {
            if (random.nextFloat() < 0.6f) {
                look.set(rare, 0);
            }
        }
        for (Slot uncommon : new Slot[]{Slot.HAIR_DYE, Slot.PUPIL, Slot.FACE_PAINT, Slot.TATTOO}) {
            if (random.nextFloat() < 0.6f) {
                look.set(uncommon, 0); // dyed hair, odd pupils and paint are the exception
            }
        }
        if (look.id(Slot.HAIR).equals("none") && random.nextFloat() < 0.85f) {
            look.set(Slot.HAIR, random.nextInt(Slot.HAIR.size() - 1)); // bald is rare
            if (look.id(Slot.HAIR).equals("none")) {
                look.set(Slot.HAIR, Slot.HAIR.size() - 1);
            }
        }
        if (random.nextFloat() < 0.5f) {
            look.set(Slot.HAIR_LENGTH, 0);
        }
        if (random.nextFloat() < 0.7f) {
            look.set(Slot.WRAPS, 0); // bandage wraps are the exception, not the rule
        }
        for (Tone tone : Tone.values()) {
            int[] palette = tone.palette;
            look.setColor(tone, palette[random.nextInt(palette.length)]);
        }
        if (random.nextFloat() < 0.8f) {
            look.setColor(Tone.HAIR, Tone.HAIR.palette[random.nextInt(NATURAL_HAIR)]); // vivid hair is the exception
        }
        // everyone starts (and re-rolls) in the default black boxers; the Body tab changes them
        look.set(Slot.UNDERWEAR, 0);
        look.setColor(Tone.UNDERWEAR, Tone.UNDERWEAR.defaultColor());
        look.setHeight(0.95f + random.nextFloat() * 0.1f);
        return look;
    }

    // ------------------------------------------------------------ saving and sending

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (Slot slot : Slot.values()) {
            tag.putString(slot.key(), id(slot));
        }
        for (Tone tone : Tone.values()) {
            tag.putInt(tone.key() + "_color", color(tone));
        }
        tag.putFloat("height", height);
        return tag;
    }

    /** Reads a saved look; unknown or missing ids fall back to each slot's first option. */
    public static PlayerLook load(CompoundTag tag) {
        PlayerLook look = new PlayerLook();
        for (Slot slot : Slot.values()) {
            look.set(slot, Math.max(0, slot.indexOf(tag.getString(slot.key()))));
        }
        for (Tone tone : Tone.values()) {
            String key = tone.key() + "_color";
            look.setColor(tone, tag.contains(key) ? tag.getInt(key) : tone.defaultColor());
        }
        look.setHeight(tag.contains("height") ? tag.getFloat("height") : 1f);
        return look;
    }

    public void encode(FriendlyByteBuf buf) {
        for (int choice : choices) {
            buf.writeByte(choice);
        }
        for (int color : colors) {
            buf.writeMedium(color);
        }
        buf.writeShort(Math.round(height * 1000f));
    }

    /** Reads a look; out-of-range values (a newer or tampered client) are clamped. */
    public static PlayerLook decode(FriendlyByteBuf buf) {
        PlayerLook look = new PlayerLook();
        for (Slot slot : Slot.values()) {
            int value = buf.readByte();
            look.set(slot, value >= 0 && value < slot.size() ? value : 0);
        }
        for (Tone tone : Tone.values()) {
            look.setColor(tone, buf.readUnsignedMedium());
        }
        look.setHeight(buf.readShort() / 1000f);
        return look;
    }

    /** Every choice and colour, by name (for tests and debugging). */
    public Map<String, String> describe() {
        Map<Slot, String> parts = new EnumMap<>(Slot.class);
        for (Slot slot : Slot.values()) {
            parts.put(slot, id(slot));
        }
        Map<String, String> out = new java.util.LinkedHashMap<>();
        parts.forEach((slot, id) -> out.put(slot.key(), id));
        for (Tone tone : Tone.values()) {
            out.put(tone.key(), String.format("#%06x", color(tone)));
        }
        out.put("height", String.valueOf(height));
        return out;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PlayerLook look && Arrays.equals(choices, look.choices) && Arrays.equals(colors, look.colors)
                && Math.abs(height - look.height) < 0.001f;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(choices) * 31 + Arrays.hashCode(colors);
    }

    @Override
    public String toString() {
        return "PlayerLook" + describe();
    }
}

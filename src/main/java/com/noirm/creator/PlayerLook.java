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

    /** A choice with a fixed list of options. The first option is the default. */
    public enum Slot {
        EYES("eyes", "round", "sharp", "wide", "narrow", "pale"),
        NOSE("nose", "straight", "button", "broad", "hooked", "flat"),
        JAW("jaw", "soft", "square", "sharp", "round"),
        MOUTH("mouth", "neutral", "smile", "frown", "wide", "thin"),
        BROWS("brows", "thin", "thick", "fierce", "soft"),
        MUSCLE("muscle", "lean", "defined", "heavy", "none"),
        SCAR("scar", "none", "eye", "nose", "cheek", "chest", "arm", "back"),
        WEATHERING("weathering", "none", "light", "worn", "battered"),
        MARKING("marking", "none", "whiskers", "fangs", "swirls", "tear_lines", "diamond", "war_paint", "freckles", "stripes"),
        UNDERWEAR("underwear", "boxers", "briefs", "fundoshi", "shorts", "bindings"),
        // Characters start in just their underwear: clothes are gear items worn on top. These three
        // slots stay (with one option) so saves and the look packet keep their shape; an old save's
        // "tee" or "sandals" is unknown now and loads as the first option, i.e. nothing.
        TOP("top", "none"),
        BOTTOM("bottom", "none"),
        FEET("feet", "bare"),
        WRAPS("wraps", "none", "legs", "arms", "both"),
        HAIR("hair", "spiky", "messy", "short", "swept", "bowl", "buzz", "topknot", "duck", "bun", "hime", "ponytail", "long", "none"),
        HAIR_LENGTH("hair_length", "as_styled", "nape", "shoulder", "waist"),
        HAIR_VOLUME("hair_volume", "flat", "full", "thick", "wild");

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
        SKIN("skin", 0xF1CDB0, 0xE8BC98, 0xE0AC84, 0xC98E66, 0xB07650, 0x8A5A3C, 0x6A4330, 0x4E3024, 0xF3D9C4, 0xD9A27A, 0xA36B47, 0x5C3A28),
        EYE("eye", 0x1E1A1F, 0x3A2A22, 0x5A3C24, 0x2E4A6B, 0x3F6E9C, 0x3E6B45, 0x6C7A3A, 0x7A7F86, 0xB9B6C8, 0x8E2B2B, 0xC9A445, 0x4B3A6E),
        HAIR("hair", 0x141418, 0x2A2224, 0x3A2618, 0x5A3A24, 0x8A5A30, 0xC08A48, 0xE6CF8E, 0xD8D4CC, 0x8E9096, 0xB4462E, 0xE07A2E, 0x6B88B8,
                0x4E6B3A, 0xC86A9E, 0x2B3A6E, 0xF2F0EA),
        // black first: new characters start in black boxers (owner, round 15)
        UNDERWEAR("underwear", 0x1A1A1E, 0x1C2233, 0x24262E, 0xD9D4C8, 0x3A3F48, 0x5B2B2B, 0x2E3A2A, 0x6B5A3A, 0x2B3A55, 0x8A8F96, 0x4B2E4E, 0xB8A27A),
        // TOP and BOTTOM: no longer used (clothes are gear), kept so the look packet keeps its shape.
        TOP("top", 0x1C2233, 0x1A1A20, 0x23293D, 0x2E3038, 0x3E7A2E, 0x5B2B2B, 0xD9D4C8, 0x6B5A3A, 0x2B3A55, 0x8A8F96, 0xC2702E, 0x3A2E4E),
        BOTTOM("bottom", 0x24262E, 0x1F2433, 0x2E3038, 0xD9D4C8, 0x3E7A2E, 0x3A2E24, 0x4B4F58, 0x2B3A55, 0x101014, 0x6B5A3A, 0x5B2B2B, 0x8A8F96);

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

    /** How much bigger the hair's outer layer is drawn (1 = as painted). */
    public float hairVolumeScale() {
        return 1f + 0.04f * get(Slot.HAIR_VOLUME);
    }

    /** A short text naming every choice: the client's skin cache key. */
    public String key() {
        StringBuilder text = new StringBuilder();
        for (int choice : choices) {
            text.append(Character.forDigit(choice, 36));
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
        for (Slot rare : new Slot[]{Slot.SCAR, Slot.MARKING, Slot.WEATHERING}) {
            if (random.nextFloat() < 0.6f) {
                look.set(rare, 0);
            }
        }
        if (random.nextFloat() < 0.85f) {
            look.set(Slot.HAIR, random.nextInt(Slot.HAIR.size() - 1)); // bald is rare
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

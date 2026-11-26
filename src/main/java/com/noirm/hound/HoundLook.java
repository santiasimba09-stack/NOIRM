package com.noirm.hound;

import net.minecraft.util.RandomSource;

import java.util.Locale;

/**
 * What a hound looks like, rolled once when you bond and packed into one int (so it's saved and
 * synced as a number, and the same hound always looks the same): its <b>breed</b> (body plan), its
 * <b>ears</b>, its <b>tail</b>, its coat <b>pattern</b>, a <b>base</b> and a <b>second</b> colour,
 * optional white <b>markings</b> and its <b>eyes</b>. The client builds the model from the breed,
 * ears and tail ({@code assets/noirm/hound/breeds/}) and colours the coat from the pattern and the
 * colours (masks painted by {@code tools/houndgen}).
 *
 * <p>Bits: breed 0–2, ears 3–4, tail 5–6, pattern 7–9, base colour 10–14, second colour 15–19,
 * markings 20–23, eyes 24–25.
 */
public record HoundLook(Breed breed, Ears ears, Tail tail, Pattern pattern, int base, int second, int marks, int eyes) {
    /** A body plan: its look comes from its recipe file; size and speed matter in play too. */
    public enum Breed {
        //        size  speed weight ears            tail
        WOLF(1.00f, 1.00f, 30, Ears.PRICKED, Tail.STRAIGHT),
        STOCKY(0.94f, 0.97f, 20, Ears.FOLDED, Tail.STRAIGHT),
        SIGHTHOUND(1.02f, 1.10f, 15, Ears.FOLDED, Tail.STRAIGHT),
        SPITZ(0.90f, 1.00f, 18, Ears.PRICKED, Tail.CURLED),
        MASTIFF(1.12f, 0.92f, 17, Ears.FLOPPY, Tail.BUSHY);

        public final float size;
        public final float speed;
        public final int weight;
        public final Ears ears;
        public final Tail tail;

        Breed(float size, float speed, int weight, Ears ears, Tail tail) {
            this.size = size;
            this.speed = speed;
            this.weight = weight;
            this.ears = ears;
            this.tail = tail;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Ears { PRICKED, FLOPPY, FOLDED;
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Tail { STRAIGHT, CURLED, BUSHY;
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Pattern { SOLID, SADDLE, MASK, SOCKS, SPOTTED, BRINDLE;
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Optional white markings (bits of {@link #marks}). */
    public static final int BLAZE = 1, CHEST = 2, TAIL_TIP = 4, BROWS = 8;
    public static final String[] MARK_KEYS = {"blaze", "chest", "tail_tip", "brows"};

    /** Coat colours: our own palette of dog and wolf shades. */
    public static final String[] COLOURS = {"white", "cream", "wheat", "fawn", "red", "ginger", "tan", "chestnut",
            "brown", "chocolate", "grey", "silver", "blue", "charcoal", "black", "sable"};
    public static final int[] RGB = {0xE8E4DA, 0xE0CFA8, 0xC9A86C, 0xB98A55, 0xA5582E, 0xC07A3A, 0xA8743E, 0x7A4424,
            0x6A4428, 0x4A2E1E, 0x8F8B86, 0xB4B2AD, 0x6E7680, 0x3E3C3A, 0x242221, 0x5A4632};
    /** How often each colour is the base coat. */
    private static final int[] BASE_WEIGHT = {10, 7, 6, 6, 4, 5, 6, 4, 6, 3, 9, 4, 3, 4, 7, 5};
    public static final String[] EYES = {"amber", "brown", "ice", "gold"};
    public static final int[] EYE_RGB = {0xC48A2A, 0x5A3418, 0x8CB8D0, 0xD8B040};

    public static final HoundLook DEFAULT = new HoundLook(Breed.WOLF, Ears.PRICKED, Tail.STRAIGHT, Pattern.SOLID, 0, 10, 0, 0);

    public int pack() {
        return breed.ordinal() | ears.ordinal() << 3 | tail.ordinal() << 5 | pattern.ordinal() << 7
                | (base & 31) << 10 | (second & 31) << 15 | (marks & 15) << 20 | (eyes & 3) << 24;
    }

    public static HoundLook unpack(int bits) {
        return new HoundLook(pick(Breed.values(), bits & 7), pick(Ears.values(), bits >> 3 & 3), pick(Tail.values(), bits >> 5 & 3),
                pick(Pattern.values(), bits >> 7 & 7), Math.min(COLOURS.length - 1, bits >> 10 & 31),
                Math.min(COLOURS.length - 1, bits >> 15 & 31), bits >> 20 & 15, bits >> 24 & 3);
    }

    private static <T> T pick(T[] values, int i) {
        return values[Math.min(values.length - 1, i)];
    }

    public HoundLook withBreed(Breed b) {
        return new HoundLook(b, ears, tail, pattern, base, second, marks, eyes);
    }

    public HoundLook withBase(int colour) {
        return new HoundLook(breed, ears, tail, pattern, Math.floorMod(colour, COLOURS.length), second, marks, eyes);
    }

    public HoundLook withPattern(Pattern p) {
        return new HoundLook(breed, ears, tail, p, base, second, marks, eyes);
    }

    /** Rolls a new look: breed by weight, its usual ears and tail most of the time, a fitting second colour. */
    public static HoundLook roll(RandomSource random) {
        Breed breed = weighted(random, Breed.values(), b -> b.weight);
        return roll(random, breed);
    }

    public static HoundLook roll(RandomSource random, Breed breed) {
        Ears ears = random.nextFloat() < 0.7f ? breed.ears : Ears.values()[random.nextInt(Ears.values().length)];
        Tail tail = random.nextFloat() < 0.7f ? breed.tail : Tail.values()[random.nextInt(Tail.values().length)];
        Pattern pattern = Pattern.values()[weightedIndex(random, new int[]{30, 18, 14, 14, 12, 12})];
        int base = weightedIndex(random, BASE_WEIGHT);
        int second = random.nextInt(COLOURS.length);
        if (second == base) {
            second = (base + 7) % COLOURS.length;
        }
        if (pattern == Pattern.SADDLE || pattern == Pattern.MASK || pattern == Pattern.BRINDLE) {
            // The darker shade goes on the back, the face or the stripes.
            if (luminance(second) > luminance(base)) {
                int t = second;
                second = base;
                base = t;
            }
            if (Math.abs(luminance(second) - luminance(base)) < 0.12f) {
                second = luminance(base) > 0.45f ? 14 : 1; // black on a light coat, cream on a dark one
            }
        }
        int marks = 0;
        for (int bit = 0; bit < 4; bit++) {
            if (random.nextFloat() < 0.22f) {
                marks |= 1 << bit;
            }
        }
        int eyes = weightedIndex(random, new int[]{5, 4, 1, 2});
        return new HoundLook(breed, ears, tail, pattern, base, second, marks, eyes);
    }

    /** 0 (black) – 1 (white). */
    public static float luminance(int colour) {
        int rgb = RGB[Math.floorMod(colour, RGB.length)];
        return (0.299f * (rgb >> 16 & 255) + 0.587f * (rgb >> 8 & 255) + 0.114f * (rgb & 255)) / 255f;
    }

    private static <T> T weighted(RandomSource random, T[] values, java.util.function.ToIntFunction<T> weight) {
        int[] w = new int[values.length];
        for (int i = 0; i < w.length; i++) {
            w[i] = weight.applyAsInt(values[i]);
        }
        return values[weightedIndex(random, w)];
    }

    private static int weightedIndex(RandomSource random, int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        int r = random.nextInt(Math.max(1, total));
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r < 0) {
                return i;
            }
        }
        return 0;
    }

    public static int colourByKey(String key) {
        for (int i = 0; i < COLOURS.length; i++) {
            if (COLOURS[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    public static Breed breedByKey(String key) {
        for (Breed b : Breed.values()) {
            if (b.key().equals(key)) {
                return b;
            }
        }
        return null;
    }
}

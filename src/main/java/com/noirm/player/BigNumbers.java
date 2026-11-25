package com.noirm.player;

import java.util.Locale;

/**
 * Big numbers for people to read. An Uzumaki's chakra pool runs into the tens of thousands, which
 * won't fit next to a HUD bar, so the HUD shows {@link #compact} ("12.4k") and chat shows
 * {@link #grouped} ("12,400"). Both work on either side (no client classes).
 */
public final class BigNumbers {
    private BigNumbers() {}

    /** "950", "2.5k", "12.4k", "124k", "1.2M": at most four characters plus the suffix. */
    public static String compact(float value) {
        long n = Math.round(value);
        long abs = Math.abs(n);
        if (abs < 1000) {
            return Long.toString(n);
        }
        if (abs < 1_000_000) {
            return scaled(n / 1000.0, "k");
        }
        return scaled(n / 1_000_000.0, "M");
    }

    /** "12,400": whole number with thousands separators. */
    public static String grouped(float value) {
        return String.format(Locale.ROOT, "%,d", Math.round(value));
    }

    private static String scaled(double v, String suffix) {
        double abs = Math.abs(v);
        // One decimal below 100 ("2.5k", "12.4k"), none above ("124k"); a trailing ".0" is dropped.
        String text = abs < 100 ? String.format(Locale.ROOT, "%.1f", Math.floor(v * 10) / 10) : Long.toString((long) Math.floor(v));
        if (text.endsWith(".0")) {
            text = text.substring(0, text.length() - 2);
        }
        return text + suffix;
    }
}

package com.noirm.hound;

import java.util.Locale;

/** What your hound does when there's no fight. */
public enum HoundOrder {
    /** Stay at your side (and catch up by jumping to you like a vanilla pet). */
    FOLLOW,
    /** Sit and stay (still defends itself). */
    SIT,
    /** Hold this spot and fight anything hostile that comes near it. */
    GUARD;

    public String key() {
        return "screen.noirm.hound.order." + name().toLowerCase(Locale.ROOT);
    }

    public static HoundOrder of(int ordinal) {
        HoundOrder[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }

    public static HoundOrder byKey(String key) {
        for (HoundOrder o : values()) {
            if (o.name().equalsIgnoreCase(key)) {
                return o;
            }
        }
        return null;
    }
}

package com.noirm.client.ui;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

/**
 * Eased hover amounts (0..1) for immediate-mode screens: each frame you ask "how hovered is element
 * {@code id}?" and it eases in when the mouse is over it and out when it leaves. Keys are ints
 * ({@link #key} mixes a few ints, e.g. a list name and a row), stored in primitive maps, so no
 * allocation once the elements have been seen. One tracker per screen.
 */
public final class HoverTracker {
    private final Int2FloatOpenHashMap amount = new Int2FloatOpenHashMap();
    private final Int2LongOpenHashMap last = new Int2LongOpenHashMap();

    /** Advances element {@code id} and returns its hover amount (eased). */
    public float get(int id, boolean hovered) {
        long now = Anim.now();
        float a = amount.getOrDefault(id, 0f);
        long then = last.containsKey(id) ? last.get(id) : now;
        float dt = Math.min(250f, now - then);
        float target = hovered ? 1f : 0f;
        a += (target - a) * Anim.approach(dt, Anim.HOVER_TAU_MS);
        if (Math.abs(target - a) < 0.01f) {
            a = target;
        }
        amount.put(id, a);
        last.put(id, now);
        return a;
    }

    /** A hover amount from the element's own rectangle. */
    public float get(int id, int mouseX, int mouseY, int x, int y, int w, int h) {
        return get(id, mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h);
    }

    /** Forget everything (e.g. when a screen's layout changes). */
    public void clear() {
        amount.clear();
        last.clear();
    }

    /** Mixes a few ints into one key. */
    public static int key(int a, int b) {
        return a * 0x9E3779B1 ^ (b + 0x7F4A7C15);
    }

    public static int key(int a, int b, int c) {
        return key(key(a, b), c);
    }
}

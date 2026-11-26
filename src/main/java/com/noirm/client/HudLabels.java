package com.noirm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * HUD captions, translated and upper-cased once (not every frame). Cleared when the language
 * changes.
 */
final class HudLabels {
    private static final Map<String, String> CACHE = new HashMap<>();
    private static String language = "";

    private HudLabels() {}

    /** The upper-case caption for a translation key. */
    static String get(String key) {
        String selected = Minecraft.getInstance().getLanguageManager().getSelected();
        if (!selected.equals(language)) {
            CACHE.clear();
            language = selected;
        }
        String label = CACHE.get(key);
        if (label == null) {
            label = Component.translatable(key).getString().toUpperCase(Locale.ROOT);
            CACHE.put(key, label);
        }
        return label;
    }
}

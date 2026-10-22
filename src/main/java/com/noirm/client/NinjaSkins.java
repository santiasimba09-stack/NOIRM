package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.entity.NinjaLook;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds ninja skins at runtime. A ninja's look key ({@link NinjaLook}) is decoded into features,
 * and the matching part layers under {@code textures/entity/ninja/parts/} are stacked into one
 * HD player skin by {@link SkinComposer}: gray parts are tinted (skin, hair, eyes, clothes),
 * coloured parts are drawn as they are. Each distinct key becomes one texture, shared by every
 * ninja with that look, cached (and released once more than {@link #MAX_CACHED} looks exist).
 */
public final class NinjaSkins {
    private static final int MAX_CACHED = 160; // HD skins: 256 KB of video memory each, ~40 MB at most
    private static final String PARTS = "textures/entity/ninja/parts/";
    /** Whatever the player model shows when a look can't be built. */
    public static final ResourceLocation FALLBACK = Noirm.vanillaId("textures/entity/player/wide/steve.png");

    private static final Map<String, ResourceLocation> TEXTURES = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
            if (size() > MAX_CACHED) {
                if (!eldest.getValue().equals(FALLBACK)) {
                    Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                }
                if (eldest.getValue().equals(lastBuilt)) {
                    lastBuilt = null;
                }
                return true;
            }
            return false;
        }
    };
    private static int counter;
    /** The newest skin built: shown for a frame or two while a new one waits for building time. */
    private static ResourceLocation lastBuilt;

    private NinjaSkins() {}

    /** The skin texture for a look key. */
    public static ResourceLocation texture(String key) {
        if (key.isEmpty()) {
            return FALLBACK;
        }
        ResourceLocation id = TEXTURES.get(key);
        if (id != null) {
            return id;
        }
        if (!SkinComposer.hasBudget() && lastBuilt != null) {
            return lastBuilt; // out of building time this frame: built next frame (shown as another ninja for a moment)
        }
        try {
            // (The texture is registered before this method returns, so the id is safe to use at once.)
            id = Noirm.id("dynamic/ninja_" + (counter++) + "_" + key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_"));
            if (!SkinComposer.compose(id, paths(layers(NinjaLook.decode(key))))) {
                id = FALLBACK;
            } else {
                lastBuilt = id;
            }
        } catch (Exception e) {
            Noirm.LOGGER.warn("Could not build the ninja skin {}", key, e);
            id = FALLBACK;
        }
        TEXTURES.put(key, id);
        return id;
    }

    private static List<Map.Entry<String, Integer>> paths(List<Map.Entry<String, Integer>> layers) {
        List<Map.Entry<String, Integer>> out = new ArrayList<>(layers.size());
        for (Map.Entry<String, Integer> layer : layers) {
            out.add(Map.entry(PARTS + layer.getKey(), layer.getValue()));
        }
        return out;
    }

    /** The part layers for a look, in drawing order, with their tint (or -1 for none). */
    public static List<Map.Entry<String, Integer>> layers(NinjaLook.Features f) {
        List<Map.Entry<String, Integer>> out = new ArrayList<>();
        out.add(Map.entry("skin", f.color("skin")));
        if (!f.get("mark").equals("none")) {
            out.add(Map.entry("mark_" + f.get("mark"), -1));
        }
        out.add(Map.entry("shirt_" + f.get("shirt"), f.color("shirt_color")));
        out.add(Map.entry("pants_" + f.get("pants"), f.color("pants_color")));
        if ((f.wraps() & 1) != 0) {
            out.add(Map.entry("leg_wraps", -1));
        }
        if ((f.wraps() & 2) != 0) {
            out.add(Map.entry("arm_wraps", -1));
        }
        out.add(Map.entry("sandals", -1));
        out.add(Map.entry("iris_" + f.get("eyes"), f.color("eye_color"))); // under the eye: lids, lashes, shine on top
        out.add(Map.entry("eyes_" + f.get("eyes"), -1));
        out.add(Map.entry("brows_" + f.get("brows"), f.color("hair_color")));
        out.add(Map.entry("hair_" + f.get("hair_style"), f.color("hair_color")));
        String accessory = f.get("accessory");
        if (!accessory.equals("none") && !accessory.isEmpty()) {
            out.add(Map.entry("acc_" + accessory, NinjaLook.tintedAccessory(accessory) ? f.color("accessory_color") : -1));
        }
        return out;
    }

    /** Colours a gray tint-layer value (see {@link SkinComposer#tint}). */
    public static int tint(int rgb, int grayValue) {
        return SkinComposer.tint(rgb, grayValue);
    }
}

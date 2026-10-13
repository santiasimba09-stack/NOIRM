package com.noirm.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.noirm.Noirm;
import com.noirm.entity.NinjaLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Builds ninja skins at runtime. A ninja's look key ({@link NinjaLook}) is decoded into features,
 * and the matching part layers under {@code textures/entity/ninja/parts/} are stacked into one
 * 64x64 player skin: gray parts are tinted (skin, hair, eyes, clothes), coloured parts are drawn as
 * they are. Each distinct key becomes one {@link DynamicTexture}, cached (and released once more
 * than {@link #MAX_CACHED} looks have been built).
 */
public final class NinjaSkins {
    private static final int MAX_CACHED = 400;
    private static final String PARTS = "textures/entity/ninja/parts/";
    /** Whatever the player model shows when a look can't be built. */
    public static final ResourceLocation FALLBACK = Noirm.vanillaId("textures/entity/player/wide/steve.png");

    private static final Map<String, ResourceLocation> TEXTURES = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
            if (size() > MAX_CACHED) {
                Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                return true;
            }
            return false;
        }
    };
    /** Loaded part images (null when a part is missing). */
    private static final Map<String, Optional<NativeImage>> PART_IMAGES = new HashMap<>();
    private static int counter;

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
        try {
            NativeImage image = compose(NinjaLook.decode(key));
            id = Noirm.id("dynamic/ninja_" + (counter++) + "_" + key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_"));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        } catch (Exception e) {
            Noirm.LOGGER.warn("Could not build the ninja skin {}", key, e);
            id = FALLBACK;
        }
        TEXTURES.put(key, id);
        return id;
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
        out.add(Map.entry("eyes_" + f.get("eyes"), -1));
        out.add(Map.entry("iris_" + f.get("eyes"), f.color("eye_color")));
        out.add(Map.entry("brows_" + f.get("brows"), f.color("hair_color")));
        out.add(Map.entry("hair_" + f.get("hair_style"), f.color("hair_color")));
        String accessory = f.get("accessory");
        if (!accessory.equals("none") && !accessory.isEmpty()) {
            out.add(Map.entry("acc_" + accessory, NinjaLook.tintedAccessory(accessory) ? f.color("accessory_color") : -1));
        }
        return out;
    }

    private static NativeImage compose(NinjaLook.Features f) {
        // Kept as straight RGBA ints while we work, then copied into the image.
        int[] out = new int[64 * 64];
        for (Map.Entry<String, Integer> layer : layers(f)) {
            Optional<NativeImage> part = part(layer.getKey());
            if (part.isEmpty()) {
                continue;
            }
            NativeImage src = part.get();
            int w = Math.min(64, src.getWidth());
            int h = Math.min(64, src.getHeight());
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int abgr = src.getPixelRGBA(x, y);
                    int a = abgr >>> 24;
                    if (a == 0) {
                        continue;
                    }
                    int r = abgr & 0xFF;
                    int g = (abgr >> 8) & 0xFF;
                    int b = (abgr >> 16) & 0xFF;
                    if (layer.getValue() >= 0) {
                        int c = tint(layer.getValue(), r);
                        r = (c >> 16) & 0xFF;
                        g = (c >> 8) & 0xFF;
                        b = c & 0xFF;
                    }
                    out[y * 64 + x] = blend(out[y * 64 + x], a, r, g, b);
                }
            }
        }
        NativeImage image = new NativeImage(64, 64, true);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int argb = out[y * 64 + x];
                int a = argb >>> 24;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                image.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return image;
    }

    /** Alpha-blends (a, r, g, b) over an ARGB pixel; same maths as pixlib.Canvas.blend. */
    private static int blend(int dst, int a, int r, int g, int b) {
        if (a >= 255) {
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        float sa = a / 255f;
        float da = (dst >>> 24) / 255f;
        float outA = sa + da * (1 - sa);
        if (outA <= 0) {
            return dst;
        }
        int or = (int) ((r * sa + ((dst >> 16) & 0xFF) * da * (1 - sa)) / outA);
        int og = (int) ((g * sa + ((dst >> 8) & 0xFF) * da * (1 - sa)) / outA);
        int ob = (int) ((b * sa + (dst & 0xFF) * da * (1 - sa)) / outA);
        return ((int) (outA * 255) << 24) | (or << 16) | (og << 8) | ob;
    }

    /**
     * Colours a gray tint-layer value: gray 128 is the base colour, every 40 levels one shading
     * step (the same rule as pixlib.tint / tone in the Python generators).
     */
    public static int tint(int rgb, int grayValue) {
        double k = (grayValue - 128) / 40.0;
        int r = tone((rgb >> 16) & 0xFF, k);
        int g = tone((rgb >> 8) & 0xFF, k);
        int b = tone(rgb & 0xFF, k);
        return (r << 16) | (g << 8) | b;
    }

    private static int tone(int v, double k) {
        if (k >= 0) {
            return Math.min(255, (int) (v * (1 + 0.13 * k) + 14 * k));
        }
        return Math.max(0, (int) (v * (1 + 0.2 * k)));
    }

    private static Optional<NativeImage> part(String name) {
        return PART_IMAGES.computeIfAbsent(name, n -> {
            ResourceLocation id = Noirm.id(PARTS + n + ".png");
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) {
                Noirm.LOGGER.warn("Missing ninja part {}", id);
                return Optional.empty();
            }
            try (InputStream in = resource.get().open()) {
                return Optional.of(NativeImage.read(in));
            } catch (Exception e) {
                Noirm.LOGGER.warn("Could not read ninja part {}", id, e);
                return Optional.empty();
            }
        });
    }
}

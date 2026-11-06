package com.noirm.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.noirm.Noirm;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds skins from part images, for {@link PlayerSkins} (players) and {@link NinjaSkins} (NPC
 * ninjas). The parts are HD (256×256 for the standard 64×64 skin layout; the game maps skins by
 * normalised UVs, so any size with that layout works) and are stacked bottom to top: gray parts
 * tinted with a colour ({@link #tint}), coloured parts drawn as they are.
 *
 * <p>Memory: each part is kept only as the rectangle where it has pixels (an eye part is a thin
 * strip of the face, not a whole 256×256 image), and a built skin lives only on the GPU: its pixels
 * are uploaded once and freed ({@link Composed}), 256 KB of video memory per skin. Building one
 * takes a few milliseconds, so at most {@link #BUDGET_NANOS} of building happens per frame; the
 * rest wait a frame or two ({@link #compose} returns null meanwhile).
 */
public final class SkinComposer {
    /** Building time allowed per ~frame (16 ms window). */
    private static final long BUDGET_NANOS = 4_000_000L;
    private static final long WINDOW_NANOS = 16_000_000L;

    /** A part image cropped to where it has pixels: ARGB values, row by row. */
    private record Cropped(int size, int x, int y, int w, int h, int[] argb) {
        static final Cropped EMPTY = new Cropped(0, 0, 0, 0, 0, new int[0]);
    }

    private static final Map<String, Optional<Cropped>> PARTS = new HashMap<>();
    private static long windowStart;
    private static long spent;

    private SkinComposer() {}

    /** Whether there's still time to build a skin this frame. */
    public static boolean hasBudget() {
        long now = Util.getNanos();
        if (now - windowStart > WINDOW_NANOS) {
            windowStart = now;
            spent = 0;
        }
        return spent < BUDGET_NANOS;
    }

    /**
     * Stacks the layers (resource path without ".png" → tint colour, or -1 for none) into one
     * texture registered as {@code id}. Returns false if nothing could be built (no parts).
     */
    public static boolean compose(ResourceLocation id, List<Map.Entry<String, Integer>> layers) {
        long start = Util.getNanos();
        try {
            int size = 0;
            for (Map.Entry<String, Integer> layer : layers) {
                Optional<Cropped> part = part(layer.getKey());
                if (part.isPresent()) {
                    size = Math.max(size, part.get().size());
                }
            }
            if (size == 0) {
                return false;
            }
            int[] out = new int[size * size];
            for (Map.Entry<String, Integer> layer : layers) {
                Optional<Cropped> found = part(layer.getKey());
                if (found.isEmpty() || found.get().w() == 0) {
                    continue;
                }
                draw(out, size, found.get(), layer.getValue());
            }
            NativeImage image = new NativeImage(size, size, true);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int argb = out[y * size + x];
                    int a = argb >>> 24;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    image.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }
            Minecraft.getInstance().getTextureManager().register(id, new Composed(image));
            return true;
        } finally {
            spent += Util.getNanos() - start;
        }
    }

    /** Draws one part over the canvas, scaled (nearest) if its size differs from the canvas. */
    private static void draw(int[] out, int size, Cropped part, int tint) {
        int[] lut = null;
        if (tint >= 0) {
            lut = new int[256];
            for (int g = 0; g < 256; g++) {
                lut[g] = tint(tint, g);
            }
        }
        int scaleNum = size;
        int scaleDen = part.size();
        int x0 = part.x() * scaleNum / scaleDen;
        int y0 = part.y() * scaleNum / scaleDen;
        int x1 = (part.x() + part.w()) * scaleNum / scaleDen;
        int y1 = (part.y() + part.h()) * scaleNum / scaleDen;
        for (int y = y0; y < y1; y++) {
            int sy = y * scaleDen / scaleNum - part.y();
            for (int x = x0; x < x1; x++) {
                int sx = x * scaleDen / scaleNum - part.x();
                int src = part.argb()[sy * part.w() + sx];
                int a = src >>> 24;
                if (a == 0) {
                    continue;
                }
                int rgb = lut != null ? lut[(src >> 16) & 0xFF] : src & 0xFFFFFF;
                out[y * size + x] = blend(out[y * size + x], a, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            }
        }
    }

    /** Alpha-blends (a, r, g, b) over an ARGB pixel (the same maths as the Python generators). */
    static int blend(int dst, int a, int r, int g, int b) {
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

    private static Optional<Cropped> part(String path) {
        return PARTS.computeIfAbsent(path, p -> {
            ResourceLocation id = Noirm.id(p + ".png");
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) {
                Noirm.LOGGER.warn("Missing skin part {}", id);
                return Optional.empty();
            }
            try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                return Optional.of(crop(image));
            } catch (Exception e) {
                Noirm.LOGGER.warn("Could not read skin part {}", id, e);
                return Optional.empty();
            }
        });
    }

    private static Cropped crop(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((image.getPixelRGBA(x, y) >>> 24) != 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return new Cropped(w, 0, 0, 0, 0, Cropped.EMPTY.argb());
        }
        int cw = maxX - minX + 1;
        int ch = maxY - minY + 1;
        int[] argb = new int[cw * ch];
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < cw; x++) {
                int abgr = image.getPixelRGBA(minX + x, minY + y);
                int a = abgr >>> 24;
                int r = abgr & 0xFF;
                int g = (abgr >> 8) & 0xFF;
                int b = (abgr >> 16) & 0xFF;
                argb[y * cw + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return new Cropped(w, minX, minY, cw, ch, argb);
    }

    /** A built skin: uploaded to the GPU once, then its pixels are freed. */
    static final class Composed extends AbstractTexture {
        Composed(NativeImage image) {
            if (RenderSystem.isOnRenderThreadOrInit()) {
                upload(image);
            } else {
                RenderSystem.recordRenderCall(() -> upload(image));
            }
        }

        private void upload(NativeImage image) {
            TextureUtil.prepareImage(getId(), image.getWidth(), image.getHeight());
            image.upload(0, 0, 0, true); // closes the image
        }

        @Override
        public void load(ResourceManager manager) {
            // built in code, nothing to load
        }
    }
}

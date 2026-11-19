package com.noirm.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.noirm.Noirm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.Optional;

/**
 * The hair strand texture ({@code textures/entity/hair/strands.png}, a gray tint layer drawn by
 * {@code tools/skingen/gen_hair.py}) coloured once per hair look with the skin parts' tint rule
 * ({@link SkinComposer#tint}), so black hair still gets its sheen and blond hair its dark roots.
 *
 * <p>Each coloured texture is twice as wide as the source: the left half is the hair as dyed (plain, or with
 * tips, roots, an ombre or highlights in the second colour), the right half the same strands all in the
 * second colour, for the locks {@link HairLayer} picks out (streaks, a split, coloured bangs). The baked
 * meshes put their u in the matching half. 512×256, 512 KB of video memory each; the oldest are released
 * past {@link #MAX_CACHED}.
 */
public final class HairTextures {
    private static final int MAX_CACHED = 32;
    private static final ResourceLocation SOURCE = Noirm.id("textures/entity/hair/strands.png");

    /** Dye patterns drawn into the texture's left half (PlayerLook's HAIR_DYE order; the rest pick locks). */
    static final int DYE_PLAIN = 0;
    static final int DYE_TIPS = 1;
    static final int DYE_ROOTS = 2;
    static final int DYE_OMBRE = 3;
    static final int DYE_HIGHLIGHTS = 4;

    private static int[] gray;   // ARGB with the gray value in the red channel
    private static int size;
    /** By look (colour, second colour, dye), oldest first (a primitive map: a lookup every frame allocates nothing). */
    private static final it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<ResourceLocation> TEXTURES =
            new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<>();

    private HairTextures() {}

    /** The strand texture in one colour (0xRRGGBB), or null if the source is missing. */
    public static ResourceLocation get(int rgb) {
        return get(rgb, rgb, DYE_PLAIN);
    }

    /** The strand texture for a hair look: the main colour, the second colour and the dye (HAIR_DYE index). */
    public static ResourceLocation get(int rgb, int rgb2, int dye) {
        rgb &= 0xFFFFFF;
        rgb2 &= 0xFFFFFF;
        int pattern = dye >= DYE_PLAIN && dye <= DYE_HIGHLIGHTS ? dye : DYE_PLAIN;
        long key = ((long) pattern << 48) | ((long) rgb2 << 24) | rgb;
        ResourceLocation id = TEXTURES.getAndMoveToLast(key);
        if (id != null) {
            return id;
        }
        if (TEXTURES.size() >= MAX_CACHED) {
            Minecraft.getInstance().getTextureManager().release(TEXTURES.removeFirst());
        }
        if (!loadSource()) {
            return null;
        }
        NativeImage image = new NativeImage(size * 2, size, false);
        int[] lut = new int[256];
        int[] lut2 = new int[256];
        for (int g = 0; g < 256; g++) {
            lut[g] = SkinComposer.tint(rgb, g);
            lut2[g] = SkinComposer.tint(rgb2, g);
        }
        for (int y = 0; y < size; y++) {
            float v = (y + 0.5f) / size; // root (top) .. tip (bottom)
            for (int x = 0; x < size; x++) {
                int src = gray[y * size + x];
                int a = src >>> 24;
                int g = (src >> 16) & 0xFF;
                int c = a == 0 ? 0 : mix(lut[g], lut2[g], amount(pattern, x, v));
                int c2 = a == 0 ? 0 : lut2[g];
                // NativeImage is ABGR
                image.setPixelRGBA(x, y, (a << 24) | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF));
                image.setPixelRGBA(x + size, y, (a << 24) | ((c2 & 0xFF) << 16) | (c2 & 0xFF00) | ((c2 >> 16) & 0xFF));
            }
        }
        id = Noirm.id(String.format("dynamic/hair_%06x_%06x_%d", rgb, rgb2, pattern));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        TEXTURES.put(key, id);
        return id;
    }

    /** How much of the second colour a pixel gets (0..1) for a dye pattern. */
    static float amount(int pattern, int x, float v) {
        return switch (pattern) {
            // a ragged edge: each strand column dips a little further or less
            case DYE_TIPS -> smooth(0.52f + jag(x) * 0.12f, 0.68f + jag(x) * 0.12f, v);
            case DYE_ROOTS -> 1f - smooth(0.2f + jag(x) * 0.08f, 0.34f + jag(x) * 0.08f, v);
            case DYE_OMBRE -> smooth(0.12f, 0.92f, v);
            // thin bright streaks: a few strand columns in each cell
            case DYE_HIGHLIGHTS -> {
                float f = x < 128 ? (x % 32) / 32f : ((x - 128) % 64) / 64f;
                yield (f >= 0.18f && f < 0.27f) || (f >= 0.62f && f < 0.7f) ? 0.85f : 0f;
            }
            default -> 0f;
        };
    }

    private static float jag(int x) {
        int h = x * 0x2F6B + 0x9E37;
        h ^= h >>> 7;
        h *= 0x45D9F3B;
        return ((h >>> 8) & 0xFF) / 255f;
    }

    private static float smooth(float e0, float e1, float v) {
        float t = Math.max(0f, Math.min(1f, (v - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }

    private static int mix(int a, int b, float t) {
        if (t <= 0f) {
            return a;
        }
        if (t >= 1f) {
            return b;
        }
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static boolean loadSource() {
        if (gray != null) {
            return gray.length > 0;
        }
        gray = new int[0];
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(SOURCE);
        if (resource.isEmpty()) {
            Noirm.LOGGER.warn("Missing hair texture {}", SOURCE);
            return false;
        }
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            size = image.getWidth();
            int[] out = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    out[y * size + x] = (abgr & 0xFF000000) | ((abgr & 0xFF) << 16);
                }
            }
            gray = out;
            return true;
        } catch (Exception e) {
            Noirm.LOGGER.warn("Could not read {}", SOURCE, e);
            return false;
        }
    }

    /** Forget everything (resource reload). */
    static void clear() {
        gray = null;
        TEXTURES.values().forEach(id -> Minecraft.getInstance().getTextureManager().release(id));
        TEXTURES.clear();
    }
}

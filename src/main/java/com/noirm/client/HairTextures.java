package com.noirm.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.noirm.Noirm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The hair strand texture ({@code textures/entity/hair/strands.png}, a gray tint layer drawn by
 * {@code tools/skingen/gen_hair.py}) coloured once per hair colour with the skin parts' tint rule
 * ({@link SkinComposer#tint}), so black hair still gets its sheen and blond hair its dark roots.
 * 256×256, 256 KB of video memory per colour; the oldest are released past {@link #MAX_CACHED}.
 */
public final class HairTextures {
    private static final int MAX_CACHED = 48;
    private static final ResourceLocation SOURCE = Noirm.id("textures/entity/hair/strands.png");

    private static int[] gray;   // ARGB with the gray value in the red channel
    private static int size;
    private static final Map<Integer, ResourceLocation> TEXTURES = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, ResourceLocation> eldest) {
            if (size() > MAX_CACHED) {
                Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                return true;
            }
            return false;
        }
    };

    private HairTextures() {}

    /** The strand texture in this colour (0xRRGGBB), or null if the source is missing. */
    public static ResourceLocation get(int rgb) {
        rgb &= 0xFFFFFF;
        ResourceLocation id = TEXTURES.get(rgb);
        if (id != null) {
            return id;
        }
        if (!loadSource()) {
            return null;
        }
        NativeImage image = new NativeImage(size, size, false);
        int[] lut = new int[256];
        for (int g = 0; g < 256; g++) {
            lut[g] = SkinComposer.tint(rgb, g);
        }
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int src = gray[y * size + x];
                int a = src >>> 24;
                int c = a == 0 ? 0 : lut[(src >> 16) & 0xFF];
                // NativeImage is ABGR
                image.setPixelRGBA(x, y, (a << 24) | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF));
            }
        }
        id = Noirm.id(String.format("dynamic/hair_%06x", rgb));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        TEXTURES.put(rgb, id);
        return id;
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

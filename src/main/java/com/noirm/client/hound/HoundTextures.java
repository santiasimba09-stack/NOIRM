package com.noirm.client.hound;

import com.mojang.blaze3d.platform.NativeImage;
import com.noirm.Noirm;
import com.noirm.hound.HoundLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Each hound's coat, mixed on the client from the atlas layers {@code tools/houndgen} paints
 * (textures/entity/hound/): fur light and shadow (young and old, blended by its fur age), the
 * countershading, its pattern's second-colour and white masks, its white markings, and the details
 * (eyes in its eye colour, nose, teeth, claws, the red scarf). One texture per look and fur age,
 * made once and kept until resources reload.
 */
public final class HoundTextures {
    private static final String DIR = "textures/entity/hound/";
    /** Fur age is rounded to this many steps (so a growing hound gets a new coat every few levels, not every frame). */
    public static final int AGE_STEPS = 6;
    private static final int CREAM = 0xF0E8D8;
    private static final int WHITE = 0xEEEAE2;

    private static final Map<String, NativeImage> LAYERS = new HashMap<>();
    private static final Map<Long, ResourceLocation> COATS = new HashMap<>();
    private static final Map<ResourceLocation, DynamicTexture> OWNED = new HashMap<>();

    private HoundTextures() {}

    /** The coat for {@code lookBits} at fur age {@code age} (0..1). */
    public static ResourceLocation get(int lookBits, float age) {
        int step = Math.round(Mth.clamp(age, 0f, 1f) * (AGE_STEPS - 1));
        long key = ((long) lookBits << 8) | step;
        ResourceLocation have = COATS.get(key);
        if (have != null) {
            return have;
        }
        ResourceLocation id = Noirm.id("hound_coat/" + Integer.toHexString(lookBits) + "_" + step);
        NativeImage img = compose(HoundLook.unpack(lookBits), step / (float) (AGE_STEPS - 1));
        if (img == null) {
            return Noirm.id(DIR + "fur_young.png");
        }
        DynamicTexture tex = new DynamicTexture(img);
        Minecraft.getInstance().getTextureManager().register(id, tex);
        OWNED.put(id, tex);
        COATS.put(key, id);
        return id;
    }

    /** Forgets every coat and layer (resources reloaded). */
    static void clear() {
        Minecraft mc = Minecraft.getInstance();
        OWNED.keySet().forEach(id -> mc.getTextureManager().release(id));
        OWNED.clear();
        COATS.clear();
        LAYERS.values().forEach(NativeImage::close);
        LAYERS.clear();
    }

    private static NativeImage layer(String name) {
        if (LAYERS.containsKey(name)) {
            return LAYERS.get(name);
        }
        NativeImage img = null;
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(Noirm.id(DIR + name + ".png"));
        if (res.isPresent()) {
            try (InputStream in = res.get().open()) {
                img = NativeImage.read(in);
            } catch (Exception e) {
                Noirm.LOGGER.warn("Couldn't read hound layer {}: {}", name, e.toString());
            }
        }
        LAYERS.put(name, img);
        return img;
    }

    // NativeImage pixels are packed ABGR.
    private static int r(int c) {
        return c & 255;
    }

    private static int g(int c) {
        return c >> 8 & 255;
    }

    private static int b(int c) {
        return c >> 16 & 255;
    }

    private static int a(int c) {
        return c >>> 24;
    }

    /** The mix (the same as {@code compose} in tools/houndgen/gen_hound.py). */
    static NativeImage compose(HoundLook look, float age) {
        NativeImage young = layer("fur_young");
        NativeImage old = layer("fur_old");
        NativeImage details = layer(age > 0.5f ? "details_old" : "details_young");
        if (young == null || old == null || details == null) {
            return null;
        }
        NativeImage pattern = look.pattern() == HoundLook.Pattern.SOLID ? null : layer("pattern_" + look.pattern().key());
        NativeImage[] marks = new NativeImage[HoundLook.MARK_KEYS.length];
        for (int i = 0; i < marks.length; i++) {
            if ((look.marks() & (1 << i)) != 0) {
                marks[i] = layer("marks_" + HoundLook.MARK_KEYS[i]);
            }
        }
        int w = young.getWidth();
        int h = young.getHeight();
        float[] base = rgb(HoundLook.RGB[look.base()]);
        float[] second = rgb(HoundLook.RGB[look.second()]);
        float[] cream = rgb(CREAM);
        float[] white = rgb(WHITE);
        float[] eye = rgb(HoundLook.EYE_RGB[look.eyes()]);
        float[] light = {base[0] * 0.55f + cream[0] * 0.45f, base[1] * 0.55f + cream[1] * 0.45f, base[2] * 0.55f + cream[2] * 0.45f};
        NativeImage out = new NativeImage(w, h, true);
        float[] col = new float[3];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int fy = young.getPixelRGBA(x, y);
                int fo = old.getPixelRGBA(x, y);
                int alpha = Math.max(a(fy), a(fo));
                if (alpha == 0) {
                    out.setPixelRGBA(x, y, 0);
                    continue;
                }
                float shade = Mth.lerp(age, r(fy), r(fo)) / 255f * 1.4f;
                float counter = Mth.lerp(age, g(fy), g(fo)) / 255f * 0.8f;
                for (int i = 0; i < 3; i++) {
                    col[i] = base[i] + (light[i] - base[i]) * counter;
                }
                float whiteAmt = 0f;
                if (pattern != null) {
                    int p = pattern.getPixelRGBA(x, y);
                    float s = r(p) / 255f;
                    for (int i = 0; i < 3; i++) {
                        col[i] += (second[i] - col[i]) * s;
                    }
                    whiteAmt = g(p) / 255f;
                }
                for (NativeImage m : marks) {
                    if (m != null) {
                        whiteAmt = Math.max(whiteAmt, g(m.getPixelRGBA(x, y)) / 255f);
                    }
                }
                for (int i = 0; i < 3; i++) {
                    col[i] += (white[i] - col[i]) * whiteAmt;
                    col[i] *= shade;
                }
                int d = details.getPixelRGBA(x, y);
                int da = a(d);
                if (da == 254) {
                    float gray = r(d) / 255f * 1.2f;
                    for (int i = 0; i < 3; i++) {
                        col[i] = eye[i] * gray;
                    }
                } else if (da > 0) {
                    float t = da / 255f;
                    col[0] += (r(d) / 255f - col[0]) * t;
                    col[1] += (g(d) / 255f - col[1]) * t;
                    col[2] += (b(d) / 255f - col[2]) * t;
                }
                int rr = Mth.clamp(Math.round(col[0] * 255f), 0, 255);
                int gg = Mth.clamp(Math.round(col[1] * 255f), 0, 255);
                int bb = Mth.clamp(Math.round(col[2] * 255f), 0, 255);
                out.setPixelRGBA(x, y, 0xFF000000 | bb << 16 | gg << 8 | rr);
            }
        }
        return out;
    }

    private static float[] rgb(int c) {
        return new float[]{(c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f};
    }
}

package com.noirm.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.noirm.Noirm;
import com.noirm.creator.PlayerLook;
import com.noirm.creator.PlayerLook.Slot;
import com.noirm.creator.PlayerLook.Tone;
import com.noirm.player.ShinobiCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.Player;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Player skins built from a {@link PlayerLook}: the part images are stacked into one 64×64 skin
 * (gray parts tinted with the look's colours, coloured parts drawn as they are), the same way
 * {@link NinjaSkins} builds NPC ninjas. Every distinct look becomes one {@link DynamicTexture},
 * cached; the oldest are released once more than {@link #MAX_CACHED} exist (the creation screen
 * makes a new one for each change you try).
 *
 * <p>A mixin on {@code AbstractClientPlayer.getSkinTextureLocation} asks {@link #override} first,
 * so the look shows everywhere the skin does: the body, first-person arms and hands.
 */
public final class PlayerSkins {
    private static final int MAX_CACHED = 48;
    private static final String PLAYER = "textures/entity/player_parts/";
    private static final String NINJA = "textures/entity/ninja/parts/";

    private static final Map<String, ResourceLocation> TEXTURES = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
            if (size() > MAX_CACHED) {
                Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                return true;
            }
            return false;
        }
    };
    private static final Map<String, Optional<NativeImage>> PART_IMAGES = new HashMap<>();
    private static int counter;

    private PlayerSkins() {}

    /** The look this player is drawn with: the creation screen's draft for yourself while it's open. */
    public static PlayerLook lookFor(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player && mc.screen instanceof CreatorScreen screen) {
            return screen.draft();
        }
        return ShinobiCapability.get(player).map(d -> d.creator().look()).orElse(null);
    }

    /** The skin to draw instead of the account skin, or null to keep the account skin. */
    public static ResourceLocation override(AbstractClientPlayer player) {
        PlayerLook look = lookFor(player);
        return look == null ? null : texture(look);
    }

    /** How tall to draw this player (the draft's height on the creation screen). */
    public static float renderHeight(Player player) {
        PlayerLook look = lookFor(player);
        return look == null ? 1f : look.height();
    }

    public static ResourceLocation texture(PlayerLook look) {
        String key = look.key();
        ResourceLocation id = TEXTURES.get(key);
        if (id != null) {
            return id;
        }
        try {
            NativeImage image = compose(look);
            id = Noirm.id("dynamic/player_look_" + (counter++));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        } catch (Exception e) {
            Noirm.LOGGER.warn("Could not build the player skin {}", key, e);
            id = NinjaSkins.FALLBACK;
        }
        TEXTURES.put(key, id);
        return id;
    }

    /** The part layers, bottom to top: (texture path, tint colour or -1). */
    public static List<Map.Entry<String, Integer>> layers(PlayerLook look) {
        List<Map.Entry<String, Integer>> out = new ArrayList<>();
        int skin = look.color(Tone.SKIN);
        int hair = look.color(Tone.HAIR);
        out.add(Map.entry(PLAYER + "skin_base", skin));
        out.add(Map.entry(PLAYER + "jaw_" + look.id(Slot.JAW), skin));
        out.add(Map.entry(PLAYER + "nose_" + look.id(Slot.NOSE), skin));
        out.add(Map.entry(PLAYER + "mouth_" + look.id(Slot.MOUTH), skin));
        if (!look.id(Slot.MUSCLE).equals("none")) {
            out.add(Map.entry(PLAYER + "muscle_" + look.id(Slot.MUSCLE), skin));
        }
        if (!look.id(Slot.WEATHERING).equals("none")) {
            out.add(Map.entry(PLAYER + "weather_" + look.id(Slot.WEATHERING), -1));
        }
        switch (look.id(Slot.SCAR)) {
            case "none" -> { }
            case "eye" -> out.add(Map.entry(NINJA + "mark_scar_eye", -1));
            case "nose" -> out.add(Map.entry(NINJA + "mark_scar_nose", -1));
            default -> out.add(Map.entry(PLAYER + "scar_" + look.id(Slot.SCAR), -1));
        }
        if (!look.id(Slot.MARKING).equals("none")) {
            out.add(Map.entry(NINJA + "mark_" + look.id(Slot.MARKING), -1));
        }
        out.add(Map.entry(PLAYER + "under_" + look.id(Slot.UNDERWEAR), look.color(Tone.UNDERWEAR)));
        if (!look.id(Slot.BOTTOM).equals("none")) {
            out.add(Map.entry(NINJA + "pants_" + look.id(Slot.BOTTOM), look.color(Tone.BOTTOM)));
        }
        if (!look.id(Slot.TOP).equals("none")) {
            out.add(Map.entry(NINJA + "shirt_" + look.id(Slot.TOP), look.color(Tone.TOP)));
        }
        String wraps = look.id(Slot.WRAPS);
        if (wraps.equals("legs") || wraps.equals("both")) {
            out.add(Map.entry(NINJA + "leg_wraps", -1));
        }
        if (wraps.equals("arms") || wraps.equals("both")) {
            out.add(Map.entry(NINJA + "arm_wraps", -1));
        }
        if (look.id(Slot.FEET).equals("sandals")) {
            out.add(Map.entry(NINJA + "sandals", -1));
        }
        out.add(Map.entry(NINJA + "eyes_" + look.id(Slot.EYES), -1));
        out.add(Map.entry(NINJA + "iris_" + look.id(Slot.EYES), look.color(Tone.EYE)));
        out.add(Map.entry(NINJA + "brows_" + look.id(Slot.BROWS), hair));
        if (!look.id(Slot.HAIR).equals("none")) {
            out.add(Map.entry(NINJA + "hair_" + look.id(Slot.HAIR), hair));
        }
        if (!look.id(Slot.HAIR_LENGTH).equals("as_styled") && !look.id(Slot.HAIR).equals("none")) {
            out.add(Map.entry(PLAYER + "hairlen_" + look.id(Slot.HAIR_LENGTH), hair));
        }
        return out;
    }

    private static NativeImage compose(PlayerLook look) {
        int[] out = new int[64 * 64];
        for (Map.Entry<String, Integer> layer : layers(look)) {
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
                        int c = NinjaSkins.tint(layer.getValue(), r);
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

    /** Alpha-blends (a, r, g, b) over an ARGB pixel (the same maths as NinjaSkins and pixlib). */
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

    private static Optional<NativeImage> part(String path) {
        return PART_IMAGES.computeIfAbsent(path, p -> {
            ResourceLocation id = Noirm.id(p + ".png");
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) {
                Noirm.LOGGER.warn("Missing player part {}", id);
                return Optional.empty();
            }
            try (InputStream in = resource.get().open()) {
                return Optional.of(NativeImage.read(in));
            } catch (Exception e) {
                Noirm.LOGGER.warn("Could not read player part {}", id, e);
                return Optional.empty();
            }
        });
    }
}

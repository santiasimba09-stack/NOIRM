package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.creator.PlayerLook;
import com.noirm.creator.PlayerLook.Slot;
import com.noirm.creator.PlayerLook.Tone;
import com.noirm.player.ShinobiCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Player skins built from a {@link PlayerLook}: the HD part images are stacked into one skin by
 * {@link SkinComposer} (gray parts tinted with the look's colours, coloured parts drawn as they
 * are), the same way {@link NinjaSkins} builds NPC ninjas. A look is the body in underwear plus face
 * and hair: clothes are gear worn on top. Every distinct look becomes one texture, cached; the
 * oldest are released once more than {@link #MAX_CACHED} exist (the creation screen makes a new one
 * for each change you try).
 *
 * <p>A mixin on {@code AbstractClientPlayer.getSkinTextureLocation} asks {@link #override} first,
 * so the look shows everywhere the skin does: the body, first-person arms and hands.
 */
public final class PlayerSkins {
    private static final int MAX_CACHED = 32;
    private static final String PLAYER = "textures/entity/player_parts/";
    private static final String NINJA = "textures/entity/ninja/parts/";

    private static final Map<String, ResourceLocation> TEXTURES = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
            if (size() > MAX_CACHED) {
                if (!eldest.getValue().equals(NinjaSkins.FALLBACK)) {
                    Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                }
                return true;
            }
            return false;
        }
    };
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
            id = Noirm.id("dynamic/player_look_" + (counter++));
            if (!SkinComposer.compose(id, layers(look))) {
                id = NinjaSkins.FALLBACK;
            }
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
        // No clothes: characters start in their underwear, and clothes are gear worn on top.
        String wraps = look.id(Slot.WRAPS);
        if (wraps.equals("legs") || wraps.equals("both")) {
            out.add(Map.entry(NINJA + "leg_wraps", -1));
        }
        if (wraps.equals("arms") || wraps.equals("both")) {
            out.add(Map.entry(NINJA + "arm_wraps", -1));
        }
        out.add(Map.entry(NINJA + "iris_" + look.id(Slot.EYES), look.color(Tone.EYE))); // under the eye's lids and shine
        out.add(Map.entry(NINJA + "eyes_" + look.id(Slot.EYES), -1));
        out.add(Map.entry(NINJA + "brows_" + look.id(Slot.BROWS), hair));
        if (!look.id(Slot.HAIR).equals("none")) {
            out.add(Map.entry(NINJA + "hair_" + look.id(Slot.HAIR), hair));
        }
        if (!look.id(Slot.HAIR_LENGTH).equals("as_styled") && !look.id(Slot.HAIR).equals("none")) {
            out.add(Map.entry(PLAYER + "hairlen_" + look.id(Slot.HAIR_LENGTH), hair));
        }
        return out;
    }
}

package com.noirm.client;

import com.noirm.Noirm;
import com.noirm.creator.LookLayers;
import com.noirm.creator.PlayerLook;
import com.noirm.player.ShinobiCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

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

    /** The part layers, bottom to top: (texture path, tint colour or -1). See {@link LookLayers}. */
    public static List<Map.Entry<String, Integer>> layers(PlayerLook look) {
        return LookLayers.layers(look);
    }
}

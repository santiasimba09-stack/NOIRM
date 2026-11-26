package com.noirm.creator;

import com.noirm.creator.PlayerLook.Slot;
import com.noirm.creator.PlayerLook.Tone;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Which skin part images a {@link PlayerLook} is built from, bottom to top: each a texture path and a tint
 * colour (or -1 for a part drawn in its own colours). The client stacks them into the player's skin
 * ({@code client/PlayerSkins}, {@code SkinComposer}); this class has no client code so the game tests can check
 * that a look saved before the creator wave still builds exactly the same skin.
 */
public final class LookLayers {
    private static final String PLAYER = "textures/entity/player_parts/";
    private static final String NINJA = "textures/entity/ninja/parts/";

    private LookLayers() {}

    /** The part layers, bottom to top: (texture path, tint colour or -1). */
    public static List<Map.Entry<String, Integer>> layers(PlayerLook look) {
        List<Map.Entry<String, Integer>> out = new ArrayList<>();
        int skin = look.color(Tone.SKIN);
        int hair = look.color(Tone.HAIR);
        out.add(Map.entry(PLAYER + "skin_base", skin));
        out.add(Map.entry(PLAYER + "jaw_" + look.id(Slot.JAW), skin));
        out.add(Map.entry(PLAYER + "nose_" + look.id(Slot.NOSE), skin));
        out.add(Map.entry(PLAYER + "mouth_" + look.id(Slot.MOUTH), skin));
        boolean feminine = look.id(Slot.FRAME).equals("feminine");
        if (feminine) {
            // the feminine frame: the body redrawn with a softer chest and waist, at the chosen muscle level
            out.add(Map.entry(PLAYER + "fem_" + look.id(Slot.MUSCLE), skin));
        } else if (!look.id(Slot.MUSCLE).equals("none")) {
            out.add(Map.entry(PLAYER + "muscle_" + look.id(Slot.MUSCLE), skin));
        }
        if (!look.id(Slot.BODY).equals("average")) {
            out.add(Map.entry(PLAYER + "build_" + look.id(Slot.BODY), skin)); // the build's shading (width: BodyShape)
        }
        int paint = look.color(Tone.PAINT);
        if (!look.id(Slot.TATTOO).equals("none")) {
            out.add(Map.entry(PLAYER + "tattoo_" + look.id(Slot.TATTOO), paint)); // ink in the skin, under grime and scars
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
        String detail = look.id(Slot.EYE_DETAIL);
        if (!detail.equals("none")) {
            // shading details (bags, circles, lines) take the skin tone; red liner and tear mark are their own colour
            boolean coloured = detail.equals("red_liner") || detail.equals("tear_mark");
            out.add(Map.entry(PLAYER + "eye_" + detail, coloured ? -1 : skin));
        }
        if (!look.id(Slot.FACE_PAINT).equals("none")) {
            out.add(Map.entry(PLAYER + "paint_" + look.id(Slot.FACE_PAINT), paint));
        }
        out.add(Map.entry(PLAYER + "under_" + look.id(Slot.UNDERWEAR), look.color(Tone.UNDERWEAR)));
        if (feminine) {
            out.add(Map.entry(PLAYER + "under_top", look.color(Tone.UNDERWEAR))); // a black top with the boxers
        }
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
        if (!look.id(Slot.BROWS).equals("none")) {
            out.add(Map.entry(NINJA + "brows_" + look.id(Slot.BROWS), hair));
        }
        // the scalp under the 3D hair (HairLayer draws the hair itself, and its length)
        String scalp = HairStyles.scalp(look.id(Slot.HAIR));
        if (scalp != null) {
            out.add(Map.entry(NINJA + "scalp_" + scalp, hair));
        }
        return out;
    }
}

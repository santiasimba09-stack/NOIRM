package com.noirm.client.hound;

import com.noirm.hound.HoundGrowth;
import com.noirm.hound.HoundLook;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds a hound's jointed body for its breed, ears, tail and <b>level</b> (docs/INUZUKA.md, "The
 * model"). The breed file gives the adult's proportions; the puppy recipe is blended into them as
 * the hound levels up ({@link HoundGrowth#adulthood}: legs shoot up first, it grows into its head
 * last), then age adds the fur (ruff, mane, tufts), the fangs and, from level 12, the clan scarf.
 *
 * <p>Everything is designed in <i>design space</i> (model pixels, y up, the ground at 0, the head
 * toward -z) and converted to Minecraft model space (y down, the ground at 24) as bones are made.
 */
public final class HoundRigBuilder {
    /** A recipe: named numbers (scalars are length-1 arrays). */
    public static final class Recipe {
        final Map<String, float[]> v = new HashMap<>();

        float f(String k) {
            float[] a = v.get(k);
            return a == null ? 0f : a[0];
        }

        float[] t(String k) {
            float[] a = v.get(k);
            return a == null ? new float[]{1, 1, 1} : a;
        }

        void put(String k, float... a) {
            v.put(k, a);
        }

        Recipe copy() {
            Recipe r = new Recipe();
            v.forEach((k, a) -> r.v.put(k, a.clone()));
            return r;
        }
    }

    private static final String[] AGE_KEYS = {"ruff", "tufts", "fang", "mane_back"};
    private static final String[] HEAD_KEYS = {"skull", "snout", "jaw", "ear", "ear_tip", "ear_flop", "brow", "ear_hang"};

    private HoundRigBuilder() {}

    // ================================================================ the recipe for a level

    /** The body numbers for one hound at {@code level}. */
    public static Recipe recipe(HoundShapes.Shapes shapes, HoundLook look, int level) {
        Recipe adult = shapes.adult(look.breed());
        Recipe puppy = shapes.puppy(look.breed());
        adult = shapes.withParts(adult, look, false);
        puppy = shapes.withParts(puppy, look, true);
        float t = HoundGrowth.adulthood(level);
        float legs = HoundGrowth.legGrowth(level);
        float head = HoundGrowth.headGrowth(level);
        Recipe r = new Recipe();
        for (Map.Entry<String, float[]> e : adult.v.entrySet()) {
            String k = e.getKey();
            if (isAgeKey(k)) {
                continue;
            }
            float tt = k.startsWith("f_") || k.startsWith("b_") ? legs : isHeadKey(k) ? head : t;
            float[] a = puppy.v.getOrDefault(k, e.getValue());
            float[] b = e.getValue();
            float[] out = new float[b.length];
            for (int i = 0; i < b.length; i++) {
                out[i] = Mth.lerp(tt, a[Math.min(i, a.length - 1)], b[i]);
            }
            r.v.put(k, out);
        }
        r.put("tail_n", Math.round(Mth.lerp(t, puppy.f("tail_n"), adult.f("tail_n"))));
        float ruffAge = Mth.clamp((level - 3) / 9f, 0f, 1f) + 0.8f * Mth.clamp((level - 12) / 18f, 0f, 1f);
        r.put("ruff", adult.f("ruff") * ruffAge);
        int tuftAge = level >= 30 ? 3 : level >= 20 ? 2 : level >= 12 ? 1 : 0;
        r.put("tufts", tuftAge > 0 ? Math.min(adult.f("tufts") + tuftAge - 1, 3) : 0);
        r.put("scarf", level >= 12 ? 1 : 0);
        r.put("fang", adult.f("fang") * (Mth.clamp((level - 4) / 8f, 0f, 1f) + 0.6f * Mth.clamp((level - 12) / 18f, 0f, 1f)));
        r.put("mane_back", adult.f("mane_back") * (Mth.clamp((level - 8) / 6f, 0f, 1f) + Mth.clamp((level - 12) / 18f, 0f, 1f)));
        float m = HoundGrowth.mass(level);
        for (String k : new String[]{"chest", "hips", "neck_th", "f_th", "b_th", "f_paw", "b_paw"}) {
            float[] a = r.v.get(k);
            if (a != null) {
                for (int i = 0; i < a.length; i++) {
                    a[i] *= m;
                }
            }
        }
        // Heavier in front with age: a broad chest under the mane.
        float k = Mth.clamp((level - 12) / 18f, 0f, 1f);
        float[] c = r.t("chest");
        r.put("chest", c[0] * (1 + 0.05f * k), c[1] * (1 + 0.04f * k), c[2]);
        return r;
    }

    private static boolean isAgeKey(String k) {
        for (String a : AGE_KEYS) {
            if (a.equals(k)) {
                return true;
            }
        }
        return k.equals("tail_n");
    }

    private static boolean isHeadKey(String k) {
        for (String a : HEAD_KEYS) {
            if (a.equals(k)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ building the bones

    /** What the builder produces: the root bone, the bones by name, and the numbers poses need. */
    public record Built(Bone root, Map<String, Bone> bones, float shoulderPx, float hipHeight, float bodyLength, float thigh, float shin) {}

    private static final class Ctx {
        final Map<String, Bone> bones = new HashMap<>();
        final HoundAtlas atlas;

        Ctx(HoundAtlas atlas) {
            this.atlas = atlas;
        }

        /** A bone at design offset (x, y, z) from its parent, rest rotation (design degrees). */
        Bone part(String name, String parent, float x, float y, float z, float rx, float ry, float rz) {
            Bone b = new Bone(name, x, -y + (parent == null ? 24f : 0f), z,
                    -rx * Mth.DEG_TO_RAD, ry * Mth.DEG_TO_RAD, -rz * Mth.DEG_TO_RAD);
            if (parent != null) {
                bones.get(parent).children.add(b);
            }
            bones.put(name, b);
            return b;
        }

        Bone part(String name, String parent, float x, float y, float z) {
            return part(name, parent, x, y, z, 0, 0, 0);
        }

        /** A box from its low corner (design space, relative to the pivot) of size (w, h, d). */
        void box(String bone, float fx, float fy, float fz, float w, float h, float d, String role) {
            w = snap(w);
            h = snap(h);
            d = snap(d);
            bones.get(bone).box(fx, -(fy + h), fz, w, h, d, atlas.uv(role));
        }

        void centered(String bone, float cx, float cy, float cz, float w, float h, float d, String role) {
            w = snap(w);
            h = snap(h);
            d = snap(d);
            box(bone, cx - w / 2, cy - h / 2, cz - d / 2, w, h, d, role);
        }
    }

    static float snap(float v) {
        return Math.max(0.25f, Math.round(v * 4f) / 4f);
    }

    public static Built build(Recipe r, HoundAtlas atlas, boolean twoHeads) {
        Ctx g = new Ctx(atlas);
        float[] chest = r.t("chest");
        float[] hips = r.t("hips");
        float cw = chest[0], ch = chest[1], cd = chest[2];
        float hw = hips[0], hh = hips[1], hd = hips[2];
        float[] fPaw = r.t("f_paw");
        float[] bPaw = r.t("b_paw");
        float fLen = r.f("f_upper") + r.f("f_lower") + fPaw[1] * 0.5f;
        float aThigh = 22, aShin = -48, aMeta = 26;
        float bDrop = (float) (r.f("b_thigh") * Math.cos(Math.toRadians(aThigh)) + r.f("b_shin") * Math.cos(Math.toRadians(aThigh + aShin))
                + r.f("b_meta") * Math.cos(Math.toRadians(aThigh + aShin + aMeta)) + bPaw[1] * 0.5f);
        float shoulderY = fLen;
        float k = (shoulderY - 0.25f) / Math.max(0.5f, bDrop);
        float bThigh = r.f("b_thigh") * k, bShin = r.f("b_shin") * k, bMeta = r.f("b_meta") * k;
        float hipY = shoulderY - 0.25f;
        float torso = cd + hd - 1f;
        float chestZ = -torso / 2 + cd / 2;
        float hipsZ = torso / 2 - hd / 2;
        float hipJointZ = hipsZ + hd * 0.2f;

        g.part("root", null, 0, 0, 0);
        g.part("body", "root", 0, hipY, hipJointZ);
        // Design (y, z) relative to the body pivot.
        float chestTop = shoulderY + ch * 0.32f;
        float chestCy = chestTop - ch / 2;
        g.part("chest", "body", 0, chestCy - hipY, chestZ - hipJointZ);
        g.centered("chest", 0, 0, 0, cw, ch, cd, "chest");
        float hipsTop = Math.min(hipY + hh * 0.5f, chestTop - 0.4f);
        float hipsCy = hipsTop - hh / 2;
        g.part("hips", "body", 0, hipsCy - hipY, hipsZ - hipJointZ);
        g.centered("hips", 0, 0, 0, hw, hh, hd, "hips");
        if (r.f("belly_drop") > 0.5f) {
            g.centered("hips", 0, -hh / 2 + 0.4f, -hd / 2 + 1f, hw * 0.85f, 1.25f, 2.5f, "belly");
        }
        float mb = r.f("mane_back");
        if (mb > 0.2f) {
            g.centered("chest", 0, ch / 2 - 0.3f + mb * 0.25f, 0.5f, cw + mb * 0.8f, 1f + mb * 0.8f, cd * 0.75f, "mane");
        }
        // Front legs: upper arm, forearm and paw.
        float fTh = r.f("f_th");
        for (int s = 0; s < 2; s++) {
            String side = s == 0 ? "l" : "r";
            float sx = s == 0 ? 1 : -1;
            // Set a little inside the chest, so the shoulder doesn't stand out like a plate.
            float x = sx * (cw / 2 - fTh / 2 - 0.2f);
            String up = "leg_f" + side;
            g.part(up, "body", x, shoulderY - hipY, chestZ - cd * 0.18f - hipJointZ, 6, 0, 0);
            g.box(up, -fTh / 2, -r.f("f_upper"), -fTh / 2 - 0.25f, fTh, r.f("f_upper") + 0.5f, fTh + 0.5f, "leg");
            String lo = up + "_lower";
            g.part(lo, up, 0, -r.f("f_upper"), 0, -6, 0, 0);
            float th = fTh * 0.88f;
            g.box(lo, -th / 2, -r.f("f_lower"), -th / 2, th, r.f("f_lower"), th, "leg_lower");
            g.box(lo, -fPaw[0] / 2, -r.f("f_lower") - fPaw[1] * 0.5f, -fPaw[2] + th / 2 + 0.1f, fPaw[0], fPaw[1], fPaw[2], "paw");
            if (r.f("tufts") >= 2) {
                g.box(up, -fTh / 2 + 0.25f, -r.f("f_upper") + 0.5f, fTh / 2 + 0.25f, fTh - 0.5f, 2f, 0.75f, "tuft");
            }
        }
        // Back legs: thigh (forward), shin (back), hock (down), paw.
        float bTh = r.f("b_th");
        for (int s = 0; s < 2; s++) {
            String side = s == 0 ? "l" : "r";
            float sx = s == 0 ? 1 : -1;
            float x = sx * (hw / 2 - bTh / 2 - 0.15f);
            String up = "leg_b" + side;
            g.part(up, "body", x, 0, 0, aThigh, 0, 0);
            g.box(up, -bTh / 2, -bThigh, -bTh / 2 - 0.5f, bTh, bThigh + 1f, bTh + 1f, "thigh");
            String sh = up + "_lower";
            g.part(sh, up, 0, -bThigh, 0.25f, aShin, 0, 0);
            float th2 = bTh * 0.78f;
            g.box(sh, -th2 / 2, -bShin, -th2 / 2, th2, bShin + 0.25f, th2, "leg");
            String ft = up + "_foot";
            g.part(ft, sh, 0, -bShin, 0, aMeta, 0, 0);
            float th3 = bTh * 0.66f;
            g.box(ft, -th3 / 2, -bMeta, -th3 / 2, th3, bMeta + 0.25f, th3, "leg_lower");
            String paw = "paw_b" + side;
            g.part(paw, ft, 0, -bMeta, 0, -(aThigh + aShin + aMeta), 0, 0);
            g.box(paw, -bPaw[0] / 2, -bPaw[1] * 0.5f, -bPaw[2] + th3 / 2 + 0.1f, bPaw[0], bPaw[1], bPaw[2], "paw");
        }
        // Neck and head.
        float neckBaseY = chestTop - 1f;
        float neckZ = chestZ - cd / 2 + 1.25f;
        float na = r.f("neck_angle");
        float nt = r.f("neck_th");
        float nl = r.f("neck_len");
        float neckX = twoHeads ? nt * 0.62f : 0f;
        g.part("neck", "body", neckX, neckBaseY - hipY, neckZ - hipJointZ, -na, 0, twoHeads ? 16 : 0);
        g.box("neck", -nt / 2, -0.5f, -nt / 2, nt, nl + 1f, nt, "neck");
        if (r.f("scarf") > 0.5f) {
            g.box("neck", -nt / 2 - 0.4f, 0.2f, -nt / 2 - 0.4f, nt + 0.8f, 1.5f, nt + 0.8f, "scarf");
            g.box("neck", -0.9f, 0f, nt / 2 + 0.2f, 1.8f, 1.8f, 0.9f, "scarf_knot");
        }
        float rf = r.f("ruff");
        if (rf > 0.1f) {
            g.box("neck", -nt / 2 - 0.3f * rf, -0.5f, -nt / 2 - 0.6f * rf, nt + 0.6f * rf, nl * 0.8f + 0.8f, 0.9f * rf + 0.5f, "ruff");
            g.box("neck", -nt / 2 - 0.45f * rf, nl * 0.25f, -nt / 2 + 0.25f, nt + 0.9f * rf, nl * 0.75f + 0.5f, nt * 0.8f, "ruff");
        }
        g.part("head", "neck", 0, nl, 0, na, twoHeads ? -12 : 0, twoHeads ? -16 : 0);
        head(g, "head", "", r);
        if (twoHeads) {
            // The Two-Headed Wolf: a second neck and head beside the first.
            g.part("neck2", "body", -nt * 0.62f, neckBaseY - hipY - 0.25f, neckZ - hipJointZ + 0.5f, -na + 5, 0, -16);
            g.box("neck2", -nt / 2, -0.5f, -nt / 2, nt, nl + 1f, nt, "neck");
            g.box("neck2", -nt / 2 - 0.3f, -0.5f, -nt / 2 - 0.6f, nt + 0.6f, nl * 0.8f + 0.8f, 1.2f, "ruff");
            g.part("head2", "neck2", 0, nl, 0, na - 5, 12, 16);
            head(g, "head2", "2", r);
        }
        // Tail: segments from the top of the hips.
        float tt = r.f("tail_th");
        float tl = r.f("tail_len");
        g.part("tail", "body", 0, hipsTop - 0.75f - hipY, hipsZ + hd / 2 - 0.25f - hipJointZ, 90 - r.f("tail_angle"), 0, 0);
        g.box("tail", -tt / 2, -0.25f, -tt / 2, tt, tl + 0.25f, tt, "tail");
        String prev = "tail";
        int n = Math.round(r.f("tail_n"));
        for (int i = 1; i < n; i++) {
            String name = "tail" + (i + 1);
            float th = tt * (i == 1 && r.f("tufts") > 0 ? 1.15f : 1f) * (float) Math.pow(0.9, i);
            g.part(name, prev, 0, tl, 0, r.f("tail_curl"), 0, 0);
            g.box(name, -th / 2, 0, -th / 2, th, tl, th, i < n - 1 ? "tail" : "tail_tip");
            prev = name;
        }
        Bone legF = g.bones.get("leg_fl");
        float bodyLength = Math.max(1f, -(legF.restZ()));
        return new Built(g.bones.get("root"), g.bones, chestTop, hipY, bodyLength, bThigh, bShin);
    }

    private static void head(Ctx g, String head, String suffix, Recipe r) {
        float[] skull = r.t("skull");
        float sw = skull[0], sh = skull[1], sd = skull[2];
        g.box(head, -sw / 2, -sh * 0.45f, -sd * 0.75f, sw, sh, sd, "head");
        if (r.f("brow") > 0.1f) {
            g.box(head, -sw / 2 + 0.25f, sh * 0.55f - 1f, -sd * 0.75f - 0.5f, sw - 0.5f, 1f, 0.75f, "brow");
        }
        float[] sn = r.t("snout");
        String snout = "snout" + suffix;
        g.part(snout, head, 0, -sh * 0.45f + 0.25f, -sd * 0.75f, -3, 0, 0);
        g.box(snout, -sn[0] / 2, 0, -sn[2], sn[0], sn[1], sn[2], "snout");
        float[] jw = r.t("jaw");
        String jaw = "jaw" + suffix;
        g.part(jaw, head, 0, -sh * 0.45f + 0.25f, -sd * 0.75f + 0.75f, -2, 0, 0);
        g.box(jaw, -jw[0] / 2, -jw[1], -jw[2] - 0.75f, jw[0], jw[1], jw[2] + 0.75f, "jaw");
        float[] ear = r.t("ear");
        float[] tip = r.t("ear_tip");
        float hang = r.f("ear_hang");
        for (int s = 0; s < 2; s++) {
            String side = s == 0 ? "l" : "r";
            float sx = s == 0 ? 1 : -1;
            String name = "ear" + suffix + "_" + side;
            String t = name + "_tip";
            if (hang > 1f) {
                // Hanging ears: from the top corners of the skull, flat down its sides (broad face outward).
                g.part(name, head, sx * (sw / 2 + ear[2] / 2 - 0.05f), sh * 0.55f - 0.4f, -sd * 0.3f, -4, 0, sx * -hang);
                g.box(name, -ear[2] / 2, 0, -ear[0] / 2, ear[2], ear[1], ear[0], "ear");
                g.part(t, name, 0, ear[1], 0, -r.f("ear_flop"), 0, 0);
                g.box(t, -tip[2] / 2, 0, -tip[0] / 2, tip[2], tip[1], tip[0], "ear_tip");
            } else {
                g.part(name, head, sx * (sw / 2 - ear[0] / 2 - 0.1f), sh * 0.55f - 0.25f, -sd * 0.25f, -6, 0, sx * -r.f("ear_spread"));
                g.box(name, -ear[0] / 2, 0, -ear[2] / 2, ear[0], ear[1], ear[2], "ear");
                g.part(t, name, 0, ear[1], 0, -r.f("ear_flop"), 0, 0);
                g.box(t, -tip[0] / 2, 0, -tip[2] / 2, tip[0], tip[1], tip[2], "ear_tip");
            }
        }
        if (r.f("tufts") >= 1) {
            for (int s = 0; s < 2; s++) {
                float sx = s == 0 ? 1 : -1;
                g.box(head, sx * (sw / 2) - (sx > 0 ? 0.75f : 0f), -sh * 0.45f + 0.25f, -sd * 0.5f, 0.75f, sh * 0.55f, sd * 0.6f, "tuft");
            }
        }
        if (r.f("fang") > 0.2f) {
            float fh = 0.5f + r.f("fang") * 0.5f;
            for (int s = 0; s < 2; s++) {
                float sx = s == 0 ? 1 : -1;
                g.box(snout, sx * (sn[0] / 2 - 0.5f) - 0.25f, -fh + 0.1f, -sn[2] + 0.25f, 0.5f, fh, 0.5f, "fang");
            }
        }
    }
}

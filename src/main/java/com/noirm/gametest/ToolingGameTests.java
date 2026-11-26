package com.noirm.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import com.noirm.model.BbGeometry;
import com.noirm.model.BbModel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The development tools: the Blockbench model reader ({@code com.noirm.model}), the quick test runner's filter
 * ({@link TestFilter}) and the lang files staying in step ({@code tools/dev/lang.py check} does the same check).
 */
@GameTestHolder(Noirm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ToolingGameTests {
    private ToolingGameTests() {}

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-3f;
    }

    /** The sample model (the Giant Insect, exported from BeetleMesh) reads with its bones, cubes, pivots and per-face UVs. */
    @GameTest(template = "empty", batch = "noirm_tooling")
    public static void blockbenchModelReads(GameTestHelper helper) {
        BbModel m = BbModel.fromClasspath("/assets/noirm/models/bb/giant_insect.bbmodel");
        helper.assertTrue(m != null, "giant_insect.bbmodel is in the mod");
        helper.assertTrue(m.textureWidth == 16 && m.textureHeight == 16, "16x16 UVs, got " + m.textureWidth + "x" + m.textureHeight);
        helper.assertTrue(m.textures.size() == 1 && "../../textures/entity/aburame/giant_insect.png".equals(m.textures.get(0).relativePath()),
                "the texture path is kept: " + m.textures);
        for (String bone : List.of("body", "wings", "legs", "leg_r0", "leg_r1", "leg_r2", "leg_l0", "leg_l1", "leg_l2")) {
            helper.assertTrue(m.bone(bone) != null, "bone " + bone);
        }
        helper.assertTrue(m.bones.size() == 9 && m.cubes.size() == 9, "9 bones and 9 cubes, got " + m.bones.size() + " / " + m.cubes.size());
        helper.assertTrue(m.bone("leg_r0").parent == m.bone("legs"), "legs nest under the legs bone");
        BbModel.Bone body = m.bone("body");
        helper.assertTrue(near(body.origin[1], 12f), "the body pivot is 0.75 blocks up: " + body.origin[1]);
        BbModel.Cube cube = body.cubes.get(0);
        helper.assertTrue(near(cube.from[0], -16.8f) && near(cube.to[2], 22.4f) && near(cube.from[1], 0.8f),
                "the body is 2.8 blocks long and 2.1 wide");
        BbModel.Face top = cube.faces.get(BbModel.Side.UP);
        helper.assertTrue(top != null && near(top.u1(), 0) && near(top.v1(), 0) && near(top.u2(), 3) && near(top.v2(), 4) && top.texture() == 0,
                "the shell's top face uses texels 0,0-3,4: " + top);
        helper.assertTrue(cube.quads().size() == 6, "a box has six faces");
        // A wing is a flat cube with only its top face.
        BbModel.Cube wing = m.bone("wings").cubes.get(0);
        helper.assertTrue(wing.quads().size() == 1, "a wing draws one face, got " + wing.quads().size());
        // A leg is turned: its pivot is its centre and its rotation isn't zero.
        BbModel.Cube leg = m.bone("leg_r0").cubes.get(0);
        helper.assertTrue(leg.rotation[0] != 0 || leg.rotation[1] != 0 || leg.rotation[2] != 0, "legs are rotated");
        helper.succeed();
    }

    /** Box UV lays faces out the vanilla way; rotation, inflate and face rotation follow Blockbench; animations sample. */
    @GameTest(template = "empty", batch = "noirm_tooling")
    public static void blockbenchGeometryRules(GameTestHelper helper) {
        String json = """
                {"meta": {"format_version": "4.5", "model_format": "free", "box_uv": true},
                 "name": "test", "resolution": {"width": 64, "height": 64},
                 "elements": [
                   {"name": "head", "type": "cube", "uuid": "c1", "from": [-4, 24, -4], "to": [4, 32, 4], "origin": [0, 24, 0],
                    "uv_offset": [0, 0], "inflate": 0.5, "faces": {}},
                   {"name": "arm", "type": "cube", "uuid": "c2", "box_uv": false, "from": [0, 0, 0], "to": [2, 4, 2],
                    "origin": [0, 0, 0], "rotation": [0, 90, 0],
                    "faces": {"north": {"uv": [0, 0, 2, 4], "texture": 0, "rotation": 90}, "up": {"uv": [2, 0, 4, 2], "texture": null}}}
                 ],
                 "outliner": [{"name": "head", "uuid": "g1", "origin": [0, 24, 0], "rotation": [0, 0, 0],
                               "children": ["c1", {"name": "hat", "uuid": "g2", "origin": [0, 32, 0], "children": []}]}, "c2"],
                 "textures": [{"name": "skin.png", "relative_path": "../../textures/entity/skin.png"}],
                 "animations": [{"name": "nod", "loop": "loop", "length": 1.0, "animators": {"g1": {"name": "head", "type": "bone",
                   "keyframes": [{"channel": "rotation", "time": 0, "data_points": [{"x": 0, "y": 0, "z": 0}], "interpolation": "linear"},
                                 {"channel": "rotation", "time": 0.5, "data_points": [{"x": "20", "y": 0, "z": 0}], "interpolation": "linear"}]}}}]}
                """;
        BbModel m = BbModel.parse(JsonParser.parseString(json).getAsJsonObject());
        helper.assertTrue(m.roots.size() == 2 && m.roots.get(0).name.equals("root") && m.bone("hat").parent == m.bone("head"),
                "loose cubes go in a root bone; groups nest");
        BbModel.Cube head = m.bone("head").cubes.get(0);
        Map<BbModel.Side, BbModel.Face> f = head.faces;
        helper.assertTrue(face(f.get(BbModel.Side.UP), 16, 8, 8, 0), "box UV top: " + f.get(BbModel.Side.UP));
        helper.assertTrue(face(f.get(BbModel.Side.DOWN), 24, 0, 16, 8), "box UV bottom: " + f.get(BbModel.Side.DOWN));
        helper.assertTrue(face(f.get(BbModel.Side.NORTH), 8, 8, 16, 16), "box UV front: " + f.get(BbModel.Side.NORTH));
        helper.assertTrue(face(f.get(BbModel.Side.EAST), 0, 8, 8, 16), "box UV right side: " + f.get(BbModel.Side.EAST));
        helper.assertTrue(face(f.get(BbModel.Side.WEST), 16, 8, 24, 16), "box UV left side: " + f.get(BbModel.Side.WEST));
        helper.assertTrue(face(f.get(BbModel.Side.SOUTH), 24, 8, 32, 16), "box UV back: " + f.get(BbModel.Side.SOUTH));
        // inflate grows the box, not the UVs
        BbModel.Quad up = head.quads().stream().filter(q -> q.side() == BbModel.Side.UP).findFirst().orElseThrow();
        helper.assertTrue(near(up.pos()[0][1], 32.5f) && near(up.pos()[0][0], -4.5f), "inflate 0.5: top at y 32.5, x -4.5");
        // the top-left corner seen from above (north-west) gets (u1, v1); quads are stored TL, BL, BR, TR
        helper.assertTrue(near(up.uv()[0][0], 16) && near(up.uv()[0][1], 8) && near(up.pos()[0][2], -4.5f), "top-left of the top face");
        // per-face UV: a face with no texture is skipped; rotation 90 turns the texture clockwise
        BbModel.Cube arm = m.cubes.get(1);
        helper.assertTrue(arm.quads().size() == 1, "only the textured face is drawn");
        BbModel.Quad north = arm.quads().get(0);
        // y 90 turns the north face (normal -z) to face west (-x)
        helper.assertTrue(near(north.normal()[0], -1) && near(north.normal()[2], 0), "rotated normal: " + java.util.Arrays.toString(north.normal()));
        // with rotation 90 the face's top-left corner shows the texture's bottom-left texel (0, 4)
        helper.assertTrue(near(north.uv()[0][0], 0) && near(north.uv()[0][1], 4), "face rotation 90: " + java.util.Arrays.toString(north.uv()[0]));
        float[] p = BbGeometry.rotate(new float[]{1, 0, 0}, new float[3], new float[]{0, 0, 90});
        helper.assertTrue(near(p[0], 0) && near(p[1], 1), "z 90 turns +x to +y");
        // animation: string values, linear, looping
        BbModel.Animation nod = m.animations.get("nod");
        helper.assertTrue(nod != null && nod.loop() == BbModel.Loop.LOOP && nod.tracks().containsKey("head"), "the nod animation targets the head");
        float[] mid = BbGeometry.sample(nod.tracks().get("head").rotation(), BbGeometry.animationTime(nod, 1.25f));
        helper.assertTrue(mid != null && near(mid[0], 10f), "a quarter in (looped) is halfway to 20 degrees: " + (mid == null ? null : mid[0]));
        helper.succeed();
    }

    private static boolean face(BbModel.Face f, float u1, float v1, float u2, float v2) {
        return f != null && near(f.u1(), u1) && near(f.v1(), v1) && near(f.u2(), u2) && near(f.v2(), v2);
    }

    /** The quick runner's filter picks tests by name, batch or exact name, and does nothing when unset. */
    @GameTest(template = "empty", batch = "noirm_tooling")
    public static void quickRunnerFilterSelects(GameTestHelper helper) {
        List<String> words = TestFilter.words(" Hound , =noirm_tooling ");
        helper.assertTrue(words.equals(List.of("hound", "=noirm_tooling")), "words are trimmed and lower-cased: " + words);
        helper.assertTrue(TestFilter.matches(words, "houndgrowsbylevel", "noirm_hound_growth"), "a name containing the word");
        helper.assertTrue(TestFilter.matches(words, "anything", "noirm_tooling"), "an exact batch");
        helper.assertTrue(!TestFilter.matches(words, "anything", "noirm_tooling_extra"), "an exact word is exact");
        helper.assertTrue(!TestFilter.matches(words, "amaterasuburnsthroughwater", "noirm_mangekyo"), "others are left out");
        helper.assertTrue(TestFilter.matches(List.of("hound"), "x", "noirm_hound_spawn"), "a batch containing the word");
        if (!TestFilter.active()) {
            List<net.minecraft.gametest.framework.TestFunction> none = List.of();
            helper.assertTrue(TestFilter.apply(none) == none, "unset: the list is returned untouched");
        }
        helper.succeed();
    }

    /** en_us, es_es and es_mx have exactly the same keys (what {@code tools/dev/lang.py check} checks). */
    @GameTest(template = "empty", batch = "noirm_tooling")
    public static void langFilesHaveTheSameKeys(GameTestHelper helper) {
        Set<String> en = keys("en_us");
        for (String lang : List.of("es_es", "es_mx")) {
            Set<String> other = keys(lang);
            Set<String> missing = new HashSet<>(en);
            missing.removeAll(other);
            Set<String> extra = new HashSet<>(other);
            extra.removeAll(en);
            helper.assertTrue(missing.isEmpty() && extra.isEmpty(), lang + ": missing " + first(missing) + ", extra " + first(extra)
                    + " (fix with tools/dev/lang.py)");
        }
        helper.succeed();
    }

    private static String first(Set<String> s) {
        return s.size() + (s.isEmpty() ? "" : " e.g. " + s.stream().sorted().limit(5).toList());
    }

    private static Set<String> keys(String file) {
        Path path = ModList.get().getModFileById(Noirm.MOD_ID).getFile().findResource("assets", Noirm.MOD_ID, "lang", file + ".json");
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
            return new HashSet<>(o.keySet());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

package com.noirm.client.hound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The ninja hound's models, read from {@code assets/noirm/hound/geometry.json} (written by
 * {@code tools/houndgen/gen_hound.py}): every body plan at every growth stage, plus the
 * Two-Headed Wolf, as named jointed boxes. Each becomes a baked {@link Rig} the animated
 * {@link HoundModel} poses by part name. Reloads with resource packs (F3+T).
 */
public final class HoundModels implements ResourceManagerReloadListener {
    public static final ResourceLocation FILE = Noirm.id("hound/geometry.json");

    /** One baked model: the part tree, its parts by name, the render scale and the numbers the poses need. */
    public static final class Rig {
        public final String key;
        public final ModelPart root;
        public final Map<String, ModelPart> parts;
        /** Model pixels → blocks: the stage's size in the world. */
        public final float scale;
        /** Hip height above the ground (model px), the hip-to-shoulder length, the back thigh and shin. */
        public final float hipHeight;
        public final float bodyLength;
        public final float thigh;
        public final float shin;

        Rig(String key, ModelPart root, Map<String, ModelPart> parts, float scale) {
            this.key = key;
            this.root = root;
            this.parts = parts;
            this.scale = scale;
            ModelPart body = parts.get("body");
            ModelPart legF = parts.get("leg_fl");
            hipHeight = body == null ? 8f : -body.getInitialPose().y;
            bodyLength = legF == null ? 6f : Math.max(1f, -legF.getInitialPose().z);
            ModelPart lower = parts.get("leg_bl_lower");
            ModelPart foot = parts.get("leg_bl_foot");
            thigh = lower == null ? 3f : lower.getInitialPose().y;
            shin = foot == null ? 2f : foot.getInitialPose().y;
        }

        /** The part, or null if this plan has none of that name. */
        public ModelPart part(String name) {
            return parts.get(name);
        }
    }

    private static final Map<String, Rig> RIGS = new HashMap<>();
    private static String chosen = "lean";
    private static List<String> plans = List.of("lean", "classic", "heavy");

    private HoundModels() {}

    /** The rig for a body plan ({@code build}: 0 = the chosen plan, then the others) at {@code stage} ("puppy" … "giant", or "fused"). */
    public static Rig get(int build, String stage) {
        String plan = plan(build);
        Rig rig = RIGS.get(plan + "/" + stage);
        if (rig == null) {
            rig = RIGS.get(chosen + "/" + stage);
        }
        if (rig == null && "fused".equals(stage)) {
            rig = RIGS.get(chosen + "/great");
        }
        return rig;
    }

    public static String plan(int build) {
        return build >= 0 && build < plans.size() ? plans.get(build) : chosen;
    }

    public static String chosenPlan() {
        return chosen;
    }

    public static int count() {
        return RIGS.size();
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        RIGS.clear();
        Optional<Resource> file = manager.getResource(FILE);
        if (file.isEmpty()) {
            Noirm.LOGGER.warn("No hound geometry at {}", FILE);
            return;
        }
        try (Reader reader = file.get().openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            chosen = json.has("chosen") ? json.get("chosen").getAsString() : "lean";
            if (json.has("plans")) {
                List<String> list = new ArrayList<>();
                json.getAsJsonArray("plans").forEach(e -> list.add(e.getAsString()));
                // Build 0 is always the chosen plan, then the others in file order.
                list.remove(chosen);
                list.add(0, chosen);
                plans = List.copyOf(list);
            }
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("models").entrySet()) {
                try {
                    RIGS.put(e.getKey(), bake(e.getKey(), e.getValue().getAsJsonObject()));
                } catch (RuntimeException ex) {
                    Noirm.LOGGER.warn("Couldn't build hound model {}: {}", e.getKey(), ex.toString());
                }
            }
        } catch (Exception e) {
            Noirm.LOGGER.warn("Couldn't read hound geometry: {}", e.toString());
        }
        Noirm.LOGGER.info("Loaded {} hound models", RIGS.size());
    }

    private static Rig bake(String key, JsonObject model) {
        JsonArray tex = model.getAsJsonArray("texture");
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> defs = new HashMap<>();
        Map<String, String> parentOf = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (JsonElement el : model.getAsJsonArray("parts")) {
            JsonObject p = el.getAsJsonObject();
            String name = p.get("name").getAsString();
            String parent = p.get("parent").getAsString();
            float[] pivot = floats(p.getAsJsonArray("pivot"));
            float[] rot = floats(p.getAsJsonArray("rot"));
            CubeListBuilder cubes = CubeListBuilder.create();
            for (JsonElement ce : p.getAsJsonArray("cubes")) {
                JsonObject c = ce.getAsJsonObject();
                float[] from = floats(c.getAsJsonArray("from"));
                float[] size = floats(c.getAsJsonArray("size"));
                float[] uv = floats(c.getAsJsonArray("uv"));
                float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0f;
                cubes.texOffs((int) uv[0], (int) uv[1]).addBox(from[0], from[1], from[2], size[0], size[1], size[2],
                        new CubeDeformation(inflate));
            }
            PartDefinition parentDef = parent.isEmpty() ? mesh.getRoot() : defs.get(parent);
            if (parentDef == null) {
                throw new IllegalStateException("part " + name + " before its parent " + parent);
            }
            PartPose pose = PartPose.offsetAndRotation(pivot[0], pivot[1], pivot[2],
                    (float) Math.toRadians(rot[0]), (float) Math.toRadians(rot[1]), (float) Math.toRadians(rot[2]));
            defs.put(name, parentDef.addOrReplaceChild(name, cubes, pose));
            parentOf.put(name, parent);
            order.add(name);
        }
        ModelPart root = LayerDefinition.create(mesh, tex.get(0).getAsInt(), tex.get(1).getAsInt()).bakeRoot();
        Map<String, ModelPart> parts = new HashMap<>();
        for (String name : order) {
            parts.put(name, find(root, name, parentOf));
        }
        float scale = model.has("scale") ? model.get("scale").getAsFloat() : 1f;
        return new Rig(key, root, parts, scale);
    }

    /** Walks from the root down the parent chain to the part called {@code name}. */
    private static ModelPart find(ModelPart root, String name, Map<String, String> parentOf) {
        List<String> chain = new ArrayList<>();
        for (String n = name; n != null && !n.isEmpty(); n = parentOf.get(n)) {
            chain.add(0, n);
        }
        ModelPart at = root;
        for (String n : chain) {
            at = at.getChild(n);
        }
        return at;
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsFloat();
        }
        return out;
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class HoundModelReloadRegistration {
        private HoundModelReloadRegistration() {}

        @SubscribeEvent
        public static void registerHoundReload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new HoundModels());
        }
    }
}

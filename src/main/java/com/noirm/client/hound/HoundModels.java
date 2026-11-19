package com.noirm.client.hound;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.noirm.Noirm;
import com.noirm.hound.HoundGrowth;
import com.noirm.hound.HoundLook;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.Reader;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The hound's shape data (breed recipes, ear and tail types, the fur atlas layout) and a cache of
 * built bodies: one per breed, ears, tail and level (past level 40 the body stops changing), and
 * the Two-Headed Wolf. Reloads with resource packs (F3+T).
 */
public final class HoundModels implements ResourceManagerReloadListener {
    /** The level past which a hound's shape stops changing (only its strength keeps growing). */
    public static final int SHAPE_LEVELS = 40;

    /** One built body and the numbers its poses and scale need. */
    public static final class Rig {
        public final Bone root;
        public final Map<String, Bone> parts;
        /** Shoulder height of the model in model pixels (for the render scale). */
        public final float shoulderPx;
        public final float hipHeight;
        public final float bodyLength;
        public final float thigh;
        public final float shin;

        Rig(HoundRigBuilder.Built b) {
            root = b.root();
            parts = b.bones();
            shoulderPx = b.shoulderPx();
            hipHeight = b.hipHeight();
            bodyLength = b.bodyLength();
            thigh = b.thigh();
            shin = b.shin();
        }

        public Bone part(String name) {
            return parts.get(name);
        }

        /** Model pixels → render scale so the shoulder stands {@code shoulderBlocks} high. */
        public float scaleFor(float shoulderBlocks) {
            return shoulderBlocks * 16f / Math.max(1f, shoulderPx);
        }
    }

    private static HoundShapes.Shapes shapes;
    private static HoundAtlas atlas;
    private static final Map<Long, Rig> RIGS = new HashMap<>();

    private HoundModels() {}

    /** The body for this look at this level ({@code fused}: the Two-Headed Wolf). Null while nothing has loaded. */
    public static Rig get(HoundLook look, int level, boolean fused) {
        if (shapes == null || atlas == null) {
            return null;
        }
        int lv = Math.max(1, Math.min(SHAPE_LEVELS, level));
        long key = (long) look.breed().ordinal() | (long) look.ears().ordinal() << 4 | (long) look.tail().ordinal() << 8
                | (long) lv << 12 | (fused ? 1L << 24 : 0L);
        return RIGS.computeIfAbsent(key, k -> new Rig(HoundRigBuilder.build(HoundRigBuilder.recipe(shapes, look, lv), atlas, fused)));
    }

    /** The render scale for a hound of this look, level and size multiplier. */
    public static float scale(Rig rig, int level, float size, boolean fused) {
        return rig.scaleFor(HoundGrowth.shoulder(level) * size * (fused ? 1.55f : 1f));
    }

    public static boolean loaded() {
        return shapes != null && atlas != null;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        RIGS.clear();
        HoundTextures.clear();
        try {
            JsonObject shapesJson = read(manager, Noirm.id("hound/shapes.json"));
            JsonObject atlasJson = read(manager, Noirm.id("hound/atlas.json"));
            Map<HoundLook.Breed, JsonObject> breeds = new EnumMap<>(HoundLook.Breed.class);
            for (HoundLook.Breed b : HoundLook.Breed.values()) {
                JsonObject json = read(manager, Noirm.id("hound/breeds/" + b.key() + ".json"));
                if (json != null) {
                    breeds.put(b, json);
                }
            }
            if (shapesJson == null || atlasJson == null || breeds.isEmpty()) {
                Noirm.LOGGER.warn("Hound shapes are missing (shapes {}, atlas {}, breeds {})", shapesJson != null, atlasJson != null, breeds.size());
                shapes = null;
                atlas = null;
                return;
            }
            shapes = new HoundShapes.Shapes(shapesJson, breeds);
            atlas = new HoundAtlas(atlasJson);
            Noirm.LOGGER.info("Loaded hound shapes: {} breeds", breeds.size());
        } catch (RuntimeException e) {
            Noirm.LOGGER.warn("Couldn't read the hound shapes: {}", e.toString());
            shapes = null;
            atlas = null;
        }
    }

    private static JsonObject read(ResourceManager manager, ResourceLocation id) {
        Optional<Resource> res = manager.getResource(id);
        if (res.isEmpty()) {
            return null;
        }
        try (Reader reader = res.get().openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            Noirm.LOGGER.warn("Couldn't read {}: {}", id, e.toString());
            return null;
        }
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

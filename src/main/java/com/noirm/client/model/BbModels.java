package com.noirm.client.model;

import com.noirm.Noirm;
import com.noirm.model.BbModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Every Blockbench model in {@code assets/<namespace>/models/bb/} ({@code .bbmodel} files), read and baked when
 * resources load and again on F3+T (so an edited model shows without restarting). Look one up with
 * {@code BbModels.get(Noirm.id("giant_insect"))}; null means it's missing or broken (the log says why), so
 * renderers keep a fallback.
 *
 * <p>The texture: the model's first texture, found through the path Blockbench saved next to it
 * ({@code relative_path}, e.g. {@code ../../textures/entity/aburame/giant_insect.png} from {@code models/bb/}),
 * else {@code textures/entity/<texture name>}.
 */
public final class BbModels implements ResourceManagerReloadListener {
    public static final String FOLDER = "models/bb";
    private static final Map<ResourceLocation, BakedBbModel> MODELS = new HashMap<>();

    private BbModels() {}

    /** The baked model {@code <namespace>:<name>} (file {@code assets/<namespace>/models/bb/<name>.bbmodel}), or null. */
    public static BakedBbModel get(ResourceLocation id) {
        return MODELS.get(id);
    }

    public static Map<ResourceLocation, BakedBbModel> all() {
        return MODELS;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        MODELS.clear();
        Map<ResourceLocation, Resource> found = manager.listResources(FOLDER, id -> id.getPath().endsWith(".bbmodel"));
        for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
            ResourceLocation file = e.getKey();
            String path = file.getPath();
            ResourceLocation id = new ResourceLocation(file.getNamespace(), path.substring(FOLDER.length() + 1, path.length() - ".bbmodel".length()));
            try (InputStream in = e.getValue().open()) {
                BbModel model = BbModel.read(in);
                MODELS.put(id, new BakedBbModel(id, texture(file, model), model));
            } catch (Exception ex) {
                Noirm.LOGGER.warn("Couldn't load Blockbench model {}: {}", file, ex.toString());
            }
        }
        Noirm.LOGGER.info("Loaded {} Blockbench models", MODELS.size());
    }

    /** Resolves the model's first texture to a resource location (see the class comment). */
    static ResourceLocation texture(ResourceLocation file, BbModel model) {
        if (model.textures.isEmpty()) {
            return null;
        }
        BbModel.Texture t = model.textures.get(0);
        String rel = t.relativePath();
        if (rel != null && !rel.isBlank()) {
            String dir = file.getPath().substring(0, file.getPath().lastIndexOf('/'));
            String resolved = normalise(dir + "/" + rel.replace('\\', '/'));
            if (resolved != null && ResourceLocation.isValidResourceLocation(file.getNamespace() + ":" + resolved)) {
                return new ResourceLocation(file.getNamespace(), resolved);
            }
        }
        String name = t.name().endsWith(".png") ? t.name() : t.name() + ".png";
        return new ResourceLocation(file.getNamespace(), "textures/entity/" + name.toLowerCase(java.util.Locale.ROOT));
    }

    /** "models/bb/../../textures/x.png" → "textures/x.png"; null if it climbs out of the namespace. */
    static String normalise(String path) {
        java.util.ArrayDeque<String> parts = new java.util.ArrayDeque<>();
        for (String p : path.split("/")) {
            if (p.isEmpty() || p.equals(".")) {
                continue;
            }
            if (p.equals("..")) {
                if (parts.isEmpty()) {
                    return null;
                }
                parts.removeLast();
            } else {
                parts.addLast(p);
            }
        }
        return String.join("/", parts);
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class BbModelReloadRegistration {
        private BbModelReloadRegistration() {}

        @SubscribeEvent
        public static void registerBbModelReload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new BbModels());
        }
    }
}

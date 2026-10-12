package com.noirm.combat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.noirm.Noirm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads moves and styles from data packs when a world starts and on {@code /reload}, so moves
 * can be tuned by editing JSON without recompiling.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class CombatData {
    /** The style everyone fights with until a master teaches them another. */
    public static final ResourceLocation DEFAULT_STYLE = Noirm.id("basic");

    private static final Gson GSON = new Gson();
    private static Map<ResourceLocation, CombatMove> moves = Map.of();
    private static Map<ResourceLocation, CombatStyle> styles = Map.of();

    private CombatData() {}

    public static CombatMove move(ResourceLocation id) {
        return moves.get(id);
    }

    public static CombatStyle style(ResourceLocation id) {
        return styles.get(id);
    }

    public static int moveCount() {
        return moves.size();
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader<>("combat/moves", CombatMove.CODEC, loaded -> moves = loaded));
        event.addListener(new Loader<>("combat/styles", CombatStyle.CODEC, loaded -> styles = loaded));
    }

    private static final class Loader<T> extends SimpleJsonResourceReloadListener {
        private final String directory;
        private final Codec<T> codec;
        private final java.util.function.Consumer<Map<ResourceLocation, T>> sink;

        Loader(String directory, Codec<T> codec, java.util.function.Consumer<Map<ResourceLocation, T>> sink) {
            super(GSON, directory);
            this.directory = directory;
            this.codec = codec;
            this.sink = sink;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, T> loaded = new HashMap<>();
            files.forEach((id, json) -> codec.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> Noirm.LOGGER.error("Invalid {} file {}: {}", directory, id, error))
                    .ifPresent(value -> loaded.put(id, value)));
            Noirm.LOGGER.info("Loaded {} {} entries", loaded.size(), directory);
            sink.accept(Map.copyOf(loaded));
        }
    }
}

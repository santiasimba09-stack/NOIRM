package com.noirm.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.noirm.Noirm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Loads every JSON file under {@code data/<namespace>/<directory>/} with a codec when a world
 * starts and on {@code /reload}. Invalid files are logged and skipped.
 */
public final class CodecJsonLoader<T> extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();

    private final String directory;
    private final Codec<T> codec;
    private final Consumer<Map<ResourceLocation, T>> sink;

    public CodecJsonLoader(String directory, Codec<T> codec, Consumer<Map<ResourceLocation, T>> sink) {
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

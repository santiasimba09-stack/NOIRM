package com.noirm.genjutsu;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.noirm.Noirm;
import com.noirm.data.CodecJsonLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Every genjutsu scene, loaded from {@code data/<ns>/genjutsu/*.json}, and which jutsu opens which. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class GenjutsuScenes {
    private static Map<ResourceLocation, GenjutsuScene> scenes = Map.of();
    private static Map<ResourceLocation, ResourceLocation> byJutsu = Map.of();

    private GenjutsuScenes() {}

    public static Map<ResourceLocation, GenjutsuScene> all() {
        return scenes;
    }

    public static GenjutsuScene get(ResourceLocation id) {
        return id == null ? null : scenes.get(id);
    }

    /** The scene a jutsu opens in its victim's mind, or null if it has none. */
    public static ResourceLocation sceneFor(ResourceLocation jutsu) {
        return jutsu == null ? null : byJutsu.get(jutsu);
    }

    /** The scene as JSON text (it travels to the victim's client in a packet). */
    public static String toJson(GenjutsuScene scene) {
        return GenjutsuScene.CODEC.encodeStart(JsonOps.INSTANCE, scene).result().map(JsonElement::toString).orElse("{}");
    }

    public static Optional<GenjutsuScene> fromJson(String json) {
        try {
            JsonElement element = com.google.gson.JsonParser.parseString(json);
            return GenjutsuScene.CODEC.parse(JsonOps.INSTANCE, element).result();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static void accept(Map<ResourceLocation, GenjutsuScene> loaded) {
        scenes = loaded;
        Map<ResourceLocation, ResourceLocation> map = new HashMap<>();
        loaded.forEach((id, scene) -> scene.jutsu().forEach(j -> map.put(j, id)));
        byJutsu = Map.copyOf(map);
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CodecJsonLoader<>("genjutsu", GenjutsuScene.CODEC, GenjutsuScenes::accept));
    }
}

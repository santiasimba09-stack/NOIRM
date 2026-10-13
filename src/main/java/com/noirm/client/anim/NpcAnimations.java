package com.noirm.client.anim;

import com.noirm.Noirm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side library of keyframe animations for NPC models. Reads the same files PlayerAnimator
 * loads for players ({@code assets/<namespace>/player_animation/*.json}) through the resource
 * manager, so resource packs and {@code F3+T} reloads apply to both.
 */
public final class NpcAnimations implements ResourceManagerReloadListener {
    public static final String FOLDER = "player_animation";
    private static final Map<ResourceLocation, KeyframeAnimation> ANIMATIONS = new HashMap<>();

    private NpcAnimations() {}

    public static KeyframeAnimation get(ResourceLocation id) {
        return id == null ? null : ANIMATIONS.get(id);
    }

    public static int count() {
        return ANIMATIONS.size();
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        ANIMATIONS.clear();
        Map<ResourceLocation, Resource> files = manager.listResources(FOLDER, path -> path.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
            String namespace = file.getKey().getNamespace();
            try (Reader reader = file.getValue().openAsReader()) {
                // Registered under the name inside the file, as PlayerAnimator does.
                KeyframeAnimation.parse(reader).forEach((name, animation) ->
                        ANIMATIONS.put(new ResourceLocation(namespace, name.toLowerCase(java.util.Locale.ROOT)), animation));
            } catch (Exception e) {
                Noirm.LOGGER.warn("Couldn't read NPC animation {}: {}", file.getKey(), e.toString());
            }
        }
        Noirm.LOGGER.info("Loaded {} NPC animations", ANIMATIONS.size());
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class NpcAnimationReloadRegistration {
        private NpcAnimationReloadRegistration() {}

        @SubscribeEvent
        public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new NpcAnimations());
        }
    }
}

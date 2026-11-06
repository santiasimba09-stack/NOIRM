package com.noirm.mission;

import com.noirm.Noirm;
import com.noirm.data.CodecJsonLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;

/** Every mission template, loaded from {@code data/<ns>/missions/**.json}. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class MissionTemplates {
    /** The jōnin trial: the hard solo mission before the fight with the jōnin examiner. */
    public static final ResourceLocation JONIN_TRIAL = Noirm.id("exam/jonin_trial");

    private static Map<ResourceLocation, MissionTemplate> templates = Map.of();

    private MissionTemplates() {}

    public static MissionTemplate get(ResourceLocation id) {
        return id == null ? null : templates.get(id);
    }

    public static Map<ResourceLocation, MissionTemplate> all() {
        return templates;
    }

    /** The templates a board can offer for one rank letter, sorted by id (so picks are stable). */
    public static List<ResourceLocation> offerable(MissionTemplate.Letter letter) {
        return templates.entrySet().stream()
                .filter(e -> e.getValue().rank() == letter && !e.getValue().exam() && e.getValue().weight() > 0)
                .map(Map.Entry::getKey).sorted().toList();
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CodecJsonLoader<>("missions", MissionTemplate.CODEC, loaded -> templates = loaded));
    }
}

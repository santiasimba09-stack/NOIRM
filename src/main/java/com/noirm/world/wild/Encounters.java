package com.noirm.world.wild;

import com.noirm.Noirm;
import com.noirm.data.CodecJsonLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/** Every wild encounter, loaded from {@code data/<ns>/encounters/*.json}. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class Encounters {
    public static final ResourceLocation WANDERER = Noirm.id("wanderer");
    public static final ResourceLocation TRAVELLERS = Noirm.id("travellers");
    public static final ResourceLocation SQUAD = Noirm.id("squad");
    public static final ResourceLocation CLAN_GROUP = Noirm.id("clan_group");
    public static final ResourceLocation ROGUE = Noirm.id("rogue");
    public static final ResourceLocation ROGUE_BAND = Noirm.id("rogue_band");
    public static final ResourceLocation ROGUE_STRONG = Noirm.id("rogue_strong");

    private static Map<ResourceLocation, EncounterType> encounters = Map.of();

    private Encounters() {}

    public static EncounterType get(ResourceLocation id) {
        return id == null ? null : encounters.get(id);
    }

    public static Map<ResourceLocation, EncounterType> all() {
        return encounters;
    }

    /** The lang key of an encounter's name: {@code encounter.<ns>.<path>}. */
    public static String nameKey(ResourceLocation id) {
        return "encounter." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CodecJsonLoader<>("encounters", EncounterType.CODEC, loaded -> encounters = loaded));
    }
}

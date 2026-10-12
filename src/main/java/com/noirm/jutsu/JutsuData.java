package com.noirm.jutsu;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.data.CodecJsonLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** All jutsu, loaded from {@code data/<ns>/jutsu/**.json}. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class JutsuData {
    private static Map<ResourceLocation, JutsuDefinition> jutsu = Map.of();

    private JutsuData() {}

    public static JutsuDefinition get(ResourceLocation id) {
        return id == null ? null : jutsu.get(id);
    }

    public static Map<ResourceLocation, JutsuDefinition> all() {
        return jutsu;
    }

    /** Starter jutsu for a nature (or the universal ones, for null), sorted by id. */
    public static List<ResourceLocation> startersFor(Nature nature) {
        return jutsu.entrySet().stream()
                .filter(e -> e.getValue().starter() && e.getValue().nature().orElse(null) == nature
                        && e.getValue().requires().isEmpty())
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CodecJsonLoader<>("jutsu", JutsuDefinition.CODEC, loaded -> jutsu = loaded));
    }
}

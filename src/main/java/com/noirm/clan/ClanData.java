package com.noirm.clan;

import com.noirm.Noirm;
import com.noirm.data.CodecJsonLoader;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Canon clans (Uchiha, Hyūga…), loaded from {@code data/<ns>/clans/*.json}. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class ClanData {
    private static List<Clan> canon = List.of();

    private ClanData() {}

    public static List<Clan> canonClans() {
        return canon;
    }

    public static Optional<Clan> canon(String id) {
        return canon.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CodecJsonLoader<>("clans", Clan.CODEC, loaded -> canon = loaded.values().stream()
                .map(c -> new Clan(c.id(), c.name(), true, c.natures(), c.traits(), c.speedBonus(), c.taijutsuBonus(),
                        c.kekkeiGenkai(), c.awakening(), c.weight()))
                .sorted(Comparator.comparing(Clan::id))
                .toList()));
    }
}

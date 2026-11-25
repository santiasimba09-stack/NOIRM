package com.noirm.clan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.noirm.Noirm;
import com.noirm.data.CodecJsonLoader;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * How likely each clan is for a newcomer (the player's creator rolls, NPC ninjas, wild ninjas, exam
 * opponents): {@code data/noirm/clan_odds/canon.json}. {@code canon_share} of all rolls are a canon
 * clan (0.8: canon clans are the normal outcome); the rest are this world's generated clans. Among
 * canon clans, {@code weights} picks one. They're written per thousand rolls, so a weight of 35 means
 * 3.5% of every roll (35 of 1000) when the canon weights add up to {@code canon_share} × 1000.
 * A canon clan missing from the table falls back to its own JSON {@code weight}.
 *
 * @param canonShare chance (0–1) that a roll is a canon clan
 * @param weights    canon clan id → weight among canon clans
 */
public record ClanOdds(float canonShare, Map<String, Integer> weights) {
    /** Used before the data loads (and if the file is missing). */
    public static final ClanOdds DEFAULT = new ClanOdds(0.8f, Map.of());
    public static final Codec<ClanOdds> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("canon_share").forGetter(ClanOdds::canonShare),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("weights", Map.of()).forGetter(ClanOdds::weights)
    ).apply(i, ClanOdds::new));

    private static ClanOdds current = DEFAULT;

    public static ClanOdds get() {
        return current;
    }

    /** A canon clan's weight among canon clans (at least 1). */
    public int weight(Clan clan) {
        return Math.max(1, weights.getOrDefault(clan.id(), clan.weight()));
    }

    /** The chance (0–1) that one roll comes out as this canon clan. */
    public float chance(Clan clan, java.util.List<Clan> canon) {
        int total = canon.stream().mapToInt(this::weight).sum();
        return total <= 0 ? 0f : canonShare * weight(clan) / total;
    }

    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
    public static final class OddsLoader {
        private OddsLoader() {}

        @SubscribeEvent
        public static void addReloadListeners(AddReloadListenerEvent event) {
            event.addListener(new CodecJsonLoader<>("clan_odds", CODEC,
                    loaded -> current = loaded.getOrDefault(Noirm.id("canon"), DEFAULT)));
        }
    }
}

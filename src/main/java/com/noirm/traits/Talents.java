package com.noirm.traits;

import com.noirm.Noirm;
import com.noirm.data.CodecJsonLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The talent list ({@code data/noirm/traits/}) and the perk packages generated clans are made of
 * ({@code data/noirm/clan_perks/}), and how talents are rolled.
 *
 * <p><b>Rolling:</b> {@link #MIN_TALENTS} talents, plus one more {@value #THIRD_CHANCE} of the time
 * and a fourth {@value #FOURTH_CHANCE} of the time. Each pick is weighted by the talent's
 * {@code weight} × the clan's bias for it; no repeats, nothing a pick excludes, and at most
 * {@value #MAX_NEGATIVE} negative talent.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class Talents {
    public static final int MIN_TALENTS = 2;
    public static final float THIRD_CHANCE = 0.5f;
    public static final float FOURTH_CHANCE = 0.25f;
    public static final int MAX_NEGATIVE = 1;
    /** Perk packages a generated clan is made of. */
    public static final int PACKAGES_PER_CLAN = 2;

    private static Map<String, Talent> talents = Map.of();
    private static Map<String, Talent> packages = Map.of();
    /** Bumped on every reload, so cached perk totals are rebuilt. */
    private static int version;

    private Talents() {}

    public static Map<String, Talent> all() {
        return talents;
    }

    public static Talent get(String id) {
        return talents.get(id);
    }

    public static Map<String, Talent> packages() {
        return packages;
    }

    public static int version() {
        return version;
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CodecJsonLoader<>("traits", Talent.CODEC, loaded -> {
            talents = byPath(loaded);
            version++;
        }));
        event.addListener(new CodecJsonLoader<>("clan_perks", Talent.CODEC, loaded -> {
            packages = byPath(loaded);
            version++;
        }));
    }

    private static Map<String, Talent> byPath(Map<ResourceLocation, Talent> loaded) {
        Map<String, Talent> out = new TreeMap<>(); // sorted: the same seed always rolls the same
        loaded.forEach((id, t) -> out.put(id.getPath(), t));
        warnUnknownPerks(out);
        return Collections.unmodifiableMap(out);
    }

    private static void warnUnknownPerks(Map<String, Talent> map) {
        map.forEach((id, t) -> t.modifiers().keySet().stream().filter(k -> Perk.byKey(k) == null)
                .forEach(k -> Noirm.LOGGER.warn("Talent/package {} names an unknown perk: {}", id, k)));
    }

    // ---------------------------------------------------------------- rolling

    /** A fresh set of 2–4 talents, biased by a clan's {@code talent_bias}. */
    public static List<String> roll(RandomSource random, Map<String, Float> bias) {
        int count = MIN_TALENTS + (random.nextFloat() < THIRD_CHANCE ? 1 : 0);
        if (count > MIN_TALENTS && random.nextFloat() < FOURTH_CHANCE) {
            count++;
        }
        return pick(talents, random, bias, count, MAX_NEGATIVE);
    }

    /** Perk packages for a generated clan. */
    public static List<String> rollPackages(RandomSource random) {
        return pick(packages, random, Map.of(), PACKAGES_PER_CLAN, 1);
    }

    static List<String> pick(Map<String, Talent> from, RandomSource random, Map<String, Float> bias, int count, int maxNegative) {
        List<String> chosen = new ArrayList<>();
        int negatives = 0;
        for (int n = 0; n < count; n++) {
            List<String> ids = new ArrayList<>();
            List<Float> weights = new ArrayList<>();
            float total = 0f;
            for (Map.Entry<String, Talent> e : from.entrySet()) {
                String id = e.getKey();
                Talent t = e.getValue();
                if (chosen.contains(id) || t.weight() <= 0 || t.kind() == Talent.Kind.NEGATIVE && negatives >= maxNegative
                        || clashes(from, chosen, id)) {
                    continue;
                }
                float w = t.weight() * Math.max(0f, bias.getOrDefault(id, 1f));
                if (w <= 0f) {
                    continue;
                }
                ids.add(id);
                weights.add(w);
                total += w;
            }
            if (ids.isEmpty()) {
                break;
            }
            float roll = random.nextFloat() * total;
            String picked = ids.get(ids.size() - 1);
            for (int i = 0; i < ids.size(); i++) {
                roll -= weights.get(i);
                if (roll <= 0f) {
                    picked = ids.get(i);
                    break;
                }
            }
            chosen.add(picked);
            if (from.get(picked).kind() == Talent.Kind.NEGATIVE) {
                negatives++;
            }
        }
        return chosen;
    }

    /** Whether {@code id} excludes, or is excluded by, something already chosen. */
    private static boolean clashes(Map<String, Talent> from, List<String> chosen, String id) {
        Talent t = from.get(id);
        for (String c : chosen) {
            Talent other = from.get(c);
            if (t.excludes().contains(c) || other != null && other.excludes().contains(id)) {
                return true;
            }
        }
        return false;
    }

    /** The perks of some packages, summed, and their talent bias multiplied together. */
    public static Map<String, Float> packagePerks(List<String> ids) {
        Map<String, Float> perks = new TreeMap<>();
        for (String id : ids) {
            Talent p = packages.get(id);
            if (p != null) {
                p.modifiers().forEach((k, v) -> perks.merge(k, v, Float::sum));
            }
        }
        return perks;
    }

    public static Map<String, Float> packageBias(List<String> ids) {
        Map<String, Float> bias = new TreeMap<>();
        for (String id : ids) {
            Talent p = packages.get(id);
            if (p != null) {
                p.talentBias().forEach((k, v) -> bias.merge(k, v, (a, b) -> a * b));
            }
        }
        return bias;
    }
}

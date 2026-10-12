package com.noirm.clan;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Invents a new clan: a Japanese-sounding name, a main nature (sometimes a second one), two
 * traits, small stat leanings and an awakening path. Every world grows its own set of clans.
 */
public final class ClanGenerator {
    /** Chance a new clan carries a kekkei genkai nature (Wood, Ice, Lava…) in its blood. */
    public static final float BLOODLINE_CHANCE = 0.08f;
    private static final String[] FIRST = {"Ka", "Mi", "Shi", "Ta", "Ha", "Ki", "Ri", "Ku", "Yo", "Sa", "To", "Fu", "Ro", "Na", "Ha", "Tsu", "Ze", "Mo", "Ai", "Ya"};
    private static final String[] MIDDLE = {"ze", "ka", "mo", "ri", "su", "ne", "to", "ku", "ga", "ra", "shi", "no", "ba", "me", "ya", "ki"};
    private static final String[] LAST = {"mori", "gawa", "zaki", "mura", "shima", "yama", "kawa", "hara", "no", "da", "ki", "be", "ta", "saki", "kage", "ishi"};

    private ClanGenerator() {}

    /** A new clan whose name isn't already in {@code takenNames} (case-insensitive). */
    public static Clan generate(RandomSource random, Collection<String> takenNames) {
        String name;
        int tries = 0;
        do {
            name = pick(random, FIRST) + (random.nextInt(3) == 0 ? pick(random, MIDDLE) : "") + pick(random, LAST);
        } while (isTaken(name, takenNames) && ++tries < 50);
        if (isTaken(name, takenNames)) {
            name = name + (takenNames.size() + 1);
        }

        List<Nature> basic = Nature.BASIC;
        Map<Nature, Float> natures = new EnumMap<>(Nature.class);
        if (random.nextFloat() < BLOODLINE_CHANCE) {
            // A bloodline: a kekkei genkai nature, with both of the natures it mixes.
            List<Nature> bloodlines = Nature.combined().stream().filter(n -> n.tier() == 2 && n.isElemental()).toList();
            Nature bloodline = bloodlines.get(random.nextInt(bloodlines.size()));
            List<Nature> parts = new ArrayList<>(bloodline.components());
            parts.sort(java.util.Comparator.naturalOrder());
            natures.put(parts.get(0), Clan.PRIMARY_AFFINITY);
            natures.put(parts.get(1), Clan.SECONDARY_AFFINITY);
            natures.put(bloodline, Clan.SECONDARY_AFFINITY);
        } else {
            Nature primary = basic.get(random.nextInt(basic.size()));
            natures.put(primary, Clan.PRIMARY_AFFINITY);
            if (random.nextFloat() < 0.4f) {
                Nature secondary;
                do {
                    secondary = basic.get(random.nextInt(basic.size()));
                } while (secondary == primary);
                natures.put(secondary, Clan.SECONDARY_AFFINITY);
            }
        }

        List<ClanTrait> pool = new ArrayList<>(List.of(ClanTrait.values()));
        List<ClanTrait> traits = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            traits.add(pool.remove(random.nextInt(pool.size())));
        }

        AwakeningPath awakening = random.nextFloat() < 0.5f ? AwakeningPath.NATURE_SURGE : AwakeningPath.WILL;
        String id = "custom_" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return new Clan(id, name, false, Map.copyOf(natures), List.copyOf(traits),
                random.nextInt(3), random.nextInt(3), java.util.Optional.empty(), awakening, 1);
    }

    private static boolean isTaken(String name, Collection<String> taken) {
        return taken.stream().anyMatch(n -> n.equalsIgnoreCase(name));
    }

    private static String pick(RandomSource random, String[] options) {
        return options[random.nextInt(options.length)];
    }
}

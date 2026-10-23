package com.noirm.jutsu;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.entity.SparringNinja;
import com.noirm.training.TrainingSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Which jutsu an NPC ninja knows: a small, personal set instead of every jutsu of its natures.
 *
 * <p>How big the set is, and how high its ranks go, depends on how strong the ninja is (its
 * {@link Tier}). The set is rolled from a seed the ninja keeps (and saves), so the same ninja
 * always knows the same jutsu. It always holds at least one jutsu of the ninja's best nature;
 * clan signature techniques and kekkei genkai jutsu are picked more often; dōjutsu techniques
 * only with the eye. Crew mates grow past their set by being taught, and on their own: when one
 * of their jutsu reaches a mastery level, they may work out the jutsu that unlocks from it
 * ({@link #selfLearn}).
 *
 * <p>All tuning numbers are the constants below.
 */
public final class NpcJutsuSets {
    // ---------------------------------------------------------------- tuning

    /**
     * Strength tiers: how many jutsu (min–max) and the highest rank they roll.
     * Strength is the average of speed and taijutsu (what {@code /noirm spawn ninja … <strength>} sets).
     */
    public enum Tier {
        GENIN(1, 2, "C"),
        CHUNIN(2, 4, "B"),
        JONIN(4, 6, "A"),
        KAGE(6, 8, "S");

        public final int min;
        public final int max;
        public final String maxRank;

        Tier(int min, int max, String maxRank) {
            this.min = min;
            this.max = max;
            this.maxRank = maxRank;
        }
    }

    /** Strength needed for each tier (below CHUNIN_STRENGTH = genin). Residents: trainees 12–27, guards 20–35, Kage 120–200. */
    public static final float CHUNIN_STRENGTH = 20f;
    public static final float JONIN_STRENGTH = 40f;
    public static final float KAGE_STRENGTH = 100f;

    /** How much more often a clan signature technique or a kekkei genkai jutsu is picked. */
    public static final float SIGNATURE_WEIGHT = 4f;
    /** Clan signature techniques may be this many ranks above the tier's limit (a chūnin Hyūga can know Rotation). */
    public static final int SIGNATURE_RANK_STRETCH = 1;
    /** Extra weight per point of nature affinity (the clan's main nature is picked more). */
    public static final float AFFINITY_WEIGHT = 1f;
    /** Weight of jutsu with no nature (Body Flicker, Shadow Clone, Rasengan). */
    public static final float UNIVERSAL_WEIGHT = 0.6f;
    /** Extra weight per rank step above E: strong ninjas lean toward their strongest techniques. */
    public static final float RANK_LEAN = 0.3f;

    /** Every ninja knows these on top of their set (the academy basics); they don't count toward its size. */
    public static final List<ResourceLocation> ALWAYS_KNOWN = List.of(Noirm.id("universal/substitution"));

    /** Clan signature techniques (by clan id), picked more often. */
    public static final Map<String, List<ResourceLocation>> CLAN_SIGNATURES = Map.of(
            "uchiha", List.of(Noirm.id("katon/great_fireball"), Noirm.id("katon/phoenix_flower"), Noirm.id("katon/fire_dragon"),
                    Noirm.id("katon/dragon_flame_caterwaul"), Noirm.id("katon/great_fire_annihilation")),
            "hyuga", List.of(Noirm.id("byakugan/rotation"), Noirm.id("byakugan/sixty_four_palms")),
            "senju", List.of(Noirm.id("doton/earth_dragon"), Noirm.id("suiton/water_dragon"), Noirm.id("yang/mystical_palm")),
            "uzumaki", List.of(Noirm.id("futon/great_breakthrough"), Noirm.id("suiton/water_formation_wall")));

    /** Crew mates work out a jutsu by themselves at most this often: once per mastery level gained. */
    public static final int SELF_LEARN_PER_LEVEL = 1;

    private static final String RANKS = "EDCBAS";

    private NpcJutsuSets() {}

    // ---------------------------------------------------------------- tiers

    /** 0 (E) … 5 (S); unknown ranks count as C. */
    public static int rankIndex(String rank) {
        int i = rank == null || rank.isEmpty() ? -1 : RANKS.indexOf(Character.toUpperCase(rank.charAt(0)));
        return i < 0 ? 2 : i;
    }

    /** The tier for a strength (average of speed and taijutsu). */
    public static Tier tierFor(float strength) {
        if (strength >= KAGE_STRENGTH) {
            return Tier.KAGE;
        }
        if (strength >= JONIN_STRENGTH) {
            return Tier.JONIN;
        }
        return strength >= CHUNIN_STRENGTH ? Tier.CHUNIN : Tier.GENIN;
    }

    /** A ninja's tier: from its strength, nudged by its village role (trainees stay genin, guards are at least chūnin) and its eyes. */
    public static Tier tierOf(SparringNinja ninja) {
        float strength = (ninja.stat(TrainingSystem.Stat.SPEED) + ninja.stat(TrainingSystem.Stat.TAIJUTSU)) / 2f;
        Tier tier = tierFor(strength);
        String role = ninja.villageRole();
        if ("TRAINEE".equals(role)) {
            tier = Tier.GENIN;
        } else if (("GUARD".equals(role) || "PATROL".equals(role)) && tier.ordinal() < Tier.CHUNIN.ordinal()) {
            tier = Tier.CHUNIN;
        }
        if (ninja.mangekyoLook() != 0 && tier.ordinal() < Tier.JONIN.ordinal()) {
            tier = Tier.JONIN; // a Mangekyō is never a beginner's eye
        }
        return tier;
    }

    // ---------------------------------------------------------------- rolling a set

    /** The ninja's personal set (without {@link #ALWAYS_KNOWN}, eye and beast techniques), rolled from {@code seed}. */
    public static List<ResourceLocation> roll(SparringNinja ninja, long seed) {
        return roll(tierOf(ninja), ninja.natures(), ninja.clanId(), ninja.byakuganActive(), seed);
    }

    public static List<ResourceLocation> roll(Tier tier, Map<Nature, Float> natures, String clanId, boolean byakugan, long seed) {
        RandomSource random = RandomSource.create(seed);
        int size = tier.min + random.nextInt(tier.max - tier.min + 1);
        int cap = rankIndex(tier.maxRank);
        List<ResourceLocation> signatures = CLAN_SIGNATURES.getOrDefault(clanId, List.of());
        List<ResourceLocation> result = new ArrayList<>();

        // The pool: everything they could know, sorted by id so the same seed gives the same set.
        List<Map.Entry<ResourceLocation, JutsuDefinition>> pool = JutsuData.all().entrySet().stream()
                .filter(e -> eligible(e.getKey(), e.getValue(), natures, byakugan))
                .filter(e -> rankIndex(e.getValue().rank()) <= cap + (signatures.contains(e.getKey()) ? SIGNATURE_RANK_STRETCH : 0))
                .sorted(Comparator.comparing(e -> e.getKey().toString()))
                .toList();

        // 1. One jutsu of their best nature (a starter if one fits, else any that fits, else its easiest).
        Nature best = natures.entrySet().stream()
                .max(Map.Entry.<Nature, Float>comparingByValue().thenComparing(e -> -e.getKey().ordinal()))
                .map(Map.Entry::getKey).orElse(null);
        if (best != null) {
            List<Map.Entry<ResourceLocation, JutsuDefinition>> ofBest = pool.stream()
                    .filter(e -> e.getValue().nature().orElse(null) == best).toList();
            List<Map.Entry<ResourceLocation, JutsuDefinition>> starters = ofBest.stream().filter(e -> e.getValue().starter()).toList();
            ResourceLocation first = null;
            if (!starters.isEmpty()) {
                first = starters.get(random.nextInt(starters.size())).getKey();
            } else if (!ofBest.isEmpty()) {
                first = pick(ofBest, natures, signatures, random);
            } else {
                // Nothing of that nature is low enough in rank (wood, dust…): they still know its easiest jutsu.
                first = JutsuData.all().entrySet().stream()
                        .filter(e -> e.getValue().nature().orElse(null) == best && eligible(e.getKey(), e.getValue(), natures, byakugan))
                        .min(Comparator.<Map.Entry<ResourceLocation, JutsuDefinition>>comparingInt(e -> rankIndex(e.getValue().rank()))
                                .thenComparing(e -> e.getKey().toString()))
                        .map(Map.Entry::getKey).orElse(null);
            }
            if (first != null) {
                result.add(first);
            }
        }

        // 2. The rest, weighted.
        List<Map.Entry<ResourceLocation, JutsuDefinition>> left = new ArrayList<>(pool);
        left.removeIf(e -> result.contains(e.getKey()));
        while (result.size() < size && !left.isEmpty()) {
            ResourceLocation id = pick(left, natures, signatures, random);
            result.add(id);
            left.removeIf(e -> e.getKey().equals(id));
        }
        return result;
    }

    /** Whether a ninja with these natures (and eyes) could know {@code def} at all. */
    static boolean eligible(ResourceLocation id, JutsuDefinition def, Map<Nature, Float> natures, boolean byakugan) {
        if (ALWAYS_KNOWN.contains(id) || com.noirm.master.MasterData.taughtByMaster(def)) {
            return false; // the basics come anyway; the Gates only from a master
        }
        if (def.requires().isPresent()) {
            // Byakugan techniques with the Byakugan; the Sharingan genjutsu, Mangekyō and Beast Ball come with the eye/beast itself.
            return byakugan && "byakugan".equals(def.requires().get().dojutsu());
        }
        Nature nature = def.nature().orElse(null);
        return nature == null || natures.getOrDefault(nature, 0f) > 0f;
    }

    private static ResourceLocation pick(List<Map.Entry<ResourceLocation, JutsuDefinition>> options, Map<Nature, Float> natures,
                                         List<ResourceLocation> signatures, RandomSource random) {
        float total = 0f;
        float[] weights = new float[options.size()];
        for (int i = 0; i < options.size(); i++) {
            weights[i] = weight(options.get(i).getKey(), options.get(i).getValue(), natures, signatures);
            total += weights[i];
        }
        float roll = random.nextFloat() * total;
        for (int i = 0; i < options.size(); i++) {
            roll -= weights[i];
            if (roll <= 0f) {
                return options.get(i).getKey();
            }
        }
        return options.get(options.size() - 1).getKey();
    }

    static float weight(ResourceLocation id, JutsuDefinition def, Map<Nature, Float> natures, List<ResourceLocation> signatures) {
        Nature nature = def.nature().orElse(null);
        float weight = nature == null ? UNIVERSAL_WEIGHT : 1f + AFFINITY_WEIGHT * natures.getOrDefault(nature, 0f);
        boolean kekkeiGenkai = nature != null && !nature.isBasic();
        if (signatures.contains(id) || kekkeiGenkai || def.requires().isPresent()) {
            weight *= SIGNATURE_WEIGHT;
        }
        return weight * (1f + RANK_LEAN * rankIndex(def.rank()));
    }

    // ---------------------------------------------------------------- crew mates growing on their own

    /**
     * A crew mate's {@code id} just reached mastery {@code level}: they may work out a jutsu that
     * unlocks from it (the same rule players follow), if they have its nature. Returns what they
     * learned, or null.
     */
    public static ResourceLocation selfLearn(SparringNinja ninja, ResourceLocation id, int level) {
        List<ResourceLocation> learned = new ArrayList<>();
        JutsuData.all().entrySet().stream()
                .filter(e -> e.getValue().unlock().map(u -> id.equals(u.after()) && u.mastery() <= level).orElse(false))
                .filter(e -> !ninja.knownJutsu().contains(e.getKey()))
                .filter(e -> eligible(e.getKey(), e.getValue(), ninja.natures(), false))
                .sorted(Comparator.comparing(e -> e.getKey().toString()))
                .limit(SELF_LEARN_PER_LEVEL)
                .forEach(e -> learned.add(e.getKey()));
        learned.forEach(ninja::teach);
        return learned.isEmpty() ? null : learned.get(0);
    }
}

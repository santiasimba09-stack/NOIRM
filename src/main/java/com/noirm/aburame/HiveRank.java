package com.noirm.aburame;

import com.noirm.Noirm;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * Hive rank: the Aburame's milestones (docs/ABURAME.md, 2.10), shaped like the hound's stages. Each
 * needs a <b>total colony</b> (body + jars + gourd + hives + swarms out) <b>and a deed</b>
 * ({@link Colony#deedDone}), so it can't be reached by sitting at home. Techniques unlock with it.
 */
public enum HiveRank {
    NESTLING(0, List.of("parasitic_swarm", "female_insect")),
    GENIN(10_000, List.of("insect_clone", "insect_wall", "scout_swarm", "poison_draw", "insect_gathering", "insect_cocoon")),
    CHUNIN(25_000, List.of("insect_sphere", "insect_jamming", "insect_jar", "insect_tether")),
    JONIN(50_000, List.of("insect_tornado", "bug_bite", "chakra_gift", "poison_cloud")),
    CLAN_MASTER(120_000, List.of("giant_insect", "jar_of_poison")),
    LIVING_HIVE(200_000, List.of("living_hive", "black_sea", "insect_legion"));

    /** Total colony needed. */
    public final int colony;
    /** The techniques it opens (paths under {@code data/noirm/jutsu/aburame/}). */
    private final List<String> unlocks;

    HiveRank(int colony, List<String> unlocks) {
        this.colony = colony;
        this.unlocks = unlocks;
    }

    public List<ResourceLocation> jutsu() {
        return unlocks.stream().map(p -> Noirm.id("aburame/" + p)).toList();
    }

    /** 1 = Nestling … 6 = Living Hive (the {@code "rank"} in a technique's {@code hiden} block). */
    public int level() {
        return ordinal() + 1;
    }

    public HiveRank next() {
        return this == LIVING_HIVE ? null : values()[ordinal() + 1];
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "aburame.noirm.rank." + key();
    }

    public static HiveRank of(int ordinal) {
        HiveRank[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }

    public static HiveRank byKey(String key) {
        for (HiveRank r : values()) {
            if (r.key().equals(key)) {
                return r;
            }
        }
        return null;
    }

    /** Species slots at this rank: 1, 2 from Chūnin hive, 3 at Clan Master. */
    public int speciesSlots() {
        return this.ordinal() >= CLAN_MASTER.ordinal() ? 3 : this.ordinal() >= CHUNIN.ordinal() ? 2 : 1;
    }

    /** The most Insect Clones out at once. */
    public int maxClones() {
        return this.ordinal() >= CLAN_MASTER.ordinal() ? 5 : 2;
    }
}

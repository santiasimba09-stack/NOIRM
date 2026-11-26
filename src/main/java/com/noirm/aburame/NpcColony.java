package com.noirm.aburame;

import com.noirm.bounty.NinjaClass;
import com.noirm.entity.SparringNinja;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * An Aburame NPC's colony (docs/ABURAME.md, 2.11), kept in the ninja's own saved data so nothing in
 * {@code SparringNinja} has to change. Rolled by class the first time it's needed:
 * <ul>
 *   <li>D–C (genin): 4,000–10,000, Genin hive (swarm, mark, clone);</li>
 *   <li>B (chūnin, scouts): 15,000–30,000, Chūnin hive (+ wall, sphere, jamming); shōkaichū 30%;</li>
 *   <li>A–S (jōnin, Root): 40,000–80,000, Jōnin hive (+ tornado, bug bite); rinkaichū 10%.</li>
 * </ul>
 */
public final class NpcColony {
    public static final String KEY = "noirm_colony";
    public static final float SHOKAICHU_CHANCE = 0.3f;
    public static final float RINKAICHU_CHANCE = 0.1f;
    private static final Map<SparringNinja, ColonyData> CACHE = new WeakHashMap<>();

    private NpcColony() {}

    /** Whether this ninja is of the Aburame. */
    public static boolean aburame(LivingEntity entity) {
        return entity instanceof SparringNinja ninja && "aburame".equals(ninja.clanId());
    }

    /** The ninja's colony (rolled the first time), or null if it isn't an Aburame. */
    public static ColonyData get(SparringNinja ninja) {
        if (!aburame(ninja) || ninja.level().isClientSide()) {
            return null;
        }
        ColonyData data = CACHE.get(ninja);
        if (data != null) {
            return data;
        }
        data = new ColonyData();
        CompoundTag tag = ninja.getPersistentData().getCompound(KEY);
        if (tag.getBoolean("Has")) {
            data.load(tag);
        } else {
            roll(ninja, data);
        }
        CACHE.put(ninja, data);
        save(ninja);
        return data;
    }

    static void roll(SparringNinja ninja, ColonyData data) {
        RandomSource r = ninja.getRandom();
        NinjaClass cls = NinjaClass.of(ninja);
        int lo, hi;
        HiveRank rank;
        switch (cls) {
            case D, C -> { lo = 4000; hi = 10000; rank = HiveRank.GENIN; }
            case B -> { lo = 15000; hi = 30000; rank = HiveRank.CHUNIN; }
            default -> { lo = 40000; hi = 80000; rank = HiveRank.JONIN; }
        }
        boolean rinkaichu = rank == HiveRank.JONIN && r.nextFloat() < RINKAICHU_CHANCE;
        data.setRinkaichuBorn(rinkaichu);
        data.setHas(true);
        data.setRank(rank);
        data.setBody(Species.KIKAICHU, lo + r.nextInt(hi - lo + 1));
        if (rinkaichu) {
            data.setBody(Species.RINKAICHU, 3000);
        }
        if (rank.ordinal() >= HiveRank.CHUNIN.ordinal() && r.nextFloat() < SHOKAICHU_CHANCE) {
            data.hosted().add(Species.SHOKAICHU);
            data.setBody(Species.SHOKAICHU, 2000);
        }
        if (rank == HiveRank.JONIN) {
            data.hosted().add(Species.KIDAICHU);
            data.setBody(Species.KIDAICHU, 600);
        }
    }

    public static void save(SparringNinja ninja) {
        ColonyData data = CACHE.get(ninja);
        if (data != null) {
            ninja.getPersistentData().put(KEY, data.save());
        }
    }

    /** Sets (or replaces) a ninja's colony, e.g. in tests. */
    public static void set(SparringNinja ninja, ColonyData data) {
        CACHE.put(ninja, data);
        save(ninja);
    }

    /** An NPC's body holds what it rolled, with a little room to regrow. */
    public static int capacity(LivingEntity entity) {
        if (entity instanceof SparringNinja ninja) {
            ColonyData data = get(ninja);
            if (data != null) {
                return switch (data.rank()) {
                    case NESTLING, GENIN -> 12000;
                    case CHUNIN -> 34000;
                    default -> 90000;
                };
            }
        }
        return 0;
    }

    /** NPC colonies slowly regrow toward capacity (a second's worth). */
    public static void tickSecond(SparringNinja ninja) {
        ColonyData data = get(ninja);
        if (data == null) {
            return;
        }
        int cap = capacity(ninja);
        if (data.bodyTotal() < cap) {
            data.addBody(Species.KIKAICHU, Math.min(cap - data.bodyTotal(), Math.round(ColonyGrowth.breedPerSecond(data.body(Species.KIKAICHU)) * 2)));
        }
        if (ninja.tickCount % 200 < 20) {
            save(ninja);
        }
    }
}

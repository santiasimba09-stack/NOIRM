package com.noirm.aburame;

import com.noirm.clan.Nature;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuCaster;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * How an Aburame ninja fights (docs/ABURAME.md, 2.11), called by the ninjas' fight goal when the ninja
 * has a colony:
 * <ol>
 *   <li>keep {@value #MIN_RANGE}–{@value #MAX_RANGE} blocks away; never start in melee;</li>
 *   <li>open with a small swarm (the quiet drain) and a female mark when close enough;</li>
 *   <li>then, from a Chūnin hive, the Sphere on a target already covered;</li>
 *   <li>hit by fire or an explosion: a clone, jamming and a retreat;</li>
 *   <li>against fire users, a wall first; Jōnin add Bug Bite on a covered target.</li>
 * </ol>
 * Returns true when it handled the tick (the usual close-in brawling is skipped).
 */
public final class AburameFightGoal {
    public static final double MIN_RANGE = 12, MAX_RANGE = 20;
    /** Ticks between technique decisions. */
    public static final int THINK_TICKS = 30;
    /** After a burn, they back off this long. */
    public static final int RETREAT_TICKS = 80;

    static final ResourceLocation SWARM = Colony.jutsu("parasitic_swarm"), MARK = Colony.jutsu("female_insect"),
            CLONE = Colony.jutsu("insect_clone"), WALL = Colony.jutsu("insect_wall"), SPHERE = Colony.jutsu("insect_sphere"),
            JAM = Colony.jutsu("insect_jamming"), BITE = Colony.jutsu("bug_bite");

    static final class State {
        int think;
        int retreat;
        boolean marked;
        boolean walled;
    }

    private static final Map<SparringNinja, State> STATES = new WeakHashMap<>();

    private AburameFightGoal() {}

    public static boolean tick(SparringNinja ninja, LivingEntity target, double distance) {
        ColonyData colony = Colony.of(ninja);
        if (colony == null) {
            return false;
        }
        State st = STATES.computeIfAbsent(ninja, n -> new State());
        boolean burned = ninja.getLastDamageSource() != null && ninja.tickCount - ninja.getLastHurtByMobTimestamp() < 30
                && (ninja.getLastDamageSource().is(DamageTypeTags.IS_FIRE) || ninja.getLastDamageSource().is(DamageTypeTags.IS_EXPLOSION));
        if (burned && st.retreat <= 0) {
            st.retreat = RETREAT_TICKS;
            cast(ninja, CLONE);
            if (colony.rank().ordinal() >= HiveRank.CHUNIN.ordinal()) {
                cast(ninja, JAM);
            }
        }
        // Distance: keep away, behind their swarms.
        if (st.retreat > 0) {
            st.retreat--;
            away(ninja, target, 1.3);
        } else if (distance < MIN_RANGE) {
            away(ninja, target, 1.15);
        } else if (distance > MAX_RANGE) {
            ninja.getNavigation().moveTo(target, 1.0);
        } else {
            ninja.getNavigation().stop();
        }
        if (--st.think > 0) {
            return distance > 3; // cornered: let it brawl
        }
        st.think = THINK_TICKS + ninja.getRandom().nextInt(20);
        boolean covered = Swarms.owned(ninja).stream().anyMatch(s -> s.target() == target
                && (s.mode() == SwarmMode.CLING || s.mode() == SwarmMode.SPHERE || s.mode() == SwarmMode.TRAVEL));
        boolean fireUser = target instanceof SparringNinja t && t.natures().containsKey(Nature.FIRE);
        if (fireUser && !st.walled && colony.rank().ordinal() >= HiveRank.GENIN.ordinal() && distance < 10) {
            st.walled = cast(ninja, WALL);
        } else if (!st.marked && distance <= 4) {
            st.marked = cast(ninja, MARK);
        } else if (!covered) {
            cast(ninja, SWARM); // the quiet drain first
        } else if (colony.rank().ordinal() >= HiveRank.CHUNIN.ordinal() && ninja.getRandom().nextFloat() < 0.5f) {
            cast(ninja, SPHERE);
        } else if (colony.rank().ordinal() >= HiveRank.JONIN.ordinal() && colony.hosts(Species.KIDAICHU)) {
            cast(ninja, BITE);
        } else if (colony.rank().ordinal() >= HiveRank.GENIN.ordinal() && ninja.getRandom().nextFloat() < 0.25f) {
            cast(ninja, CLONE);
        }
        return distance > 3;
    }

    static boolean cast(SparringNinja ninja, ResourceLocation id) {
        return JutsuCaster.npcReady(ninja, id) && JutsuCaster.npcCast(ninja, id, 1f);
    }

    static void away(SparringNinja ninja, LivingEntity target, double speed) {
        Vec3 away = ninja.position().subtract(target.position());
        away = new Vec3(away.x, 0, away.z);
        if (away.lengthSqr() < 1e-4) {
            away = new Vec3(1, 0, 0);
        }
        away = away.normalize().scale(6);
        ninja.getNavigation().moveTo(ninja.getX() + away.x, ninja.getY(), ninja.getZ() + away.z, speed);
    }
}

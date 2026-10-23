package com.noirm.world.wild;

import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Ninjas met out in the wild (see {@link WildSpawner}): who belongs to which group, who leads,
 * whether they're hostile, how a group fights together, and when they go away again.
 *
 * <p>A wild ninja is an ordinary {@link SparringNinja} with a {@code NoirmWild} tag in its Forge
 * persistent data (group id, encounter type, leader, hostile, spar, hunts rogues, formation slot).
 * The tag is read back into a small registry here whenever the ninja is loaded, so nothing is
 * scanned: groups, caps and despawning work from that registry.
 *
 * <ul>
 *   <li><b>Together:</b> group mates are allies; when one has a fight, the others nearby join it.</li>
 *   <li><b>Hostility:</b> rogues attack players they see; squads attack rogues; everyone else only
 *       fights back (or, for some lone wanderers, takes up a player's challenge).</li>
 *   <li><b>Despawning:</b> a group goes away when every member is more than {@link #DESPAWN_FAR}
 *       blocks from every player, or more than {@link #UNSEEN_NEAR} blocks away and unseen for
 *       {@link #UNSEEN_TICKS}. Crew mates, named ninjas and anyone made persistent never go.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class WildNinjas {
    public static final String TAG = "NoirmWild";
    /** A group goes away when no player is within this many blocks of any member. */
    public static final double DESPAWN_FAR = 128;
    /** Or when no player is this close and none has seen them for {@link #UNSEEN_TICKS}. */
    public static final double UNSEEN_NEAR = 64;
    public static final int UNSEEN_TICKS = 20 * 60;
    /** A player this close counts as seeing them even without a clear line of sight. */
    public static final double SEEN_ALWAYS = 24;
    /** How long (ticks) a group mate's fight calls the others, and how far it carries. */
    public static final int ALERT_TICKS = 200;
    public static final double ALERT_RANGE = 32;
    /** A follower further than this from the leader makes the leader wait. */
    public static final double STRAGGLE = 10;

    /** One wild ninja's part in its group (mirrors the saved tag). */
    public static final class Member {
        public final UUID group;
        public final ResourceLocation type;
        boolean leader;
        public final boolean hostile;
        public final boolean spar;
        public final boolean huntsRogues;
        public final int slot;
        long lastSeen;

        public Member(UUID group, ResourceLocation type, boolean leader, boolean hostile, boolean spar, boolean huntsRogues, int slot) {
            this.group = group;
            this.type = type;
            this.leader = leader;
            this.hostile = hostile;
            this.spar = spar;
            this.huntsRogues = huntsRogues;
            this.slot = slot;
        }

        public boolean isLeader() {
            return leader;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Group", group);
            tag.putString("Type", type.toString());
            tag.putBoolean("Leader", leader);
            tag.putBoolean("Hostile", hostile);
            tag.putBoolean("Spar", spar);
            tag.putBoolean("HuntsRogues", huntsRogues);
            tag.putInt("Slot", slot);
            return tag;
        }

        static Member load(CompoundTag tag) {
            if (!tag.hasUUID("Group")) {
                return null;
            }
            ResourceLocation type = ResourceLocation.tryParse(tag.getString("Type"));
            return new Member(tag.getUUID("Group"), type == null ? Encounters.WANDERER : type, tag.getBoolean("Leader"),
                    tag.getBoolean("Hostile"), tag.getBoolean("Spar"), tag.getBoolean("HuntsRogues"), tag.getInt("Slot"));
        }
    }

    /** A group's members (as loaded right now) and the fight it's in. */
    static final class Group {
        final Set<SparringNinja> members = Collections.newSetFromMap(new WeakHashMap<>());
        SparringNinja leader;
        LivingEntity alert;
        long alertAt;
        /** The furthest a follower is from the leader (updated by the leader). */
        double spread;
    }

    private static final Map<SparringNinja, Member> MEMBERS = new WeakHashMap<>();
    private static final Map<UUID, Group> GROUPS = new HashMap<>();

    private WildNinjas() {}

    // ------------------------------------------------------------ registry

    /** Makes {@code ninja} a wild ninja (before it's added to the world). */
    public static void mark(SparringNinja ninja, Member member) {
        ninja.getPersistentData().put(TAG, member.save());
        register(ninja, member);
    }

    private static void register(SparringNinja ninja, Member member) {
        MEMBERS.put(ninja, member);
        GROUPS.computeIfAbsent(member.group, g -> new Group()).members.add(ninja);
    }

    private static void unregister(SparringNinja ninja) {
        Member member = MEMBERS.remove(ninja);
        if (member == null) {
            return;
        }
        Group group = GROUPS.get(member.group);
        if (group != null) {
            group.members.remove(ninja);
            if (group.leader == ninja) {
                group.leader = null;
            }
            if (group.members.isEmpty()) {
                GROUPS.remove(member.group);
            }
        }
    }

    /** No longer a wild ninja (they joined a crew): forgets the tag for good. */
    public static void forget(SparringNinja ninja) {
        unregister(ninja);
        ninja.getPersistentData().remove(TAG);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof SparringNinja ninja) || MEMBERS.containsKey(ninja)) {
            return;
        }
        CompoundTag data = ninja.getPersistentData();
        if (data.contains(TAG)) {
            Member member = Member.load(data.getCompound(TAG));
            if (member != null) {
                member.lastSeen = event.getLevel().getGameTime();
                register(ninja, member);
            }
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof SparringNinja ninja) {
            unregister(ninja);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MEMBERS.clear();
        GROUPS.clear();
    }

    // ------------------------------------------------------------ questions

    /** {@code ninja}'s place in a wild group, or null if it isn't a wild ninja (or has joined a crew). */
    public static Member member(Entity entity) {
        return entity instanceof SparringNinja ninja && ninja.leaderId() == null ? MEMBERS.get(ninja) : null;
    }

    public static boolean isWild(Entity entity) {
        return member(entity) != null;
    }

    /** A wild rogue (hostile to players). */
    public static boolean isRogue(Entity entity) {
        Member member = member(entity);
        return member != null && member.hostile;
    }

    public static boolean sameGroup(Entity a, Entity b) {
        Member ma = member(a), mb = member(b);
        return ma != null && mb != null && ma.group.equals(mb.group);
    }

    /** The loaded members of {@code ninja}'s group (including itself; empty if not wild). */
    public static List<SparringNinja> groupOf(Entity entity) {
        Member member = member(entity);
        Group group = member == null ? null : GROUPS.get(member.group);
        if (group == null) {
            return List.of();
        }
        List<SparringNinja> out = new ArrayList<>();
        for (SparringNinja n : group.members) {
            if (n.isAlive() && n.leaderId() == null) {
                out.add(n);
            }
        }
        return out;
    }

    /** Who leads {@code ninja}'s group: the member marked leader, or (if they're gone) the next one, promoted. */
    public static SparringNinja leaderOf(Entity entity) {
        Member member = member(entity);
        Group group = member == null ? null : GROUPS.get(member.group);
        if (group == null) {
            return null;
        }
        SparringNinja leader = group.leader;
        if (leader != null && leader.isAlive() && member(leader) != null) {
            return leader;
        }
        group.leader = null;
        SparringNinja first = null;
        for (SparringNinja n : group.members) {
            Member m = member(n);
            if (m == null || !n.isAlive()) {
                continue;
            }
            if (m.leader) {
                group.leader = n;
                return n;
            }
            if (first == null || m.slot < MEMBERS.get(first).slot) {
                first = n;
            }
        }
        if (first != null) {
            Member m = MEMBERS.get(first); // the leader fell: the next in line takes over
            m.leader = true;
            first.getPersistentData().put(TAG, m.save());
            group.leader = first;
        }
        return first;
    }

    /** Whether the leader should wait: a follower has fallen behind. */
    public static boolean straggling(SparringNinja leader) {
        Member member = member(leader);
        Group group = member == null ? null : GROUPS.get(member.group);
        return group != null && group.spread > STRAGGLE;
    }

    /**
     * Whether a wild {@code ninja} goes after {@code target} on its own: rogues attack players (not
     * in creative or spectator), squads attack rogues. Never a group mate, never a crew of its own.
     */
    public static boolean wouldAttack(SparringNinja ninja, LivingEntity target) {
        Member member = member(ninja);
        if (member == null || ninja.isYielded() || target == ninja || !target.isAlive() || sameGroup(ninja, target)) {
            return false;
        }
        if (target instanceof Player player) {
            return member.hostile && !player.isCreative() && !player.isSpectator();
        }
        return member.huntsRogues && isRogue(target);
    }

    /** Whether this wild ninja ever picks a fight by itself (so its targeting goal is worth running). */
    public static boolean seeksFights(SparringNinja ninja) {
        Member member = member(ninja);
        return member != null && (member.hostile || member.huntsRogues);
    }

    /**
     * Whether {@code ninja} may challenge a player who goes into combat mode nearby. Ninjas that
     * aren't wild keep doing so; wild ones only if they're the sparring kind.
     */
    public static boolean mayChallenge(SparringNinja ninja) {
        Member member = MEMBERS.get(ninja);
        return member == null || member.spar || member.hostile;
    }

    // ------------------------------------------------------------ every tick (from the ninja's AI step)

    /** Per ninja, server side: leaves the wild for a crew, shares fights with group mates, keeps the leader's view of its group. */
    public static void tick(SparringNinja ninja) {
        Member member = MEMBERS.get(ninja);
        if (member == null) {
            return;
        }
        if (ninja.leaderId() != null) {
            forget(ninja); // recruited: a crew mate now, never despawns
            return;
        }
        if ((ninja.tickCount + ninja.getId()) % 10 != 0) {
            return;
        }
        Group group = GROUPS.get(member.group);
        if (group == null) {
            return;
        }
        long now = ninja.level().getGameTime();
        LivingEntity target = ninja.getTarget();
        if (target != null && target.isAlive() && !sameGroup(ninja, target)) {
            group.alert = target; // "with me!"
            group.alertAt = now;
        } else if (target == null && !ninja.isYielded() && group.alert != null && group.alert.isAlive()
                && now - group.alertAt < ALERT_TICKS && ninja.distanceToSqr(group.alert) < ALERT_RANGE * ALERT_RANGE
                && ninja.canAttack(group.alert) && !ninja.isAlliedTo(group.alert)) {
            ninja.setTarget(group.alert);
        }
        if (leaderOf(ninja) == ninja) {
            double spread = 0;
            for (SparringNinja other : group.members) {
                if (other != ninja && other.isAlive() && other.getTarget() == null) {
                    spread = Math.max(spread, other.distanceTo(ninja));
                }
            }
            group.spread = spread;
        }
    }

    // ------------------------------------------------------------ caps and despawning

    /** How many wild groups and ninjas are loaded within {@code radius} blocks of {@code pos}: {groups, ninjas}. */
    public static int[] countNear(ServerLevel level, BlockPos pos, double radius) {
        Set<UUID> groups = new java.util.HashSet<>();
        int ninjas = 0;
        double r2 = radius * radius;
        for (Map.Entry<SparringNinja, Member> entry : MEMBERS.entrySet()) {
            SparringNinja n = entry.getKey();
            if (n.level() == level && n.isAlive() && !n.isRemoved() && n.leaderId() == null && n.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= r2) {
                groups.add(entry.getValue().group);
                ninjas++;
            }
        }
        return new int[] {groups.size(), ninjas};
    }

    /**
     * Whether a wild ninja may go away: never a crew mate, a named ninja or one made persistent;
     * otherwise when the nearest player is beyond {@link #DESPAWN_FAR}, or beyond
     * {@link #UNSEEN_NEAR} and nobody has seen it for {@link #UNSEEN_TICKS}.
     */
    public static boolean mayDespawn(SparringNinja ninja, double nearestPlayer, long ticksUnseen) {
        if (ninja.leaderId() != null || ninja.inCrew() || ninja.hasCustomName() || ninja.isPersistenceRequired()
                || ninja.isResident() || MEMBERS.get(ninja) == null) {
            return false;
        }
        return nearestPlayer > DESPAWN_FAR || nearestPlayer > UNSEEN_NEAR && ticksUnseen > UNSEEN_TICKS;
    }

    /**
     * Goes over the wild ninjas in {@code level} (every few seconds, from {@link WildSpawner}):
     * notes who is seen, and removes each group whose every loaded member may despawn.
     * Returns how many ninjas were removed.
     */
    public static int sweep(ServerLevel level) {
        long now = level.getGameTime();
        Map<UUID, List<SparringNinja>> byGroup = new HashMap<>();
        Map<UUID, Boolean> canGo = new HashMap<>();
        for (Map.Entry<SparringNinja, Member> entry : new ArrayList<>(MEMBERS.entrySet())) {
            SparringNinja n = entry.getKey();
            Member m = entry.getValue();
            if (n.level() != level || !n.isAlive()) {
                continue;
            }
            Player nearest = level.getNearestPlayer(n, -1);
            double distance = nearest == null ? Double.MAX_VALUE : nearest.distanceTo(n);
            if (nearest != null && distance <= UNSEEN_NEAR && (distance <= SEEN_ALWAYS || nearest.hasLineOfSight(n))) {
                m.lastSeen = now;
            }
            byGroup.computeIfAbsent(m.group, g -> new ArrayList<>()).add(n);
            canGo.merge(m.group, mayDespawn(n, distance, now - m.lastSeen), Boolean::logicalAnd);
        }
        int removed = 0;
        for (Map.Entry<UUID, List<SparringNinja>> entry : byGroup.entrySet()) {
            if (canGo.getOrDefault(entry.getKey(), false)) {
                for (SparringNinja n : entry.getValue()) {
                    n.discard();
                    removed++;
                }
            }
        }
        return removed;
    }

    /** Forgets when a ninja was last seen (tests). */
    public static void setLastSeen(SparringNinja ninja, long time) {
        Member member = MEMBERS.get(ninja);
        if (member != null) {
            member.lastSeen = time;
        }
    }
}

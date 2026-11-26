package com.noirm.aburame;

import com.noirm.jutsu.JutsuBehavior;
import com.noirm.jutsu.JutsuBehaviors;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuEffects;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The Aburame's techniques (docs/ABURAME.md, 2.5): what each {@code "behavior"} in
 * {@code data/noirm/jutsu/aburame/} does. None has hand seals; each sends insects (its {@code hiden}
 * block says how many), and the numbers live in the JSON and at the top of {@link ColonyGrowth}.
 */
public final class AburameBehaviors {
    /** Clone: how many it takes, Legion's five. */
    public static final int LEGION_CLONES = 5;
    /** Poison Draw: one effect every 2 s, and the insects that drew it die (unless Antivenom). */
    public static final int DRAW_INTERVAL = 40, DRAW_INSECTS = 200;
    /** Chakra Gift: the most it hands over. */
    public static final float GIFT_MAX = 60f;
    /** Cocoon: breeding ×6, generations ×3. */
    public static final float COCOON_BREED = 6f, COCOON_AGE = 3f;
    /** Insect Gathering: catch radius, what it can tell you about. */
    public static final double GATHER_RADIUS = 16, GATHER_TELL = 160;
    /** Swarm Body: what it costs, how far you re-form, its cooldown and invulnerable time. */
    public static final float SWARM_BODY_COST = 0.4f;
    public static final double SWARM_BODY_JUMP = 12;
    public static final long SWARM_BODY_COOLDOWN = 20 * 300;
    public static final int SWARM_BODY_INVULNERABLE = 30;
    /** The Black Sea floods for 20 s and needs a day to regrow. */
    public static final int SEA_TICKS = 400;
    public static final long SEA_REGROW = 24000;
    /** The Jar of Poison needs this many rot corpses within its barrier. */
    public static final int JAR_CORPSES = 3;

    private AburameBehaviors() {}

    /** The behaviours, added to {@code JutsuBehaviors}. */
    public static Map<String, JutsuBehavior> all() {
        return Map.ofEntries(
                Map.entry("swarm_send", AburameBehaviors::send),
                Map.entry("swarm_mark", AburameBehaviors::mark),
                Map.entry("swarm_clone", AburameBehaviors::clone),
                Map.entry("swarm_wall", AburameBehaviors::wall),
                Map.entry("swarm_scout", AburameBehaviors::scout),
                Map.entry("poison_draw", POISON_DRAW),
                Map.entry("swarm_sphere", AburameBehaviors::sphere),
                Map.entry("swarm_jam", AburameBehaviors::jam),
                Map.entry("swarm_dome", DOME),
                Map.entry("swarm_tether", AburameBehaviors::tether),
                Map.entry("insect_gathering", AburameBehaviors::gathering),
                Map.entry("insect_cocoon", COCOON),
                Map.entry("bug_bite", AburameBehaviors::bugBite),
                Map.entry("swarm_tornado", TORNADO),
                Map.entry("chakra_gift", AburameBehaviors::gift),
                Map.entry("poison_cloud", AburameBehaviors::poisonCloud),
                Map.entry("jar_of_poison", AburameBehaviors::jarOfPoison),
                Map.entry("living_hive", AburameBehaviors::livingHive),
                Map.entry("black_sea", AburameBehaviors::blackSea),
                Map.entry("insect_legion", AburameBehaviors::legion),
                Map.entry("giant_insect", AburameBehaviors::giantInsect));
    }

    // ================================================================ aiming

    /**
     * Who a technique goes for: an NPC's target; otherwise the creature you look at; otherwise one of
     * your marked creatures in range (a mark needs no line of sight, and invisibility doesn't hide it).
     */
    public static LivingEntity target(LivingEntity caster, double range) {
        if (caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive() && mob.distanceTo(mob.getTarget()) <= range) {
            return mob.getTarget();
        }
        LivingEntity looked = JutsuBehaviors.lookTarget(caster, range, 20);
        if (looked != null) {
            return looked;
        }
        ColonyData colony = Colony.of(caster);
        if (colony == null || !(caster.level() instanceof ServerLevel level)) {
            return null;
        }
        Vec3 look = caster.getViewVector(1f);
        LivingEntity best = null;
        double bestDot = Math.cos(Math.toRadians(45));
        for (ColonyData.Mark m : colony.marks()) {
            Entity e = level.getEntity(m.target());
            if (e instanceof LivingEntity l && l.isAlive() && l.distanceTo(caster) <= range * 4) {
                double dot = look.dot(l.position().subtract(caster.position()).normalize());
                if (dot > bestDot) {
                    bestDot = dot;
                    best = l;
                }
            }
        }
        return best;
    }

    /** The block you look at within {@code range}, or the point that far ahead. */
    static Vec3 aimPoint(LivingEntity caster, double range) {
        if (caster instanceof Mob mob && mob.getTarget() != null) {
            return mob.getTarget().position();
        }
        Vec3 start = caster.getEyePosition();
        Vec3 end = start.add(caster.getViewVector(1f).scale(range));
        BlockHitResult hit = caster.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    static void tell(LivingEntity to, String key, Object... args) {
        if (to instanceof Player p) {
            p.displayClientMessage(Component.translatable(key, args), true);
        }
    }

    // ================================================================ the Nestling's kit

    /** Parasitic Swarm: a homing swarm that clings and drains (range 28, 12 blocks a second). */
    static void send(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        LivingEntity target = target(caster, ctx.params().range());
        InsectSwarm swarm = Swarms.send(caster, ctx.id(), ctx.def(), Swarms.chest(caster), SwarmMode.TRAVEL);
        if (swarm == null) {
            return;
        }
        if (target != null) {
            swarm.setTarget(target, SwarmMode.CLING);
        } else {
            swarm.setPark(aimPoint(caster, ctx.params().range()).add(0, 1, 0)); // nothing to cover: an ambush cloud there
        }
    }

    /** Female Insect: a mark on a creature for 20 minutes (touch range, or the one you look at nearby). */
    static void mark(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, ctx.params().range(), 25);
        if (target == null && caster instanceof Mob mob) {
            target = mob.getTarget();
        }
        if (target == null || target.distanceTo(caster) > ctx.params().range() + 1) {
            tell(caster, "message.noirm.colony.mark_miss");
            return;
        }
        Colony.commit(caster, Species.KIKAICHU, 1);
        Colony.killed(caster, 1, Harm.JUTSU); // she stays with the target
        Colony.mark(caster, target, ctx.params().duration() > 20 ? ctx.params().duration() : Colony.MARK_TICKS);
        if (caster.level() instanceof ServerLevel level) {
            level.sendParticles(SwarmFx.TEAL, target.getX(), target.getY() + target.getBbHeight() * 0.8, target.getZ(), 6, 0.1, 0.1, 0.1, 0);
        }
        tell(caster, "message.noirm.colony.marked", target.getName());
    }

    // ================================================================ Genin hive

    /** Insect Clone: a copy of you made of insects, at your feet or at one of your swarms. */
    static void clone(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        ColonyData colony = Colony.of(caster);
        int max = colony == null ? 2 : colony.rank().maxClones();
        List<InsectClone> mine = level.getEntitiesOfClass(InsectClone.class, caster.getBoundingBox().inflate(64), c -> c.isOwnedBy(caster));
        if (mine.size() >= max) {
            mine.get(0).dispel();
        }
        Vec3 at = caster.position().add(caster.getViewVector(1f).multiply(1, 0, 1).normalize().scale(1.5));
        // Where your insects already are: the nearest of your swarms to where you look.
        Vec3 aim = aimPoint(caster, 40);
        InsectSwarm near = Swarms.owned(caster).stream().filter(s -> s.mode() != SwarmMode.RETURN)
                .min(Comparator.comparingDouble(s -> s.position().distanceToSqr(aim))).orElse(null);
        if (near != null) {
            at = near.position();
        }
        spawnClone(caster, ctx, at, false);
    }

    static InsectClone spawnClone(LivingEntity caster, JutsuContext ctx, Vec3 at, boolean legion) {
        ServerLevel level = (ServerLevel) caster.level();
        int insects = ctx.def().hiden().map(h -> h.insects()).orElse(2500) / (legion ? LEGION_CLONES : 1);
        Colony.Commit commit = Colony.commit(caster, Species.KIKAICHU, insects);
        if (commit.count() <= 0) {
            return null;
        }
        InsectClone clone = AburameRegistry.INSECT_CLONE.get().create(level);
        if (clone == null) {
            Colony.returnHome(caster, Species.KIKAICHU, commit.count(), 0);
            return null;
        }
        clone.moveTo(at.x, at.y, at.z, caster.getYRot(), 0f);
        clone.setOwner(caster);
        clone.setInsects(commit.count());
        clone.setLegion(legion);
        if (legion) {
            clone.knownJutsu().clear();
            clone.knownJutsu().add(Colony.jutsu("parasitic_swarm"));
        } else {
            clone.knownJutsu().clear(); // a simple decoy: it punches, nothing more
        }
        level.addFreshEntity(clone);
        level.sendParticles(SwarmFx.DUST, at.x, at.y + 1, at.z, 50, 0.3, 0.8, 0.3, 0.05);
        return clone;
    }

    /** Insect Wall: 6 wide, 4 high, 2 blocks ahead, for 8 s. */
    static void wall(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        Vec3 flat = ctx.flatLook();
        Vec3 at = caster.position().add(flat.scale(2.5)).add(0, InsectSwarm.WALL_HEIGHT / 2, 0);
        InsectSwarm swarm = Swarms.send(caster, ctx.id(), ctx.def(), at, SwarmMode.WALL);
        if (swarm != null) {
            swarm.setShape(caster.getYRot());
            swarm.setPark(at);
            swarm.setLife(ctx.params().duration());
        }
    }

    /** Scout Swarm: flies to a spot up to 200 blocks away, watches for 3 minutes, reports, draws a map. */
    static void scout(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        Vec3 at = aimPoint(caster, ctx.params().range()).add(0, 2, 0);
        InsectSwarm swarm = Swarms.send(caster, ctx.id(), ctx.def(), Swarms.chest(caster), SwarmMode.TRAVEL);
        if (swarm != null) {
            swarm.setPark(at);
            swarm.setTarget(null, SwarmMode.SCOUT);
        }
    }

    /** Poison Draw: touch yourself or an ally; insects pull one harmful effect out every 2 s. */
    static final JutsuBehavior POISON_DRAW = new JutsuBehavior() {
        @Override
        public void cast(JutsuContext ctx) {
            tell(ctx.caster(), "message.noirm.colony.drawing");
        }

        @Override
        public void channel(JutsuContext ctx, int tick) {
            if (tick % DRAW_INTERVAL != 0) {
                return;
            }
            LivingEntity caster = ctx.caster();
            LivingEntity patient = friendInReach(caster, 3);
            if (patient == null) {
                patient = caster;
            }
            if (drawOne(patient)) {
                ColonyData colony = Colony.of(caster);
                if (colony != null && !colony.antivenom()) {
                    Colony.commit(caster, Species.KIKAICHU, DRAW_INSECTS);
                    Colony.killed(caster, DRAW_INSECTS, Harm.POISON); // they die drawing it
                }
                if (caster.level() instanceof ServerLevel level) {
                    level.sendParticles(SwarmFx.DUST, patient.getX(), patient.getY() + 1, patient.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
                }
            }
        }
    };

    static LivingEntity friendInReach(LivingEntity caster, double range) {
        Vec3 look = caster.getViewVector(1f);
        return caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range),
                        e -> e != caster && e.isAlive() && JutsuEffects.isFriendly(caster, e)
                                && look.dot(e.position().subtract(caster.position()).normalize()) > 0.5)
                .stream().min(Comparator.comparingDouble(caster::distanceTo)).orElse(null);
    }

    /** Pulls out one poison: Cell Rot, burrowed kidaichū, or any harmful effect. */
    public static boolean drawOne(LivingEntity patient) {
        if (RotTouch.stacks(patient) > 0) {
            return RotTouch.cure(patient);
        }
        if (BugBite.cure(patient)) {
            return true;
        }
        for (MobEffectInstance e : new ArrayList<>(patient.getActiveEffects())) {
            MobEffect effect = e.getEffect();
            if (effect.getCategory() == MobEffectCategory.HARMFUL && effect != AburameRegistry.DRAINED.get() && effect != AburameRegistry.EXHAUSTED.get()) {
                patient.removeEffect(effect);
                return true;
            }
        }
        return false;
    }

    /** Insect Gathering: calls the wild insects nearby, catches one you lack, and tells you one thing. */
    static void gathering(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        if (!(caster instanceof Player player) || !(caster.level() instanceof ServerLevel level)) {
            return;
        }
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        List<WildInsect> bugs = level.getEntitiesOfClass(WildInsect.class, caster.getBoundingBox().inflate(GATHER_RADIUS));
        WildInsect pick = bugs.stream().filter(b -> data != null && !data.colony().caught().contains(b.kind().key())).findFirst().orElse(null);
        if (pick != null) {
            WildInsect.catchInto(player, pick);
        }
        for (WildInsect b : bugs) {
            if (!b.isRemoved()) {
                b.getNavigation().stop();
                b.teleportTo(b.getX() + (caster.getX() - b.getX()) * 0.5, b.getY(), b.getZ() + (caster.getZ() - b.getZ()) * 0.5);
            }
        }
        // What the bugs saw: the nearest ninja within 160 blocks.
        Entity nearest = level.getEntitiesOfClass(com.noirm.entity.SparringNinja.class, caster.getBoundingBox().inflate(GATHER_TELL),
                n -> n.isAlive() && !(n instanceof InsectClone)).stream().min(Comparator.comparingDouble(caster::distanceTo)).orElse(null);
        if (nearest != null) {
            Vec3 to = nearest.position().subtract(caster.position());
            player.sendSystemMessage(Component.translatable("message.noirm.colony.gathering_tells", nearest.getName(),
                    Math.round(to.length()), AburameItems.compass(to)).withStyle(ChatFormatting.DARK_AQUA));
        } else {
            player.sendSystemMessage(Component.translatable("message.noirm.colony.gathering_quiet").withStyle(ChatFormatting.GRAY));
        }
        level.sendParticles(SwarmFx.TEAL, caster.getX(), caster.getY() + 0.1, caster.getZ(), 60, GATHER_RADIUS / 3, 0.1, GATHER_RADIUS / 3, 0.01);
    }

    /** Insect Cocoon: you can't move; your colony breeds ×6 and ages ×3. Sneak + cast: a Human Cocoon (sets your spawn). */
    static final JutsuBehavior COCOON = new JutsuBehavior() {
        @Override
        public void cast(JutsuContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster instanceof ServerPlayer player && player.isShiftKeyDown()) {
                player.setRespawnPosition(player.level().dimension(), player.blockPosition(), player.getYRot(), true, false);
                player.displayClientMessage(Component.translatable("message.noirm.colony.human_cocoon"), false);
            }
        }

        @Override
        public void channel(JutsuContext ctx, int tick) {
            LivingEntity caster = ctx.caster();
            caster.setDeltaMovement(0, Math.min(0, caster.getDeltaMovement().y), 0);
            caster.hurtMarked = true;
            if (caster.level() instanceof ServerLevel level && tick % 5 == 0) {
                level.sendParticles(SwarmFx.DUST, caster.getX(), caster.getY() + 1, caster.getZ(), 12, 0.35, 0.7, 0.35, 0.01);
            }
            if (tick % 20 == 0 && caster instanceof Player player) {
                ShinobiData data = ShinobiCapability.get(player).orElse(null);
                if (data != null && data.colony().has()) {
                    Colony.breed(player, data, data.colony(), COCOON_BREED - 1f); // on top of the usual second
                    Colony.age(player, data.colony(), COCOON_AGE - 1f);
                    data.markPowersDirty();
                }
            }
        }
    };

    // ================================================================ Chūnin hive

    /** Insect Sphere: insects gather at once into a ball around the target (needs a target). */
    static void sphere(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        LivingEntity target = target(caster, ctx.params().range());
        if (target == null) {
            tell(caster, "message.noirm.colony.no_target");
            return;
        }
        // They spread wide and close in at once: the ball forms right at the target.
        InsectSwarm swarm = Swarms.send(caster, ctx.id(), ctx.def(), target.position().add(0, target.getBbHeight() / 2, 0), SwarmMode.TRAVEL);
        if (swarm != null) {
            swarm.setTarget(target, SwarmMode.SPHERE);
            swarm.setSpeedMul(3f);
        }
    }

    /** Insect Jamming: 30 s of false chakra over 24 blocks around you. */
    static void jam(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        InsectSwarm swarm = Swarms.send(caster, ctx.id(), ctx.def(), caster.position().add(0, 1.5, 0), SwarmMode.JAM);
        if (swarm != null) {
            swarm.setShape(SwarmShapes.JAM_RADIUS);
            swarm.setPark(caster.position().add(0, 1.5, 0));
            swarm.setLife(ctx.params().duration());
        }
    }

    /** Insect Jar (the dome): hold to keep it spinning round you. */
    static final JutsuBehavior DOME = new JutsuBehavior() {
        @Override
        public void cast(JutsuContext ctx) {
            InsectSwarm swarm = Swarms.send(ctx.caster(), ctx.id(), ctx.def(), ctx.caster().position().add(0, 1, 0), SwarmMode.DOME);
            if (swarm != null) {
                swarm.setShape(InsectSwarm.DOME_RADIUS);
                swarm.setLife(10);
            }
        }

        @Override
        public void channel(JutsuContext ctx, int tick) {
            for (InsectSwarm s : Swarms.owned(ctx.caster())) {
                if (s.mode() == SwarmMode.DOME) {
                    s.setLife(10); // held: it keeps spinning (and goes home half a second after you let go)
                }
            }
        }
    };

    /** Insect Tether: a rope of insects pulls you to a block (and breaks a fall). */
    static void tether(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        Vec3 point = aimPoint(caster, ctx.params().range());
        if (point.distanceTo(caster.getEyePosition()) >= ctx.params().range() - 0.5) {
            tell(caster, "message.noirm.colony.tether_miss");
            return;
        }
        Vec3 pull = point.subtract(caster.position()).normalize().scale(Math.min(2.2, point.distanceTo(caster.position()) * 0.18 + 0.6));
        caster.setDeltaMovement(pull.add(0, 0.3, 0));
        caster.hurtMarked = true;
        caster.fallDistance = 0;
        InsectSwarm rope = Swarms.send(caster, ctx.id(), ctx.def(), point, SwarmMode.TETHER);
        if (rope != null) {
            rope.setPark(point);
        }
        if (caster.level() instanceof ServerLevel level) {
            Vec3 from = caster.getEyePosition();
            for (int i = 0; i <= 16; i++) {
                Vec3 p = from.lerp(point, i / 16.0);
                level.sendParticles(SwarmFx.DUST, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
            }
        }
    }

    // ================================================================ Jōnin hive

    /** Bug Bite: kidaichū on the skin (touch range, or riding a swarm already clinging to the target). */
    static void bugBite(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        LivingEntity target = target(caster, 24);
        if (target == null) {
            tell(caster, "message.noirm.colony.no_target");
            return;
        }
        boolean riding = Swarms.owned(caster).stream().anyMatch(s -> s.target() == target && (s.mode() == SwarmMode.CLING || s.mode() == SwarmMode.SPHERE));
        if (!riding && target.distanceTo(caster) > ctx.params().range() + 0.5) {
            tell(caster, "message.noirm.colony.too_far");
            return;
        }
        if (BugBite.wrongDose(caster) && caster.getRandom().nextFloat() < BugBite.HOST_BITE_CHANCE) {
            BugBite.biteHost(caster); // the wrong dose: they bite their host
            tell(caster, "message.noirm.colony.wrong_dose");
            return;
        }
        int n = ctx.def().hiden().map(h -> h.insects()).orElse(300);
        Colony.Commit c = Colony.commit(caster, Species.KIDAICHU, n);
        if (c.count() <= 0) {
            tell(caster, "message.noirm.jutsu.needs_colony");
            return;
        }
        Colony.killed(caster, c.count(), Harm.JUTSU); // they stay in the victim
        BugBite.start(caster, target);
    }

    /** Insect Tornado: hold; the target is lifted, bitten and drained, and enemies around are pulled in. */
    static final JutsuBehavior TORNADO = new JutsuBehavior() {
        @Override
        public void cast(JutsuContext ctx) {
            LivingEntity caster = ctx.caster();
            LivingEntity target = target(caster, ctx.params().range());
            if (target == null) {
                tell(caster, "message.noirm.colony.no_target");
                return;
            }
            InsectSwarm swarm = Swarms.send(caster, ctx.id(), ctx.def(), target.position().add(0, 1, 0), SwarmMode.TORNADO);
            if (swarm != null) {
                swarm.setTarget(target, SwarmMode.TORNADO);
                swarm.setLife(ctx.def().maxDuration());
            }
        }

        @Override
        public void channel(JutsuContext ctx, int tick) {
            if (tick % 5 != 0 || !(ctx.caster().level() instanceof ServerLevel level)) {
                return;
            }
            for (InsectSwarm s : Swarms.owned(ctx.caster())) {
                if (s.mode() == SwarmMode.TORNADO) {
                    Vec3 c = s.center();
                    for (int i = 0; i < 6; i++) {
                        double a = tick * 0.6 + i;
                        level.sendParticles(SwarmFx.DUST, c.x + Math.cos(a) * 1.5, c.y - 1.5 + i * 0.6, c.z + Math.sin(a) * 1.5, 2, 0.1, 0.1, 0.1, 0.02);
                    }
                }
            }
        }
    };

    /** Chakra Gift: your swarms' banked chakra (what they ate and haven't digested) to an ally; revives a Drained one. */
    static void gift(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        LivingEntity ally = friendInReach(caster, ctx.params().range());
        ColonyData colony = Colony.of(caster);
        if (ally == null || colony == null) {
            tell(caster, "message.noirm.colony.no_ally");
            return;
        }
        float give = Math.min(GIFT_MAX, colony.banked());
        colony.setBanked(colony.banked() - give);
        Drain.give(ally, give);
        Drain.revive(ally);
        Colony.changed(caster);
        if (caster.level() instanceof ServerLevel level) {
            level.sendParticles(SwarmFx.TEAL, ally.getX(), ally.getY() + 1, ally.getZ(), 30, 0.3, 0.6, 0.3, 0.03);
        }
        tell(caster, "message.noirm.colony.gift", Math.round(give), ally.getName());
    }

    /** Poison Cloud: a growing cone of rinkaichū in front of you (allies too). */
    static void poisonCloud(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        InsectSwarm swarm = Swarms.send(caster, ctx.id(), ctx.def(), caster.getEyePosition(), SwarmMode.POISON);
        if (swarm != null) {
            swarm.setYRot(caster.getYRot());
            swarm.setShape(SwarmShapes.POISON_START);
            swarm.setLife(ctx.params().duration());
        }
    }

    // ================================================================ Clan Master

    /** The Jar of Poison (kinjutsu): a barrier around 3+ rotting corpses; 20 s later a poison storm over 48 blocks. */
    static void jarOfPoison(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        int corpses = RotTouch.corpsesNear(level, caster.position(), SwarmShapes.JAR_BARRIER);
        if (corpses < JAR_CORPSES) {
            tell(caster, "message.noirm.colony.jar_needs", corpses, JAR_CORPSES);
            return;
        }
        InsectSwarm swarm = Swarms.send(caster, Species.RINKAICHU, 1000, caster.position(), SwarmMode.JAR, 1f, 1f);
        if (swarm == null) {
            swarm = Swarms.send(caster, Species.KIKAICHU, 1000, caster.position(), SwarmMode.JAR, 1f, 1f);
        }
        if (swarm != null) {
            swarm.setPark(caster.position());
            swarm.setShape(SwarmShapes.JAR_BARRIER);
            swarm.setLife(SwarmShapes.JAR_RITUAL_TICKS);
            tell(caster, "message.noirm.colony.jar_begins");
        }
    }

    /** The Giant Insect: summoned from a bonded Queen. */
    static void giantInsect(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ColonyData colony = Colony.of(caster);
        if (!(caster instanceof Player player) || colony == null || !colony.queen() || !(caster.level() instanceof ServerLevel level)) {
            tell(caster, "message.noirm.colony.needs_queen");
            return;
        }
        level.getEntitiesOfClass(GiantInsect.class, caster.getBoundingBox().inflate(128),
                g -> g.owner().map(player.getUUID()::equals).orElse(false)).forEach(GiantInsect::discard);
        GiantInsect mount = AburameRegistry.GIANT_INSECT.get().create(level);
        if (mount == null) {
            return;
        }
        Vec3 at = caster.position().add(ctx.flatLook().scale(3));
        mount.moveTo(at.x, at.y + 0.5, at.z, caster.getYRot(), 0f);
        mount.setOwner(player);
        level.addFreshEntity(mount);
        level.sendParticles(SwarmFx.DUST, at.x, at.y + 1, at.z, 80, 1.2, 0.8, 1.2, 0.08);
    }

    // ================================================================ Living Hive (endgame)

    /** The Living Hive: a mode. Body capacity ×2, insects on every hit, Swarm Body; upkeep ×2, fire +50%. */
    static void livingHive(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ColonyData colony = Colony.of(caster);
        if (colony == null) {
            return;
        }
        colony.setLivingHive(!colony.livingHive());
        Colony.changed(caster);
        tell(caster, colony.livingHive() ? "message.noirm.colony.living_hive_on" : "message.noirm.colony.living_hive_off");
    }

    /** The Black Sea: every insect you own floods 28 blocks for 20 s; it costs 60% of them and a day to regrow. */
    static void blackSea(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ColonyData colony = Colony.of(caster);
        if (colony == null || !(caster.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() < colony.blackSeaRegrowUntil()) {
            tell(caster, "message.noirm.colony.sea_regrowing", (colony.blackSeaRegrowUntil() - level.getGameTime()) / 1200);
            return;
        }
        int fromHives = 0;
        if (caster instanceof Player player) {
            // The hives send theirs by courier.
            for (InsectHiveBlockEntity hive : java.util.Set.copyOf(InsectHiveBlockEntity.LOADED)) {
                if (player.getUUID().equals(hive.owner())) {
                    fromHives += hive.count();
                    hive.setCount(0);
                }
            }
            for (ColonyData.HiveRef h : colony.hives()) {
                Colony.noteHive(player, h.dimension(), h.pos(), 0);
            }
        }
        int body = Colony.available(caster, Species.KIKAICHU);
        Colony.Commit c = Colony.commit(caster, Species.KIKAICHU, body);
        colony.addField(fromHives);
        InsectSwarm sea = InsectSwarm.create(level, caster, Species.KIKAICHU, c.count() + fromHives, caster.position().add(0, 1, 0), SwarmMode.SEA);
        sea.setShape(SwarmShapes.SEA_RADIUS);
        sea.setPark(caster.position().add(0, 1, 0));
        sea.setLife(SEA_TICKS);
        level.addFreshEntity(sea);
        colony.setBlackSeaRegrowUntil(level.getGameTime() + SEA_REGROW);
        Colony.changed(caster);
        level.playSound(null, caster.blockPosition(), com.noirm.sound.NoirmSounds.INSECT_HUM_LARGE.get(), net.minecraft.sounds.SoundSource.PLAYERS, 3f, 0.5f);
    }

    /** The Insect Legion: five clones that fight for real for 45 s. */
    static void legion(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        if (!(caster.level() instanceof ServerLevel)) {
            return;
        }
        for (int i = 0; i < LEGION_CLONES; i++) {
            double a = i * Math.PI * 2 / LEGION_CLONES;
            spawnClone(caster, ctx, caster.position().add(Math.cos(a) * 2, 0, Math.sin(a) * 2), true);
        }
    }

    /**
     * Swarm Body (the Living Hive): a blow that would kill you dissolves you into the swarm instead; you
     * re-form 12 blocks away. Costs 40% of the body colony; 5-minute cooldown. Returns whether it saved you.
     */
    public static boolean swarmBody(LivingEntity victim) {
        ColonyData colony = Colony.of(victim);
        if (colony == null || !colony.livingHive() || !(victim.level() instanceof ServerLevel level)
                || level.getGameTime() < colony.swarmBodyReadyAt()) {
            return false;
        }
        for (Species s : Species.values()) {
            colony.setBody(s, Math.round(colony.body(s) * (1f - SWARM_BODY_COST)));
        }
        colony.setSwarmBodyReadyAt(level.getGameTime() + SWARM_BODY_COOLDOWN);
        level.sendParticles(SwarmFx.DUST, victim.getX(), victim.getY() + 1, victim.getZ(), 120, 0.5, 0.9, 0.5, 0.15);
        double a = victim.getRandom().nextDouble() * Math.PI * 2;
        BlockPos land = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(victim.getX() + Math.cos(a) * SWARM_BODY_JUMP, victim.getY(), victim.getZ() + Math.sin(a) * SWARM_BODY_JUMP));
        if (Math.abs(land.getY() - victim.getY()) > 8) {
            land = victim.blockPosition();
        }
        victim.setHealth(Math.max(2f, victim.getMaxHealth() * 0.25f));
        victim.teleportTo(land.getX() + 0.5, land.getY(), land.getZ() + 0.5);
        victim.invulnerableTime = SWARM_BODY_INVULNERABLE;
        victim.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, SWARM_BODY_INVULNERABLE, 4, false, false));
        level.sendParticles(SwarmFx.DUST, land.getX() + 0.5, land.getY() + 1, land.getZ() + 0.5, 120, 0.5, 0.9, 0.5, 0.1);
        Colony.changed(victim);
        tell(victim, "message.noirm.colony.swarm_body");
        return true;
    }
}

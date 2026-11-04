package com.noirm.jutsu;

import com.noirm.Noirm;
import com.noirm.ally.Crew;
import com.noirm.combat.CombatSystem;
import com.noirm.combat.Perception;
import com.noirm.entity.SparringNinja;
import com.noirm.network.NoirmNetwork;
import com.noirm.network.SpiritPackets;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.vfx.FireVfx;
import com.noirm.vfx.SpiritVfx;
import com.noirm.vfx.Vfx;
import com.noirm.world.Destruction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The spirit and body natures (chakra wave, part 1): Yin, Yang and Yin-Yang.
 *
 * <p><b>Yin</b> (genjutsu and shadows). Every genjutsu here has counterplay: a strong mind
 * (willpower, perception) may resist it, an active Sharingan sees straight through it, the
 * <i>Kai</i> release breaks it (even while stunned), and a hit from anyone but the caster wakes the
 * victim up.
 * <ul>
 *   <li><b>genjutsu_release</b> (Kai): break a genjutsu on yourself, or on a crew mate close by.</li>
 *   <li><b>mirage</b> (False Surroundings): the target sees you elsewhere: their next {@code count}
 *       attacks on you strike a false image; creatures turn on their own side.</li>
 *   <li><b>shadow_neck_bind</b> (channel): a shadow runs to the target, holds them and climbs to
 *       their throat, choking for {@code damage} every half second.</li>
 *   <li><b>shadow_stitch</b>: up to {@code count} shadow tendrils race over the ground and burst up as
 *       ink spikes under the enemies in front, piercing and pinning them.</li>
 *   <li><b>tree_bind</b> (Tree Binding Death): a phantom tree grows through the victim and holds
 *       them for {@code stun} ticks; then your phantom strikes from behind for {@code damage}. Hit
 *       them yourself meanwhile and the strike lands at once.</li>
 * </ul>
 * <b>Yang</b> (body and life): <b>regen_burst</b>, <b>giant_fist</b>, <b>life_conversion</b> (health
 * into chakra) and <b>reincarnation</b> (a fallen crew mate lives again, at a heavy cost). Yang
 * Vitality is a {@code mode}.
 * <p><b>Yin-Yang</b> (creation): <b>renewal</b>, <b>attraction</b>, <b>repulsion</b>, <b>hawks</b>
 * (chakra constructs that hunt your enemies) and <b>equilibrium</b> (the S-rank: pull everything in,
 * then burst).
 *
 * <p>Tuning: the jutsu JSON, and the constants below.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class SpiritTechniques {
    // ---------------------------------------------------------------- tuning

    /** Genjutsu: the chance to catch someone of an equal mind (it falls as theirs grows past yours). */
    public static final float GENJUTSU_BASE_CHANCE = 1.3f;
    public static final float GENJUTSU_RATIO_WEIGHT = 0.5f;
    public static final float GENJUTSU_MIN_CHANCE = 0.12f;
    public static final float GENJUTSU_MAX_CHANCE = 0.95f;
    /** NPC ninjas caught in a genjutsu try a Kai every second after this long, with this chance per tier (genin 0 … Kage 3). */
    public static final int NPC_KAI_AFTER = 30;
    public static final float NPC_KAI_CHANCE_BASE = 0.08f;
    public static final float NPC_KAI_CHANCE_PER_TIER = 0.12f;
    /** Kai helps a crew mate within this many blocks. */
    public static final double KAI_REACH = 5;
    /** An NPC crew mate this close (blocks) to a caught friend for a second shakes them awake. */
    public static final double FRIEND_WAKE_REACH = 2.5;
    /** Every pull and push here stays under this speed (no one is thrown out of the fight). */
    public static final float MAX_THROW = 1.6f;
    /** A fallen crew mate's body keeps for this long at most (ticks), for Reincarnation. */
    public static final int FALLEN_KEEP_TICKS = 12000;
    private static final int MAX_FALLEN = 32;

    // ---------------------------------------------------------------- state

    public enum Illusion { MIRAGE, TREE }

    private static final class Caught {
        final LivingEntity caster;
        final Illusion kind;
        final long start;
        final long until;
        final float damage;
        final float posture;
        final Vec3 decoy;
        int misses;

        Caught(LivingEntity caster, Illusion kind, long start, long until, float damage, float posture, Vec3 decoy, int misses) {
            this.caster = caster;
            this.kind = kind;
            this.start = start;
            this.until = until;
            this.damage = damage;
            this.posture = posture;
            this.decoy = decoy;
            this.misses = misses;
        }
    }

    private static final class Choke {
        final LivingEntity target;
        Vec3 tip;
        int caughtAt = -1;
        long lastTick;

        Choke(LivingEntity target, Vec3 tip, long now) {
            this.target = target;
            this.tip = tip;
            this.lastTick = now;
        }
    }

    private record Fallen(UUID id, UUID leader, CompoundTag tag, ResourceKey<Level> dimension, Vec3 pos, long time) {}

    private static final Map<LivingEntity, Caught> CAUGHT = new WeakHashMap<>();
    private static final Map<LivingEntity, Choke> CHOKES = new WeakHashMap<>();
    private static final List<Fallen> FALLEN = new ArrayList<>();

    private SpiritTechniques() {}

    // ================================================================ Yin: genjutsu

    /** How strong a mind is: perception, and for players the average with willpower (WIL). */
    public static float mind(LivingEntity entity) {
        float perception = Perception.perception(entity);
        if (entity instanceof Player player) {
            float will = ShinobiCapability.get(player).map(ShinobiData::getWillpower).orElse(ShinobiData.DEFAULT_STAT);
            return (perception + will) / 2f;
        }
        return perception;
    }

    /** The chance a genjutsu of {@code power} from {@code caster} takes hold of {@code target}. */
    public static float genjutsuChance(LivingEntity caster, LivingEntity target, float power) {
        float ratio = mind(target) / Math.max(1f, mind(caster));
        return Mth.clamp((GENJUTSU_BASE_CHANCE - GENJUTSU_RATIO_WEIGHT * ratio) * Math.min(1.5f, power), GENJUTSU_MIN_CHANCE, GENJUTSU_MAX_CHANCE);
    }

    /** An open Sharingan sees through any of these illusions. */
    public static boolean seesThrough(LivingEntity target) {
        return com.noirm.dojutsu.Sharingan.activeStage(target) > 0;
    }

    /** Does the illusion take hold? Draws and says what happened either way. */
    private static boolean takes(JutsuContext ctx, LivingEntity target) {
        LivingEntity caster = ctx.caster();
        Vec3 eyes = target.position().add(0, target.getBbHeight() * 0.85, 0);
        if (seesThrough(target)) {
            SpiritVfx.shatter(ctx.level(), eyes);
            Vfx.burst(ctx.level(), Vfx.ring(0xD00010, 1.4f, 10), eyes, 1, 0, 0);
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.genjutsu.seen_through", target.getDisplayName()));
            return false;
        }
        if (caster.getRandom().nextFloat() >= genjutsuChance(caster, target, ctx.power())) {
            SpiritVfx.shatter(ctx.level(), eyes);
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.genjutsu.resisted", target.getDisplayName()));
            return false;
        }
        return true;
    }

    /** Is {@code entity} held by one of these illusions? */
    public static boolean inIllusion(LivingEntity entity) {
        return CAUGHT.containsKey(entity);
    }

    public static Illusion illusionOf(LivingEntity entity) {
        Caught caught = CAUGHT.get(entity);
        return caught == null ? null : caught.kind;
    }

    /** Under any genjutsu Kai can break: these illusions, or the dazed, staggered state of the eye genjutsu. */
    public static boolean underGenjutsu(LivingEntity entity) {
        if (CAUGHT.containsKey(entity) || entity.hasEffect(MobEffects.CONFUSION)) {
            return true;
        }
        MobEffectInstance slow = entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return slow != null && slow.getAmplifier() >= 3 && CombatSystem.isStaggered(entity);
    }

    // ---------------------------------------------------------------- Kai

    static void release(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        Vec3 chest = caster.position().add(0, caster.getBbHeight() * 0.6, 0);
        // The sharp pulse of chakra that breaks the flow: a crisp white ring and a violet flash.
        Vfx.burst(level, Vfx.ring(0xFFFFFF, 2f, 7), chest, 1, 0, 0);
        Vfx.burst(level, Vfx.glow(0xFFFFFF, SpiritVfx.YIN.core(), 0.6f, 0.1f, 4), chest, 1, 0, 0);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1f, 1.4f);
        if (releaseFrom(caster)) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.genjutsu.released"));
            return;
        }
        for (LivingEntity mate : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(KAI_REACH),
                e -> e != caster && e.isAlive() && JutsuEffects.isFriendly(caster, e) && underGenjutsu(e))) {
            releaseFrom(mate);
            Vfx.line(level, chest, mate.position().add(0, mate.getBbHeight() * 0.6, 0), SpiritVfx.YIN, 0.12f);
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.genjutsu.released_other", mate.getDisplayName()));
            return;
        }
        JutsuBehaviors.message(caster, Component.translatable("message.noirm.genjutsu.nothing"));
    }

    /** Breaks any genjutsu on {@code victim} (Kai, or a hit from a friend). Returns whether there was one. */
    public static boolean releaseFrom(LivingEntity victim) {
        if (!underGenjutsu(victim)) {
            return false;
        }
        Caught caught = CAUGHT.remove(victim);
        if (caught != null && caught.caster.isAlive()) {
            JutsuBehaviors.message(caught.caster, Component.translatable("message.noirm.genjutsu.woken", victim.getDisplayName()));
        }
        victim.removeEffect(MobEffects.CONFUSION);
        victim.removeEffect(MobEffects.BLINDNESS);
        victim.removeEffect(MobEffects.DARKNESS);
        MobEffectInstance slow = victim.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slow != null && slow.getAmplifier() >= 3) {
            victim.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        CombatSystem.clearStun(victim);
        vision(victim, SpiritPackets.RELEASE, 0);
        if (victim.level() instanceof ServerLevel level) {
            SpiritVfx.shatter(level, victim.position().add(0, victim.getBbHeight() * 0.85, 0));
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 1.5f);
        }
        return true;
    }

    // ---------------------------------------------------------------- False Surroundings

    static void mirage(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 12);
        if (target == null) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.genjutsu.no_target"));
            return;
        }
        if (!takes(ctx, target)) {
            return;
        }
        int duration = Math.round(p.duration() * Math.min(1.4f, ctx.power()));
        catchIn(caster, target, Illusion.MIRAGE, duration, 0, 0, Math.max(1, p.count()));
        JutsuBehaviors.message(caster, Component.translatable("message.noirm.genjutsu.caught", target.getDisplayName()));
    }

    /**
     * Puts {@code target} in one of these illusions for {@code ticks} (no resist roll: the jutsu
     * rolls first; tests call this directly). {@code damage}/{@code posture}: the Tree's final strike;
     * {@code misses}: how many attacks False Surroundings turns aside.
     */
    public static void catchIn(LivingEntity caster, LivingEntity target, Illusion kind, int ticks, float damage, float posture, int misses) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        Vec3 decoy = null;
        if (kind == Illusion.MIRAGE) {
            // The false image stands a few steps to the side of where you really are.
            Vec3 toCaster = caster.position().subtract(target.position());
            Vec3 side = new Vec3(-toCaster.z, 0, toCaster.x);
            side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
            decoy = caster.position().add(side.scale(caster.getRandom().nextBoolean() ? 3 : -3));
            // Where you stood, the ink slides off you to become the image.
            Vfx.burst(level, SpiritVfx.ink(0.4f, 14), caster.position().add(0, 1, 0), 8, 0.3, 0.02);
            SpiritVfx.shadowPath(level, caster.position().add(0, 0.05, 0), decoy.add(0, 0.05, 0), 0.35f);
            JutsuBehaviors.sound(target, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 0.7f);
        } else {
            CombatSystem.stun(target, 5);
            Vfx.quake(level, target.position(), 0.3f, 10);
            JutsuBehaviors.sound(target, SoundEvents.ILLUSIONER_CAST_SPELL, 1.2f, 0.5f);
            JutsuBehaviors.sound(target, SoundEvents.WOOD_BREAK, 1f, 0.5f);
        }
        CAUGHT.put(target, new Caught(caster, kind, now, now + ticks, damage, posture, decoy, misses));
        vision(target, SpiritPackets.GENJUTSU, ticks + (kind == Illusion.TREE ? 10 : 0));
        SpiritVfx.takeHold(level, target);
        if (kind == Illusion.MIRAGE && target instanceof Mob mob) {
            turnOnAllies(mob, caster);
        }
    }

    /** False Surroundings: attacks it still turns aside on {@code victim} (0 if none or not caught). */
    public static int missesLeft(LivingEntity victim) {
        Caught caught = CAUGHT.get(victim);
        return caught == null ? 0 : caught.misses;
    }

    /** A creature in False Surroundings turns on whoever is closest to it (its own side first), never the caster. */
    private static void turnOnAllies(Mob mob, LivingEntity caster) {
        LivingEntity best = mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(10),
                        e -> e != mob && e != caster && e.isAlive() && !e.isSpectator() && !JutsuEffects.isFriendly(caster, e))
                .stream()
                .min(Comparator.<LivingEntity>comparingInt(e -> JutsuEffects.isFriendly(mob, e) ? 0 : 1).thenComparingDouble(mob::distanceTo))
                .orElse(null);
        mob.setTarget(best);
    }

    // ---------------------------------------------------------------- Tree Binding Death

    static void treeBind(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 12);
        if (target == null) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.genjutsu.no_target"));
            return;
        }
        if (!takes(ctx, target)) {
            return;
        }
        int hold = Math.max(10, Math.round(p.stun() * Math.min(1.3f, ctx.power())));
        catchIn(caster, target, Illusion.TREE, hold, p.damage() * ctx.power(), p.posture() * ctx.power(), 0);
        JutsuBehaviors.message(caster, Component.translatable("message.noirm.genjutsu.caught", target.getDisplayName()));
    }

    /** The phantom's strike at the end of Tree Binding Death (or the moment the caster hits the bound victim). */
    private static void treeStrike(LivingEntity victim, Caught caught) {
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 chest = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
        Vec3 behind = victim.position().subtract(victim.getLookAngle().multiply(1, 0, 1).normalize().scale(0.9));
        SpiritVfx.phantom(level, behind, victim.getBbHeight(), (float) Math.toRadians(victim.getYRot()));
        SpiritVfx.inkSpike(level, victim.position(), victim.getBbHeight() + 0.5f);
        SpiritVfx.shatter(level, chest);
        Vfx.flash(level, chest, SpiritVfx.YIN, 1.6f);
        Vfx.quake(level, victim.position(), 0.6f, 12);
        hitstop(victim, 5);
        if (caught.caster.isAlive()) {
            hitstop(caught.caster, 5);
        }
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    private static void endTree(LivingEntity victim, Caught caught, boolean strike) {
        CAUGHT.remove(victim);
        vision(victim, SpiritPackets.RELEASE, 0);
        if (strike) {
            treeStrike(victim, caught);
            JutsuEffects.hit(caught.caster, victim, caught.damage, 0.3f, caught.posture, 0, victim.position().subtract(caught.caster.position()));
            CombatSystem.stun(victim, 12);
        } else if (victim.level() instanceof ServerLevel level) {
            SpiritVfx.shatter(level, victim.position().add(0, victim.getBbHeight() * 0.85, 0));
        }
    }

    // ---------------------------------------------------------------- Shadow Neck Bind

    static final JutsuBehavior NECK_BIND = new JutsuBehavior() {
        @Override
        public void cast(JutsuContext ctx) {
            LivingEntity caster = ctx.caster();
            float light = CanonTechniques.lightFactor(caster);
            LivingEntity target = JutsuBehaviors.lookTarget(caster, ctx.params().range() * light, 20);
            if (target == null) {
                JutsuBehaviors.message(caster, Component.translatable("message.noirm.jutsu.no_target"));
                return;
            }
            CHOKES.put(caster, new Choke(target, caster.position(), caster.level().getGameTime()));
            JutsuBehaviors.sound(caster, SoundEvents.SCULK_CLICKING, 1f, 0.5f);
        }

        @Override
        public void channel(JutsuContext ctx, int tick) {
            LivingEntity caster = ctx.caster();
            Choke choke = CHOKES.get(caster);
            if (choke == null || !choke.target.isAlive()) {
                return;
            }
            choke.lastTick = caster.level().getGameTime();
            LivingEntity target = choke.target;
            Level level = caster.level();
            if (choke.caughtAt < 0) {
                Vec3 goal = target.position();
                Vec3 step = goal.subtract(choke.tip);
                double speed = 1.1 * CanonTechniques.lightFactor(caster);
                choke.tip = step.length() <= speed ? goal : choke.tip.add(step.normalize().scale(speed));
                if (choke.tip.distanceTo(goal) < 0.6) {
                    choke.caughtAt = tick;
                    JutsuBehaviors.sound(target, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6f, 1.6f);
                    JutsuBehaviors.message(caster, Component.translatable("message.noirm.shadow.caught", target.getDisplayName()));
                }
            } else {
                choke.tip = target.position();
            }
            if (tick % 2 == 0) {
                SpiritVfx.shadowPath(level, caster.position().add(0, 0.05, 0), choke.tip.add(0, 0.05, 0), 0.38f);
            }
            if (choke.caughtAt < 0) {
                return;
            }
            CombatSystem.stun(target, 5);
            target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
            target.hurtMarked = true;
            float climb = (tick - choke.caughtAt) / 12f;
            if (tick % 2 == 0) {
                SpiritVfx.inkWrap(level, target, Math.min(0.9f, climb));
            }
            if (climb >= 1f && (tick - choke.caughtAt) % 10 == 0) {
                // The hands close on the throat.
                JutsuEffects.hit(caster, target, ctx.params().damage() * ctx.power(), 0, ctx.params().posture() * ctx.power(), 0, Vec3.ZERO);
                Vec3 neck = target.position().add(0, target.getBbHeight() * 0.82, 0);
                Vfx.burst(level, new com.noirm.vfx.VfxOptions(com.noirm.vfx.VfxKind.RING, SpiritVfx.YIN.mid(), SpiritVfx.YIN.edge(), 0.9f, 0.3f, 8, 0f, 1f),
                        neck, 1, 0, 0);
                Vfx.burst(level, SpiritVfx.ink(0.2f, 10), neck, 6, 0.15, 0.01);
                JutsuBehaviors.sound(target, SoundEvents.PLAYER_HURT_DROWN, 0.7f, 0.7f);
            }
        }
    };

    /** Is {@code target} held by someone's Shadow Neck Bind? */
    public static boolean isChoked(LivingEntity target) {
        for (Choke choke : CHOKES.values()) {
            if (choke.caughtAt >= 0 && choke.target == target) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- Shadow Stitching

    static void stitch(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        float reach = p.range() * CanonTechniques.lightFactor(caster);
        List<LivingEntity> targets = JutsuBehaviors.inCone(caster, reach, 32).stream()
                .sorted(Comparator.comparingDouble(caster::distanceTo)).limit(Math.max(1, p.count())).toList();
        Vec3 origin = caster.position().add(0, 0.05, 0);
        JutsuBehaviors.sound(caster, SoundEvents.SCULK_CLICKING, 1.2f, 0.4f);
        Vfx.burst(level, SpiritVfx.ink(0.6f, 14), origin, 6, 0.4, 0.02);
        if (targets.isEmpty()) {
            // Nothing to pierce: the tendrils lash out in a fan and spike where they end.
            Vec3 look = ctx.flatLook();
            for (int i = 0; i < Math.max(1, p.count()); i++) {
                float angle = (i - (p.count() - 1) / 2f) * 12f;
                Vec3 end = origin.add(look.yRot(angle * Mth.DEG_TO_RAD).scale(reach * 0.7));
                tendril(caster, level, origin, null, end, p, ctx.power());
            }
            return;
        }
        for (LivingEntity target : targets) {
            tendril(caster, level, origin, target, target.position(), p, ctx.power());
        }
    }

    /** One tendril racing over the ground to {@code end}; it bursts up as a spike under {@code target} (if any). */
    private static void tendril(LivingEntity caster, ServerLevel level, Vec3 origin, LivingEntity target, Vec3 end, JutsuDefinition.Params p, float power) {
        Vec3[] tip = {origin};
        GreatTechniques.later(now -> {
            Vec3 goal = target != null && target.isAlive() ? target.position().add(0, 0.05, 0) : end;
            Vec3 step = goal.subtract(tip[0]);
            tip[0] = step.length() <= 1.6 ? goal : tip[0].add(step.normalize().scale(1.6));
            SpiritVfx.shadowPath(level, origin, tip[0], 0.28f);
            if (tip[0].distanceTo(goal) > 0.3) {
                return false;
            }
            float height = target != null ? target.getBbHeight() + 0.6f : 1.6f;
            SpiritVfx.inkSpike(level, goal, height);
            for (int k = 0; k < 3; k++) {
                double a = k * Math.PI * 2 / 3 + level.random.nextDouble();
                SpiritVfx.inkSpike(level, goal.add(Math.cos(a) * 0.55, 0, Math.sin(a) * 0.55), height * 0.6f);
            }
            Vfx.flash(level, goal.add(0, height * 0.6, 0), SpiritVfx.YIN, 0.9f);
            level.playSound(null, goal.x, goal.y, goal.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1f, 0.6f);
            if (target != null && target.isAlive()) {
                JutsuEffects.hit(caster, target, p.damage() * power, 0, p.posture() * power, 0, Vec3.ZERO);
                CombatSystem.stun(target, p.stun());
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.max(1, p.stun()), 9, false, false));
                target.setDeltaMovement(Vec3.ZERO);
                target.hurtMarked = true;
                hitstop(target, 3);
                Vfx.quake(level, goal, 0.3f, 10);
            }
            return true;
        });
    }

    // ================================================================ Yang

    static void regenBurst(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        JutsuDefinition.Params p = ctx.params();
        caster.heal(p.damage() * ctx.power());
        caster.clearFire();
        JutsuEffects.applyEffects(caster, p.effect(), p.duration());
        SpiritVfx.yangBurst(ctx.level(), caster.position(), caster.getBbHeight(), 1f);
        // The body keeps glowing while it knits itself back together.
        int[] age = {0};
        GreatTechniques.later(now -> {
            if (!caster.isAlive() || ++age[0] > p.duration()) {
                return true;
            }
            if (age[0] % 3 == 0) {
                SpiritVfx.yangGlow(caster.level(), caster, 3);
            }
            return false;
        });
        JutsuBehaviors.sound(caster, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.6f);
        JutsuBehaviors.sound(caster, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.2f);
    }

    /** Yang Vitality's aura, every other tick while the mode is on (called by {@link ModeSystem}). */
    public static void yangModeAura(LivingEntity entity) {
        SpiritVfx.yangGlow(entity.level(), entity, 4);
    }

    static void giantFist(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        Vec3 dir = ctx.flatLook();
        float radius = p.radius() * (float) Math.sqrt(Math.max(0.5f, power));
        LivingEntity aimed = JutsuBehaviors.lookTarget(caster, p.range(), 25);
        int windup = 6;
        int[] age = {0};
        JutsuBehaviors.sound(caster, SoundEvents.ANVIL_PLACE, 0.6f, 0.5f);
        GreatTechniques.later(now -> {
            if (!caster.isAlive()) {
                return true;
            }
            age[0]++;
            float k = age[0] / (float) windup;
            Vec3 hand = caster.getEyePosition().add(0, -0.5, 0).add(dir.scale(0.8 + k * Math.min(p.range(), 4) * 0.5));
            if (age[0] < windup) {
                // The arm swells: the fist grows out ahead of the caster.
                SpiritVfx.fist(level, hand.add(0, k * radius * 0.25, 0), dir, 0.5f + radius * 0.55f * k);
                return false;
            }
            Vec3 impact = aimed != null && aimed.isAlive() && aimed.distanceTo(caster) <= p.range() + 1 ? aimed.position()
                    : FireVfx.floorBelow(level, caster.position().add(dir.scale(Math.min(p.range(), 4.5))), 4);
            slam(caster, level, impact, dir, radius, p, power);
            return true;
        });
    }

    private static void slam(LivingEntity caster, ServerLevel level, Vec3 impact, Vec3 dir, float radius, JutsuDefinition.Params p, float power) {
        SpiritVfx.fist(level, impact.add(0, radius * 0.4, 0), dir, radius * 0.9f);
        SpiritVfx.yangBurst(level, impact, 1.8f, radius * 0.6f);
        Vfx.explosion(level, impact.add(0, 0.3, 0), SpiritVfx.YANG, radius * 0.7f);
        FireVfx.groundDebris(level, impact, radius * 0.7f);
        Vfx.quake(level, impact, 0.9f, radius * 5);
        Destruction.crater(level, impact, radius * 0.45f, power);
        Destruction.shatterFragile(level, BlockPos.containing(impact).above(), 2);
        hitstop(caster, 4);
        float throwing = Math.min(MAX_THROW, p.knockback());
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(radius + 1),
                e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator() && e.position().distanceTo(impact) <= radius + e.getBbWidth())) {
            float falloff = 1f - (float) Math.min(1, victim.position().distanceTo(impact) / (radius + 1));
            Vec3 push = victim.position().subtract(caster.position());
            JutsuEffects.hit(caster, victim, p.damage() * power * (0.5f + 0.5f * falloff), throwing, p.posture() * power, 0, push);
            CombatSystem.stun(victim, p.stun());
            hitstop(victim, 4);
        }
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4f, 0.6f);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1f, 0.5f);
    }

    static void lifeConversion(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        JutsuDefinition.Params p = ctx.params();
        float spent = Math.min(caster.getHealth() - 2f, p.damage());
        if (spent < 1f) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.yang.too_weak"));
            return;
        }
        caster.setHealth(caster.getHealth() - spent);
        float gained = spent * p.range() * ctx.power();
        addChakra(caster, gained);
        SpiritVfx.lifeWisps(ctx.level(), caster);
        Vfx.burst(ctx.level(), Vfx.ring(SpiritVfx.YANG.mid(), 1.6f, 10), caster.position().add(0, 0.05, 0), 1, 0, 0);
        JutsuBehaviors.sound(caster, SoundEvents.WARDEN_HEARTBEAT, 1f, 1.2f);
        JutsuBehaviors.message(caster, Component.translatable("message.noirm.yang.converted", Math.round(gained)));
    }

    /** Adds chakra to a player or an NPC ninja, up to their maximum. */
    public static void addChakra(LivingEntity entity, float amount) {
        if (entity instanceof Player player) {
            ShinobiCapability.get(player).ifPresent(d -> d.setChakra(Math.min(d.getMaxChakra(), d.getChakra() + amount)));
        } else if (entity instanceof SparringNinja ninja) {
            ninja.setChakra(Math.min(ninja.maxChakra(), ninja.chakra() + amount));
        }
    }

    // ---------------------------------------------------------------- One's Own Life Reincarnation

    /** A crew mate falls: remember them (their whole self) for a while, so a Yang master could bring them back. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof SparringNinja ninja) || ninja.leaderId() == null || ninja.level().isClientSide()) {
            return;
        }
        CompoundTag tag = ninja.saveWithoutId(new CompoundTag());
        for (String key : List.of("DeathTime", "HurtTime", "HurtByTimestamp", "ActiveEffects", "Fire", "Motion", "FallDistance", "Brain")) {
            tag.remove(key);
        }
        FALLEN.removeIf(f -> f.id().equals(ninja.getUUID()));
        FALLEN.add(new Fallen(ninja.getUUID(), ninja.leaderId(), tag, ninja.level().dimension(), ninja.position(), ninja.level().getGameTime()));
        while (FALLEN.size() > MAX_FALLEN) {
            FALLEN.remove(0);
        }
    }

    /** How many fallen crew mates are remembered (for tests). */
    public static int fallenCount() {
        return FALLEN.size();
    }

    static void reincarnation(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        UUID leader = caster instanceof Player ? caster.getUUID() : caster instanceof SparringNinja n ? n.leaderId() : null;
        long now = level.getGameTime();
        Fallen fallen = null;
        for (int i = FALLEN.size() - 1; i >= 0; i--) {
            Fallen f = FALLEN.get(i);
            if (f.leader().equals(leader) && f.dimension() == level.dimension() && f.pos().distanceTo(caster.position()) <= p.range()
                    && now - f.time() <= Math.min(p.duration(), FALLEN_KEEP_TICKS) && level.getEntity(f.id()) == null) {
                fallen = f;
                break;
            }
        }
        if (fallen == null) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.yang.no_fallen"));
            // Nothing happened: the chakra comes back and the jutsu is ready again.
            addChakra(caster, JutsuCaster.cost(caster, ctx.id(), ctx.def()));
            if (caster instanceof Player player) {
                GreatTechniques.later(t -> {
                    ShinobiCapability.get(player).ifPresent(d -> d.jutsu().setCooldown(ctx.id(), 0));
                    return true;
                });
            }
            return;
        }
        FALLEN.remove(fallen);
        Fallen chosen = fallen;
        // The price: most of the caster's life, all their chakra, and a body that needs days to recover.
        caster.setHealth(Math.max(1f, caster.getHealth() - caster.getMaxHealth() * p.damage()));
        if (caster instanceof Player player) {
            ShinobiCapability.get(player).ifPresent(d -> d.setChakra(0));
        } else if (caster instanceof SparringNinja n) {
            n.setChakra(0);
        }
        caster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 1));
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 1200, 1));
        JutsuBehaviors.sound(caster, SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.6f);
        int[] age = {0};
        GreatTechniques.later(t -> {
            age[0]++;
            Vec3 spot = chosen.pos();
            if (age[0] < 30) {
                // The caster's life flows out to where their friend fell; a column of light gathers there.
                if (caster.isAlive() && age[0] % 2 == 0) {
                    Vfx.line(level, caster.position().add(0, caster.getBbHeight() * 0.6, 0), spot.add(0, 1, 0), SpiritVfx.YANG, 0.14f);
                }
                Vfx.gather(level, spot.add(0, 1, 0), SpiritVfx.YANG, 2.5f, 8);
                for (int y = 0; y < 8; y++) {
                    Vfx.one(level, Vfx.glow(SpiritVfx.YANG.core(), SpiritVfx.YANG.mid(), 0.5f, 0.2f, 4), spot.add(0, y * 1.2, 0), Vec3.ZERO);
                }
                return false;
            }
            SparringNinja ninja = com.noirm.registry.NoirmEntities.SPARRING_NINJA.get().create(level);
            if (ninja == null) {
                return true;
            }
            ninja.load(chosen.tag());
            ninja.moveTo(spot.x, spot.y, spot.z, ninja.getYRot(), 0f);
            ninja.setHealth(ninja.getMaxHealth() * 0.5f);
            ninja.joinCrew(chosen.leader());
            level.addFreshEntity(ninja);
            Player lead = level.getPlayerByUUID(chosen.leader());
            if (lead != null) {
                ShinobiCapability.get(lead).ifPresent(d -> {
                    if (!d.crew().contains(ninja.getUUID())) {
                        d.crew().add(ninja.getUUID());
                    }
                });
            }
            SpiritVfx.yangBurst(level, spot, 1.8f, 1.6f);
            Vfx.quake(level, spot, 0.4f, 14);
            level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 0.8f);
            if (caster.isAlive()) {
                JutsuBehaviors.message(caster, Component.translatable("message.noirm.yang.revived", ninja.getDisplayName()));
            }
            return true;
        });
    }

    // ================================================================ Yin-Yang

    static void renewal(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        caster.heal(ctx.params().damage() * ctx.power());
        cleanse(caster);
        Vfx.flash(ctx.level(), caster.position().add(0, caster.getBbHeight() * 0.6, 0), SpiritVfx.YIN_YANG, 1.4f);
        int[] age = {0};
        GreatTechniques.later(now -> {
            if (!caster.isAlive() || ++age[0] > 12) {
                return true;
            }
            if (age[0] % 3 == 1) {
                SpiritVfx.swirl(caster.level(), caster.position(), caster.getBbWidth() * 0.7f + 0.2f, caster.getBbHeight() * (0.4f + age[0] / 18f), 1.5f);
            }
            return false;
        });
        JutsuBehaviors.sound(caster, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.8f);
    }

    /** Removes every harmful effect and puts out fire. Returns how many effects went. */
    public static int cleanse(LivingEntity entity) {
        List<net.minecraft.world.effect.MobEffect> bad = entity.getActiveEffects().stream()
                .map(MobEffectInstance::getEffect).filter(e -> !e.isBeneficial()).toList();
        bad.forEach(entity::removeEffect);
        entity.clearFire();
        return bad.size();
    }

    static void attraction(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 15);
        if (target == null) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.jutsu.no_target"));
            return;
        }
        Vec3 from = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 hands = caster.getEyePosition().add(ctx.look().scale(0.7)).add(0, -0.4, 0);
        Vec3 toward = caster.position().subtract(target.position());
        double distance = toward.horizontalDistance();
        Vec3 flat = new Vec3(toward.x, 0, toward.z);
        flat = flat.lengthSqr() < 1e-6 ? Vec3.ZERO : flat.normalize();
        double speed = Math.min(Math.min(MAX_THROW, Math.abs(p.knockback()) * 1.4), Math.max(0, distance - 1.6) * 0.3);
        JutsuEffects.hit(caster, target, p.damage() * ctx.power(), 0, p.posture() * ctx.power(), 0, Vec3.ZERO);
        target.setDeltaMovement(flat.scale(speed).add(0, 0.25, 0));
        target.hurtMarked = true;
        CombatSystem.stun(target, p.stun());
        SpiritVfx.implode(level, from, 2.2f, 16);
        // White and black chakra streaming from the target into the caster's palm.
        for (double d = 0; d < from.distanceTo(hands); d += 0.5) {
            Vec3 at = from.add(hands.subtract(from).normalize().scale(d));
            Vec3 pull = hands.subtract(at).normalize().scale(0.25);
            Vfx.one(level, ((int) (d * 2)) % 2 == 0 ? Vfx.glow(0xFFFFFF, 0xD8D4FF, 0.2f, 0.05f, 6) : SpiritVfx.ink(0.2f, 6), at, pull);
        }
        SpiritVfx.taiji(level, hands, 0.5f);
        JutsuBehaviors.sound(target, SoundEvents.ENDER_EYE_DEATH, 1f, 0.6f);
    }

    static void repulsion(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        float radius = p.radius() * (float) Math.sqrt(Math.max(0.5f, ctx.power()));
        Vec3 chest = caster.position().add(0, caster.getBbHeight() * 0.55, 0);
        float throwing = Math.min(MAX_THROW, p.knockback());
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(radius),
                e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator() && e.distanceTo(caster) <= radius + e.getBbWidth())) {
            float falloff = 1f - (float) Math.min(1, victim.distanceTo(caster) / (radius + 1));
            JutsuEffects.hit(caster, victim, p.damage() * ctx.power() * (0.5f + 0.5f * falloff), throwing, p.posture() * ctx.power(), 0,
                    victim.position().subtract(caster.position()));
            hitstop(victim, 3);
        }
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, caster.getBoundingBox().inflate(radius + 1),
                e -> e.getOwner() != caster)) {
            Vec3 away = projectile.position().subtract(chest);
            projectile.setDeltaMovement(away.lengthSqr() < 1e-6 ? projectile.getDeltaMovement().scale(-1) : away.normalize().scale(1.2));
            projectile.setOwner(caster);
        }
        SpiritVfx.explode(level, chest, radius * 0.6f);
        Vfx.shockwave(level, caster.position().add(0, 0.05, 0), SpiritVfx.YIN_YANG, radius);
        Vfx.burst(level, new com.noirm.vfx.VfxOptions(com.noirm.vfx.VfxKind.RING, SpiritVfx.INK, 0x000000, 0.4f, radius * 1.2f, 12, 0f, 1f),
                caster.position().add(0, 0.1, 0), 1, 0, 0);
        Vfx.quake(level, caster.position(), 0.6f, radius * 3);
        Destruction.shatterFragile(level, caster.blockPosition().above(), 2);
        hitstop(caster, 3);
        JutsuBehaviors.sound(caster, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.3f);
    }

    // ---------------------------------------------------------------- Creation: Chakra Hawks

    private static final class Hawk {
        Vec3 pos;
        Vec3 vel;
        int rest;
        LivingEntity target;
        final int seed;

        Hawk(Vec3 pos, Vec3 vel, int seed) {
            this.pos = pos;
            this.vel = vel;
            this.seed = seed;
        }
    }

    /** Hawks alive right now (for tests). */
    private static int hawksAlive;

    public static int hawksAlive() {
        return hawksAlive;
    }

    static void hawks(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        List<Hawk> flock = new ArrayList<>();
        Vec3 look = ctx.flatLook();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        int count = Math.max(1, p.count());
        for (int i = 0; i < count; i++) {
            Vec3 start = caster.getEyePosition().add(side.scale((i - (count - 1) / 2.0) * 0.8)).add(0, 0.3, 0);
            flock.add(new Hawk(start, look.scale(0.3).add(0, 0.25, 0), i * 37));
            SpiritVfx.swirl(level, start.add(0, -0.4, 0), 0.4f, 0.8f, 1f);
        }
        hawksAlive += flock.size();
        LivingEntity first = JutsuBehaviors.lookTarget(caster, p.range(), 20);
        float damage = p.damage() * ctx.power();
        int life = Math.max(20, p.duration());
        int[] age = {0};
        JutsuBehaviors.sound(caster, SoundEvents.PHANTOM_FLAP, 1f, 1.4f);
        GreatTechniques.later(now -> {
            age[0]++;
            boolean over = !caster.isAlive() || caster.level() != level || age[0] > life;
            for (Iterator<Hawk> it = flock.iterator(); it.hasNext(); ) {
                Hawk hawk = it.next();
                if (over) {
                    dissolve(level, hawk.pos);
                    it.remove();
                    hawksAlive--;
                    continue;
                }
                flyHawk(caster, level, hawk, age[0], p, damage, first);
            }
            return flock.isEmpty();
        });
    }

    private static void flyHawk(LivingEntity caster, ServerLevel level, Hawk hawk, int age, JutsuDefinition.Params p, float damage, LivingEntity first) {
        if (hawk.target == null || !hawk.target.isAlive() || hawk.target.distanceTo(caster) > p.range() + 4) {
            hawk.target = first != null && first.isAlive() && first.distanceTo(caster) <= p.range() ? first
                    : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(p.range()),
                            e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator() && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand))
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(hawk.pos))).orElse(null);
        }
        Vec3 goal;
        if (hawk.target == null) {
            double a = age * 0.12 + hawk.seed;
            goal = caster.position().add(Math.cos(a) * 1.8, 2.6, Math.sin(a) * 1.8);
        } else if (hawk.rest > 0) {
            hawk.rest--;
            double a = age * 0.25 + hawk.seed;
            goal = hawk.target.position().add(Math.cos(a) * 2.2, 2.8, Math.sin(a) * 2.2);
        } else {
            goal = hawk.target.position().add(0, hawk.target.getBbHeight() * 0.6, 0);
        }
        Vec3 desire = goal.subtract(hawk.pos);
        double speed = Math.max(0.2, p.speed());
        Vec3 want = desire.lengthSqr() < 1e-6 ? Vec3.ZERO : desire.normalize().scale(speed);
        hawk.vel = hawk.vel.scale(0.75).add(want.scale(0.25 * 1.6));
        if (hawk.vel.length() > speed) {
            hawk.vel = hawk.vel.normalize().scale(speed);
        }
        hawk.pos = hawk.pos.add(hawk.vel);
        SpiritVfx.hawk(level, hawk.pos, hawk.vel, hawk.seed);
        if (hawk.target != null && hawk.rest == 0
                && hawk.pos.distanceTo(hawk.target.position().add(0, hawk.target.getBbHeight() * 0.6, 0)) < 0.6 + hawk.target.getBbWidth() * 0.5) {
            JutsuEffects.hit(caster, hawk.target, damage, 0.2f, p.posture(), 0, hawk.vel);
            Vfx.radial(level, Vfx.shard(0xF4F2FF, 0.14f, 12), hawk.pos, 5, 0.25);
            Vfx.radial(level, Vfx.shard(SpiritVfx.INK, 0.14f, 12), hawk.pos, 5, 0.25);
            level.playSound(null, hawk.pos.x, hawk.pos.y, hawk.pos.z, SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 0.8f, 1.3f);
            hawk.rest = 14;
            hawk.vel = hawk.vel.scale(-0.6).add(0, 0.45, 0);
        }
    }

    private static void dissolve(ServerLevel level, Vec3 at) {
        Vfx.radial(level, Vfx.glow(0xFFFFFF, 0xD8D4FF, 0.18f, 0.04f, 14), at, 6, 0.15);
        Vfx.burst(level, SpiritVfx.ink(0.25f, 14), at, 5, 0.2, 0.02);
    }

    // ---------------------------------------------------------------- Equilibrium Collapse (S)

    static void equilibrium(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        float power = ctx.power();
        float radius = p.radius() * (float) Math.sqrt(Math.max(0.5f, power));
        Vec3 center = ElementalBehaviors.aim(caster, p.range()).add(0, 1.4, 0);
        int gather = Math.max(10, p.duration());
        int[] age = {0};
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2f, 0.5f);
        GreatTechniques.later(now -> {
            age[0]++;
            float k = Math.min(1f, age[0] / (float) gather);
            if (age[0] < gather) {
                // Yin: everything is drawn in toward a turning ball of black and white.
                SpiritVfx.taiji(level, center, 0.6f + radius * 0.4f * k);
                if (age[0] % 4 == 0) {
                    SpiritVfx.implode(level, center, radius * 1.6f, 14);
                }
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius * 1.8),
                        e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator())) {
                    Vec3 in = center.subtract(victim.position());
                    if (in.horizontalDistance() > 0.8) {
                        Vec3 flat = new Vec3(in.x, 0, in.z).normalize().scale(0.12);
                        victim.setDeltaMovement(victim.getDeltaMovement().scale(0.6).add(flat));
                        victim.hurtMarked = true;
                    }
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2, false, false));
                }
                if (age[0] % 10 == 0) {
                    level.playSound(null, center.x, center.y, center.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.2f, 0.5f + k);
                }
                return false;
            }
            // Yang: it all comes apart at once.
            SpiritVfx.explode(level, center, radius);
            Vec3 ground = FireVfx.floorBelow(level, center, 4);
            Vfx.shockwave(level, ground, SpiritVfx.YIN_YANG, radius * 1.4f);
            FireVfx.groundDebris(level, ground, radius * 0.6f);
            Vfx.quake(level, center, 1.2f, radius * 6);
            Destruction.crater(level, ground, radius * 0.5f, power * 1.2f);
            float throwing = Math.min(MAX_THROW, p.knockback());
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius + 1),
                    e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator()
                            && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(center) <= radius + e.getBbWidth())) {
                float falloff = 1f - (float) Math.min(1, victim.position().distanceTo(center) / (radius + 1));
                JutsuEffects.hit(caster, victim, p.damage() * power * (0.45f + 0.55f * falloff), throwing, p.posture() * power, 0,
                        victim.position().subtract(center));
                CombatSystem.stun(victim, p.stun());
                hitstop(victim, 5);
            }
            if (caster.isAlive()) {
                hitstop(caster, 5);
            }
            level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2f, 0.5f);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, 0.7f);
            return true;
        });
    }

    // ================================================================ shared

    static void vision(LivingEntity entity, int kind, int ticks) {
        if (entity instanceof ServerPlayer player) {
            NoirmNetwork.sendToPlayer(player, new SpiritPackets.Vision(kind, ticks));
        }
    }

    /** A heavy blow's freeze frame on {@code entity} (players and ninjas). */
    static void hitstop(LivingEntity entity, int ticks) {
        JutsuCaster.castAnimation(entity, com.noirm.network.CombatClientPackets.HITSTOP, ticks);
    }

    // ---------------------------------------------------------------- events and ticking

    /** False Surroundings: the attack lands on a false image instead. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker.level().isClientSide()) {
            return;
        }
        Caught caught = CAUGHT.get(attacker);
        if (caught == null || caught.kind != Illusion.MIRAGE || caught.caster != event.getEntity() || caught.misses <= 0) {
            return;
        }
        caught.misses--;
        event.setCanceled(true);
        LivingEntity victim = event.getEntity();
        if (victim.level() instanceof ServerLevel level) {
            Vec3 chest = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
            Vfx.burst(level, SpiritVfx.ink(0.45f, 16), chest, 8, 0.3, 0.03);
            Vfx.burst(level, SpiritVfx.rim(0.3f, 8), chest, 4, 0.3, 0.02);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8f, 1.4f);
        }
        JutsuBehaviors.message(attacker, Component.translatable("message.noirm.genjutsu.false_hit"));
    }

    /** A hit wakes the victim of a genjutsu, unless it's the caster's; the caster's own hit lands the Tree's strike at once. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        Caught caught = CAUGHT.get(victim);
        if (caught == null || victim.level().isClientSide()) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker == caught.caster) {
            if (caught.kind == Illusion.TREE) {
                CAUGHT.remove(victim);
                vision(victim, SpiritPackets.RELEASE, 0);
                event.setAmount(event.getAmount() + caught.damage);
                CombatSystem.addPostureDamage(victim, caught.posture);
                treeStrike(victim, caught);
            }
            return;
        }
        if (attacker instanceof LivingEntity friend && JutsuEffects.isFriendly(friend, victim)) {
            releaseFrom(victim); // a friend's blow shakes them out of it
        }
    }

    /** A friend slapping (attacking) or shaking (using) a caught player or ninja wakes them up. */
    @SubscribeEvent
    public static void onFriendAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        wakeByFriend(event.getEntity(), event.getTarget());
    }

    @SubscribeEvent
    public static void onFriendShake(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (wakeByFriend(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
        }
    }

    private static boolean wakeByFriend(Player friend, Entity target) {
        if (friend.level().isClientSide() || !(target instanceof LivingEntity victim) || !CAUGHT.containsKey(victim)
                || !JutsuEffects.isFriendly(friend, victim) || CAUGHT.get(victim).caster == friend) {
            return false;
        }
        return releaseFrom(victim);
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (Map.Entry<LivingEntity, Caught> entry : new ArrayList<>(CAUGHT.entrySet())) {
            tickCaught(entry.getKey(), entry.getValue());
        }
        CHOKES.entrySet().removeIf(e -> e.getKey().level().getGameTime() - e.getValue().lastTick > 3 || !e.getKey().isAlive());
    }

    private static void tickCaught(LivingEntity victim, Caught caught) {
        if (!(victim.level() instanceof ServerLevel level)) {
            CAUGHT.remove(victim);
            return;
        }
        long now = level.getGameTime();
        if (!victim.isAlive() || !caught.caster.isAlive() || caught.caster.level() != level) {
            CAUGHT.remove(victim);
            vision(victim, SpiritPackets.RELEASE, 0);
            return;
        }
        if (now >= caught.until) {
            if (caught.kind == Illusion.TREE) {
                endTree(victim, caught, true);
            } else {
                CAUGHT.remove(victim);
                vision(victim, SpiritPackets.RELEASE, 0);
            }
            return;
        }
        long age = now - caught.start;
        // A crew mate (an NPC ninja on the victim's side) who stays close for a second shakes them awake.
        if (age > 0 && age % 20 == 0 && !level.getEntitiesOfClass(SparringNinja.class, victim.getBoundingBox().inflate(FRIEND_WAKE_REACH),
                n -> n != victim && n != caught.caster && n.isAlive() && JutsuEffects.isFriendly(n, victim)).isEmpty()) {
            releaseFrom(victim);
            return;
        }
        // NPC ninjas fight their way out of it: a Kai, the stronger they are the sooner.
        if (victim instanceof SparringNinja ninja && age >= NPC_KAI_AFTER && age % 20 == 0) {
            float chance = NPC_KAI_CHANCE_BASE + NPC_KAI_CHANCE_PER_TIER * NpcJutsuSets.tierOf(ninja).ordinal();
            if (ninja.getRandom().nextFloat() < chance) {
                releaseFrom(ninja);
                return;
            }
        }
        if (caught.kind == Illusion.TREE) {
            CombatSystem.stun(victim, 5);
            victim.setDeltaMovement(0, Math.min(0, victim.getDeltaMovement().y), 0);
            victim.hurtMarked = true;
            if (victim instanceof Mob mob) {
                mob.setTarget(null);
            }
            if (age % 2 == 0) {
                float progress = age / 14f;
                Vec3 away = victim.position().subtract(caught.caster.position()).multiply(1, 0, 1);
                Vec3 back = away.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : away.normalize();
                SpiritVfx.illusionTree(level, victim.position(), back, victim.getBbHeight(), progress);
                SpiritVfx.inkWrap(level, victim, Math.min(0.75f, progress));
            }
            if (caught.until - now <= 14 && age % 2 == 0) {
                // The caster's phantom rises behind the bound victim, blade ready.
                Vec3 behind = victim.position().subtract(victim.getLookAngle().multiply(1, 0, 1).normalize().scale(0.9));
                SpiritVfx.phantom(level, behind, caught.caster.getBbHeight(), (float) Math.toRadians(victim.getYRot()));
            }
            return;
        }
        // False Surroundings: the image stands where the victim thinks you are.
        if (age % 2 == 0) {
            SpiritVfx.phantom(level, caught.decoy, caught.caster.getBbHeight(), (float) Math.toRadians(caught.caster.getYRot()));
        }
        if (victim instanceof Mob mob && mob.getTarget() == caught.caster) {
            turnOnAllies(mob, caught.caster);
        }
        if (age % 6 == 0) {
            Vfx.burst(level, SpiritVfx.rim(0.16f, 10), victim.position().add(0, victim.getBbHeight() * 0.9, 0), 2, 0.25, 0.01);
        }
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        CAUGHT.clear();
        CHOKES.clear();
        FALLEN.clear();
        hawksAlive = 0;
    }
}

package com.noirm.hound;

import com.noirm.Noirm;
import com.noirm.ally.Crew;
import com.noirm.combat.CombatAnimated;
import com.noirm.entity.ShadowClone;
import com.noirm.jutsu.JutsuBehaviors;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.JutsuEffects;
import com.noirm.network.CombatClientPackets;
import com.noirm.network.NoirmNetwork;
import com.noirm.vfx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The Inuzuka's techniques with their hound (jutsu behaviours, docs/INUZUKA.md):
 * <ul>
 *   <li><b>fang_over_fang</b> (牙通牙, Gatsūga): you tip forward and spin into a drill that tears
 *   through everything in a line; your hound (young or older, within reach) spins beside you and
 *   the two drills cross on your target. Alone it's Passing Fang (通牙, Tsūga). Riding the
 *   Two-Headed Wolf it becomes Fang Wolf Fang (牙狼牙, Garōga): one huge drill.</li>
 *   <li><b>dynamic_marking</b>: the hound dashes to the target and marks it with its scent: it glows
 *   and you both hit it harder for a minute.</li>
 *   <li><b>man_beast_clone</b> (獣人分身, Jūjin Bunshin): the hound takes your shape (a clone of you
 *   that fights like a beast) until it's beaten or time runs out.</li>
 *   <li><b>two_headed_wolf</b> (双頭狼, Sōtōrō): you and a great hound fuse into a giant two-headed
 *   wolf: you ride inside it, it hits far harder and shrugs off half the damage.</li>
 * </ul>
 * Params from the jutsu JSON: {@code damage}, {@code duration}, {@code speed}, {@code knockback},
 * {@code posture}, {@code range}.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class HoundTechniques {
    /** How long a drill spin lasts (ticks) and how often one drill may hit the same body. */
    public static final int SPIN_TICKS = 14;
    public static final int HIT_EVERY = 5;
    /** The hound's share of its bite per drill hit; how far it may be to join (blocks). */
    public static final float HOUND_SPIN_BITE = 0.8f;
    public static final double JOIN_RANGE = 16.0;
    /** Fang Wolf Fang: the giant drill's damage multiplier. */
    public static final float WOLF_FANG_MULT = 2.2f;
    /** Dynamic Marking: how long (ticks) and how much harder you both hit a marked target. */
    public static final int MARK_TICKS = 20 * 60;
    public static final float MARK_BONUS = 1.25f;
    /** Man Beast Clone: how long it lasts per stage (ticks). */
    public static final int CLONE_TICKS = 20 * 25;
    public static final int CLONE_TICKS_PER_STAGE = 20 * 8;
    /** Two-Headed Wolf: how long the fusion lasts (ticks). */
    public static final int FUSION_TICKS = 20 * 30;

    public static final ResourceLocation DRILL_ANIMATION = Noirm.id("fang_drill");

    private HoundTechniques() {}

    // ================================================================ Fang Over Fang

    private static final class Drill {
        final LivingEntity caster;
        final Vec3 dir;
        final float damage;
        final float knockback;
        final float posture;
        final double speed;
        int ticks;
        final Map<Integer, Integer> hits = new HashMap<>();

        Drill(LivingEntity caster, Vec3 dir, float damage, float knockback, float posture, double speed, int ticks) {
            this.caster = caster;
            this.dir = dir;
            this.damage = damage;
            this.knockback = knockback;
            this.posture = posture;
            this.speed = speed;
            this.ticks = ticks;
        }
    }

    private static final Map<LivingEntity, Drill> DRILLS = new WeakHashMap<>();
    /** Drill hits each body landed (for tests and tuning). */
    private static final Map<Entity, Integer> HITS = new WeakHashMap<>();

    public static int hitsBy(Entity entity) {
        return HITS.getOrDefault(entity, 0);
    }

    public static boolean drilling(LivingEntity entity) {
        return DRILLS.containsKey(entity);
    }

    public static void fangOverFang(JutsuContext ctx) {
        JutsuDefinition.Params p = ctx.params();
        LivingEntity caster = ctx.caster();
        NinjaHound hound = Hounds.houndOf(caster);
        // Riding the Two-Headed Wolf: Fang Wolf Fang, one giant drill.
        if (caster.getVehicle() instanceof NinjaHound ridden && ridden.isFused()) {
            ridden.startSpin(ctx.flatLook(), SPIN_TICKS + 6, ridden.stage().bite * NinjaHound.FUSED_BITE * WOLF_FANG_MULT * ctx.power());
            JutsuBehaviors.sound(caster, SoundEvents.WOLF_HOWL, 1.4f, 0.5f);
            return;
        }
        LivingEntity target = JutsuBehaviors.lookTarget(caster, Math.max(8, p.range()), 30);
        Vec3 dir = target != null ? flat(target.position().subtract(caster.position())) : ctx.flatLook();
        int ticks = p.duration() > 0 ? p.duration() : SPIN_TICKS;
        double speed = p.speed() > 0 ? p.speed() : 0.75;
        DRILLS.put(caster, new Drill(caster, dir, p.damage() * ctx.power(), p.knockback(), p.posture() * ctx.power(), speed, ticks));
        if (caster instanceof Player) {
            NoirmNetwork.sendToTrackingAndSelf(caster, new CombatClientPackets.PlayAnimation(caster.getId(), DRILL_ANIMATION, ticks + 4));
        } else if (caster instanceof CombatAnimated animated) {
            animated.playCombatAnimation(DRILL_ANIMATION, ticks + 4);
        }
        JutsuBehaviors.sound(caster, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
        // The hound joins: its drill aims to cross yours on the target.
        if (hound != null && hound.stage().canSpin() && !hound.isDown() && !hound.isHidden()
                && hound.level() == caster.level() && hound.distanceTo(caster) <= JOIN_RANGE) {
            if (hound.isCarried()) {
                Hounds.putDown(hound);
            }
            Vec3 aim = target != null ? target.position() : caster.position().add(dir.scale(ticks * speed * 0.7));
            if (target != null) {
                hound.setTarget(target);
            }
            Vec3 hdir = flat(aim.subtract(hound.position()));
            hound.startSpin(hdir.lengthSqr() < 1e-4 ? dir : hdir, ticks, hound.stage().bite * HOUND_SPIN_BITE
                    * (1f + NinjaHound.BOND_BITE_BONUS * hound.bond() / 100f));
            Hounds.addXp(hound, 2f);
        }
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DRILLS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<LivingEntity, Drill>> it = DRILLS.entrySet().iterator();
        while (it.hasNext()) {
            Drill d = it.next().getValue();
            LivingEntity c = d.caster;
            if (!c.isAlive() || --d.ticks < 0 || !(c.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            c.setDeltaMovement(d.dir.x * d.speed, Math.min(c.getDeltaMovement().y, 0.05), d.dir.z * d.speed);
            c.hurtMarked = true;
            c.fallDistance = 0;
            drillHits(c, d.damage, d.hits, 1.4, d.knockback, d.posture);
            drillTrail(level, c);
        }
    }

    /** Hits everything around a spinning body (each one at most every {@link #HIT_EVERY} ticks). */
    public static int drillHits(LivingEntity driller, float damage, Map<Integer, Integer> hits, double radius) {
        return drillHits(driller, damage, hits, radius, 0.5f, 4f);
    }

    static int drillHits(LivingEntity driller, float damage, Map<Integer, Integer> hits, double radius, float knockback, float posture) {
        int landed = 0;
        Vec3 centre = driller.position().add(0, driller.getBbHeight() * 0.5, 0);
        for (LivingEntity e : driller.level().getEntitiesOfClass(LivingEntity.class, driller.getBoundingBox().inflate(radius),
                e -> e != driller && e.isAlive() && !(e instanceof NinjaHound h && h.isCarried()) && !JutsuEffects.isFriendly(driller, e))) {
            if (e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(centre) > radius + e.getBbWidth() * 0.5 + 0.6) {
                continue;
            }
            Integer last = hits.get(e.getId());
            if (last != null && driller.tickCount - last < HIT_EVERY) {
                continue;
            }
            hits.put(e.getId(), driller.tickCount);
            float before = e.getHealth();
            JutsuEffects.hit(driller, e, damage, knockback, posture, 0, e.position().subtract(driller.position()));
            if (e.getHealth() < before) {
                landed++;
                HITS.merge(driller, 1, Integer::sum);
                if (driller instanceof NinjaHound hound) {
                    Hounds.addXp(hound, (before - e.getHealth()) * Hounds.XP_PER_DAMAGE);
                }
            }
        }
        return landed;
    }

    /** The drill's wake: a grey spiral of wind and grit. */
    public static void drillTrail(ServerLevel level, LivingEntity e) {
        double h = e.getBbHeight() * 0.5;
        double r = Math.max(0.5, e.getBbWidth() * 0.7);
        float a = e.tickCount * 1.3f;
        for (int i = 0; i < 3; i++) {
            double ang = a + i * 2.094;
            Vec3 at = e.position().add(Math.cos(ang) * r, h + Math.sin(ang) * r, Math.sin(ang * 0.5) * 0.3);
            Vfx.one(level, Vfx.smoke(0xC8C2B8, 0.4f, 8), at, Vec3.ZERO);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + h, e.getZ(), 1, 0.2, 0.2, 0.2, 0);
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-6 ? Vec3.ZERO : f.normalize();
    }

    // ================================================================ Dynamic Marking

    private record Mark(UUID hound, long until) {}

    private static final Map<LivingEntity, Mark> MARKS = new WeakHashMap<>();

    public static boolean isMarked(LivingEntity target, NinjaHound hound) {
        Mark m = MARKS.get(target);
        return m != null && m.hound.equals(hound.getUUID()) && m.until > target.level().getGameTime();
    }

    /** Whether {@code target} carries the mark of {@code owner}'s hound (you hit it harder too). */
    public static boolean markedFor(LivingEntity target, LivingEntity owner) {
        Mark m = MARKS.get(target);
        if (m == null || m.until <= target.level().getGameTime()) {
            return false;
        }
        Entity hound = target.level() instanceof ServerLevel level ? level.getEntity(m.hound) : null;
        return hound instanceof NinjaHound h && owner.getUUID().equals(h.getOwnerUUID());
    }

    public static void dynamicMarking(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        NinjaHound hound = Hounds.houndOf(caster);
        if (hound == null || hound.isDown() || hound.isHidden()) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.hound.away"));
            return;
        }
        LivingEntity target = JutsuBehaviors.lookTarget(caster, Math.max(12, ctx.params().range()), 30);
        if (target == null && caster instanceof Player player) {
            target = HoundSmell.findScent(hound, player);
        }
        if (target == null) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.jutsu.no_target"));
            return;
        }
        if (hound.isCarried()) {
            Hounds.putDown(hound);
        }
        mark(hound, target, ctx.params().duration() > 0 ? ctx.params().duration() : MARK_TICKS);
        hound.setTarget(target);
        Vec3 to = target.position().subtract(hound.position());
        if (to.length() < 20) {
            // A dash in, the mark, and back to its fight.
            hound.setDeltaMovement(to.normalize().scale(Math.min(1.4, 0.4 + to.length() * 0.15)).add(0, 0.25, 0));
            hound.hasImpulse = true;
        }
    }

    public static void mark(NinjaHound hound, LivingEntity target, int ticks) {
        MARKS.put(target, new Mark(hound.getUUID(), target.level().getGameTime() + ticks));
        HoundSmell.mark(target, ticks);
        if (target.level() instanceof ServerLevel level) {
            Vfx.burst(level, Vfx.smoke(0xB8C27A, 0.5f, 30), target.position().add(0, target.getBbHeight() * 0.5, 0), 10, 0.4, 0.02);
        }
        hound.playSound(SoundEvents.WOLF_GROWL, 1f, hound.getVoicePitch());
        Hounds.addXp(hound, 3f);
    }

    // ================================================================ Man Beast Clone

    private static final Map<UUID, float[]> CLONE_STATE = new HashMap<>();

    public static void manBeastClone(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        NinjaHound hound = Hounds.houndOf(caster);
        if (hound == null || hound.isDown() || hound.isHidden() || hound.isFused() || hound.distanceTo(caster) > JOIN_RANGE) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.hound.away"));
            return;
        }
        if (!(hound.level() instanceof ServerLevel level)) {
            return;
        }
        if (hound.isCarried()) {
            Hounds.putDown(hound);
        }
        ShadowClone clone = com.noirm.registry.NoirmEntities.SHADOW_CLONE.get().create(level);
        if (clone == null) {
            return;
        }
        clone.moveTo(hound.getX(), hound.getY(), hound.getZ(), hound.getYRot(), 0f);
        clone.setOwner(caster);
        clone.setLife(CLONE_TICKS + CLONE_TICKS_PER_STAGE * hound.stage().ordinal());
        clone.setCombatStyle(Noirm.id("beast")); // on all fours, claws and fangs
        var health = clone.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(Math.max(clone.getMaxHealth(), hound.getMaxHealth()));
        }
        clone.setHealth(clone.getMaxHealth() * (hound.getHealth() / hound.getMaxHealth()));
        clone.setCustomName(Component.translatable("entity.noirm.beast_clone", caster.getDisplayName()));
        level.addFreshEntity(clone);
        hound.setHidden(true, clone.getUUID());
        CLONE_STATE.put(clone.getUUID(), new float[]{1f, (float) clone.getX(), (float) clone.getY(), (float) clone.getZ()});
        level.sendParticles(ParticleTypes.POOF, hound.getX(), hound.getY() + 0.6, hound.getZ(), 25, 0.4, 0.5, 0.4, 0.03);
        level.playSound(null, hound.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 1f, 0.6f);
        Hounds.addXp(hound, 4f);
    }

    /** While the hound wears your shape: it stays with the clone; when the clone goes, it's back. */
    static void tickHidden(NinjaHound hound, UUID cloneId) {
        if (!(hound.level() instanceof ServerLevel level)) {
            return;
        }
        Entity e = cloneId == null ? null : level.getEntity(cloneId);
        float[] state = cloneId == null ? null : CLONE_STATE.get(cloneId);
        if (e instanceof ShadowClone clone && clone.isAlive()) {
            hound.setPos(clone.getX(), clone.getY(), clone.getZ());
            if (state != null) {
                state[0] = clone.getHealth() / clone.getMaxHealth();
                state[1] = (float) clone.getX();
                state[2] = (float) clone.getY();
                state[3] = (float) clone.getZ();
            }
            return;
        }
        // The clone was dispelled (hit down or out of time): the hound reappears where it stood.
        if (state != null) {
            hound.setPos(state[1], state[2], state[3]);
            hound.setHealth(Math.max(1f, hound.getMaxHealth() * Math.max(0.1f, state[0])));
            CLONE_STATE.remove(cloneId);
        }
        hound.setHidden(false, null);
        level.sendParticles(ParticleTypes.POOF, hound.getX(), hound.getY() + 0.5, hound.getZ(), 15, 0.3, 0.3, 0.3, 0.02);
    }

    // ================================================================ Two-Headed Wolf

    public static void twoHeadedWolf(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        NinjaHound hound = Hounds.houndOf(caster);
        if (hound == null || hound.isDown() || hound.isHidden() || !hound.stage().canBeRidden() || hound.distanceTo(caster) > JOIN_RANGE) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.hound.too_small"));
            return;
        }
        if (hound.isFused()) {
            endFusion(hound);
            return;
        }
        fuse(hound, caster, ctx.params().duration() > 0 ? ctx.params().duration() : FUSION_TICKS);
    }

    public static void fuse(NinjaHound hound, LivingEntity owner, int ticks) {
        if (!(hound.level() instanceof ServerLevel level)) {
            return;
        }
        hound.setOrder(HoundOrder.FOLLOW);
        hound.setFused(ticks);
        if (owner.getVehicle() != hound) {
            owner.stopRiding();
            owner.startRiding(hound, true);
        }
        owner.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks + 10, 0, false, false));
        hound.setHealth(hound.getMaxHealth());
        level.sendParticles(ParticleTypes.POOF, hound.getX(), hound.getY() + 1.5, hound.getZ(), 60, 1.2, 1.2, 1.2, 0.05);
        Vfx.burst(level, Vfx.smoke(0xE8E4DC, 1.2f, 40), hound.position().add(0, 1.5, 0), 20, 1.2, 0.05);
        level.playSound(null, hound.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.NEUTRAL, 2f, 0.5f);
        Hounds.addXp(hound, 10f);
    }

    static void tickFused(NinjaHound hound) {
        int t = hound.fusedTicks() - 1;
        LivingEntity owner = hound.getOwner();
        if (t <= 0 || owner == null || owner.getVehicle() != hound) {
            endFusion(hound);
            return;
        }
        hound.setFused(t);
    }

    public static void endFusion(NinjaHound hound) {
        hound.setFused(0);
        LivingEntity owner = hound.getOwner();
        if (owner != null) {
            owner.removeEffect(MobEffects.INVISIBILITY);
            if (owner.getVehicle() == hound && !hound.stage().canBeRidden()) {
                owner.stopRiding();
            }
        }
        if (hound.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.POOF, hound.getX(), hound.getY() + 1, hound.getZ(), 40, 1, 1, 1, 0.04);
            level.playSound(null, hound.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 1.5f, 0.5f);
        }
    }

    /** Your damage dealt to a marked target, and taken while fused (from HoundEvents). */
    public static float ownerDamageDealt(LivingEntity owner, LivingEntity target, float amount) {
        return markedFor(target, owner) ? amount * MARK_BONUS : amount;
    }

    public static float ownerDamageTaken(LivingEntity owner, float amount) {
        return owner.getVehicle() instanceof NinjaHound hound && hound.isFused() && Crew.sameSide(hound, owner)
                ? amount * (1f - NinjaHound.FUSED_ARMOUR) : amount;
    }
}

package com.noirm.jutsu;

import com.noirm.combat.CombatSystem;
import com.noirm.vfx.Vfx;
import com.noirm.vfx.WoodVfx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wood Release: Wood Dragon. A long dragon of bark-scaled wood bursts out of the ground where it
 * stands, surges over to its target and coils round it from the feet up, horned head over theirs
 * with its jaw open. While it holds them they can't move or act, and it drinks their chakra (half
 * of it flows back to the caster). When the hold ends it crushes them and falls apart. Big
 * targets (a tailed beast) get a bigger coil. With no target it surges forward and dives back into
 * the ground, hitting what it passes.
 *
 * <p>The body is not blocks: the server and the client both work out the same path from the
 * dragon's spot, its age and its target ({@link #path}), and the client draws bark segments along
 * it (client/WoodReleaseRenderers). The entity itself never moves.
 */
public class WoodDragon extends Entity {
    /** Ticks for the head to reach the target. */
    public static final int SURGE = 16;
    /** Chakra drained from the target each second it's held (players' and ninjas' chakra). */
    public static final float DRAIN_PER_SECOND = 6f;
    /** Share of the drained chakra that flows back to the caster. */
    public static final float DRAIN_SHARE = 0.5f;
    /** Distance between the body's segments, in blocks. */
    public static final float SEGMENT = 0.85f;

    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(WoodDragon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HOLD = SynchedEntityData.defineId(WoodDragon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> END = SynchedEntityData.defineId(WoodDragon.class, EntityDataSerializers.VECTOR3);

    private LivingEntity owner;
    private LivingEntity target;
    private float damage;
    private float posture;
    private float power = 1f;
    private final Set<LivingEntity> struck = new HashSet<>();

    public WoodDragon(EntityType<? extends WoodDragon> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Sets it on its way: at {@code target} (held {@code hold} ticks), or with no target to {@code end}. */
    public void launch(LivingEntity owner, LivingEntity target, Vec3 end, int hold, float damage, float posture, float power) {
        this.owner = owner;
        this.target = target;
        this.damage = damage;
        this.posture = posture;
        this.power = power;
        entityData.set(TARGET, target == null ? -1 : target.getId());
        entityData.set(HOLD, Math.max(10, hold));
        entityData.set(END, new Vector3f((float) end.x, (float) end.y, (float) end.z));
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TARGET, -1);
        entityData.define(HOLD, 60);
        entityData.define(END, new Vector3f());
    }

    public int targetId() {
        return entityData.get(TARGET);
    }

    public int hold() {
        return entityData.get(HOLD);
    }

    public Vec3 end() {
        Vector3f v = entityData.get(END);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public LivingEntity heldTarget() {
        return target;
    }

    /** How big the dragon is for a target this wide: thicker and longer round a tailed beast. */
    public static float scaleFor(float targetWidth) {
        return Mth.clamp((float) Math.sqrt(Math.max(0.6f, targetWidth) / 0.6f), 1f, 3f);
    }

    // ---------------------------------------------------------------- the path

    /**
     * The dragon's body at {@code age} (ticks, may be fractional), head first, one point every
     * {@link #SEGMENT}·scale blocks back to where it comes out of the ground. {@code feet} is the
     * target's position (null: no target, it heads for {@link #end()}).
     */
    public List<Vec3> path(Vec3 feet, float width, float height, float age) {
        List<Vec3> line = fullPath(feet, width, height, age);
        float scale = feet == null ? 1f : scaleFor(width);
        // Cumulative lengths.
        double[] at = new double[line.size()];
        for (int i = 1; i < line.size(); i++) {
            at[i] = at[i - 1] + line.get(i).distanceTo(line.get(i - 1));
        }
        double total = at[at.length - 1];
        float surge = Mth.clamp(age / SURGE, 0f, 1f);
        double head = total * (1 - (1 - surge) * (1 - surge)); // eases out as it arrives
        List<Vec3> out = new ArrayList<>();
        double spacing = SEGMENT * scale;
        for (double s = head; s >= 0 && out.size() < 96; s -= spacing) {
            int i = 1;
            while (i < line.size() - 1 && at[i] < s) {
                i++;
            }
            double span = at[i] - at[i - 1];
            double t = span < 1e-6 ? 0 : (s - at[i - 1]) / span;
            out.add(line.get(i - 1).lerp(line.get(i), Mth.clamp(t, 0, 1)));
        }
        return out;
    }

    /** The whole route, tail (underground) to head, densely sampled. */
    private List<Vec3> fullPath(Vec3 feet, float width, float height, float age) {
        Vec3 origin = position();
        List<Vec3> pts = new ArrayList<>();
        // Out of the ground at a slant, already leaning toward where it's going.
        Vec3 goal = feet != null ? feet : end();
        Vec3 toward = goal.subtract(origin).multiply(1, 0, 1);
        toward = toward.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : toward.normalize();
        Vec3 deep = origin.subtract(toward.scale(1.6)).add(0, -2.5, 0);
        Vec3 rise = origin.add(toward.scale(0.9)).add(0, 2.2, 0);
        for (int i = 0; i <= 6; i++) {
            pts.add(deep.lerp(rise, i / 6.0));
        }
        if (feet == null) {
            Vec3 end = end();
            Vec3 dive = end.add(end.subtract(origin).multiply(1, 0, 1).normalize().scale(2)).add(0, -2.5, 0);
            Vec3 apex = rise.lerp(end, 0.5).add(0, 3 + rise.distanceTo(end) * 0.15, 0);
            bezier(pts, rise, apex, end.add(0, 0.6, 0), 24);
            bezier(pts, end.add(0, 0.6, 0), end.add(0, 0.2, 0).lerp(dive, 0.3), dive, 6);
            return pts;
        }
        float scale = scaleFor(width);
        // Squeezing: the coil tightens and loosens slowly, then crushes at the end.
        float hold = hold();
        float held = Math.max(0f, age - SURGE);
        float squeeze = 1f - 0.07f * (float) Math.sin(held * 0.35f) - (held > hold - 6 ? 0.25f * Mth.clamp((held - hold + 6) / 6f, 0f, 1f) : 0f);
        double radius = (width * 0.5 + 0.55 * scale) * squeeze;
        double top = height + 0.2;
        double start = Math.atan2(origin.z - feet.z, origin.x - feet.x);
        Vec3 first = feet.add(Math.cos(start) * radius, 0.25, Math.sin(start) * radius);
        Vec3 apex = rise.lerp(first, 0.5).add(0, 2 + rise.distanceTo(first) * 0.2, 0);
        bezier(pts, rise, apex, first, 20);
        double turns = 2.2;
        int steps = 44;
        for (int i = 1; i <= steps; i++) {
            double f = i / (double) steps;
            double a = start + f * turns * Math.PI * 2;
            pts.add(feet.add(Math.cos(a) * radius, 0.25 + f * (top - 0.25), Math.sin(a) * radius));
        }
        // The neck rears up over the head and looks down into the target's face.
        Vec3 last = pts.get(pts.size() - 1);
        Vec3 out = new Vec3(last.x - feet.x, 0, last.z - feet.z).normalize();
        Vec3 crest = feet.add(out.scale(radius * 1.25)).add(0, top + 1.3 * scale, 0);
        Vec3 face = feet.add(out.scale(radius * 0.55)).add(0, top + 0.7 * scale, 0);
        bezier(pts, last, crest, face, 8);
        return pts;
    }

    private static void bezier(List<Vec3> pts, Vec3 a, Vec3 b, Vec3 c, int steps) {
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 ab = a.lerp(b, t);
            Vec3 bc = b.lerp(c, t);
            pts.add(ab.lerp(bc, t));
        }
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        int age = tickCount;
        if (owner == null || !owner.isAlive() || age > SURGE + hold() + 40) {
            fallApart(level);
            return;
        }
        if (age <= 4) {
            WoodVfx.emerge(level, position(), 2.2f - age * 0.3f);
        }
        if (age == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 1.6f, 0.5f);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2f, 0.6f);
        }
        if (target == null) {
            tickFree(level, age);
        } else {
            tickHold(level, age);
        }
    }

    /** No target: it surges to its end point, hitting what the head passes, and dives into the ground. */
    private void tickFree(ServerLevel level, int age) {
        List<Vec3> body = path(null, 0, 0, age);
        Vec3 head = body.get(0);
        if (age % 2 == 0) {
            WoodVfx.tip(level, head, 1.2f);
        }
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(head, head).inflate(1.6),
                e -> JutsuEffects.isTarget(owner, e) && e.isAlive() && !struck.contains(e))) {
            struck.add(victim);
            JutsuEffects.hit(owner, victim, damage, 1.0f, posture, 0, victim.position().subtract(position()));
            CombatSystem.stun(victim, 15);
        }
        if (age >= SURGE + 4) {
            WoodVfx.emerge(level, end(), 2.5f);
            fallApart(level);
        }
    }

    private void tickHold(ServerLevel level, int age) {
        if (!target.isAlive() || target.level() != level || target.distanceToSqr(this) > 48 * 48) {
            fallApart(level);
            return;
        }
        List<Vec3> body = path(target.position(), target.getBbWidth(), target.getBbHeight(), age);
        if (age < SURGE) {
            if (age % 2 == 0) {
                WoodVfx.tip(level, body.get(0), scaleFor(target.getBbWidth()));
            }
            return;
        }
        int held = age - SURGE;
        if (held == 0) {
            catchTarget(level);
        }
        // Held fast: no moving, no acting.
        target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
        target.hurtMarked = true;
        if (held % 10 == 0) {
            CombatSystem.stun(target, 14);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 9, false, false));
            WoodVfx.crumble(level, body.get(level.random.nextInt(body.size())), 0.4f);
        }
        if (held > 0 && held % 20 == 0) {
            drain(level);
        }
        if (held >= hold()) {
            crush(level);
            fallApart(level);
        }
    }

    private void catchTarget(ServerLevel level) {
        Vec3 chest = target.position().add(0, target.getBbHeight() * 0.5, 0);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 1.6f, 0.4f);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 0.5f);
        WoodVfx.crumble(level, chest, 1.2f);
        Vfx.quake(level, chest, 0.6f, 16);
        JutsuEffects.hit(owner, target, damage * 0.5f, 0, posture * 0.5f, 0, Vec3.ZERO);
        CombatSystem.stun(target, 20);
        hitStop(target, 4);
    }

    /** The hold drinks chakra: from a player's or a ninja's pool; half flows back to the caster. */
    private void drain(ServerLevel level) {
        float taken = 0f;
        float want = DRAIN_PER_SECOND * power;
        if (target instanceof Player player) {
            taken = com.noirm.player.ShinobiCapability.get(player).map(d -> {
                float t = Math.min(want, d.getChakra());
                d.setChakra(d.getChakra() - t);
                return t;
            }).orElse(0f);
        } else if (target instanceof com.noirm.entity.SparringNinja ninja) {
            taken = Math.min(want, ninja.chakra());
            ninja.setChakra(ninja.chakra() - taken);
        } else {
            taken = want * 0.5f; // a beast's chakra is boundless; the wood still drinks some
        }
        float back = taken * DRAIN_SHARE;
        if (back > 0 && owner instanceof Player player) {
            com.noirm.player.ShinobiCapability.get(player).ifPresent(d -> d.setChakra(Math.min(d.getMaxChakra(), d.getChakra() + back)));
        } else if (back > 0 && owner instanceof com.noirm.entity.SparringNinja ninja) {
            ninja.setChakra(Math.min(ninja.maxChakra(), ninja.chakra() + back));
        }
        Vec3 chest = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vfx.streak(level, chest, owner.position().add(0, 1, 0), new Vfx.Palette(WoodVfx.SAP, WoodVfx.SAP_DEEP, 0x142808));
        Vfx.burst(level, Vfx.glow(WoodVfx.SAP, WoodVfx.SAP_DEEP, 0.5f, 0.1f, 12), chest, 6, target.getBbWidth() * 0.5, 0.03);
        JutsuEffects.hit(owner, target, damage * 0.15f, 0, 0, 0, Vec3.ZERO);
    }

    private void crush(ServerLevel level) {
        Vec3 chest = target.position().add(0, target.getBbHeight() * 0.5, 0);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 1.8f, 0.5f);
        JutsuEffects.hit(owner, target, damage, 0, posture, 0, Vec3.ZERO);
        CombatSystem.stun(target, 16);
        hitStop(target, 5);
        Vfx.quake(level, chest, 0.8f, 18);
    }

    private void fallApart(ServerLevel level) {
        if (isRemoved()) {
            return;
        }
        List<Vec3> body = target != null && target.isAlive() ? path(target.position(), target.getBbWidth(), target.getBbHeight(), tickCount)
                : path(null, 0, 0, Math.min(tickCount, SURGE));
        for (int i = 0; i < body.size(); i += 3) {
            WoodVfx.crumble(level, body.get(i), 0.7f);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 1.4f, 0.5f);
        discard();
    }

    static void hitStop(LivingEntity entity, int ticks) {
        com.noirm.network.NoirmNetwork.sendToTrackingAndSelf(entity, new com.noirm.network.CombatClientPackets.PlayAnimation(
                entity.getId(), com.noirm.network.CombatClientPackets.HITSTOP, ticks));
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(28);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    /** Gone with the world: never saved. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    public Entity ownerEntity() {
        return owner;
    }
}

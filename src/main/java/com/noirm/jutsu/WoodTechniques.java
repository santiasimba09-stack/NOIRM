package com.noirm.jutsu;

import com.noirm.combat.CombatSystem;
import com.noirm.entity.ShadowClone;
import com.noirm.entity.WoodClone;
import com.noirm.registry.NoirmBlocks;
import com.noirm.registry.NoirmEntities;
import com.noirm.vfx.Vfx;
import com.noirm.vfx.WoodVfx;
import com.noirm.world.Destruction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wood Release (Mokuton) at its epic scale (docs/CHAKRA_WAVE.md, docs/ART_STYLE.md): raw grey-green
 * wood, dust and rock on every emergence, slow heavy motion.
 * <ul>
 *   <li><b>wood_roots</b> (Root Snare): roots race under the ground to the enemy you look at and
 *       burst up round their legs: a hit, a {@code stun}, held in place; real roots for a moment.</li>
 *   <li><b>wood_clone</b>: {@code count} wood clones step out of the ground in a fan behind you.
 *       They fight; whoever breaks one is caught by its roots; your Substitution swaps you with one
 *       ({@link WoodClone}).</li>
 *   <li><b>wood_dome</b> (Wood Locking Wall): a dome of wood ({@code radius}) closes over you from
 *       the ground up, with slits to see through, and splinters away after {@code duration}.</li>
 *   <li><b>wood_dragon</b>: the Wood Dragon ({@link WoodDragon}): coils round the target, holds it
 *       {@code duration} ticks, drinks its chakra, crushes it.</li>
 *   <li><b>deep_forest</b> (Deep Forest Emergence): {@code count} huge twisting trunks burst out of
 *       the ground across {@code radius} round where you aim, one after another, throwing whoever is
 *       on them into the air; the forest tangles everyone in it for {@code duration} ticks and
 *       regrows away with the terrain.</li>
 *   <li><b>wood_colossus</b>: the Thousand-Arm Colossus ({@link WoodColossus}): {@code count} fists
 *       of {@code radius} crash down round the target.</li>
 * </ul>
 */
public final class WoodTechniques {
    /** The most blocks one Deep Forest grows (its trunks stop growing past this). */
    public static final int FOREST_BLOCK_BUDGET = 1300;
    /** Deep Forest trunks regrow away at this share of the terrain's regen time. */
    public static final float FOREST_REGEN_SCALE = 0.75f;
    /** Ticks between one trunk bursting out and the next. */
    public static final int TRUNK_STAGGER = 2;
    /** No trunk grows this close to the caster. */
    public static final double FOREST_CLEAR = 5;

    private WoodTechniques() {}

    private static BlockState wood(Direction.Axis axis) {
        return NoirmBlocks.MOKUTON_WOOD.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    // ---------------------------------------------------------------- Root Snare

    static void roots(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 18);
        Vec3 at = target != null ? target.position() : ElementalBehaviors.ground(level, ElementalBehaviors.aim(caster, p.range()));
        WoodVfx.rootLine(level, caster.position().add(ctx.flatLook().scale(0.8)), at);
        WoodVfx.emerge(level, at, 1.3f);
        Destruction.spikes(level, at, 1.4f, wood(Direction.Axis.Y), 2);
        JutsuBehaviors.sound(caster, com.noirm.sound.NoirmSounds.EARTH_RUMBLE.get(), 1.4f, 0.77f);
        level.playSound(null, at.x, at.y, at.z, com.noirm.sound.NoirmSounds.WOOD_BURST.get(), SoundSource.PLAYERS, 1.2f, 0.77f);
        List<LivingEntity> caught = new ArrayList<>();
        if (target != null) {
            caught.add(target);
        } else {
            caught.addAll(level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.5, 2, 1.5),
                    e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator()));
        }
        int hold = Math.round(p.stun() * Math.min(1.5f, ctx.power()));
        for (LivingEntity victim : caught) {
            JutsuEffects.hit(caster, victim, p.damage() * ctx.power(), 0, p.posture() * ctx.power(), 0, Vec3.ZERO);
            CombatSystem.stun(victim, hold);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, hold + 20, 3, false, false));
            victim.setDeltaMovement(Vec3.ZERO);
            victim.hurtMarked = true;
            JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
            WoodVfx.crumble(level, victim.position().add(0, 0.5, 0), 0.6f);
        }
    }

    // ---------------------------------------------------------------- Wood Clone

    static void clones(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        int count = Math.max(1, ctx.params().count());
        List<ShadowClone> existing = level.getEntitiesOfClass(ShadowClone.class, caster.getBoundingBox().inflate(64), c -> c.isOwnedBy(caster));
        for (int i = 0; i < existing.size() + count - ShadowClone.MAX_PER_OWNER; i++) {
            existing.get(i).dispel();
        }
        Vec3 back = ctx.flatLook().scale(-1);
        Vec3 side = new Vec3(-back.z, 0, back.x);
        for (int i = 0; i < count; i++) {
            WoodClone clone = NoirmEntities.WOOD_CLONE.get().create(level);
            if (clone == null) {
                continue;
            }
            // A fan behind you: the middle one furthest back.
            double spread = count == 1 ? 0 : (i / (double) (count - 1) - 0.5) * 2;
            Vec3 at = caster.position().add(back.scale(1.8 + 0.8 * (1 - Math.abs(spread)))).add(side.scale(spread * 2.2));
            if (!level.noCollision(clone, clone.getType().getAABB(at.x, at.y, at.z))) {
                at = caster.position();
            }
            clone.moveTo(at.x, at.y, at.z, caster.getYRot(), 0f);
            clone.setOwner(caster);
            clone.setLife(ctx.params().duration());
            level.addFreshEntity(clone);
            WoodVfx.emerge(level, at, 1.4f);
            WoodVfx.crumble(level, at.add(0, 1, 0), 0.7f);
        }
        level.playSound(null, caster.blockPosition(), com.noirm.sound.NoirmSounds.EARTH_RUMBLE.get(), SoundSource.PLAYERS, 1.5f, 0.71f);
        level.playSound(null, caster.blockPosition(), com.noirm.sound.NoirmSounds.WOOD_CREAK.get(), SoundSource.PLAYERS, 1.5f, 0.77f);
    }

    // ---------------------------------------------------------------- Wood Locking Wall

    static void dome(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float radius = Mth.clamp(p.radius(), 2.5f, 6f);
        BlockPos center = caster.blockPosition();
        Vec3 c = Vec3.atBottomCenterOf(center);
        int r = (int) Math.ceil(radius);
        // Shell blocks by height, so it closes from the ground up over a few ticks.
        List<List<BlockPos>> layers = new ArrayList<>();
        for (int y = 0; y <= r; y++) {
            layers.add(new ArrayList<>());
        }
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, 0, -r), center.offset(r, r, r))) {
            double d = Math.sqrt(pos.distToCenterSqr(c.x, c.y, c.z));
            if (d > radius - 0.5 && d <= radius + 0.5) {
                int y = pos.getY() - center.getY();
                // Slits at eye height to see out of (every third block round the ring).
                if (y == 1 && Math.floorMod(pos.getX() * 3 + pos.getZ(), 3) == 0) {
                    continue;
                }
                layers.get(Math.min(r, y)).add(pos.immutable());
            }
        }
        int[] tick = {0};
        WoodVfx.emerge(level, c, radius * 0.6f);
        JutsuBehaviors.sound(caster, com.noirm.sound.NoirmSounds.EARTH_RUMBLE.get(), 1.6f, 0.71f);
        GreatTechniques.later(now -> {
            int y = tick[0]++;
            if (y >= layers.size()) {
                return true;
            }
            for (BlockPos pos : layers.get(y)) {
                if (level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos).getFluidState().isEmpty()
                        && level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) {
                    BlockState block = wood(y == 0 ? Direction.Axis.Y : pos.getX() == center.getX() ? Direction.Axis.Z : Direction.Axis.X);
                    level.setBlock(pos, block, 3);
                    level.scheduleTick(pos, block.getBlock(), p.duration() + level.random.nextInt(12));
                    if (level.random.nextInt(4) == 0) {
                        WoodVfx.tip(level, Vec3.atCenterOf(pos), 0.8f);
                    }
                }
            }
            level.playSound(null, center, com.noirm.sound.NoirmSounds.WOOD_CREAK.get(), SoundSource.PLAYERS, 1.2f, 0.5f + y * 0.1f);
            return false;
        });
    }

    // ---------------------------------------------------------------- Wood Dragon

    static void dragon(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 20);
        WoodDragon dragon = NoirmEntities.WOOD_DRAGON.get().create(level);
        if (dragon == null) {
            return;
        }
        Vec3 toward = target != null ? target.position().subtract(caster.position()).multiply(1, 0, 1) : ctx.flatLook();
        toward = toward.lengthSqr() < 1e-4 ? ctx.flatLook() : toward.normalize();
        // It breaks out of the ground near the target and off to one side, so the caster still sees
        // what it does (bursting out at the caster's feet, its neck hid the target).
        Vec3 side = new Vec3(-toward.z, 0, toward.x).scale(level.random.nextBoolean() ? 1.8 : -1.8);
        Vec3 from = target != null ? target.position().subtract(toward.scale(Math.min(4.5, caster.distanceTo(target) * 0.5))).add(side)
                : caster.position().add(toward.scale(3)).add(side);
        Vec3 at = ElementalBehaviors.ground(level, from.add(0, 1, 0));
        Vec3 end = ElementalBehaviors.ground(level, ElementalBehaviors.aim(caster, p.range()));
        dragon.moveTo(at.x, at.y, at.z, 0, 0);
        dragon.launch(caster, target, end, p.duration(), p.damage() * ctx.power(), p.posture() * ctx.power(), ctx.power());
        level.addFreshEntity(dragon);
        WoodVfx.gather(level, caster.getEyePosition().add(ctx.look().scale(0.6)).add(0, -0.45, 0));
    }

    // ---------------------------------------------------------------- Deep Forest Emergence

    /** One trunk: where, how thick, how tall, its twist, when it starts. */
    private record Trunk(Vec3 base, float thickness, int height, double phase, double twist, int delay, Vec3 lean) {}

    static void forest(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        RandomSource random = level.random;
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 20);
        Vec3 center = ElementalBehaviors.ground(level, target != null ? target.position() : ElementalBehaviors.aim(caster, p.range()));
        float radius = Mth.clamp(p.radius() * (float) Math.sqrt(Math.max(0.5f, power)), 4f, 16f);
        int count = Math.max(3, p.count());
        List<Trunk> trunks = new ArrayList<>();
        // The first trunk bursts right under the target; the rest spread out from there.
        for (int i = 0; i < count * 3 && trunks.size() < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = trunks.isEmpty() ? 0.6 : radius * Math.sqrt(0.08 + 0.92 * random.nextDouble());
            Vec3 at = center.add(Math.cos(a) * d, 0, Math.sin(a) * d);
            if (at.distanceTo(caster.position()) < FOREST_CLEAR || trunks.stream().anyMatch(t -> t.base().distanceTo(at) < 3.6)) {
                continue;
            }
            Vec3 floor = floor(level, at);
            if (floor == null) {
                continue;
            }
            boolean big = trunks.size() % 3 != 2;
            float thick = big ? 1.5f + random.nextFloat() * 0.5f : 0.9f + random.nextFloat() * 0.3f;
            int height = big ? 11 + random.nextInt(7) : 7 + random.nextInt(4);
            Vec3 lean = floor.subtract(center).multiply(1, 0, 1);
            lean = lean.lengthSqr() < 1e-4 ? Vec3.ZERO : lean.normalize().scale(0.12); // splaying out from the middle
            int delay = trunks.size() * TRUNK_STAGGER + random.nextInt(2);
            trunks.add(new Trunk(floor, thick, height, random.nextDouble() * Math.PI * 2, 0.9 + random.nextDouble() * 0.6, delay, lean));
        }
        int[] age = {0};
        int[] placed = {0};
        Set<LivingEntity> thrown = new HashSet<>();
        int growth = trunks.isEmpty() ? 0 : trunks.get(trunks.size() - 1).delay() + 20;
        int life = growth + Math.max(20, p.duration());
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 2.5f, 0.6f);
        level.playSound(null, center.x, center.y, center.z, com.noirm.sound.NoirmSounds.EARTH_RUMBLE.get(), SoundSource.PLAYERS, 3f, 0.63f);
        WoodVfx.gather(level, caster.getEyePosition().add(ctx.look().scale(0.6)).add(0, -0.45, 0));
        GreatTechniques.later(now -> {
            int t = age[0]++;
            if (!caster.isAlive() || caster.level() != level) {
                return true;
            }
            // The ground heaves outward from the middle before the first trunks break through.
            if (t < 10) {
                float ring = radius * (t + 1) / 10f;
                for (int k = 0; k < 3; k++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    WoodVfx.emerge(level, floorOr(level, center.add(Math.cos(a) * ring, 0, Math.sin(a) * ring)), 1.6f);
                }
                if (t % 3 == 0) {
                    Vfx.quake(level, center, 0.8f, radius * 4);
                }
            }
            for (Trunk trunk : trunks) {
                int layer = t - trunk.delay();
                if (layer < 0 || layer >= trunk.height()) {
                    continue;
                }
                if (layer == 0) {
                    burst(caster, level, trunk, p, power, thrown);
                }
                // Slow and heavy: one layer a tick, a little slower near the top.
                if (layer > trunk.height() * 0.7 && t % 2 == 1) {
                    continue;
                }
                placed[0] += growLayer(level, trunk, layer, placed[0]);
            }
            // The forest tangles everyone in it while it stands.
            if (t % 10 == 0) {
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, 6, radius),
                        e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator()
                                && Math.hypot(e.getX() - center.x, e.getZ() - center.z) <= radius)) {
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 1, false, false));
                    JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
                }
                if (t > growth) {
                    Vec3 drift = center.add((random.nextDouble() - 0.5) * radius * 1.6, 3 + random.nextDouble() * 6, (random.nextDouble() - 0.5) * radius * 1.6);
                    Vfx.burst(level, WoodVfx.dust(1.2f, 60), drift, 2, 1.5, 0.01); // dust still hanging in the canopy
                }
            }
            return t >= life;
        });
    }

    /** A trunk breaks through: rock and dust, and whoever stands on it is hurled up. */
    private static void burst(LivingEntity caster, ServerLevel level, Trunk trunk, JutsuDefinition.Params p, float power, Set<LivingEntity> thrown) {
        Vec3 at = trunk.base();
        WoodVfx.emerge(level, at, 1.4f + trunk.thickness() * 0.9f);
        level.playSound(null, at.x, at.y, at.z, com.noirm.sound.NoirmSounds.WOOD_BURST.get(), SoundSource.PLAYERS, 1.4f, 0.4f + level.random.nextFloat() * 0.2f);
        double reach = trunk.thickness() + 1.2;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach, 2.5, reach),
                e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator()
                        && Math.hypot(e.getX() - at.x, e.getZ() - at.z) <= reach + e.getBbWidth() / 2)) {
            float scale = thrown.add(victim) ? 1f : 0.4f; // the first trunk to catch you hits hardest
            JutsuEffects.hit(caster, victim, p.damage() * power * scale, 0, p.posture() * power * scale, 0, Vec3.ZERO);
            Vec3 motion = victim.getDeltaMovement();
            victim.setDeltaMovement(motion.x * 0.3, Math.max(motion.y, p.knockback()), motion.z * 0.3);
            victim.hurtMarked = true;
            if (p.stun() > 0) {
                CombatSystem.stun(victim, p.stun());
            }
            WoodDragon.hitStop(victim, 3);
        }
    }

    /** One horizontal slice of a twisting trunk (and its branches near the top). Returns blocks placed. */
    private static int growLayer(ServerLevel level, Trunk trunk, int layer, int placedSoFar) {
        if (placedSoFar >= FOREST_BLOCK_BUDGET) {
            return 0;
        }
        double f = layer / (double) trunk.height();
        // The trunk winds round a slowly leaning axis and thins toward the top.
        double sway = 0.5 + f * 0.6;
        double a = trunk.phase() + layer * 0.22 * trunk.twist();
        Vec3 axis = trunk.base().add(trunk.lean().scale(layer)).add(Math.cos(a) * sway, layer, Math.sin(a) * sway);
        double r = Math.max(0.95, trunk.thickness() * (1 - 0.4 * f)); // never thinner than a cross, so it stays whole
        int placed = 0;
        int span = (int) Math.ceil(r);
        BlockPos middle = BlockPos.containing(axis);
        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                double ox = middle.getX() + 0.5 + dx - axis.x;
                double oz = middle.getZ() + 0.5 + dz - axis.z;
                if (ox * ox + oz * oz <= r * r + 0.25 && Destruction.grow(level, middle.offset(dx, 0, dz), wood(Direction.Axis.Y), FOREST_REGEN_SCALE)) {
                    placed++;
                }
            }
        }
        if (layer % 2 == 0) {
            WoodVfx.tip(level, axis.add(0, 0.5, 0), (float) r + 0.5f);
        }
        // Branches: two or three heavy limbs thrown out near the crown.
        if (layer == trunk.height() - 3 || layer == trunk.height() - 6 && trunk.thickness() > 1.2f) {
            double ba = a + Math.PI * (layer % 2);
            Direction.Axis branchAxis = Math.abs(Math.cos(ba)) > Math.abs(Math.sin(ba)) ? Direction.Axis.X : Direction.Axis.Z;
            int length = 3 + (int) (trunk.thickness() * 1.5);
            for (int s = 1; s <= length; s++) {
                BlockPos pos = BlockPos.containing(axis.add(Math.cos(ba) * (r + s * 0.8), s * 0.3, Math.sin(ba) * (r + s * 0.8)));
                if (Destruction.grow(level, pos, wood(branchAxis), FOREST_REGEN_SCALE)) {
                    placed++;
                }
            }
        }
        return placed;
    }

    /** The top of the ground at {@code at} (searching 6 up and 8 down), or null if there is none. */
    private static Vec3 floor(ServerLevel level, Vec3 at) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(at.x, at.y + 6, at.z).mutable();
        for (int i = 0; i < 14; i++) {
            BlockPos below = pos.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty() && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return new Vec3(at.x, pos.getY(), at.z);
            }
            pos.move(0, -1, 0);
        }
        return null;
    }

    private static Vec3 floorOr(ServerLevel level, Vec3 at) {
        Vec3 found = floor(level, at);
        return found != null ? found : at;
    }

    // ---------------------------------------------------------------- the Thousand-Arm Colossus

    static void colossus(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        WoodColossus colossus = NoirmEntities.WOOD_COLOSSUS.get().create(level);
        if (colossus == null) {
            return;
        }
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 20);
        Vec3 focus = target != null && target.onGround() ? target.position()
                : ElementalBehaviors.ground(level, target != null ? target.position() : ElementalBehaviors.aim(caster, p.range()));
        Vec3 behind = floorOr(level, caster.position().subtract(ctx.flatLook().scale(6)));
        colossus.moveTo(behind.x, behind.y, behind.z, caster.getYRot(), 0);
        colossus.setYRot(caster.getYRot());
        colossus.launch(caster, focus, p.count(), p.damage(), p.radius(), p.posture(), p.stun(), ctx.power());
        level.addFreshEntity(colossus);
        WoodVfx.gather(level, caster.getEyePosition().add(ctx.look().scale(0.6)).add(0, -0.45, 0));
        Vfx.quake(level, behind, 1.2f, 48);
    }
}

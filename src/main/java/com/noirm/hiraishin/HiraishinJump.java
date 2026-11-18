package com.noirm.hiraishin;

import com.noirm.Noirm;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatSystem;
import com.noirm.combat.Perception;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.JutsuEffects;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.weapon.ThrownWeapon;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The Flying Raijin itself: you are at the mark. No travel time, no dash: one tick you're here, the
 * next you're there, a yellow flash at both ends and an afterimage where you stood.
 * <ul>
 *   <li><b>Which mark:</b> the one nearest your crosshair (within {@value #AIM_CONE}°, up to
 *       {@value #MAX_RANGE} blocks), else the one the cycle key picked.</li>
 *   <li><b>Where you land:</b> on a block mark, standing on it or in front of it; at a kunai, holding it
 *       (still in flight, you keep a little of its speed); at a marked body, behind an enemy or beside an
 *       ally. Never inside a wall. Falls are forgiven for {@value #FALL_GUARD_TICKS} ticks.</li>
 *   <li><b>The Flying Thunder God Slash:</b> land at a kunai or a mark within {@value #SLASH_REACH}
 *       blocks of an enemy and you're behind them, facing their back: your next blow is a counter
 *       (can't be guarded, ×1.5) and hits ×{@value #SLASH_BONUS} more, within {@value #SLASH_WINDOW} ticks.</li>
 *   <li><b>Bring someone:</b> an enemy you're grabbing always comes; from ★{@value #CARRY_STARS} allies
 *       touching you (within {@value #CARRY_REACH} blocks) come too.</li>
 *   <li><b>Redirect (Guiding Thunder):</b> with an enemy projectile about to hit you, the jump sends <i>it</i>
 *       to the mark instead; a mark on an enemy sends it straight at them.</li>
 *   <li><b>Cost:</b> the jutsu's chakra per jump, {@value #FLOW_COST}× within {@value #FLOW_WINDOW} ticks of
 *       the last one. The wait between jumps shrinks by {@value #COOLDOWN_PER_STAR} per ★; from
 *       ★{@value #CHAIN_STARS} a slash resets it to {@value #CHAIN_COOLDOWN} ticks: the chain.</li>
 *   <li><b>Counterplay:</b> an enemy with an open Sharingan, or perception ×{@value #READ_RATIO} yours,
 *       reads the flash: they're warned where you land (an NPC turns and guards) and the slash's counter
 *       doesn't open.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class HiraishinJump {
    public static final double MAX_RANGE = 256;
    public static final double AIM_CONE = 22;
    public static final float COOLDOWN_PER_STAR = 0.15f;
    public static final int FLOW_WINDOW = 24;
    public static final float FLOW_COST = 0.6f;
    public static final int SEALLESS_STARS = 3;
    /** Below ★3 the jump waits for one seal (ticks). */
    public static final int SEAL_TICKS = 4;
    public static final int CARRY_STARS = 2;
    public static final int CHAIN_STARS = 4;
    public static final int CHAIN_COOLDOWN = 3;
    public static final double CARRY_REACH = 1.8;
    public static final double SLASH_REACH = 4.0;
    public static final int SLASH_WINDOW = 20;
    public static final float SLASH_BONUS = 1.3f;
    public static final double BEHIND = 1.0;
    public static final float READ_RATIO = 1.3f;
    public static final double REDIRECT_RANGE = 7;
    public static final int REDIRECT_ETA = 14;
    public static final int FALL_GUARD_TICKS = 40;
    /** A kunai still in flight passes this share of its speed on to you. */
    public static final double KUNAI_MOMENTUM = 0.3;

    /** The outcome of a jump (tests read it). */
    public record Result(boolean ok, Vec3 from, Vec3 to, HiraishinMark mark, LivingEntity slashTarget, List<LivingEntity> carried,
                         boolean read, Projectile redirected) {
        static Result fail(Vec3 at) {
            return new Result(false, at, at, null, null, List.of(), false, null);
        }
    }

    private record Slash(LivingEntity target, long until) {}

    private static final Map<LivingEntity, Long> FALL_GUARD = new WeakHashMap<>();
    private static final Map<LivingEntity, Slash> SLASHES = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> LAST_JUMP = new WeakHashMap<>();
    private static final Map<LivingEntity, Boolean> CHAINED = new WeakHashMap<>();
    private static final Map<Player, Long> PENDING_SEAL = new WeakHashMap<>();

    private HiraishinJump() {}

    // ---------------------------------------------------------------- entry points

    /** The Flying Raijin in a loadout slot (JutsuCaster already took the chakra). */
    public static void cast(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        if (redirect(caster) != null) {
            return;
        }
        HiraishinMark mark = choose(caster);
        if (mark == null) {
            message(caster, Component.translatable("message.noirm.hiraishin.no_mark"));
            com.noirm.jutsu.SpiritTechniques.addChakra(caster, JutsuCaster.cost(caster, ctx.id(), ctx.def()) * 0.8f); // nothing to go to
            return;
        }
        jumpTo(caster, mark);
    }

    /** The jump key (X): works in and out of combat mode. */
    static void fromKey(ServerPlayer player) {
        JutsuDefinition def = JutsuData.get(HiraishinLearning.RAIJIN);
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (def == null || data == null) {
            return;
        }
        if (!data.jutsu().knows(HiraishinLearning.RAIJIN)) {
            message(player, HiraishinLearning.knowsFormula(player) ? Component.translatable("message.noirm.hiraishin.not_yet")
                    : Component.translatable("message.noirm.hiraishin.needs_formula"));
            return;
        }
        if (PENDING_SEAL.containsKey(player) || !CombatSystem.canAct(player) && !CombatSystem.canAttackNow(player)) {
            return;
        }
        if (JutsuCaster.checkRequirements(player, HiraishinLearning.RAIJIN, def) != JutsuCaster.Result.OK) {
            message(player, Component.translatable("message.noirm.jutsu.sealed"));
            return;
        }
        if (data.jutsu().cooldown(HiraishinLearning.RAIJIN) > 0) {
            return; // quietly: masters hammer this key
        }
        boolean incoming = incomingProjectile(player) != null;
        if (!incoming && choose(player) == null) {
            message(player, Component.translatable("message.noirm.hiraishin.no_mark"));
            return;
        }
        float cost = JutsuCaster.cost(player, HiraishinLearning.RAIJIN, def);
        if (!player.getAbilities().instabuild && !data.tryConsumeChakra(cost)) {
            message(player, Component.translatable("message.noirm.jutsu.no_chakra"));
            return;
        }
        int stars = data.jutsu().masteryLevel(HiraishinLearning.RAIJIN);
        if (stars < SEALLESS_STARS && !incoming) {
            // One quick seal first (the ram); masters skip it.
            PENDING_SEAL.put(player, player.level().getGameTime() + SEAL_TICKS);
            com.noirm.network.NoirmNetwork.sendToTrackingAndSelf(player,
                    new com.noirm.network.CombatClientPackets.PlayAnimation(player.getId(), Noirm.id("cast_seals"), SEAL_TICKS + 2));
            return;
        }
        fire(player, data);
    }

    private static void fire(ServerPlayer player, ShinobiData data) {
        JutsuDefinition def = JutsuData.get(HiraishinLearning.RAIJIN);
        if (redirect(player) == null) {
            HiraishinMark mark = choose(player);
            if (mark == null) {
                data.setChakra(Math.min(data.getMaxChakra(), data.getChakra() + JutsuCaster.cost(player, HiraishinLearning.RAIJIN, def)));
                message(player, Component.translatable("message.noirm.hiraishin.no_mark"));
                return;
            }
            jumpTo(player, mark);
        }
        data.jutsu().setCooldown(HiraishinLearning.RAIJIN, JutsuCaster.cooldown(player, HiraishinLearning.RAIJIN, def));
        data.jutsu().addXp(HiraishinLearning.RAIJIN, 1);
        com.noirm.training.TrainingSystem.onJutsuCast(player, "hiraishin");
        data.markPowersDirty();
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING_SEAL.isEmpty()) {
            return;
        }
        for (Map.Entry<Player, Long> entry : new ArrayList<>(PENDING_SEAL.entrySet())) {
            Player player = entry.getKey();
            if (player.level().getGameTime() >= entry.getValue()) {
                PENDING_SEAL.remove(player);
                if (player instanceof ServerPlayer sp && sp.isAlive()) {
                    ShinobiCapability.get(sp).ifPresent(d -> fire(sp, d));
                }
            }
        }
    }

    // ---------------------------------------------------------------- tuning hooks (JutsuCaster)

    /** The wait after a jump: the jutsu's cooldown, −15% per ★, a sealing clan's bonus; the chain's reset. */
    public static int cooldownTicks(LivingEntity caster, JutsuDefinition def) {
        if (Boolean.TRUE.equals(CHAINED.remove(caster))) {
            return CHAIN_COOLDOWN;
        }
        int stars = HiraishinLearning.stars(caster, HiraishinLearning.RAIJIN);
        float scale = Math.max(0.2f, 1f - COOLDOWN_PER_STAR * stars) * HiraishinAffinity.of(caster).cooldownMultiplier();
        return Math.max(CHAIN_COOLDOWN, Math.round(def.cooldown() * scale));
    }

    /** Chakra per jump: cheaper in the flow of a chain, and for a sealing clan. */
    public static float costFactor(LivingEntity caster) {
        Long last = LAST_JUMP.get(caster);
        float flow = last != null && caster.level().getGameTime() - last <= FLOW_WINDOW ? FLOW_COST : 1f;
        return flow * HiraishinAffinity.of(caster).costMultiplier();
    }

    // ---------------------------------------------------------------- choosing a mark

    /** The mark {@code jumper} means: nearest the crosshair, else the selected one; null if none in reach. */
    public static HiraishinMark choose(LivingEntity jumper) {
        if (jumper.getServer() == null) {
            return null;
        }
        HiraishinMarks data = HiraishinMarks.get(jumper.getServer());
        Vec3 eye = jumper.getEyePosition();
        Vec3 look = jumper.getViewVector(1f);
        HiraishinMark best = null;
        double bestAngle = Double.MAX_VALUE;
        for (HiraishinMark mark : data.marks(jumper.getUUID())) {
            if (!mark.dim().equals(jumper.level().dimension())) {
                continue;
            }
            Vec3 at = HiraishinMarking.resolve(jumper.getServer(), jumper.getUUID(), mark);
            Entity carrier = HiraishinMarking.carrier(jumper.getServer(), mark);
            if (carrier == jumper) {
                continue; // a mark on yourself goes nowhere
            }
            Vec3 aimAt = carrier instanceof LivingEntity body ? at.add(0, body.getBbHeight() * 0.6, 0) : at;
            double distance = aimAt.distanceTo(eye);
            if (distance > MAX_RANGE || distance < 0.8) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(aimAt.subtract(eye).normalize()), -1, 1)));
            double slack = Math.toDegrees(Math.atan2(0.8, distance)); // close marks are easy to point at
            if (angle <= AIM_CONE + slack && angle < bestAngle) {
                best = mark;
                bestAngle = angle;
            }
        }
        if (best != null) {
            return best;
        }
        HiraishinMark selected = data.selected(jumper.getUUID());
        if (selected != null && selected.dim().equals(jumper.level().dimension())
                && HiraishinMarking.resolve(jumper.getServer(), jumper.getUUID(), selected).distanceTo(eye) <= MAX_RANGE
                && HiraishinMarking.carrier(jumper.getServer(), selected) != jumper) {
            return selected;
        }
        return null;
    }

    // ---------------------------------------------------------------- the jump

    /** {@code jumper} is at {@code mark}, now. */
    public static Result jumpTo(LivingEntity jumper, HiraishinMark mark) {
        if (!(jumper.level() instanceof ServerLevel level) || jumper.getServer() == null || mark == null) {
            return Result.fail(jumper.position());
        }
        if (!mark.dim().equals(level.dimension())) {
            return Result.fail(jumper.position());
        }
        Vec3 from = jumper.position();
        Vec3 at = HiraishinMarking.resolve(jumper.getServer(), jumper.getUUID(), mark);
        Entity carrier = HiraishinMarking.carrier(jumper.getServer(), mark);
        Vec3 velocity = Vec3.ZERO;
        Vec3 desired;
        LivingEntity slashTarget = null;
        switch (mark.kind()) {
            case KUNAI -> {
                boolean flying = carrier instanceof ThrownWeapon kunai && !kunai.isStuck() && kunai.getDeltaMovement().lengthSqr() > 0.04;
                desired = at.subtract(0, flying ? 1.25 : 0.6, 0);
                if (flying) {
                    velocity = carrier.getDeltaMovement().scale(KUNAI_MOMENTUM);
                    if (carrier instanceof ThrownWeapon kunai) {
                        ItemCatch.catchKunai(jumper, kunai); // you land holding it
                    }
                }
            }
            case BLOCK -> desired = blockStand(jumper, mark);
            case BODY, ALLY -> {
                if (carrier instanceof LivingEntity body) {
                    desired = mark.kind() == HiraishinMark.Kind.BODY ? behind(body, jumper) : beside(body, jumper);
                    if (mark.kind() == HiraishinMark.Kind.BODY && JutsuEffects.isTarget(jumper, body)) {
                        slashTarget = body;
                    }
                } else {
                    desired = at;
                }
            }
            default -> desired = at;
        }
        // The Flying Thunder God Slash: an enemy close to where you arrive, and you're behind them.
        boolean fromSide = false;
        if (slashTarget == null && mark.kind() != HiraishinMark.Kind.ALLY) {
            slashTarget = nearestEnemy(jumper, desired.add(0, 0.9, 0), SLASH_REACH);
            if (slashTarget != null) {
                // A kunai at their feet: behind them. One scattered further off: you strike from that side,
                // so a ring of kunai means blows from every side (the chain).
                Vec3 off = desired.subtract(slashTarget.position()).multiply(1, 0, 1);
                fromSide = off.length() >= 1.3;
                desired = !fromSide ? behind(slashTarget, jumper)
                        : slashTarget.position().add(off.normalize().scale(BEHIND + slashTarget.getBbWidth() * 0.5 + jumper.getBbWidth() * 0.5));
                velocity = Vec3.ZERO;
            }
        }
        Vec3 to = safeSpot(level, jumper, desired);
        if (to == null) {
            message(jumper, Component.translatable("message.noirm.hiraishin.blocked"));
            return Result.fail(from);
        }
        // Who comes along: a grabbed enemy always; allies touching you from ★2.
        List<LivingEntity> carried = passengers(jumper);
        float yaw = jumper.getYRot();
        float pitch = jumper.getXRot();
        if (slashTarget != null) {
            yaw = (float) (Mth.atan2(slashTarget.getZ() - to.z, slashTarget.getX() - to.x) * Mth.RAD_TO_DEG) - 90f;
            pitch = 10f;
        }
        teleport(jumper, to, yaw, pitch);
        jumper.setDeltaMovement(velocity);
        jumper.hurtMarked = true;
        jumper.fallDistance = 0;
        FALL_GUARD.put(jumper, level.getGameTime() + FALL_GUARD_TICKS);
        LAST_JUMP.put(jumper, level.getGameTime());
        for (int i = 0; i < carried.size(); i++) {
            LivingEntity other = carried.get(i);
            Vec3 ring = new Vec3(Math.cos(i * 2.1 + 0.6), 0, Math.sin(i * 2.1 + 0.6)).scale(1.2);
            Vec3 spot = safeSpot(level, other, to.add(ring));
            if (spot != null) {
                Vec3 was = other.position();
                teleport(other, spot, other.getYRot(), other.getXRot());
                other.setDeltaMovement(Vec3.ZERO);
                other.fallDistance = 0;
                FALL_GUARD.put(other, level.getGameTime() + FALL_GUARD_TICKS);
                HiraishinFx.flash(level, was, spot, other, HiraishinFx.JUMP);
            }
        }
        boolean read = false;
        if (slashTarget != null) {
            read = reads(slashTarget, jumper);
            if (read) {
                warn(slashTarget, jumper, to);
            } else {
                CombatSystem.openDodgeCounter(jumper, slashTarget, fromSide ? 1f : 0f);
                SLASHES.put(jumper, new Slash(slashTarget, level.getGameTime() + SLASH_WINDOW));
                if (HiraishinLearning.stars(jumper, HiraishinLearning.RAIJIN) >= CHAIN_STARS) {
                    CHAINED.put(jumper, true);
                }
            }
        }
        HiraishinFx.flash(level, from, to, jumper, slashTarget != null ? HiraishinFx.SLASH : HiraishinFx.JUMP);
        return new Result(true, from, to, mark, slashTarget, carried, read, null);
    }

    /** Where to stand for a block mark: on top of it, under it, or in front of a wall, the mark at chest height. */
    private static Vec3 blockStand(LivingEntity jumper, HiraishinMark mark) {
        Direction face = mark.face();
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        return switch (face) {
            case UP -> mark.pos().add(0, 0.01, 0);
            case DOWN -> mark.pos().subtract(0, jumper.getBbHeight() + 0.05, 0);
            default -> mark.pos().add(normal.scale(jumper.getBbWidth() * 0.5 + 0.08)).subtract(0, jumper.getBbHeight() * 0.62, 0);
        };
    }

    /** Behind {@code target}, facing their back. */
    public static Vec3 behind(LivingEntity target, LivingEntity jumper) {
        Vec3 facing = Vec3.directionFromRotation(0, target.getYRot());
        return target.position().subtract(facing.scale(BEHIND + target.getBbWidth() * 0.5 + jumper.getBbWidth() * 0.5));
    }

    private static Vec3 beside(LivingEntity target, LivingEntity jumper) {
        Vec3 facing = Vec3.directionFromRotation(0, target.getYRot());
        Vec3 side = new Vec3(-facing.z, 0, facing.x);
        return target.position().add(side.scale(0.6 + target.getBbWidth() * 0.5 + jumper.getBbWidth() * 0.5));
    }

    private static LivingEntity nearestEnemy(LivingEntity jumper, Vec3 at, double reach) {
        return jumper.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach),
                        e -> e != jumper && e.isAlive() && !e.isSpectator() && JutsuEffects.isTarget(jumper, e)
                                && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand) && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(at) <= reach)
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(at))).orElse(null);
    }

    /** A free spot for {@code entity} at or near {@code desired} (never inside a wall), or null. */
    public static Vec3 safeSpot(ServerLevel level, Entity entity, Vec3 desired) {
        AABB box = entity.getBoundingBox();
        Vec3 base = entity.position();
        if (fits(level, entity, box, base, desired)) {
            return desired;
        }
        double[] ys = {0.5, 1.0, -0.5, 1.5, -1.0, 2.0};
        double[] rs = {0.5, 1.0, 1.5};
        for (double dy : ys) {
            Vec3 up = desired.add(0, dy, 0);
            if (fits(level, entity, box, base, up)) {
                return up;
            }
        }
        for (double r : rs) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                for (double dy : new double[] {0, 1, -1}) {
                    Vec3 spot = desired.add(Math.cos(a) * r, dy, Math.sin(a) * r);
                    if (fits(level, entity, box, base, spot)) {
                        return spot;
                    }
                }
            }
        }
        return null;
    }

    private static boolean fits(ServerLevel level, Entity entity, AABB box, Vec3 base, Vec3 at) {
        return level.noCollision(entity, box.move(at.subtract(base)));
    }

    private static List<LivingEntity> passengers(LivingEntity jumper) {
        List<LivingEntity> out = new ArrayList<>();
        CombatState state = CombatStates.getIfPresent(jumper);
        if (state != null && state.clinchTarget() != null && state.clinchTarget().isAlive()) {
            out.add(state.clinchTarget()); // the enemy you're grabbing comes with you (into your trap)
        }
        int stars = HiraishinLearning.stars(jumper, HiraishinLearning.RAIJIN);
        int allies = stars >= 4 ? 4 : stars >= 3 ? 2 : stars >= CARRY_STARS ? 1 : 0;
        if (allies > 0) {
            jumper.level().getEntitiesOfClass(LivingEntity.class, jumper.getBoundingBox().inflate(CARRY_REACH),
                            e -> e != jumper && e.isAlive() && !e.isSpectator() && !e.isPassenger() && JutsuEffects.isFriendly(jumper, e)
                                    && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand) && !out.contains(e))
                    .stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(jumper))).limit(allies).forEach(out::add);
        }
        return out;
    }

    private static void teleport(Entity entity, Vec3 to, float yaw, float pitch) {
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(to.x, to.y, to.z, yaw, pitch);
        } else {
            entity.moveTo(to.x, to.y, to.z, yaw, pitch);
            entity.setYHeadRot(yaw);
            if (entity instanceof LivingEntity living) {
                living.setYBodyRot(yaw);
            }
        }
        entity.setYRot(yaw);
    }

    // ---------------------------------------------------------------- counterplay

    /** Does {@code target} read the jump coming? An open Sharingan, or perception far above the jumper's. */
    public static boolean reads(LivingEntity target, LivingEntity jumper) {
        if (com.noirm.dojutsu.Sharingan.activeStage(target) > 0) {
            return true;
        }
        return Perception.perception(target) >= Perception.perception(jumper) * READ_RATIO;
    }

    private static void warn(LivingEntity reader, LivingEntity jumper, Vec3 at) {
        if (reader instanceof ServerPlayer player) {
            HiraishinFx.read(player, at, jumper);
            player.displayClientMessage(Component.translatable("message.noirm.hiraishin.read"), true);
        } else if (reader instanceof Mob mob) {
            mob.getLookControl().setLookAt(at.x, at.y + 1.4, at.z, 180, 90);
            float yaw = (float) (Mth.atan2(at.z - mob.getZ(), at.x - mob.getX()) * Mth.RAD_TO_DEG) - 90f;
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setYHeadRot(yaw);
            CombatSystem.setGuard(mob, true);
        }
        message(jumper, Component.translatable("message.noirm.hiraishin.was_read", reader.getDisplayName()));
    }

    // ---------------------------------------------------------------- Guiding Thunder: redirect

    /** The enemy projectile about to hit {@code jumper}, or null. */
    public static Projectile incomingProjectile(LivingEntity jumper) {
        Vec3 chest = jumper.position().add(0, jumper.getBbHeight() * 0.55, 0);
        return jumper.level().getEntitiesOfClass(Projectile.class, jumper.getBoundingBox().inflate(REDIRECT_RANGE), p -> {
            if (p.getOwner() == jumper || p.getOwner() != null && JutsuEffects.isFriendly(jumper, p.getOwner())) {
                return false;
            }
            if (p instanceof ThrownWeapon kunai && (kunai.isStuck() || kunai.hasDealtDamage())) {
                return false;
            }
            Vec3 v = p.getDeltaMovement();
            double speed = v.length();
            Vec3 to = chest.subtract(p.position());
            if (speed < 0.15 || to.length() > REDIRECT_RANGE) {
                return false;
            }
            return v.normalize().dot(to.normalize()) > 0.55 && to.length() / speed <= REDIRECT_ETA;
        }).stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(jumper))).orElse(null);
    }

    /**
     * Sends the projectile about to hit {@code jumper} to their chosen mark (a marked enemy: straight
     * at them). Returns the projectile, or null if there was none or no mark.
     */
    public static Projectile redirect(LivingEntity jumper) {
        Projectile projectile = incomingProjectile(jumper);
        if (projectile == null || !(jumper.level() instanceof ServerLevel level)) {
            return null;
        }
        HiraishinMark mark = choose(jumper);
        if (mark == null || !mark.dim().equals(level.dimension())) {
            return null;
        }
        Vec3 from = projectile.position();
        Vec3 at = HiraishinMarking.resolve(jumper.getServer(), jumper.getUUID(), mark);
        Entity carrier = HiraishinMarking.carrier(jumper.getServer(), mark);
        double speed = projectile.getDeltaMovement().length();
        Vec3 to;
        Vec3 velocity;
        if (carrier instanceof LivingEntity body && JutsuEffects.isTarget(jumper, body)) {
            Vec3 chest = body.position().add(0, body.getBbHeight() * 0.6, 0);
            Vec3 back = projectile.getDeltaMovement().normalize().scale(-1);
            to = chest.add(back.scale(2.5));
            velocity = chest.subtract(to).normalize().scale(speed);
        } else {
            to = at.add(0, mark.kind() == HiraishinMark.Kind.BLOCK && mark.face() == Direction.UP ? 1.2 : 0.4, 0);
            velocity = projectile.getDeltaMovement();
        }
        projectile.setOwner(jumper); // it's yours now: it can hit the one who threw it
        projectile.moveTo(to.x, to.y, to.z, projectile.getYRot(), projectile.getXRot());
        projectile.setDeltaMovement(velocity);
        projectile.hurtMarked = true;
        LAST_JUMP.put(jumper, level.getGameTime());
        HiraishinFx.flash(level, from, to, jumper, HiraishinFx.REDIRECT);
        message(jumper, Component.translatable("message.noirm.hiraishin.redirected"));
        return projectile;
    }

    // ---------------------------------------------------------------- events

    /** No fall damage right after a jump (you arrive where you meant to, standing). */
    @SubscribeEvent
    public static void fall(LivingFallEvent event) {
        Long until = FALL_GUARD.get(event.getEntity());
        if (until != null && event.getEntity().level().getGameTime() <= until) {
            event.setCanceled(true);
        }
    }

    /** The Thunder God Slash: the first blow after landing behind them hits harder. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void slashLands(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker.level().isClientSide()) {
            return;
        }
        Slash slash = SLASHES.get(attacker);
        if (slash == null || slash.target() != event.getEntity()) {
            return;
        }
        SLASHES.remove(attacker);
        if (attacker.level().getGameTime() > slash.until()) {
            return;
        }
        event.setAmount(event.getAmount() * SLASH_BONUS);
        if (attacker.level() instanceof ServerLevel level) {
            LivingEntity victim = event.getEntity();
            Vec3 chest = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
            com.noirm.vfx.Vfx.burst(level, com.noirm.vfx.Vfx.glow(HiraishinFx.YELLOW.core(), HiraishinFx.YELLOW.mid(), 1.1f, 0.1f, 5), chest, 1, 0, 0);
            com.noirm.vfx.Vfx.radial(level, com.noirm.vfx.Vfx.spark(HiraishinFx.YELLOW.mid(), 0.2f, 7), chest, 12, 0.5);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), com.noirm.sound.NoirmSounds.HIT_HEAVY.get(), SoundSource.PLAYERS, 1.1f, 1.2f);
        }
    }

    /** Is a slash window open for {@code attacker} on {@code target}? (tests) */
    public static boolean slashReady(LivingEntity attacker, LivingEntity target) {
        Slash slash = SLASHES.get(attacker);
        return slash != null && slash.target() == target && attacker.level().getGameTime() <= slash.until();
    }

    public static void reset(LivingEntity entity) {
        FALL_GUARD.remove(entity);
        SLASHES.remove(entity);
        LAST_JUMP.remove(entity);
        CHAINED.remove(entity);
    }

    private static void message(LivingEntity to, Component text) {
        if (to instanceof Player player) {
            player.displayClientMessage(text, true);
        }
    }

    /** Landing on a kunai still in flight: you catch it (it goes back in your hand or inventory). */
    static final class ItemCatch {
        private ItemCatch() {}

        static void catchKunai(LivingEntity jumper, ThrownWeapon kunai) {
            UUID id = kunai.getUUID();
            if (jumper instanceof Player player && !player.getAbilities().instabuild) {
                if (!player.getInventory().add(kunai.getItem().copy())) {
                    player.drop(kunai.getItem().copy(), false);
                }
            }
            kunai.discard();
            if (jumper.getServer() != null) {
                HiraishinMarks.get(jumper.getServer()).removeEverywhere(m -> id.equals(m.entity()));
            }
        }
    }
}

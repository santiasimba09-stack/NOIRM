package com.noirm.entity;

import com.noirm.combat.AttackInput;
import com.noirm.combat.CombatPhase;
import com.noirm.combat.CombatState;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatSystem;
import com.noirm.combat.Perception;
import com.noirm.dojutsu.Byakugan;
import com.noirm.jutsu.CastType;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.JutsuEffects;
import com.noirm.jutsu.ModeSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * How a ninja fights: close in, throw a combo, back off, and react to the opponent's wind-ups
 * with a guard, a dodge or a substitution (more often against weaker opponents, since their
 * attacks look slower to it: perception works both ways).
 *
 * <p><b>Jutsu</b> are picked by range: a mode or shadow clones to open a fight, projectiles and
 * binds at range, a Chidori-style dash in between, bursts and the Rasengan up close, a dome or
 * a wall when badly hurt. NPCs pay chakra like players (see {@code SparringNinja.chakra()}),
 * respect each jutsu's cooldown, keep a little chakra back for a Substitution, and never fire
 * through a crew mate. Crew mates cast what's in their loadout. All tuning is below.
 *
 * <p><b>Weapons:</b> a bow or crossbow in hand makes them keep their distance and shoot; a
 * shield makes them guard rather than dodge. Swords and the like just make their hits count
 * more (that's in {@code CombatSystem}).
 */
class NinjaFightGoal extends Goal {
    private static final double ATTACK_RANGE = 2.7;
    private static final float BASE_REACTION_CHANCE = 0.25f;

    // ---------------------------------------------------------------- jutsu tuning
    /** Ticks between jutsu decisions. */
    static final int JUTSU_THINK_TICKS = 8;
    /** Rest after casting a jutsu before the next one (random in between). */
    static final int JUTSU_REST_MIN = 30;
    static final int JUTSU_REST_MAX = 70;
    /** The first jutsu of a fight can come this soon. */
    static final int JUTSU_FIRST_REST = 6;
    /** Chances per decision: a ranged jutsu at range, a close one up close, a dash or flicker in between. */
    static final float RANGED_CHANCE = 0.6f;
    static final float CLOSE_CHANCE = 0.3f;
    static final float DASH_CHANCE = 0.35f;
    /** Chance to open a fight with a mode (Lightning Armor…) or shadow clones, if they know one. */
    static final float OPENER_CHANCE = 0.8f;
    /** Below this share of health with the enemy close: chance of a defensive jutsu (dome, wall). */
    static final float DEFENSIVE_HEALTH = 0.5f;
    static final float DEFENSIVE_CHANCE = 0.4f;
    /** Range bands (blocks). */
    static final double CLOSE_RANGE = 4.0;
    static final double DASH_MIN = 3.5;
    static final double DASH_MAX = 9.0;
    static final double RANGED_MIN = 4.5;
    static final double RANGED_MAX = 20.0;
    /** Chakra kept back for a Substitution (offensive jutsu won't dip below it). */
    static final float CHAKRA_RESERVE = 15f;
    /** Village residents don't use S-rank jutsu at home (craters in their own streets). */
    static final String RESIDENT_BANNED_RANK = "S";

    // ---------------------------------------------------------------- weapon tuning
    /** With a bow or crossbow: the distance band they keep, draw / reload time, arrow speed and spread. */
    static final double ARCHER_MIN = 6;
    static final double ARCHER_MAX = 14;
    static final int BOW_DRAW_TICKS = 20;
    static final int CROSSBOW_RELOAD_TICKS = 32;
    static final float ARROW_SPEED = 2.4f;
    static final float ARROW_SPREAD = 3f;
    /** When the opponent winds up and they react: chance to guard (rather than dodge), with and without a shield. */
    static final float GUARD_CHANCE = 0.6f;
    static final float SHIELD_GUARD_CHANCE = 0.9f;

    private static final List<List<AttackInput>> COMBOS = List.of(
            List.of(AttackInput.LIGHT, AttackInput.LIGHT, AttackInput.LIGHT),
            List.of(AttackInput.LIGHT, AttackInput.LIGHT, AttackInput.HEAVY),
            List.of(AttackInput.LIGHT, AttackInput.HEAVY),
            List.of(AttackInput.HEAVY),
            List.of(AttackInput.LAUNCHER, AttackInput.LIGHT),
            List.of(AttackInput.LIGHT, AttackInput.BACK_HEAVY),
            List.of(AttackInput.BACK_LIGHT, AttackInput.LIGHT, AttackInput.HEAVY),
            List.of(AttackInput.FORWARD_LIGHT, AttackInput.LIGHT),
            List.of(AttackInput.LIGHT, AttackInput.LIGHT, AttackInput.LAUNCHER, AttackInput.LIGHT, AttackInput.LIGHT));
    /** Against a raised guard: grab (grabs beat guard), knee twice, then headbutt or throw. */
    private static final List<List<AttackInput>> GRAPPLES = List.of(
            List.of(AttackInput.GRAB, AttackInput.LIGHT, AttackInput.LIGHT, AttackInput.HEAVY),
            List.of(AttackInput.GRAB, AttackInput.LIGHT, AttackInput.BACK_HEAVY));

    private static final ResourceLocation SUBSTITUTION = com.noirm.Noirm.id("universal/substitution");
    /** Jutsu behaviors by when they're used. */
    private static final Set<String> RANGED = Set.of("projectile", "genjutsu", "beam", "bind", "pillar", "rain", "cloud", "strike", "wave",
            "amaterasu", "tsukuyomi", "kotoamatsukami");
    private static final Set<String> CLOSE = Set.of("burst", "nova", "rasengan", "palms", "susanoo", "kamui");
    private static final Set<String> DASH = Set.of("dash_strike");
    private static final Set<String> FLICKER = Set.of("flicker");
    private static final Set<String> OPENERS = Set.of("mode", "shadow_clone", "buff", "susanoo");
    private static final Set<String> DEFENSIVE = Set.of("dome", "wall", "susanoo", "izanagi");

    private final SparringNinja ninja;
    private final Deque<AttackInput> combo = new ArrayDeque<>();
    private int attackCooldown;
    private int jutsuRest;
    private boolean openerDone;
    private int guardTicks;
    private boolean reactedToCurrentWindup;
    /** Bow drawn (or crossbow loading) for this many ticks. */
    private int drawTicks;

    NinjaFightGoal(SparringNinja ninja) {
        this.ninja = ninja;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = ninja.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        openerDone = false;
        jutsuRest = JUTSU_FIRST_REST;
        drawTicks = 0;
    }

    @Override
    public void stop() {
        combo.clear();
        CombatSystem.setGuard(ninja, false);
        ninja.getNavigation().stop();
        ninja.stopUsingItem();
        if (ModeSystem.activeMode(ninja) != null && !(ninja instanceof ShadowClone)) {
            ModeSystem.end(ninja); // the fight's over: power down
        }
    }

    @Override
    public void tick() {
        LivingEntity target = ninja.getTarget();
        if (target == null) {
            return;
        }
        ninja.getLookControl().setLookAt(target, 60f, 60f);
        CombatState self = CombatStates.get(ninja);
        double distance = ninja.distanceTo(target);
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (jutsuRest > 0) {
            jutsuRest--;
        }
        payForMode();

        if (self.phase() == CombatPhase.STAGGER) {
            combo.clear();
            ninja.stopUsingItem();
            drawTicks = 0;
            // Held in a clinch: struggle free now and then.
            if (self.clinchedBy() != null && ninja.getRandom().nextFloat() < 0.04f) {
                CombatSystem.requestDodge(ninja, -1f, 0f);
            }
            return;
        }
        if (guardTicks > 0 && --guardTicks == 0) {
            CombatSystem.setGuard(ninja, false);
        }
        reactToOpponent(target, distance);

        // Badly hurt crew mates back off to recover, guard up, and only throw jutsu from range.
        if (com.noirm.ally.AllyMind.shouldRetreat(ninja)) {
            combo.clear();
            if (distance < 8) {
                Vec3 away = ninja.position().subtract(target.position()).normalize().scale(6);
                ninja.getNavigation().moveTo(ninja.getX() + away.x, ninja.getY(), ninja.getZ() + away.z, 1.2);
            }
            if (distance < 3 && !self.isGuarding()) {
                CombatSystem.setGuard(ninja, true);
                guardTicks = 20;
            }
            thinkJutsu(target, distance, true);
            return;
        }
        if (holdsRangedWeapon() && distance > ATTACK_RANGE) {
            archer(target, distance, self);
            thinkJutsu(target, distance, false);
            return;
        }
        if (drawTicks > 0) {
            drawTicks = 0;
            ninja.stopUsingItem();
        }
        boolean busy = self.phase() != CombatPhase.IDLE || self.isGuarding();
        com.noirm.ally.Tactics.Range range = com.noirm.ally.Tactics.range(ninja);
        if (busy) {
            ninja.getNavigation().stop();
        } else if (range == com.noirm.ally.Tactics.Range.FAR || (range == com.noirm.ally.Tactics.Range.MID && attackCooldown > 0)) {
            holdBand(target, distance, range); // a crew mate's tactics: hang back (mid range only between combos)
        } else if (distance > ATTACK_RANGE - 0.2) {
            ninja.getNavigation().moveTo(target, 1.0);
        } else {
            ninja.getNavigation().stop();
        }

        thinkJutsu(target, distance, false);
        CombatState opponent = CombatStates.getIfPresent(target);
        if (combo.isEmpty() && attackCooldown <= 0 && distance <= 2.2 && !self.isGuarding()
                && opponent != null && opponent.isGuarding() && ninja.getRandom().nextFloat() < 0.5f) {
            combo.addAll(GRAPPLES.get(ninja.getRandom().nextInt(GRAPPLES.size())));
        }
        if (combo.isEmpty() && attackCooldown <= 0 && distance > ATTACK_RANGE + 0.5 && distance < 5 && !self.isGuarding()
                && ninja.getRandom().nextFloat() < 0.03f) {
            combo.add(AttackInput.FORWARD_HEAVY); // a dash attack to close the gap
        }
        if (combo.isEmpty() && attackCooldown <= 0 && distance <= ATTACK_RANGE && !self.isGuarding()) {
            combo.addAll(COMBOS.get(ninja.getRandom().nextInt(COMBOS.size())));
        }
        if (!combo.isEmpty() && CombatSystem.canAttackNow(ninja)) {
            if (distance > (combo.peek() == AttackInput.FORWARD_HEAVY ? 5.5 : ATTACK_RANGE + 1)) {
                combo.clear(); // target escaped: stop swinging at air
            } else if (CombatSystem.requestAttack(ninja, combo.peek())) {
                combo.poll();
                if (combo.isEmpty()) {
                    attackCooldown = 20 + ninja.getRandom().nextInt(25);
                }
            }
        }
    }

    // ---------------------------------------------------------------- jutsu

    /** Keeps the distance band a crew mate's range tactic asks for. */
    private void holdBand(LivingEntity target, double distance, com.noirm.ally.Tactics.Range range) {
        boolean far = range == com.noirm.ally.Tactics.Range.FAR;
        double min = far ? com.noirm.ally.Tactics.FAR_MIN : com.noirm.ally.Tactics.MID_MIN;
        double max = far ? com.noirm.ally.Tactics.FAR_MAX : com.noirm.ally.Tactics.MID_MAX;
        if (distance > max) {
            ninja.getNavigation().moveTo(target, 1.0);
        } else if (distance < min) {
            Vec3 away = ninja.position().subtract(target.position()).normalize().scale(4);
            ninja.getNavigation().moveTo(ninja.getX() + away.x, ninja.getY(), ninja.getZ() + away.z, 1.15);
        } else {
            ninja.getNavigation().stop();
        }
    }

    /** Chakra kept back: the Substitution reserve, or half their chakra when told to conserve it. */
    private float chakraReserve() {
        return com.noirm.ally.Tactics.jutsuUse(ninja) == com.noirm.ally.Tactics.JutsuUse.CONSERVE
                ? Math.max(CHAKRA_RESERVE, ninja.maxChakra() * com.noirm.ally.Tactics.CONSERVE_RESERVE) : CHAKRA_RESERVE;
    }

    /** Every few ticks, between combos: maybe use a jutsu that suits the range. */
    private void thinkJutsu(LivingEntity target, double distance, boolean retreating) {
        if (com.noirm.ally.Tactics.jutsuUse(ninja) == com.noirm.ally.Tactics.JutsuUse.NONE) {
            return; // told to fight without jutsu
        }
        if (jutsuRest > 0 || !combo.isEmpty() || ninja.tickCount % JUTSU_THINK_TICKS != 0 || !CombatSystem.canAct(ninja)) {
            return;
        }
        var random = ninja.getRandom();
        ResourceLocation pick = null;
        boolean away = false;
        if (!openerDone) {
            openerDone = true;
            if (!retreating && random.nextFloat() < OPENER_CHANCE) {
                pick = choose(OPENERS, distance, false);
            }
        }
        if (pick == null && distance < 6 && ninja.getHealth() < ninja.getMaxHealth() * DEFENSIVE_HEALTH && random.nextFloat() < DEFENSIVE_CHANCE) {
            pick = choose(DEFENSIVE, distance, false);
        }
        if (pick == null && retreating && distance < CLOSE_RANGE && random.nextFloat() < DASH_CHANCE) {
            pick = choose(FLICKER, distance, false);
            away = true; // a body flicker out of reach
        }
        if (pick == null && !retreating && distance <= CLOSE_RANGE && random.nextFloat() < CLOSE_CHANCE) {
            pick = choose(CLOSE, distance, false);
        }
        if (pick == null && !retreating && distance >= DASH_MIN && distance <= DASH_MAX && random.nextFloat() < DASH_CHANCE) {
            pick = choose(DASH, distance, false);
        }
        boolean clear = distance >= RANGED_MIN && ninja.hasLineOfSight(target) && clearShot(target);
        if (pick == null && clear && distance <= RANGED_MAX && random.nextFloat() < RANGED_CHANCE) {
            pick = choose(RANGED, distance, true);
        }
        if (pick == null && !retreating && distance > DASH_MAX && random.nextFloat() < DASH_CHANCE) {
            pick = choose(FLICKER, distance, false); // too far: flicker in
        }
        if (pick == null && distance >= RANGED_MIN && !clear && ninja.hasLineOfSight(target) && ninja.getNavigation().isDone()) {
            sidestep(target); // a crew mate in the way: find a clear line
        }
        if (pick != null && cast(pick, target, 0.5f + 0.5f * random.nextFloat(), away)) {
            jutsuRest = JUTSU_REST_MIN + random.nextInt(JUTSU_REST_MAX - JUTSU_REST_MIN + 1);
            if (com.noirm.ally.Tactics.jutsuUse(ninja) == com.noirm.ally.Tactics.JutsuUse.CONSERVE) {
                jutsuRest *= com.noirm.ally.Tactics.CONSERVE_REST_FACTOR;
            }
        }
    }

    /** A random castable jutsu with one of {@code behaviors} that's ready, affordable and in range, or null. */
    private ResourceLocation choose(Set<String> behaviors, double distance, boolean ranged) {
        List<ResourceLocation> options = ninja.castableJutsu().stream().filter(id -> {
            JutsuDefinition def = JutsuData.get(id);
            if (def == null || !behaviors.contains(def.behavior()) || def.cast() == CastType.CHANNEL || id.equals(SUBSTITUTION)) {
                return false;
            }
            if (ninja.isResident() && RESIDENT_BANNED_RANK.equals(def.rank())) {
                return false;
            }
            if (ranged && def.params().range() > 0 && distance > def.params().range() + 1) {
                return false; // out of its reach
            }
            if ("mode".equals(def.behavior()) && ModeSystem.activeMode(ninja) != null) {
                return false; // already powered up (casting it again would switch it off)
            }
            if ("shadow_clone".equals(def.behavior()) && (ninja instanceof ShadowClone || hasClones())) {
                return false;
            }
            return JutsuCaster.npcReady(ninja, id) && JutsuCaster.checkRequirements(ninja, id, def) == JutsuCaster.Result.OK
                    && ninja.chakra() - JutsuCaster.cost(ninja, id, def) >= chakraReserve();
        }).toList();
        return options.isEmpty() ? null : options.get(ninja.getRandom().nextInt(options.size()));
    }

    /** Faces the target (or away from it) and casts, paying the chakra. */
    private boolean cast(ResourceLocation id, LivingEntity target, float charge, boolean away) {
        JutsuDefinition def = JutsuData.get(id);
        if (def == null) {
            return false;
        }
        float cost = JutsuCaster.cost(ninja, id, def);
        if (ninja.chakra() < cost) {
            return false;
        }
        if (target != null) {
            aim(target, away);
        }
        if (!JutsuCaster.npcCast(ninja, id, charge)) {
            return false;
        }
        ninja.tryUseChakra(cost);
        return true;
    }

    /** Modes cost chakra every second while on; out of chakra, the mode ends. */
    private void payForMode() {
        ResourceLocation mode = ModeSystem.activeMode(ninja);
        if (mode == null || ninja instanceof ShadowClone) {
            return;
        }
        JutsuDefinition def = JutsuData.get(mode);
        float drain = def == null ? 0f : def.mode().map(JutsuDefinition.Mode::drain).orElse(0f) / 20f;
        if (drain > 0 && !ninja.tryUseChakra(drain)) {
            ModeSystem.end(ninja);
        }
    }

    private boolean hasClones() {
        return !ninja.level().getEntitiesOfClass(ShadowClone.class, ninja.getBoundingBox().inflate(32), c -> c.isOwnedBy(ninja)).isEmpty();
    }

    /** Turns to face the target exactly (jutsu fly where the caster looks), or straight away from it. */
    private void aim(LivingEntity target, boolean away) {
        double dx = target.getX() - ninja.getX();
        double dz = target.getZ() - ninja.getZ();
        double dy = target.getY(0.5) - ninja.getEyeY();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f + (away ? 180f : 0f);
        float pitch = away ? 0f : (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        ninja.setYRot(yaw);
        ninja.setYHeadRot(yaw);
        ninja.setYBodyRot(yaw);
        ninja.setXRot(pitch);
    }

    /** Nobody on their side stands in the line between them and the target. */
    private boolean clearShot(LivingEntity target) {
        Vec3 from = ninja.getEyePosition();
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 line = to.subtract(from);
        double length = line.lengthSqr();
        if (length < 1e-4) {
            return true;
        }
        for (LivingEntity other : ninja.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.5),
                e -> e != ninja && e != target && e.isAlive() && (JutsuEffects.isFriendly(ninja, e) || ninja.isAlliedTo(e)))) {
            Vec3 center = other.position().add(0, other.getBbHeight() * 0.5, 0);
            double t = Mth.clamp(center.subtract(from).dot(line) / length, 0, 1);
            if (from.add(line.scale(t)).distanceTo(center) < other.getBbWidth() * 0.5 + 0.6) {
                return false;
            }
        }
        return true;
    }

    /** Steps a few blocks to the side to get a clear line on the target. */
    private void sidestep(LivingEntity target) {
        Vec3 toTarget = target.position().subtract(ninja.position());
        Vec3 side = new Vec3(-toTarget.z, 0, toTarget.x).normalize().scale(ninja.getRandom().nextBoolean() ? 3 : -3);
        ninja.getNavigation().moveTo(ninja.getX() + side.x, ninja.getY(), ninja.getZ() + side.z, 1.1);
    }

    // ---------------------------------------------------------------- bows and crossbows

    private boolean holdsRangedWeapon() {
        ItemStack weapon = ninja.getMainHandItem();
        return weapon.getItem() instanceof BowItem || weapon.getItem() instanceof CrossbowItem;
    }

    /** With a bow: keep a good distance and shoot whenever there's a clear line. */
    private void archer(LivingEntity target, double distance, CombatState self) {
        boolean sees = ninja.getSensing().hasLineOfSight(target);
        if (!sees || distance > ARCHER_MAX) {
            ninja.getNavigation().moveTo(target, 1.0);
        } else if (distance < ARCHER_MIN) {
            Vec3 away = ninja.position().subtract(target.position()).normalize().scale(4);
            ninja.getNavigation().moveTo(ninja.getX() + away.x, ninja.getY(), ninja.getZ() + away.z, 1.1);
        } else {
            ninja.getNavigation().stop();
        }
        ItemStack weapon = ninja.getMainHandItem();
        boolean crossbow = weapon.getItem() instanceof CrossbowItem;
        if (!sees || distance > ARCHER_MAX + 6 || self.isGuarding() || !CombatSystem.canAct(ninja) || !clearShot(target)) {
            if (drawTicks > 0) {
                drawTicks = 0;
                ninja.stopUsingItem();
            }
            return;
        }
        if (!crossbow && !ninja.isUsingItem()) {
            ninja.startUsingItem(InteractionHand.MAIN_HAND); // the bow visibly draws
        }
        if (++drawTicks >= (crossbow ? CROSSBOW_RELOAD_TICKS : BOW_DRAW_TICKS)) {
            aim(target, false);
            shoot(target, weapon, crossbow);
            drawTicks = 0;
            ninja.stopUsingItem();
        }
    }

    /**
     * Looses an arrow: from the off hand if they hold arrows there (tipped ones too; used up unless
     * the bow has Infinity), otherwise a plain one that can't be picked up. Power, Punch, Flame and
     * Piercing apply; the bow wears.
     */
    private void shoot(LivingEntity target, ItemStack weapon, boolean crossbow) {
        ItemStack offhand = ninja.getOffhandItem();
        boolean ownArrows = offhand.getItem() instanceof ArrowItem && !(ninja instanceof ShadowClone); // (a clone's are chakra copies)
        ItemStack ammo = ownArrows ? offhand : new ItemStack(Items.ARROW);
        float power = crossbow ? 1f : BowItem.getPowerForTime(drawTicks);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(ninja, ammo, power);
        if (weapon.getItem() instanceof BowItem bow) {
            arrow = bow.customArrow(arrow);
        }
        if (crossbow) {
            arrow.setShotFromCrossbow(true);
            int piercing = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, weapon);
            if (piercing > 0) {
                arrow.setPierceLevel((byte) piercing);
            }
        }
        double dx = target.getX() - ninja.getX();
        double dy = target.getY(0.3333) - arrow.getY();
        double dz = target.getZ() - ninja.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.08, dz, ARROW_SPEED * (crossbow ? 1.3f : power), ARROW_SPREAD);
        if (ownArrows && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, weapon) == 0) {
            offhand.shrink(1);
            arrow.pickup = AbstractArrow.Pickup.ALLOWED; // a real arrow: it can be picked up again
        }
        ninja.playSound(crossbow ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.ARROW_SHOOT, 1f, 1f / (ninja.getRandom().nextFloat() * 0.4f + 1.2f));
        ninja.level().addFreshEntity(arrow);
        weapon.hurtAndBreak(1, ninja, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
    }

    // ---------------------------------------------------------------- reactions

    /** When the opponent winds up nearby, maybe guard or dodge — once per wind-up. */
    private void reactToOpponent(LivingEntity target, double distance) {
        CombatState opponent = CombatStates.getIfPresent(target);
        if (opponent == null || opponent.phase() != CombatPhase.WINDUP) {
            reactedToCurrentWindup = false;
            return;
        }
        if (reactedToCurrentWindup || distance > 4.5) {
            return;
        }
        reactedToCurrentWindup = true;
        CombatState self = CombatStates.get(ninja);
        if (self.phase() != CombatPhase.IDLE && self.phase() != CombatPhase.RECOVERY) {
            return;
        }
        float chance = Math.min(0.85f, BASE_REACTION_CHANCE * Perception.slowFactor(ninja, target));
        if (ninja.getRandom().nextFloat() >= chance) {
            return;
        }
        combo.clear();
        if (ninja.knownJutsu().contains(SUBSTITUTION) && ninja.getRandom().nextFloat() < 0.25f
                && cast(SUBSTITUTION, null, 1f, false)) {
            return;
        }
        if (ninja.knownJutsu().contains(Byakugan.ROTATION) && ninja.getRandom().nextFloat() < 0.35f
                && cast(Byakugan.ROTATION, null, 1f, false)) {
            return;
        }
        boolean shield = !CombatSystem.heldShield(ninja).isEmpty();
        if (ninja.getRandom().nextFloat() < (shield ? SHIELD_GUARD_CHANCE : GUARD_CHANCE)) {
            ninja.stopUsingItem();
            drawTicks = 0;
            CombatSystem.setGuard(ninja, true);
            guardTicks = shield ? 25 : 15;
        } else {
            float strafe = ninja.getRandom().nextBoolean() ? 0.6f : -0.6f;
            CombatSystem.requestDodge(ninja, -1f, strafe);
        }
    }
}

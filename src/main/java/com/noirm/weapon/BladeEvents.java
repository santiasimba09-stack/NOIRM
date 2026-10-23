package com.noirm.weapon;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.combat.CombatPhase;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatSystem;
import com.noirm.jutsu.JutsuUser;
import com.noirm.player.ShinobiCapability;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What the v0.13 blades ({@link NinjaBlades}) do, server side: their traits and chakra flow on
 * every melee hit (vanilla swings and combat moves alike), bleeding, the flow glow, and the tessen
 * swatting projectiles away. The trait numbers are here; damage and speed are data
 * ({@link BladeStats}).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class BladeEvents {
    /** Tantō: damage multiplier for a hit in the back… */
    public static final float BACKSTAB_MULTIPLIER = 1.6f;
    /** …"in the back" = the target's facing points this much away from the attacker (dot product). */
    public static final double BACKSTAB_DOT = -0.25;
    /** Tonfa: posture damage added by every blow. */
    public static final float CRUSH_POSTURE = 8f;
    /** Tekko-kagi: bleeding lasts this long (seconds) and hurts this much each second. */
    public static final int BLEED_SECONDS = 3;
    public static final float BLEED_DAMAGE = 1f;
    /** Nodachi: how hard every hit shoves the target back (on top of the move's own knockback). */
    public static final double CLEAVE_PUSH = 0.45;
    /** Tessen: a swatted projectile keeps this share of its speed, knocked aside. */
    public static final double DEFLECT_SPEED = 0.25;
    /** How often (ticks) the flow glow is checked. */
    public static final int FLOW_CHECK_TICKS = 10;

    /** Who is bleeding, and for how many more ticks (server side). */
    private static final Map<LivingEntity, Integer> BLEEDING = new WeakHashMap<>();

    private BladeEvents() {}

    @SubscribeEvent
    public static void hurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || event.getSource().getDirectEntity() != attacker
                || attacker.level().isClientSide()) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        if (!(weapon.getItem() instanceof NinjaBladeItem blade)) {
            return;
        }
        LivingEntity target = event.getEntity();
        ServerLevel level = (ServerLevel) attacker.level();
        Vec3 chest = target.position().add(0, target.getBbHeight() * 0.6, 0);
        BladeStats stats = blade.stats();
        float amount = event.getAmount();
        int bleedBonus = 0;

        BladeStats.Flow flow = stats.flow();
        if (flow != null && hasNature(attacker, flow.nature()) && pay(attacker, flow.chakra())) {
            amount *= flow.damage();
            if (flow.stun() > 0) {
                CombatSystem.stun(target, flow.stun());
            }
            bleedBonus = Math.round(flow.bonus());
            flowParticles(level, flow.nature(), chest);
        }

        switch (stats.trait()) {
            case BACKSTAB -> {
                if (isBehind(attacker, target)) {
                    amount *= BACKSTAB_MULTIPLIER;
                    level.sendParticles(ParticleTypes.CRIT, chest.x, chest.y, chest.z, 12, 0.25, 0.3, 0.25, 0.25);
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9f, 0.7f);
                }
            }
            case CRUSH -> CombatSystem.addPostureDamage(target, CRUSH_POSTURE);
            case BLEED -> startBleeding(target, (BLEED_SECONDS + bleedBonus) * 20);
            case CLEAVE -> {
                Vec3 away = target.position().subtract(attacker.position()).multiply(1, 0, 1);
                if (away.lengthSqr() < 1.0e-4) {
                    away = Vec3.directionFromRotation(0, attacker.getYRot());
                }
                away = away.normalize();
                target.push(away.x * CLEAVE_PUSH, 0.08, away.z * CLEAVE_PUSH);
                target.hurtMarked = true;
            }
            default -> { }
        }
        event.setAmount(amount);
    }

    /** Is {@code attacker} behind {@code target} (the target faces away from them)? */
    public static boolean isBehind(LivingEntity attacker, LivingEntity target) {
        Vec3 toAttacker = attacker.position().subtract(target.position()).multiply(1, 0, 1);
        if (toAttacker.lengthSqr() < 1.0e-4) {
            return false;
        }
        Vec3 facing = Vec3.directionFromRotation(0, target.getYHeadRot());
        return toAttacker.normalize().dot(facing) < BACKSTAB_DOT;
    }

    // ---------------------------------------------------------------- bleeding

    /** Starts (or lengthens) bleeding on {@code target} for {@code ticks}. */
    public static void startBleeding(LivingEntity target, int ticks) {
        BLEEDING.merge(target, ticks, Math::max);
    }

    /** Ticks of bleeding left (0 = not bleeding). */
    public static int bleedTicks(LivingEntity target) {
        return BLEEDING.getOrDefault(target, 0);
    }

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (!BLEEDING.isEmpty()) {
            Integer left = BLEEDING.get(entity);
            if (left != null) {
                bleed(entity, left);
            }
        }
        if (entity.tickCount % FLOW_CHECK_TICKS == 0) {
            updateFlow(entity);
        }
    }

    private static void bleed(LivingEntity entity, int left) {
        if (left <= 0 || !entity.isAlive()) {
            BLEEDING.remove(entity);
            return;
        }
        BLEEDING.put(entity, left - 1);
        if (left % 20 == 1) { // a second after the cut, then every second
            int before = entity.invulnerableTime;
            entity.invulnerableTime = 0; // a bleed tick doesn't wait for (or eat) the hit's immunity
            entity.hurt(entity.damageSources().generic(), BLEED_DAMAGE);
            entity.invulnerableTime = before;
        }
        if (left % 4 == 0) {
            ((ServerLevel) entity.level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.55, entity.getZ(), 3, 0.18, 0.25, 0.18, 0.05);
        }
    }

    // ---------------------------------------------------------------- chakra flow

    /** Marks the main-hand blade as flowing (it glows) while its wielder has the blade's nature. */
    private static void updateFlow(LivingEntity entity) {
        ItemStack stack = entity.getMainHandItem();
        if (!(stack.getItem() instanceof NinjaBladeItem blade) || blade.stats().flow() == null) {
            return;
        }
        BladeStats.Flow flow = blade.stats().flow();
        boolean should = hasNature(entity, flow.nature()) && canPay(entity, flow.chakra());
        if (should != NinjaBladeItem.isFlowing(stack)) {
            if (should) {
                stack.getOrCreateTag().putBoolean(NinjaBladeItem.FLOW_TAG, true);
            } else if (stack.getTag() != null) {
                stack.getTag().remove(NinjaBladeItem.FLOW_TAG);
                if (stack.getTag().isEmpty()) {
                    stack.setTag(null);
                }
            }
        }
    }

    /** Does {@code wielder} have {@code nature} (a player's clan/trained natures, a ninja's affinity)? */
    public static boolean hasNature(LivingEntity wielder, Nature nature) {
        if (wielder instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> d.identity().affinity(nature) > 0f).orElse(false);
        }
        return wielder instanceof JutsuUser user && user.jutsuAffinity(nature) > 0f;
    }

    private static boolean canPay(LivingEntity attacker, float chakra) {
        if (attacker instanceof Player player && !player.getAbilities().instabuild) {
            return ShinobiCapability.get(player).map(d -> d.getChakra() >= chakra).orElse(false);
        }
        return true;
    }

    /** Players pay chakra for a flowing hit; ninjas (and creative players) don't. */
    private static boolean pay(LivingEntity attacker, float chakra) {
        if (attacker instanceof Player player && !player.getAbilities().instabuild) {
            return ShinobiCapability.get(player).map(d -> d.tryConsumeChakra(chakra)).orElse(false);
        }
        return true;
    }

    private static void flowParticles(ServerLevel level, Nature nature, Vec3 at) {
        switch (nature) {
            case LIGHTNING -> {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 16, 0.3, 0.4, 0.3, 0.18);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.2f, 2.0f);
            }
            case WIND -> {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 6, 0.25, 0.25, 0.25, 0.06);
            }
            case FIRE -> level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 10, 0.25, 0.3, 0.25, 0.03);
            default -> level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.25, 0.3, 0.25, 0.1);
        }
    }

    // ---------------------------------------------------------------- tessen

    /** A tessen, swung or raised in guard, swats projectiles from the front out of the air. */
    @SubscribeEvent
    public static void projectile(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide() || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity defender) || !deflects(defender, projectile)) {
            return;
        }
        Vec3 motion = projectile.getDeltaMovement();
        Vec3 aside = new Vec3(-motion.z, 0, motion.x).normalize().scale(motion.length() * DEFLECT_SPEED);
        projectile.setDeltaMovement(aside.add(0, 0.15, 0));
        projectile.hurtMarked = true;
        event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        defender.level().playSound(null, defender.getX(), defender.getY(), defender.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.9f);
        ((ServerLevel) defender.level()).sendParticles(ParticleTypes.CRIT, projectile.getX(), projectile.getY(), projectile.getZ(), 8, 0.1, 0.1, 0.1, 0.2);
    }

    /** Would {@code defender}'s tessen swat this projectile (fan in hand, swinging or guarding, facing it)? */
    public static boolean deflects(LivingEntity defender, Projectile projectile) {
        ItemStack held = defender.getMainHandItem();
        if (!(held.getItem() instanceof NinjaBladeItem blade) || blade.stats().trait() != BladeStats.Trait.DEFLECT) {
            return false;
        }
        var state = CombatStates.getIfPresent(defender);
        boolean busy = defender.swinging || (state != null && (state.isGuarding() || state.phase() == CombatPhase.WINDUP
                || state.phase() == CombatPhase.ACTIVE || state.phase() == CombatPhase.RECOVERY));
        if (!busy) {
            return false;
        }
        Vec3 motion = projectile.getDeltaMovement();
        Vec3 facing = Vec3.directionFromRotation(0, defender.getYRot());
        return motion.x * facing.x + motion.z * facing.z < 0;
    }
}

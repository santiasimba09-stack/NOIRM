package com.noirm.dojutsu;

import com.noirm.Noirm;
import com.noirm.jutsu.JutsuUser;
import com.noirm.network.NoirmNetwork;
import com.noirm.network.PowerPackets;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Byakugan, the Hyūga's eye. Hyūga are born with it; G opens it (it drains a little chakra).
 * While open:
 * <ul>
 *   <li>you see everyone nearby as chakra silhouettes, through walls (client side);</li>
 *   <li>perception +15%, and nearly 360° vision: you're warned of every attack aimed at you and
 *       can guard from any direction;</li>
 *   <li><b>Gentle Fist</b>: your taijutsu hits strike chakra points. Extra posture damage, a
 *       guard keeps out less, and each hit drains the target's chakra and closes chakra points.
 *       Close {@link #SEAL_POINTS} and their jutsu are sealed for a while.</li>
 * </ul>
 * Hyūga learn Eight Trigrams Palm Rotation and 64 Palms (see {@code HyugaTechniques}).
 */
public final class Byakugan {
    public static final ResourceLocation ROTATION = Noirm.id("byakugan/rotation");
    public static final ResourceLocation SIXTY_FOUR_PALMS = Noirm.id("byakugan/sixty_four_palms");
    public static final List<ResourceLocation> JUTSU = List.of(ROTATION, SIXTY_FOUR_PALMS);

    /** Chakra per tick while open: 0.6/s (regen is 1/s). */
    public static final float DRAIN_PER_TICK = 0.03f;
    public static final float PERCEPTION_MULTIPLIER = 1.15f;
    /** How far the client draws chakra silhouettes. */
    public static final double SIGHT_RANGE = 40;
    public static final float GENTLE_FIST_POSTURE = 1.4f;
    /** Gentle Fist hurts through a guard: 50% instead of the usual 25%. */
    public static final float GENTLE_FIST_GUARD = 0.5f;
    public static final float GENTLE_FIST_CHAKRA = 4f;
    /** Chakra points closed per light hit (heavies close twice as many). */
    public static final int POINTS_PER_HIT = 2;
    /** Closed points needed to seal someone's jutsu, and for how long (ticks). */
    public static final int SEAL_POINTS = 24;
    public static final int SEAL_TICKS = 120;
    /** Closed points reopen if no Gentle Fist lands for this long. */
    public static final int POINTS_RESET_TICKS = 100;

    private static final DustParticleOptions CHAKRA = new DustParticleOptions(new Vector3f(0.55f, 0.75f, 1f), 0.9f);
    private static final Map<LivingEntity, Points> POINTS = new WeakHashMap<>();

    private Byakugan() {}

    /** Is the Byakugan open (players and NPCs)? */
    public static boolean isActive(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> d.eyes().isByakuganActive()).orElse(false);
        }
        return entity instanceof JutsuUser user && user.byakuganActive();
    }

    public static float perceptionMultiplier(LivingEntity entity) {
        return isActive(entity) ? PERCEPTION_MULTIPLIER : 1f;
    }

    static void onOpened(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1f, 1.6f);
        player.displayClientMessage(Component.translatable("message.noirm.byakugan.open"), true);
    }

    /** Every server tick for players: the open eye drains chakra. */
    public static void tick(ServerPlayer player, ShinobiData data) {
        if (!data.eyes().isByakuganActive() || data.awakening().isLockedIn() || player.getAbilities().instabuild) {
            return;
        }
        if (!data.tryConsumeChakra(DRAIN_PER_TICK)) {
            Sharingan.setActive(player, data, false);
            player.displayClientMessage(Component.translatable("message.noirm.byakugan.exhausted"), true);
        }
    }

    /** Someone winds up an attack at {@code target}: with the Byakugan open, they see it coming. */
    public static void onAttackWindup(LivingEntity attacker, LivingEntity target, int windupTicks) {
        if (attacker != target && target instanceof ServerPlayer player && isActive(player) && Sharingan.activeStage(player) == 0) {
            NoirmNetwork.sendToPlayer(player, new PowerPackets.Precognition(attacker.getId(), windupTicks));
        }
    }

    // ---------------------------------------------------------------- Gentle Fist

    /** A Gentle Fist hit landed: drain chakra and close chakra points (heavy hits close more). */
    public static void gentleFist(LivingEntity attacker, LivingEntity target, boolean heavy) {
        closePoints(attacker, target, heavy ? POINTS_PER_HIT * 2 : POINTS_PER_HIT, heavy ? GENTLE_FIST_CHAKRA * 2 : GENTLE_FIST_CHAKRA);
    }

    /** Closes {@code points} chakra points on {@code target} and drains {@code chakra} (players). */
    public static void closePoints(LivingEntity attacker, LivingEntity target, int points, float chakra) {
        if (target instanceof Player player) {
            ShinobiCapability.get(player).ifPresent(d -> d.setChakra(d.getChakra() - chakra));
        }
        long now = target.level().getGameTime();
        Points state = POINTS.computeIfAbsent(target, t -> new Points());
        if (now - state.lastHit > POINTS_RESET_TICKS) {
            state.closed = 0;
        }
        state.lastHit = now;
        if (now < state.sealedUntil) {
            return;
        }
        state.closed += points;
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(CHAKRA, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    4 + points, 0.25, 0.4, 0.25, 0.01);
        }
        if (state.closed >= SEAL_POINTS) {
            state.closed = 0;
            state.sealedUntil = now + SEAL_TICKS;
            target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1f, 1.4f);
            if (target instanceof Player player) {
                player.displayClientMessage(Component.translatable("message.noirm.byakugan.sealed_you"), true);
            }
            if (attacker instanceof Player player) {
                player.displayClientMessage(Component.translatable("message.noirm.byakugan.sealed", target.getDisplayName()), true);
            }
        }
    }

    /** How many chakra points are closed right now. */
    public static int closedPoints(LivingEntity entity) {
        Points state = POINTS.get(entity);
        return state == null || entity.level().getGameTime() - state.lastHit > POINTS_RESET_TICKS ? 0 : state.closed;
    }

    /** True while the target's chakra points are sealed: they can't cast jutsu. */
    public static boolean isSealed(LivingEntity entity) {
        Points state = POINTS.get(entity);
        return state != null && entity.level().getGameTime() < state.sealedUntil;
    }

    private static final class Points {
        int closed;
        long lastHit = Long.MIN_VALUE / 2;
        long sealedUntil;
    }
}

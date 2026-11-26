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
 *   <li>you see everyone nearby as chakra silhouettes, through walls, with their chakra points
 *       (tenketsu) lit up, and a 360° radar of chakra signatures (client side);</li>
 *   <li>no blind spot: attacks from behind show up like attacks from the front, and you can
 *       guard in any direction. It does <em>not</em> predict or copy: that's the Sharingan;</li>
 *   <li><b>Gentle Fist</b>: your taijutsu hits strike chakra points. Extra posture damage, a
 *       guard keeps out less, and each hit drains the target's chakra and closes chakra points.
 *       Close {@link #SEAL_POINTS} and their jutsu are sealed for a while.</li>
 * </ul>
 * Hyūga learn Eight Trigrams Palm Rotation and 64 Palms (see {@code HyugaTechniques}), then 128 Palms, the Air Palm
 * and the Twin Lion Fists.
 *
 * <p><b>Wave "clones" (2026-11-26): a better eye, a broken fist.</b>
 * <ul>
 *   <li><b>Range</b> grows with chakra control (CTL): {@link #SIGHT_RANGE} at the start, up to {@link #MAX_SIGHT}.</li>
 *   <li><b>The blind spot</b>: a narrow cone right behind the neck ({@link #BLIND_SPOT_DEGREES}) that the eye doesn't
 *       cover. Attacks from there get no warning and slip past an any-direction guard. It narrows as CTL grows and is
 *       gone at {@link #BLIND_SPOT_CLOSED_AT}.</li>
 *   <li><b>X-ray</b> (client): ores and containers through walls, close by ({@code ByakuganVision}).</li>
 *   <li><b>Chakra-point sight</b>: the attacker sees which chakra points are closed (dark red) and when someone is
 *       sealed (all red), via {@code PowerPackets.Tenketsu}.</li>
 *   <li><b>Gentle Fist</b> is overpowered, as in the show: a guard barely helps ({@link #GENTLE_FIST_GUARD}), every
 *       hit also deals <b>internal damage</b> that ignores armour ({@link #INTERNAL_DAMAGE}), closes more points the
 *       better your CTL (tenketsu targeting), and closed points <b>shut off chakra</b>: regen falls with every point
 *       closed, and once sealed the target's chakra is emptied, its mode ends, its eyes close and it can't cast.</li>
 * </ul>
 */
public final class Byakugan {
    public static final ResourceLocation ROTATION = Noirm.id("byakugan/rotation");
    public static final ResourceLocation SIXTY_FOUR_PALMS = Noirm.id("byakugan/sixty_four_palms");
    public static final ResourceLocation HUNDRED_TWENTY_EIGHT_PALMS = Noirm.id("byakugan/hundred_twenty_eight_palms");
    public static final ResourceLocation AIR_PALM = Noirm.id("byakugan/air_palm");
    public static final ResourceLocation TWIN_LION_FISTS = Noirm.id("byakugan/twin_lion_fists");
    /** What a Hyūga starts with (the rest unlock through mastery, see their JSON). */
    public static final List<ResourceLocation> JUTSU = List.of(ROTATION, SIXTY_FOUR_PALMS);

    /** Chakra per tick while open. Wave "Hyūga": 0 (was 0.03 = 0.6/s): the eye is the clan's birthright, not a jutsu. */
    public static final float DRAIN_PER_TICK = 0f;
    /** How far the eye sees at CTL 10 (chakra silhouettes, the radar). */
    public static final double SIGHT_RANGE = 64; // wave "Hyūga": was 40
    /** Blocks of sight per CTL point above 10, up to {@link #MAX_SIGHT}. */
    public static final double SIGHT_PER_CONTROL = 3; // was 1.5
    public static final double MAX_SIGHT = 256; // was 120
    /** X-ray (ores, containers) radius at CTL 10, per CTL point above 10, and at most. */
    public static final double XRAY_RANGE = 12; // was 8
    public static final double XRAY_PER_CONTROL = 0.3; // was 0.2
    public static final double MAX_XRAY = 32; // was 20
    /** The blind spot: half-angle of the cone right behind you that the eye misses, at CTL 10. */
    public static final float BLIND_SPOT_DEGREES = 20f;
    /** CTL at which the blind spot is closed for good (it narrows evenly until then). */
    public static final float BLIND_SPOT_CLOSED_AT = 45f; // was 60 (the blind spot itself stays: a canon cost)
    public static final float GENTLE_FIST_POSTURE = 2.2f; // was 1.8
    /** Gentle Fist hurts through a guard: all of it gets through (was 90%; the usual is 25%). It strikes inside the body. */
    public static final float GENTLE_FIST_GUARD = 1.0f;
    /** Every Gentle Fist hit also deals this share of its damage again as internal damage that ignores armour. */
    public static final float INTERNAL_DAMAGE = 0.8f; // was 0.6
    public static final float GENTLE_FIST_CHAKRA = 12f; // was 8
    /** Chakra points closed per light hit (heavies close twice as many)… */
    public static final int POINTS_PER_HIT = 4; // was 3
    /** …plus one more per this many CTL points above 10 (tenketsu targeting: a trained eye strikes true). */
    public static final float CONTROL_PER_EXTRA_POINT = 10f; // was 15
    /** Closed points needed to seal someone's chakra, and for how long (ticks). */
    public static final int SEAL_POINTS = 24;
    public static final int SEAL_TICKS = 400; // was 300 (15 s → 20 s)
    /** Closed points reopen if no Gentle Fist lands for this long. */
    public static final int POINTS_RESET_TICKS = 400; // was 300
    /** Twin Lion Fists: points closed and internal damage are multiplied by this while they burn. */
    public static final float LION_FISTS_MULTIPLIER = 3f; // was 2.5

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

    static void onOpened(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1f, 1.6f);
        player.displayClientMessage(Component.translatable("message.noirm.byakugan.open"), true);
    }

    /** Every server tick for players: the open eye drains chakra. */
    public static void tick(ServerPlayer player, ShinobiData data) {
        if (DRAIN_PER_TICK <= 0f || !data.eyes().isByakuganActive() || data.awakening().isLockedIn() || player.getAbilities().instabuild) {
            return;
        }
        if (!data.tryConsumeChakra(DRAIN_PER_TICK)) {
            Sharingan.setActive(player, data, false);
            player.displayClientMessage(Component.translatable("message.noirm.byakugan.exhausted"), true);
        }
    }

    /**
     * Someone winds up an attack at {@code target}. The Byakugan doesn't predict: it just has no
     * blind spot, so a wind-up <em>behind</em> you shows up like one in front would.
     */
    public static void onAttackWindup(LivingEntity attacker, LivingEntity target, int windupTicks) {
        if (attacker != target && target instanceof ServerPlayer player && isActive(player) && Sharingan.activeStage(player) == 0
                && ThreatSense.isBehind(player, attacker) && !inBlindSpot(player, attacker)) {
            NoirmNetwork.sendToPlayer(player, new PowerPackets.Precognition(attacker.getId(), windupTicks));
        }
    }

    // ---------------------------------------------------------------- Gentle Fist

    /** A Gentle Fist hit landed: drain chakra and close chakra points (heavy hits close more). */
    public static void gentleFist(LivingEntity attacker, LivingEntity target, boolean heavy) {
        gentleFist(attacker, target, heavy, 0f);
    }

    /**
     * A Gentle Fist hit of {@code damage} landed: internal damage that ignores armour, then chakra drained and points
     * closed (more with CTL and while the Twin Lion Fists burn).
     */
    public static void gentleFist(LivingEntity attacker, LivingEntity target, boolean heavy, float damage) {
        float art = com.noirm.traits.Perks.mult(attacker, com.noirm.traits.Perk.GENTLE_FIST); // the Hyūga's own art
        float lion = com.noirm.jutsu.HyugaTechniques.lionFistsActive(attacker) ? LION_FISTS_MULTIPLIER : 1f;
        if (damage > 0f) {
            internalDamage(attacker, target, damage * INTERNAL_DAMAGE * lion);
        }
        int perHit = POINTS_PER_HIT + extraPoints(controlOf(attacker));
        int points = Math.round((heavy ? perHit * 2 : perHit) * art * lion);
        closePoints(attacker, target, points, (heavy ? GENTLE_FIST_CHAKRA * 2 : GENTLE_FIST_CHAKRA) * art * lion);
    }

    /** Extra points per hit from chakra control (tenketsu targeting). */
    public static int extraPoints(float control) {
        return Math.max(0, (int) ((control - ShinobiData.DEFAULT_STAT) / CONTROL_PER_EXTRA_POINT));
    }

    /** Damage straight to the organs: ignores armour and guards (a magic-type hit from the attacker). */
    public static void internalDamage(LivingEntity attacker, LivingEntity target, float amount) {
        if (amount <= 0f || !target.isAlive() || target.level().isClientSide()) {
            return;
        }
        target.invulnerableTime = 0;
        // No guard or parry stops it (the palm already landed), and no vanilla shove: 64 Palms holds its target.
        com.noirm.combat.CombatSystem.unblockable(
                () -> target.hurt(target.damageSources().indirectMagic(attacker, attacker), amount));
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(CHAKRA, target.getX(), target.getY() + target.getBbHeight() * 0.55, target.getZ(), 6, 0.15, 0.2, 0.15, 0.08);
        }
    }

    // ---------------------------------------------------------------- the eye: range, blind spot

    /** Chakra control (CTL) of {@code entity}: players' stat; NPCs count as a trained Hyūga by their taijutsu. */
    public static float controlOf(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(ShinobiData::getControl).orElse(ShinobiData.DEFAULT_STAT);
        }
        if (entity instanceof com.noirm.entity.SparringNinja ninja) {
            return ninja.combatTaijutsu();
        }
        return ShinobiData.DEFAULT_STAT;
    }

    /** How far the eye sees at this CTL. */
    public static double sightRange(float control) {
        return Math.min(MAX_SIGHT, SIGHT_RANGE + Math.max(0f, control - ShinobiData.DEFAULT_STAT) * SIGHT_PER_CONTROL);
    }

    /** The x-ray radius (ores, containers) at this CTL. */
    public static double xrayRange(float control) {
        return Math.min(MAX_XRAY, XRAY_RANGE + Math.max(0f, control - ShinobiData.DEFAULT_STAT) * XRAY_PER_CONTROL);
    }

    /** Half-angle of the blind spot behind the neck at this CTL (0 = closed). */
    public static float blindSpotDegrees(float control) {
        float span = BLIND_SPOT_CLOSED_AT - ShinobiData.DEFAULT_STAT;
        float left = 1f - Math.max(0f, control - ShinobiData.DEFAULT_STAT) / span;
        return Math.max(0f, BLIND_SPOT_DEGREES * left);
    }

    /** True if {@code threat} stands in {@code viewer}'s blind spot: right behind the neck. */
    public static boolean inBlindSpot(LivingEntity viewer, net.minecraft.world.entity.Entity threat) {
        float degrees = blindSpotDegrees(controlOf(viewer));
        if (degrees <= 0f) {
            return false;
        }
        net.minecraft.world.phys.Vec3 offset = threat.position().subtract(viewer.position());
        net.minecraft.world.phys.Vec3 flat = new net.minecraft.world.phys.Vec3(offset.x, 0, offset.z);
        if (flat.lengthSqr() < 0.01) {
            return false;
        }
        net.minecraft.world.phys.Vec3 back = net.minecraft.world.phys.Vec3.directionFromRotation(0, viewer.getYHeadRot()).scale(-1);
        return back.dot(flat.normalize()) > Math.cos(Math.toRadians(degrees));
    }

    /** The open eye sees {@code threat} (it's open, and they're not in the blind spot). */
    public static boolean sees(LivingEntity viewer, net.minecraft.world.entity.Entity threat) {
        return isActive(viewer) && !inBlindSpot(viewer, threat);
    }

    /** How freely chakra flows: 1 with every point open, falling with each one closed, 0 while sealed. */
    public static float chakraFlow(LivingEntity entity) {
        if (isSealed(entity)) {
            return 0f;
        }
        int closed = closedPoints(entity);
        return closed <= 0 ? 1f : Math.max(0f, 1f - closed / (float) SEAL_POINTS);
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
        try {
            closeAndSync(attacker, target, points, state, now);
        } finally {
            sync(target, state);
        }
    }

    private static void closeAndSync(LivingEntity attacker, LivingEntity target, int points, Points state, long now) {
        state.closed += points;
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(CHAKRA, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    4 + points, 0.25, 0.4, 0.25, 0.01);
        }
        if (state.closed >= SEAL_POINTS) {
            state.closed = 0;
            state.sealedUntil = now + SEAL_TICKS;
            shutOff(target);
            target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1f, 1.4f);
            if (target instanceof Player player) {
                player.displayClientMessage(Component.translatable("message.noirm.byakugan.sealed_you"), true);
            }
            if (attacker instanceof Player player) {
                player.displayClientMessage(Component.translatable("message.noirm.byakugan.sealed", target.getDisplayName()), true);
            }
        }
    }

    /** Sealed: chakra stops. Their chakra empties, their mode ends and their eyes close. */
    private static void shutOff(LivingEntity target) {
        if (target instanceof Player player) {
            ShinobiCapability.get(player).ifPresent(d -> {
                d.setChakra(0f);
                if (d.eyes().isActive() && player instanceof ServerPlayer server) {
                    Sharingan.setActive(server, d, false);
                }
            });
        }
        com.noirm.jutsu.ModeSystem.end(target);
    }

    /** Tells the attacker (and anyone watching) how many points are closed, for the eye's chakra-point sight. */
    private static void sync(LivingEntity target, Points state) {
        if (target.level().isClientSide()) {
            return;
        }
        long now = target.level().getGameTime();
        int sealed = (int) Math.max(0, state.sealedUntil - now);
        NoirmNetwork.sendToTrackingAndSelf(target, new PowerPackets.Tenketsu(target.getId(), state.closed, sealed));
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

package com.noirm.movement;

import com.noirm.Noirm;
import com.noirm.chakra.WaterWalking;
import com.noirm.network.MovementPackets;
import com.noirm.network.NoirmNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * How moving feels (blueprint section 3), kept light so combat stays smooth (owner):
 * <ul>
 *   <li><b>Grounded inertia</b>: let go of the keys and you slide to a stop over ~200 ms instead of
 *       stopping dead; turn right around and your old momentum carries you for a couple of ticks
 *       (a pivot, not ice). Starting to move is as quick as ever. Player movement is predicted by the
 *       client, so this part lives in {@code client/MovementFeelClient}; its numbers are here.</li>
 *   <li><b>Heavier, snappier gravity</b> for players, with the jump made stronger to match, so you
 *       jump exactly as high as before but spend less time floating.</li>
 *   <li><b>Landing recovery</b>: after a real fall, 1 to 3 ticks of slower movement (more for a
 *       bigger fall). Dodging as you land (our roll) skips it.</li>
 * </ul>
 * All of it can be switched off with {@code /gamerule noirmMovementFeel false}.
 *
 * <p>NPC ninjas keep vanilla gravity: their roof leaps, launchers and juggles are aimed with it.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class MovementFeel {
    // ------------------------------------------------------------ grounded inertia (client side)

    /** Ticks you keep sliding after letting go of the movement keys: 4 ticks = 200 ms. */
    public static final int SLIDE_TICKS = 4;
    /**
     * Speed kept each tick while sliding. Vanilla ground keeps 0.546 a tick (stone, dirt...), which
     * stops you almost dead; 0.72 is a short, visible slide. Higher = longer slide (0.85 starts to feel like ice).
     */
    public static final float SLIDE_KEEP = 0.72f;
    /** Ticks your old momentum fights a sharp turn (a reversal). 2 ticks = 100 ms. */
    public static final int PIVOT_TICKS = 2;
    /** How sharp a turn is a pivot: the new direction against the old (cosine; -0.3 = more than ~107°). */
    public static final double PIVOT_DOT = -0.3;
    /** Below this speed (blocks a tick) there's no momentum to pivot on. Walking is ~0.22, sprinting ~0.28. */
    public static final double PIVOT_MIN_SPEED = 0.12;
    /**
     * During a pivot the new direction pushes at this fraction of its usual strength, and the old
     * momentum fades at {@link #SLIDE_KEEP} instead of the ground's usual grip.
     */
    public static final float PIVOT_INPUT_SCALE = 0.5f;
    /** A sudden speed-up bigger than this (blocks a tick) means something pushed you (dodge, lunge, knockback): no slide on top. */
    public static final double IMPULSE_TOLERANCE = 0.04;

    // ------------------------------------------------------------ gravity

    /** Players fall this much faster: vanilla gravity is 0.08 blocks/tick², ×1.2 = 0.096. */
    public static final double GRAVITY_MULTIPLIER = 1.2;
    /** Vanilla's jump speed (blocks a tick), used to work out {@link #JUMP_SCALE}. */
    private static final double VANILLA_JUMP = 0.42;
    /**
     * Every player jump is multiplied by this so it reaches the same height as before under the
     * heavier gravity (worked out from the game's own physics; ×1.08 for gravity ×1.2). A jump
     * now takes 11 ticks instead of 12: same height, snappier arc.
     */
    public static final double JUMP_SCALE = solveJumpScale(GRAVITY_MULTIPLIER);

    // ------------------------------------------------------------ landing recovery

    /** Falls shorter than this many blocks have no recovery (a normal jump falls ~1.25). */
    public static final float RECOVERY_MIN_FALL = 3f;
    /** One more tick of recovery for every this many blocks above {@link #RECOVERY_MIN_FALL}. */
    public static final float RECOVERY_BLOCKS_PER_TICK = 3f;
    /** The most recovery a landing costs: 3 ticks (150 ms), from a fall of 9 blocks or more. */
    public static final int RECOVERY_MAX_TICKS = 3;
    /** Movement keys push at this fraction of their strength while recovering (and no jumping). */
    public static final float RECOVERY_INPUT_SCALE = 0.35f;
    /** A dodge this many ticks before landing (or during the recovery) skips it: the roll. */
    public static final int DODGE_GRACE_TICKS = 10;

    // ------------------------------------------------------------ the switch

    /** {@code /gamerule noirmMovementFeel false} turns all of this off (the server tells clients). */
    public static GameRules.Key<GameRules.BooleanValue> RULE;
    /** The server's rule as the local client last heard it (client side only). */
    public static volatile boolean clientEnabled = true;

    private static final UUID GRAVITY_ID = UUID.fromString("a3d6f1e2-5b7c-4e19-8f2a-6c1d9e0b7a45");
    private static final AttributeModifier GRAVITY = new AttributeModifier(GRAVITY_ID, "noirm.heavier_gravity",
            GRAVITY_MULTIPLIER - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);

    /** Per player: the last landing and dodge. One map per side (the integrated server runs beside the client). */
    private static final Map<Player, State> SERVER_STATES = new WeakHashMap<>();
    private static final Map<Player, State> CLIENT_STATES = new WeakHashMap<>();

    private static final class State {
        int landTick = Integer.MIN_VALUE / 2;
        int recoveryTicks;
        int lastDodge = Integer.MIN_VALUE / 2;
    }

    private MovementFeel() {}

    /** Called from the mod constructor. Changing the rule tells every client. */
    public static void registerGameRules() {
        RULE = GameRules.register("noirmMovementFeel", GameRules.Category.PLAYER,
                GameRules.BooleanValue.create(true, MovementFeel::ruleChanged));
    }

    private static void ruleChanged(MinecraftServer server, GameRules.BooleanValue value) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NoirmNetwork.sendToPlayer(player, new MovementPackets.FeelState(value.get()));
        }
    }

    /** Is the movement feel on? Server: the game rule; client: what the server said. */
    public static boolean enabled(Level level) {
        if (level.isClientSide()) {
            return clientEnabled;
        }
        return RULE == null || level.getGameRules().getBoolean(RULE);
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NoirmNetwork.sendToPlayer(player, new MovementPackets.FeelState(enabled(player.level())));
        }
    }

    // ------------------------------------------------------------ gravity

    /** Should this player fall with the heavier gravity right now? Not while flying, gliding or spectating. */
    public static boolean wantsHeavyGravity(Player player) {
        return enabled(player.level()) && !player.getAbilities().flying && !player.isFallFlying() && !player.isSpectator();
    }

    /** Has the heavier-gravity modifier? */
    public static boolean hasHeavyGravity(LivingEntity entity) {
        AttributeInstance gravity = entity.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        return gravity != null && gravity.getModifier(GRAVITY_ID) != null;
    }

    /** Puts the (transient: never saved) gravity modifier on or off to match {@link #wantsHeavyGravity}. */
    public static void updateGravity(Player player) {
        AttributeInstance gravity = player.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        if (gravity == null) {
            return;
        }
        boolean want = wantsHeavyGravity(player);
        boolean has = gravity.getModifier(GRAVITY_ID) != null;
        if (want && !has) {
            gravity.addTransientModifier(GRAVITY);
        } else if (!want && has) {
            gravity.removeModifier(GRAVITY_ID);
        }
    }

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Player player && (!player.level().isClientSide() || player.isLocalPlayer())) {
            updateGravity(player);
        }
    }

    /** Runs after every other jump boost (speed stat, leaps): the whole jump scales, so heights stay the same. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void jump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player && hasHeavyGravity(player)) {
            Vec3 motion = player.getDeltaMovement();
            if (motion.y > 0) {
                player.setDeltaMovement(motion.x, motion.y * JUMP_SCALE, motion.z);
            }
        }
    }

    /**
     * The jump multiplier that gives the same peak height under gravity × {@code multiplier}, found
     * by running the game's own vertical physics (move, then speed = (speed - gravity) × 0.98).
     */
    static double solveJumpScale(double multiplier) {
        double target = peak(VANILLA_JUMP, 0.08);
        double low = 0.5, high = 2.0;
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) / 2;
            if (peak(VANILLA_JUMP * mid, 0.08 * multiplier) < target) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2;
    }

    /** The peak height of a jump at {@code speed} under {@code gravity}, as the game steps it. */
    public static double peak(double speed, double gravity) {
        double y = 0, top = 0;
        for (int i = 0; i < 200 && speed > 0; i++) {
            y += speed;
            top = Math.max(top, y);
            speed = (speed - gravity) * 0.98;
        }
        return top;
    }

    // ------------------------------------------------------------ landing recovery

    /** Recovery ticks for a fall of {@code blocks} (after the speed stat's soft landing): 0 below 3, then 1 per 3 blocks, at most 3. */
    public static int recoveryTicksFor(float blocks) {
        if (blocks < RECOVERY_MIN_FALL) {
            return 0;
        }
        return Mth.clamp(1 + (int) ((blocks - RECOVERY_MIN_FALL) / RECOVERY_BLOCKS_PER_TICK), 1, RECOVERY_MAX_TICKS);
    }

    private static State state(Player player) {
        Map<Player, State> map = player.level().isClientSide() ? CLIENT_STATES : SERVER_STATES;
        return map.computeIfAbsent(player, p -> new State());
    }

    /**
     * Runs last, so it sees the fall after the speed stat's soft landing and gear perks. Soft
     * landings (slime, the water skin) and landing out of a dodge cost nothing.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && event.getDamageMultiplier() > 0 && !WaterWalking.isOnSurface(player)) {
            landed(player, event.getDistance());
        }
    }

    /** A player landed from {@code blocks} high: starts the recovery (unless they dodged just before). Returns its length. */
    public static int landed(Player player, float blocks) {
        if (!enabled(player.level()) || player.getAbilities().flying || player.isInWater()) {
            return 0;
        }
        State state = state(player);
        int ticks = recoveryTicksFor(blocks);
        if (ticks == 0 || player.tickCount - state.lastDodge <= DODGE_GRACE_TICKS) {
            return 0; // a small drop, or they rolled out of it
        }
        state.landTick = player.tickCount;
        state.recoveryTicks = ticks;
        return ticks;
    }

    /** A dodge started: it cancels any landing recovery, and a landing soon after costs nothing. */
    public static void noteDodge(LivingEntity entity) {
        if (entity instanceof Player player) {
            State state = state(player);
            state.lastDodge = player.tickCount;
            state.recoveryTicks = 0;
        }
    }

    /** Is the player in their landing recovery this tick (the ticks after the landing tick)? */
    public static boolean recovering(Player player) {
        State state = state(player);
        int since = player.tickCount - state.landTick;
        return state.recoveryTicks > 0 && since > 0 && since <= state.recoveryTicks;
    }

    /** Recovery ticks left after this one (0 = free). */
    public static int recoveryLeft(Player player) {
        State state = state(player);
        return state.recoveryTicks <= 0 ? 0 : Math.max(0, state.landTick + state.recoveryTicks - player.tickCount);
    }
}

package com.noirm.chakra;

import com.noirm.Noirm;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.vfx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Walking on water with chakra, like every ninja in the Naruto-verse.
 *
 * <p><b>How it works:</b> the top block of any water (still, flowing, river or ocean) gets a thin
 * solid "skin" just above the visible surface, but only for someone who can water-walk right
 * now. A mixin ({@code mixin/BlockStateBaseMixin}) asks {@link #surfaceShape} every time the
 * game checks what an entity collides with. Players move on their own computer, so the rule must
 * give the same answer there: it only reads things the client knows (its synced chakra, sneaking,
 * flying, riding).
 *
 * <p><b>Rules:</b> you need at least {@link #MIN_CHAKRA} chakra. Standing, walking and running on
 * water drain chakra (the server does that); run dry and you sink. Hold sneak to sink or dive.
 * Under the surface you swim as usual; hold jump near the surface (with
 * {@link #RESURFACE_MIN_CHAKRA}) to spring back up onto it. A fall onto water is cushioned (no
 * damage, like falling into water) and costs a little chakra. Bubble columns stay as they are.
 * Lava walking exists but is off ({@link #LAVA_WALKING}).
 *
 * <p>NPC ninjas ({@link WaterWalker}) follow the same surface rule without chakra.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class WaterWalking {
    /** Below this much chakra the water stops holding you. */
    public static final float MIN_CHAKRA = 2f;
    /** Chakra needed to spring back up onto the surface from the water. */
    public static final float RESURFACE_MIN_CHAKRA = 10f;
    /** Chakra per tick while standing on water: 1/s, which cancels the base regen. */
    public static final float DRAIN_STANDING = 0.05f;
    /** Chakra per tick while walking on water: 1.6/s. */
    public static final float DRAIN_WALKING = 0.08f;
    /** Chakra per tick while running on water: 2.4/s. */
    public static final float DRAIN_RUNNING = 0.12f;
    /** Landing on water from a fall: no damage, but this much chakra per block fallen past 3. */
    public static final float LANDING_CHAKRA_PER_BLOCK = 1f;
    /** Horizontal blocks per tick that count as moving / running (for the drain and the effects). */
    public static final double MOVING_SPEED = 0.03;
    public static final double RUNNING_SPEED = 0.2;
    /** Upward speed of the spring back onto the surface, and how deep you can be to do it. */
    public static final double RESURFACE_SPEED = 0.6;
    public static final double RESURFACE_MAX_DEPTH = 1.6;
    /** Off by default: lava walking for those with a lot of chakra (and a hefty drain). */
    public static final boolean LAVA_WALKING = false;
    public static final float LAVA_MIN_CHAKRA = 60f;
    public static final float LAVA_DRAIN_MULTIPLIER = 4f;

    /** How much NPC ninjas avoid paths over water (vanilla: 8, i.e. "go around if at all possible"). */
    public static final float NPC_WATER_PATH_MALUS = 1f;

    /** The skin's height steps: 1/32 of a block. */
    private static final int STEPS = 32;
    private static final VoxelShape[] SHAPES = new VoxelShape[STEPS + 1];

    /** The local player's chakra, from the last stat sync (client side only). */
    public static volatile float clientChakra = 0f;

    /** Per-walker bookkeeping for the drain and the effects (server side). */
    private static final class Walk {
        double lastX;
        double lastZ;
        int ticksOn;
        boolean warned;
    }

    private static final Map<LivingEntity, Walk> WALKS = new WeakHashMap<>();

    private WaterWalking() {}

    // ---------------------------------------------------------------- the rule

    /** Whether {@code entity} can stand on {@code fluid} right now (same answer on client and server). */
    public static boolean canWalkOn(LivingEntity entity, FluidState fluid) {
        boolean lava = fluid.is(FluidTags.LAVA);
        if (!fluid.is(FluidTags.WATER) && !(LAVA_WALKING && lava)) {
            return false;
        }
        if (entity instanceof Player player) {
            return playerCanWalk(player, lava);
        }
        return !lava && entity instanceof WaterWalker walker && walker.canWaterWalk();
    }

    /** Not sneaking (that's how you dive), not flying, riding or gliding, and enough chakra. */
    public static boolean playerCanWalk(Player player, boolean lava) {
        if (player.isSpectator() || player.getAbilities().flying || player.isShiftKeyDown() || player.isPassenger()
                || player.isFallFlying()) {
            return false;
        }
        return chakra(player) >= (lava ? LAVA_MIN_CHAKRA : MIN_CHAKRA);
    }

    /** The client reads its synced copy; the server its own data. */
    public static float chakra(Player player) {
        if (player.level().isClientSide()) {
            return clientChakra;
        }
        return ShinobiCapability.get(player).map(ShinobiData::getChakra).orElse(0f);
    }

    /**
     * Called by the mixin for every collision check against a block: the water skin, or null to
     * leave the block's own shape alone. Cheap for the common case (no fluid in the block).
     */
    public static VoxelShape surfaceShape(BlockBehaviour.BlockStateBase state, BlockGetter level, BlockPos pos, CollisionContext context) {
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty() || !(context instanceof EntityCollisionContext entityContext)
                || !(entityContext.getEntity() instanceof LivingEntity entity)) {
            return null;
        }
        if (state.is(Blocks.BUBBLE_COLUMN) || level.getFluidState(pos.above()).getType().isSame(fluid.getType())) {
            return null; // only the top block of the water, and bubble columns keep pushing and pulling
        }
        if (!canWalkOn(entity, fluid)) {
            return null;
        }
        VoxelShape shape = skin(fluid.getOwnHeight());
        // Only from above: someone already under the surface swims instead of getting stuck.
        return context.isAbove(shape, pos, false) ? shape : null;
    }

    /** A thin solid layer whose top is just above the water's visible surface. */
    static VoxelShape skin(float fluidHeight) {
        int step = Mth.clamp(Mth.ceil(fluidHeight * STEPS + 0.5f), 1, STEPS);
        VoxelShape shape = SHAPES[step];
        if (shape == null) {
            shape = Shapes.box(0, 0, 0, 1, step / (double) STEPS, 1);
            SHAPES[step] = shape;
        }
        return shape;
    }

    /** Standing on the water skin (not in the water, on the ground, the block underfoot is water). */
    public static boolean isOnSurface(LivingEntity entity) {
        if (!entity.onGround() || entity.isInWater() || entity.isInLava()) {
            return false;
        }
        return surfaceUnder(entity.level(), entity.position()) != null;
    }

    /** The water (or lava) surface block right under {@code feet}, or null. */
    private static FluidState surfaceUnder(Level level, Vec3 feet) {
        BlockPos below = BlockPos.containing(feet.x, feet.y - 0.01, feet.z);
        BlockState state = level.getBlockState(below);
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty() || !(fluid.is(FluidTags.WATER) || (LAVA_WALKING && fluid.is(FluidTags.LAVA)))) {
            return null;
        }
        // A waterlogged slab or stairs hold you up on their own: that's not water walking.
        return state.getCollisionShape(level, below).isEmpty() ? fluid : null;
    }

    // ---------------------------------------------------------------- the server: drain and feel

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            tick(player);
        }
    }

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof WaterWalker walker)) {
            return;
        }
        if (entity instanceof Mob mob && mob.getPathfindingMalus(BlockPathTypes.WATER) != NPC_WATER_PATH_MALUS) {
            mob.setPathfindingMalus(BlockPathTypes.WATER, NPC_WATER_PATH_MALUS); // happy to cross water
        }
        resurfaceNpc(entity, walker);
        tick(entity);
    }

    /** Every server tick for a walker: drains a player's chakra and makes ripples while on water. */
    public static void tick(LivingEntity entity) {
        Walk walk = WALKS.computeIfAbsent(entity, e -> {
            Walk fresh = new Walk();
            fresh.lastX = e.getX();
            fresh.lastZ = e.getZ();
            return fresh;
        });
        double moved = Math.sqrt(Mth.square(entity.getX() - walk.lastX) + Mth.square(entity.getZ() - walk.lastZ));
        walk.lastX = entity.getX();
        walk.lastZ = entity.getZ();
        if (!isOnSurface(entity)) {
            walk.ticksOn = 0;
            return;
        }
        walk.ticksOn++;
        boolean running = moved > RUNNING_SPEED || (entity.isSprinting() && moved > MOVING_SPEED);
        boolean moving = moved > MOVING_SPEED;
        if (entity instanceof Player player) {
            drain(player, walk, running, moving);
        }
        effects(entity, walk, running, moving);
    }

    private static void drain(Player player, Walk walk, boolean running, boolean moving) {
        if (player.getAbilities().instabuild) {
            return;
        }
        ShinobiCapability.get(player).ifPresent(data -> {
            boolean lava = player.level().getFluidState(BlockPos.containing(player.getX(), player.getY() - 0.01, player.getZ())).is(FluidTags.LAVA);
            float drain = (running ? DRAIN_RUNNING : moving ? DRAIN_WALKING : DRAIN_STANDING) * (lava ? LAVA_DRAIN_MULTIPLIER : 1f);
            if (!data.tryConsumeChakra(drain)) {
                data.setChakra(0f);
            }
            if (data.getChakra() < MIN_CHAKRA && !walk.warned) {
                walk.warned = true;
                player.displayClientMessage(Component.translatable("message.noirm.water_walk.sinking"), true);
            } else if (data.getChakra() >= RESURFACE_MIN_CHAKRA) {
                walk.warned = false;
            }
        });
    }

    /** Ripples under the feet, splashes while moving, a soft sound now and then. */
    private static void effects(LivingEntity entity, Walk walk, boolean running, boolean moving) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 feet = entity.position().add(0, 0.02, 0);
        int rippleEvery = running ? 4 : moving ? 7 : 30;
        if (walk.ticksOn % rippleEvery == 1) {
            Vfx.burst(level, Vfx.ring(0xBFE6FF, running ? 1.3f : moving ? 0.9f : 0.7f, running ? 10 : 16), feet, 1, 0, 0);
        }
        if (moving && walk.ticksOn % 2 == 0) {
            level.sendParticles(ParticleTypes.SPLASH, feet.x, feet.y + 0.05, feet.z, running ? 4 : 2, 0.2, 0.02, 0.2, 0.1);
        }
        if (moving && walk.ticksOn % (running ? 4 : 8) == 0) {
            level.sendParticles(ParticleTypes.FISHING, feet.x, feet.y, feet.z, 1, 0.15, 0, 0.15, 0.01);
        }
        int soundEvery = running ? 9 : 16;
        if (moving && walk.ticksOn % soundEvery == 0) {
            level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.PLAYER_SWIM, SoundSource.NEUTRAL,
                    running ? 0.14f : 0.08f, 1.5f + entity.getRandom().nextFloat() * 0.3f);
        }
    }

    /** An NPC that fell in (or was knocked in) springs back up onto the surface. */
    private static void resurfaceNpc(LivingEntity entity, WaterWalker walker) {
        if (!entity.isInWater() || !walker.canWaterWalk()) {
            return;
        }
        double depth = entity.getFluidTypeHeight(ForgeMod.WATER_TYPE.get());
        Vec3 motion = entity.getDeltaMovement();
        if (depth > 0 && depth < RESURFACE_MAX_DEPTH && motion.y < RESURFACE_SPEED * 0.5) {
            entity.setDeltaMovement(motion.x, RESURFACE_SPEED, motion.z);
        }
    }

    /** Whether a player in the water may spring back onto it (the client's jump key does it). */
    public static boolean canResurface(Player player) {
        return playerCanWalk(player, false) && chakra(player) >= RESURFACE_MIN_CHAKRA && player.isInWater()
                && player.getFluidTypeHeight(ForgeMod.WATER_TYPE.get()) < RESURFACE_MAX_DEPTH;
    }

    // ---------------------------------------------------------------- landing

    /**
     * Landing on the water skin: water always broke falls, and a water-walker keeps that, paying
     * a little chakra to land softly (on its feet, on the surface) instead of plunging in.
     */
    @SubscribeEvent
    public static void fall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || entity.isInWater()) {
            return;
        }
        FluidState fluid = surfaceUnder(entity.level(), entity.position());
        if (fluid == null || !canWalkOn(entity, fluid)) {
            return;
        }
        float distance = event.getDistance();
        event.setDamageMultiplier(0f);
        if (entity instanceof Player player && !player.getAbilities().instabuild) {
            ShinobiCapability.get(player).ifPresent(data ->
                    data.setChakra(data.getChakra() - Math.max(0f, distance - 3f) * LANDING_CHAKRA_PER_BLOCK));
        }
        if (distance > 2f && entity.level() instanceof ServerLevel level) {
            Vec3 feet = entity.position().add(0, 0.02, 0);
            Vfx.burst(level, Vfx.ring(0xBFE6FF, Math.min(3f, 1f + distance * 0.12f), 14), feet, 1, 0, 0);
            level.sendParticles(ParticleTypes.SPLASH, feet.x, feet.y + 0.1, feet.z, 12 + Math.round(distance), 0.4, 0.05, 0.4, 0.2);
            level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL,
                    Math.min(0.6f, 0.15f + distance * 0.03f), 1.3f);
        }
    }
}

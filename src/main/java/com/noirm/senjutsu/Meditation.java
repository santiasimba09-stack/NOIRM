package com.noirm.senjutsu;

import com.noirm.Noirm;
import com.noirm.combat.CombatStates;
import com.noirm.combat.CombatSystem;
import com.noirm.network.CombatClientPackets;
import com.noirm.network.NoirmNetwork;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.training.TrainingSystem;
import com.noirm.vfx.Vfx;
import com.noirm.vfx.VfxKind;
import com.noirm.vfx.VfxOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Meditation: sit still (key N or {@code /noirm meditate}) to recover chakra and stamina fast,
 * train Reserves, and gather <b>nature energy</b>, the resource Sage Mode will run on.
 *
 * <p>You can start on the ground, out of water, not riding, and not in a fight (no damage taken
 * or dealt in the last {@link #FIGHT_COOLDOWN_TICKS}). Meditation ends when you move, jump,
 * attack, use something, take damage, enter combat mode, cast a jutsu or press N again.
 *
 * <p>Nature energy builds while meditating (faster among plants, water and trees, and at night)
 * and slowly fades otherwise. Its cap and gain rates are constants here so the coming chakra
 * control stat (CTL, see docs/BLUEPRINT.md §5) can gate them: Sage Mode hooks in at
 * {@link #SAGE_MODE_THRESHOLD}.
 */
public final class Meditation {
    // ---------------------------------------------------------------- tuning
    /** Chakra recovers this many times faster while meditating. */
    public static final float CHAKRA_REGEN_MULTIPLIER = 3f;
    /** Stamina too. */
    public static final float STAMINA_REGEN_MULTIPLIER = 3f;
    /** Reserves XP granted every second of meditation (45 per minute; a solo drill, so only up to the drill cap). */
    public static final float RESERVES_XP_PER_SECOND = 0.75f;
    /** Nature energy is full at this value. (CTL will later lower or raise the safe amount.) */
    public static final float NATURE_MAX = 100f;
    /** Hook for Sage Mode (not built yet): the energy needed to enter it. */
    public static final float SAGE_MODE_THRESHOLD = NATURE_MAX;
    /** Base gain while meditating: +1 every 3 seconds. */
    public static final float NATURE_GAIN_PER_TICK = 1f / 60f;
    /** Fading while not meditating: -1 every 30 seconds. */
    public static final float NATURE_FADE_PER_TICK = 1f / 600f;
    /** How often (ticks) the nature around you is counted. */
    public static final int NATURE_SCAN_INTERVAL = 100;
    /** Counted area: this many blocks out sideways, {@link #NATURE_SCAN_DOWN} down and {@link #NATURE_SCAN_UP} up. */
    public static final int NATURE_SCAN_RADIUS = 3;
    public static final int NATURE_SCAN_DOWN = 2;
    public static final int NATURE_SCAN_UP = 3;
    /** This many natural blocks nearby give the full multiplier. */
    public static final int NATURE_BLOCKS_FOR_MAX = 80;
    /** Gain multiplier deep in nature (1 on bare stone, up to this in a forest or by a pond). */
    public static final float NATURE_MAX_MULTIPLIER = 3f;
    /** Extra gain at night (the one small bonus: the world is quiet). */
    public static final float NIGHT_BONUS = 1.5f;
    /** "In a fight": hurt, or hurt something, in the last 5 seconds. */
    public static final int FIGHT_COOLDOWN_TICKS = 100;
    /** Moving further than this (blocks, sideways) from where you sat down ends meditation. */
    public static final double MOVE_TOLERANCE = 0.35;
    /** Vertical tolerance (blocks). */
    public static final double MOVE_TOLERANCE_Y = 0.6;
    /** The seated animation's length (ticks, five 80-tick breaths) and how often it is re-sent (a whole number of breaths, so it loops seamlessly). */
    public static final int ANIMATION_TICKS = 400;
    public static final int ANIMATION_RESEND = 320;
    /** The sitting-down animation, played first; the loop starts when it ends. */
    public static final int SIT_TICKS = 16;
    /** The short animation that stands you back up. */
    public static final int RISE_TICKS = 12;
    /** Rising particles every this many ticks. */
    public static final int PARTICLE_INTERVAL = 6;

    public static final net.minecraft.resources.ResourceLocation ANIMATION = Noirm.id("meditate");
    public static final net.minecraft.resources.ResourceLocation SIT_ANIMATION = Noirm.id("meditate_sit");
    public static final net.minecraft.resources.ResourceLocation RISE_ANIMATION = Noirm.id("meditate_rise");

    /** Why meditation can't start. */
    public enum Refusal { NONE, AIR, WATER, RIDING, FIGHTING, DEAD }

    private Meditation() {}

    // ---------------------------------------------------------------- API (crew mates, HUD, commands)

    /** Is this player meditating right now? */
    public static boolean isMeditating(Player player) {
        return ShinobiCapability.get(player).map(d -> d.senjutsu().isMeditating()).orElse(false);
    }

    /** This player's nature energy, 0..{@link #NATURE_MAX}. */
    public static float natureEnergy(Player player) {
        return ShinobiCapability.get(player).map(d -> d.senjutsu().natureEnergy()).orElse(0f);
    }

    // ---------------------------------------------------------------- starting and stopping

    public static Refusal canStart(Player player) {
        if (!player.isAlive()) {
            return Refusal.DEAD;
        }
        if (player.isPassenger()) {
            return Refusal.RIDING;
        }
        if (player.isInWater() || player.isInLava() || player.isUnderWater()) {
            return Refusal.WATER;
        }
        if (!player.onGround()) {
            return Refusal.AIR;
        }
        return inFight(player) ? Refusal.FIGHTING : Refusal.NONE;
    }

    /** Hurt, or hurt something, in the last {@link #FIGHT_COOLDOWN_TICKS}. */
    public static boolean inFight(Player player) {
        long hurt = ShinobiCapability.get(player).map(d -> d.senjutsu().lastHurtTime()).orElse(Long.MIN_VALUE / 2);
        if (player.level().getGameTime() - hurt < FIGHT_COOLDOWN_TICKS) {
            return true;
        }
        return player.getLastHurtMob() != null && player.tickCount - player.getLastHurtMobTimestamp() < FIGHT_COOLDOWN_TICKS;
    }

    /** Starts or stops meditating (key N, {@code /noirm meditate}). Returns true if meditating afterwards. */
    public static boolean toggle(Player player) {
        if (isMeditating(player)) {
            stop(player);
            return false;
        }
        return start(player);
    }

    /** Sits down to meditate if allowed; tells the player why not otherwise. */
    public static boolean start(Player player) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null || player.level().isClientSide()) {
            return false;
        }
        if (data.senjutsu().isMeditating()) {
            return true;
        }
        Refusal refusal = canStart(player);
        if (refusal != Refusal.NONE) {
            notify(player, Component.translatable("message.noirm.meditate.refused." + refusal.name().toLowerCase(java.util.Locale.ROOT))
                    .withStyle(ChatFormatting.GRAY));
            return false;
        }
        CombatSystem.setCombatMode(player, false); // hands down: you can't meditate in a guard
        player.setSprinting(false);
        data.senjutsu().begin(player.getX(), player.getY(), player.getZ());
        data.markPowersDirty();
        playAnimation(player, SIT_ANIMATION, SIT_TICKS);
        Level level = player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.25f, 1.6f);
        Vfx.burst(level, Vfx.ring(0x9FE8A0, 1.4f, 16), player.position().add(0, 0.05, 0), 1, 0, 0);
        notify(player, Component.translatable("message.noirm.meditate.start").withStyle(ChatFormatting.GREEN));
        return true;
    }

    /** Stands up (if meditating). */
    public static void stop(Player player) {
        ShinobiCapability.get(player).ifPresent(data -> {
            if (!data.senjutsu().isMeditating()) {
                return;
            }
            data.senjutsu().end();
            data.markPowersDirty();
            playAnimation(player, RISE_ANIMATION, RISE_TICKS);
            notify(player, Component.translatable("message.noirm.meditate.stop").withStyle(ChatFormatting.GRAY));
        });
    }

    // ---------------------------------------------------------------- every server tick

    /** Runs every server tick for every player (public so tests can drive it). */
    public static void tick(Player player, ShinobiData data) {
        MeditationData m = data.senjutsu();
        if (!m.isMeditating()) {
            if (m.natureEnergy() > 0f) {
                m.setNatureEnergy(m.natureEnergy() - NATURE_FADE_PER_TICK);
                syncEnergy(data);
            }
            return;
        }
        if (shouldBreak(player)) {
            stop(player);
            return;
        }
        int ticks = m.tickAndCount();
        // Fast recovery: the extra on top of normal regen.
        data.setChakra(data.getChakra() + ShinobiData.CHAKRA_REGEN_PER_TICK * data.chakraRegenMultiplier() * (CHAKRA_REGEN_MULTIPLIER - 1f));
        data.setStamina(data.getStamina() + ShinobiData.STAMINA_REGEN_PER_TICK * data.staminaRegenMultiplier() * (STAMINA_REGEN_MULTIPLIER - 1f));
        // Reserves: a solo drill, so it stops teaching at the drill cap.
        if (ticks % 20 == 0 && !player.getAbilities().instabuild) {
            TrainingSystem.grant(player, TrainingSystem.Stat.RESERVES,
                    RESERVES_XP_PER_SECOND * TrainingSystem.drillFactor(player, TrainingSystem.Stat.RESERVES));
        }
        // Nature energy.
        if (ticks == 1 || ticks % NATURE_SCAN_INTERVAL == 0) {
            m.setNatureFactor(natureFactor(player.level(), player.blockPosition()));
        }
        boolean wasFull = m.natureEnergy() >= NATURE_MAX;
        m.setNatureEnergy(m.natureEnergy() + gainPerTick(player.level(), m.natureFactor()));
        if (!wasFull && m.natureEnergy() >= NATURE_MAX) {
            notify(player, Component.translatable("message.noirm.meditate.full").withStyle(ChatFormatting.GREEN));
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
        syncEnergy(data);
        if (ticks >= SIT_TICKS && (ticks - SIT_TICKS) % ANIMATION_RESEND == 0) {
            playAnimation(player, ANIMATION, ANIMATION_TICKS); // keep the seated loop going
        }
        if (ticks % PARTICLE_INTERVAL == 0) {
            particles(player, m.natureEnergy() / NATURE_MAX);
        }
    }

    /** Nature energy gained per tick with this nature multiplier (night adds {@link #NIGHT_BONUS}). */
    public static float gainPerTick(Level level, float natureFactor) {
        return NATURE_GAIN_PER_TICK * natureFactor * (level.isNight() ? NIGHT_BONUS : 1f);
    }

    /** Should meditation end this tick? Moving, jumping off the ground, water, riding, combat mode, casting. */
    static boolean shouldBreak(Player player) {
        MeditationData m = ShinobiCapability.get(player).map(ShinobiData::senjutsu).orElse(null);
        if (m == null || !player.isAlive() || player.isPassenger() || player.isInWater() || player.isInLava()) {
            return true;
        }
        double dx = player.getX() - m.anchorX(), dz = player.getZ() - m.anchorZ();
        if (dx * dx + dz * dz > MOVE_TOLERANCE * MOVE_TOLERANCE || Math.abs(player.getY() - m.anchorY()) > MOVE_TOLERANCE_Y) {
            return true;
        }
        if (CombatStates.get(player).isCombatMode() || player.isUsingItem()) {
            return true;
        }
        return com.noirm.jutsu.JutsuCaster.casting(player) != null;
    }

    /** 1 with no nature around, up to {@link #NATURE_MAX_MULTIPLIER} among {@link #NATURE_BLOCKS_FOR_MAX} natural blocks. */
    public static float natureFactor(Level level, BlockPos center) {
        int count = countNature(level, center);
        float share = Math.min(1f, count / (float) NATURE_BLOCKS_FOR_MAX);
        return 1f + (NATURE_MAX_MULTIPLIER - 1f) * share;
    }

    /** Natural blocks (plants, trees, grass, moss, water…) around {@code center}. */
    public static int countNature(Level level, BlockPos center) {
        int count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -NATURE_SCAN_RADIUS; dx <= NATURE_SCAN_RADIUS; dx++) {
            for (int dz = -NATURE_SCAN_RADIUS; dz <= NATURE_SCAN_RADIUS; dz++) {
                for (int dy = -NATURE_SCAN_DOWN; dy <= NATURE_SCAN_UP; dy++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (isNatural(level.getBlockState(pos))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public static boolean isNatural(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.getFluidState().is(FluidTags.WATER)) {
            return true;
        }
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.CROPS)
                || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MOSS_CARPET) || state.is(Blocks.VINE) || state.is(Blocks.LILY_PAD)
                || state.is(Blocks.BAMBOO) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.MUSHROOM_STEM)
                || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.AZALEA)
                || state.is(Blocks.FLOWERING_AZALEA) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.CACTUS);
    }

    // ---------------------------------------------------------------- what breaks it (events call these)

    /** Damage taken: remember it (a fight) and stand up. */
    public static void onHurt(Player player) {
        ShinobiCapability.get(player).ifPresent(d -> d.senjutsu().setLastHurtTime(player.level().getGameTime()));
        stop(player);
    }

    // ---------------------------------------------------------------- helpers

    private static void syncEnergy(ShinobiData data) {
        if (data.senjutsu().energyChangedForSync()) {
            data.markPowersDirty();
        }
    }

    private static void playAnimation(Player player, net.minecraft.resources.ResourceLocation animation, int ticks) {
        if (player instanceof ServerPlayer) {
            NoirmNetwork.sendToTrackingAndSelf(player, new CombatClientPackets.PlayAnimation(player.getId(), animation, ticks));
        }
    }

    private static void notify(Player player, Component message) {
        if (player instanceof ServerPlayer server) {
            server.displayClientMessage(message, true);
        }
    }

    /** Gentle rising motes: chakra blue, turning leaf green as nature energy fills. */
    private static void particles(Player player, float fullness) {
        Level level = player.level();
        var random = player.getRandom();
        for (int i = 0; i < 2; i++) {
            boolean nature = random.nextFloat() < 0.25f + 0.75f * fullness;
            int color = nature ? (random.nextBoolean() ? 0x7CE860 : 0xB8F070) : 0x8FC8FF;
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 0.5 + random.nextDouble() * 0.4;
            Vec3 at = player.position().add(Math.cos(angle) * radius, 0.1 + random.nextDouble() * 0.6, Math.sin(angle) * radius);
            Vfx.one(level, new VfxOptions(VfxKind.GLOW, color, nature ? 0x2E6A1E : 0x24407A, 0.14f, 0.03f, 34, -0.004f, 0.96f),
                    at, new Vec3(0, 0.035 + random.nextDouble() * 0.02, 0));
        }
    }
}

package com.noirm.senjutsu;

import com.noirm.Noirm;
import com.noirm.entity.MasterNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.training.Supplementary;
import com.noirm.training.TrainingSystem;
import com.noirm.vfx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * The sage path's rules: how much nature energy your body can hold (your <b>safe limit</b>, from
 * chakra control), what the Stone Hermit's lessons change, and <b>petrification</b> when you
 * hold more than your limit.
 *
 * <p><b>The safe limit</b> is {@value #LIMIT_BASE} + {@value #LIMIT_PER_CTL} × (CTL − 10), between
 * {@value #LIMIT_BASE} and 100: CTL 10 → 20, CTL 20 → 40, CTL 30 → 60, CTL 50 → 100.
 *
 * <p><b>The path</b> (lessons of {@link #HERMIT}, see {@code data/noirm/masters/stone_hermit.json}):
 * <ol>
 *   <li>Before the hermit, your body simply won't take in more than its limit: meditation stops
 *       there, and anything above it (an old save, a command) drains away harmlessly.</li>
 *   <li><b>Open to Nature</b> ({@link #LESSON_OPEN}): you draw nature energy ×{@value #GAIN_OPEN}
 *       as fast and can draw past your limit. That's the danger: above the limit you start to turn
 *       to stone.</li>
 *   <li><b>Stone Balance</b> ({@link #LESSON_BALANCE}): ×{@value #GAIN_BALANCE} as fast, and
 *       meditation now stops by itself at your limit (Nature Gathering still can overdraw).</li>
 *   <li><b>Sage Mode</b> ({@link #LESSON_SAGE}): see {@link SageMode}.</li>
 * </ol>
 *
 * <p><b>Petrification</b> (0–100) rises while you're over the limit, by {@value #PETRIFY_BASE} +
 * {@value #PETRIFY_PER_EXCESS} × the excess per second (5 over: ~1.3/s, 20 over: 3.5/s), and
 * falls by {@value #PETRIFY_RECOVERY}/s once you're back under. While you aren't drawing, the
 * excess bleeds off at {@value #VENT_PER_SECOND}/s. At 25 you slow down and your skin greys; at 50
 * it spreads over the limbs; at 75 everything but the face is stone; at 100 you are a statue: you
 * can't move or act, and after {@value #STATUE_SECONDS} s you die, unless the hermit is near
 * (within {@value #RESCUE_RANGE} blocks) and you're his student: he cracks the stone off you.
 */
public final class Sage {
    /** The Stone Hermit (our own master): his test, lessons and the sanctuary he lives in. */
    public static final ResourceLocation HERMIT = Noirm.id("stone_hermit");
    public static final String LESSON_OPEN = "open_to_nature";
    public static final String LESSON_BALANCE = "stone_balance";
    public static final String LESSON_SAGE = "sage_mode";
    /** The three sage jutsu (data/noirm/jutsu/senjutsu/). */
    public static final ResourceLocation NATURE_GATHERING = Noirm.id("senjutsu/nature_gathering");
    public static final ResourceLocation NATURE_PURGE = Noirm.id("senjutsu/nature_purge");
    public static final ResourceLocation SAGE_MODE = Noirm.id("senjutsu/sage_mode");
    public static final ResourceKey<DamageType> PETRIFIED = ResourceKey.create(Registries.DAMAGE_TYPE, Noirm.id("petrified"));

    // ---------------------------------------------------------------- tuning
    /** The safe limit at CTL 10 (and the lowest it goes). */
    public static final float LIMIT_BASE = 20f;
    /** Extra safe nature energy per CTL point above 10. */
    public static final float LIMIT_PER_CTL = 2f;
    /** Gain multipliers after each lesson. */
    public static final float GAIN_OPEN = 1.5f;
    public static final float GAIN_BALANCE = 2f;
    /** Petrification per second while over the limit: this … */
    public static final float PETRIFY_BASE = 0.5f;
    /** … plus this per point of excess. */
    public static final float PETRIFY_PER_EXCESS = 0.15f;
    /** Petrification lost per second while under the limit. */
    public static final float PETRIFY_RECOVERY = 2f;
    /** Excess energy bled off per second while not drawing (students; untrained bodies drain it ×4 and never petrify). */
    public static final float VENT_PER_SECOND = 0.5f;
    public static final float UNTRAINED_VENT_MULTIPLIER = 4f;
    /** Petrification stages: slowed and grey hands, grey limbs, all stone but the face, a statue. */
    public static final float STAGE_1 = 25f, STAGE_2 = 50f, STAGE_3 = 75f, STATUE = 100f;
    /** How long a statue lasts before it's for good (death). */
    public static final int STATUE_SECONDS = 20;
    /** The hermit saves a student turned statue within this many blocks … */
    public static final double RESCUE_RANGE = 32;
    /** … this many ticks after it happens. */
    public static final int RESCUE_DELAY = 60;
    /** CTL XP per second for meditating at the edge (≥ {@link #EDGE_SHARE} of your limit, holding steady). */
    public static final float CTL_EDGE_XP = 1.5f;
    public static final float EDGE_SHARE = 0.9f;
    /** Balancing alone teaches CTL up to the drill cap; next to the hermit, up to this. */
    public static final float CTL_EDGE_CAP_WITH_MASTER = 50f;
    public static final double MASTER_GUIDE_RANGE = 24;

    private static final UUID STONE_SLOW_ID = UUID.fromString("7c1e2b3a-5d4f-4a6b-9c8d-1e2f3a4b5c6d");

    private Sage() {}

    // ---------------------------------------------------------------- limits and lessons

    /** The most nature energy this body holds safely. */
    public static float safeLimit(ShinobiData data) {
        return safeLimit(data.getControl());
    }

    public static float safeLimit(float control) {
        return Mth.clamp(LIMIT_BASE + LIMIT_PER_CTL * (control - ShinobiData.DEFAULT_STAT), LIMIT_BASE, Meditation.NATURE_MAX);
    }

    public static boolean learned(ShinobiData data, String lesson) {
        return data.masters().learned(HERMIT.toString(), lesson);
    }

    /** Opened to nature by the hermit (can draw past the limit, and so can petrify). */
    public static boolean opened(ShinobiData data) {
        return learned(data, LESSON_OPEN);
    }

    /** The hermit's student: passed his test. */
    public static boolean student(ShinobiData data) {
        return data.masters().passed(HERMIT.toString());
    }

    /** How fast you draw nature energy (meditation and Nature Gathering). */
    public static float gainMultiplier(ShinobiData data) {
        if (learned(data, LESSON_BALANCE)) {
            return GAIN_BALANCE;
        }
        return opened(data) ? GAIN_OPEN : 1f;
    }

    /** Where meditation stops drawing: your limit, except for a student opened but not yet balanced. */
    public static float meditationCap(ShinobiData data) {
        if (opened(data) && !learned(data, LESSON_BALANCE)) {
            return Meditation.NATURE_MAX;
        }
        return safeLimit(data);
    }

    // ---------------------------------------------------------------- every server tick

    /**
     * Petrification, venting and the statue, every server tick for every player (public so tests
     * can drive it). {@code drawing}: meditating or channelling Nature Gathering this tick.
     */
    public static void tick(Player player, ShinobiData data) {
        MeditationData m = data.senjutsu();
        SageData sage = m.sage();
        if (sage.isStatue()) {
            tickStatue(player, data, sage);
            return;
        }
        float limit = safeLimit(data);
        float energy = m.natureEnergy();
        float excess = energy - limit;
        boolean drawing = m.isMeditating() || SageMode.gathering(player);
        boolean creative = player.getAbilities().instabuild;
        if (excess > 0f && !opened(data)) {
            // An untrained body won't hold it: the excess just drains away.
            m.setNatureEnergy(Math.max(limit, energy - VENT_PER_SECOND * UNTRAINED_VENT_MULTIPLIER / 20f));
        } else if (excess > 0f) {
            if (!drawing && !sage.active()) {
                m.setNatureEnergy(Math.max(limit, energy - VENT_PER_SECOND / 20f));
            }
            if (!creative) {
                float before = sage.petrification();
                sage.setPetrification(before + (PETRIFY_BASE + PETRIFY_PER_EXCESS * excess) / 20f);
                warnOnStage(player, before, sage.petrification());
                if (sage.petrification() >= STATUE) {
                    becomeStatue(player, data, sage);
                    return;
                }
            }
        } else if (sage.petrification() > 0f) {
            sage.setPetrification(sage.petrification() - PETRIFY_RECOVERY / 20f);
        }
        // Balancing at the edge teaches control (meditating, holding at ≥ 90% of the limit).
        if (m.isMeditating() && energy >= limit * EDGE_SHARE && player.tickCount % 20 == 0 && !creative) {
            float cap = nearHermit(player, MASTER_GUIDE_RANGE) != null ? CTL_EDGE_CAP_WITH_MASTER : TrainingSystem.DRILL_CAP;
            if (data.getControl() < cap) {
                Supplementary.grant(player, data, Supplementary.Stat.CONTROL, CTL_EDGE_XP);
            }
        }
        applyStoneBody(player, sage);
        sync(player, data);
    }

    /** The stage (0–4) a petrification value shows: 0 none, 1–3 spreading grey, 4 statue. */
    public static int stage(float petrification) {
        if (petrification >= STATUE) {
            return 4;
        }
        return petrification >= STAGE_3 ? 3 : petrification >= STAGE_2 ? 2 : petrification >= STAGE_1 ? 1 : 0;
    }

    private static void warnOnStage(Player player, float before, float after) {
        int was = stage(before), now = stage(after);
        if (now > was && now < 4) {
            notify(player, Component.translatable("message.noirm.sage.petrify." + now).withStyle(ChatFormatting.GRAY));
            player.level().playSound(null, player.blockPosition(), SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.8f, 0.5f + 0.1f * now);
        } else if (before == 0f && after > 0f) {
            notify(player, Component.translatable("message.noirm.sage.petrify.0").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Slowness that deepens with each stage (a transient attribute, so it never sticks after a reload). */
    private static void applyStoneBody(Player player, SageData sage) {
        int stage = stage(sage.petrification());
        if (stage == sage.appliedStage()) {
            return;
        }
        sage.setAppliedStage(stage);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(STONE_SLOW_ID);
        double slow = switch (stage) {
            case 1 -> -0.15;
            case 2 -> -0.35;
            case 3 -> -0.6;
            case 4 -> -1.0;
            default -> 0.0;
        };
        if (slow != 0.0) {
            speed.addTransientModifier(new AttributeModifier(STONE_SLOW_ID, "noirm_stone_body", slow, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // ---------------------------------------------------------------- the statue

    private static void becomeStatue(Player player, ShinobiData data, SageData sage) {
        sage.setPetrification(STATUE);
        sage.setStatueTicks(1);
        Meditation.stop(player);
        SageMode.end(player, false);
        com.noirm.jutsu.JutsuCaster.cancel(player);
        player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
        applyStoneBody(player, sage);
        player.sendSystemMessage(Component.translatable("message.noirm.sage.statue", STATUE_SECONDS).withStyle(ChatFormatting.DARK_RED));
        player.level().playSound(null, player.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.2f, 0.5f);
        player.level().playSound(null, player.blockPosition(), SoundEvents.DEEPSLATE_PLACE, SoundSource.PLAYERS, 1f, 0.6f);
        Vfx.burst(player.level(), Vfx.smoke(0x8C8A84, 0.8f, 30), player.position().add(0, 1, 0), 12, 0.4, 0.04);
        data.markDirty();
        sync(player, data);
    }

    private static void tickStatue(Player player, ShinobiData data, SageData sage) {
        int ticks = sage.statueTicks() + 1;
        sage.setStatueTicks(ticks);
        // Frozen: no sliding, no jumping, no acting (SageEvents cancels attacks and item use).
        player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
        player.hurtMarked = true;
        applyStoneBody(player, sage);
        MasterNinja hermit = data.masters().passed(HERMIT.toString()) ? nearHermit(player, RESCUE_RANGE) : null;
        if (hermit != null && ticks >= RESCUE_DELAY) {
            rescue(player, data, sage, hermit);
            return;
        }
        if (ticks % 20 == 0 && ticks < STATUE_SECONDS * 20) {
            notify(player, Component.translatable("message.noirm.sage.statue_left", STATUE_SECONDS - ticks / 20).withStyle(ChatFormatting.DARK_RED));
        }
        if (ticks >= STATUE_SECONDS * 20) {
            sage.setStatueTicks(0);
            sage.setPetrification(0f);
            data.senjutsu().setNatureEnergy(0f);
            applyStoneBody(player, sage);
            sync(player, data);
            if (!player.getAbilities().invulnerable) {
                var source = new net.minecraft.world.damagesource.DamageSource(player.level().registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PETRIFIED));
                player.hurt(source, Float.MAX_VALUE);
                if (player.isAlive()) {
                    player.setHealth(0f);
                    player.die(source);
                }
            }
        }
    }

    /** The hermit cracks the stone off his student: alive, spent, slowed for a while. */
    public static void rescue(Player player, ShinobiData data, SageData sage, MasterNinja hermit) {
        sage.setStatueTicks(0);
        sage.setPetrification(0f);
        data.senjutsu().setNatureEnergy(safeLimit(data) * 0.5f);
        applyStoneBody(player, sage);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
        player.setHealth(Math.max(1f, player.getHealth() * 0.5f));
        player.sendSystemMessage(Component.translatable("message.noirm.sage.rescued", hermit.getDisplayName()).withStyle(ChatFormatting.GREEN));
        player.level().playSound(null, player.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.2f, 0.7f);
        Vfx.burst(player.level(), Vfx.shard(0x8C8A84, 0.25f, 30), player.position().add(0, 1, 0), 24, 0.5, 0.25);
        data.markDirty();
        sync(player, data);
    }

    /** The nearest Stone Hermit within {@code range} blocks, or null. */
    public static MasterNinja nearHermit(Player player, double range) {
        MasterNinja best = null;
        double bestDistance = range * range;
        for (MasterNinja master : player.level().getEntitiesOfClass(MasterNinja.class, player.getBoundingBox().inflate(range))) {
            double d = master.distanceToSqr(player);
            if (master.isAlive() && HERMIT.equals(master.masterId()) && d <= bestDistance) {
                best = master;
                bestDistance = d;
            }
        }
        return best;
    }

    /** A statue can't move or act. */
    public static boolean isStatue(Player player) {
        return ShinobiCapability.get(player).map(d -> d.senjutsu().sage().isStatue()).orElse(false);
    }

    /** Ends petrification and the statue at once (death, respawn, commands). */
    public static void clearStone(Player player, ShinobiData data) {
        SageData sage = data.senjutsu().sage();
        sage.setStatueTicks(0);
        sage.setPetrification(0f);
        applyStoneBody(player, sage);
        sync(player, data);
    }

    // ---------------------------------------------------------------- helpers

    /** Tells the player (and everyone who sees them) how they look, when it changed. */
    static void sync(Player player, ShinobiData data) {
        if (player instanceof ServerPlayer server && data.senjutsu().sage().visualsChanged(safeLimit(data))) {
            SagePackets.broadcast(server, data);
        }
    }

    static void notify(Player player, Component message) {
        if (player instanceof ServerPlayer server) {
            server.displayClientMessage(message, true);
        }
    }
}

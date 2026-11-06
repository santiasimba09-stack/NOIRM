package com.noirm.training;

import com.noirm.combat.Perception;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The blueprint's two <b>supplementary</b> stats, next to the four core ones in {@link TrainingSystem}:
 * <ul>
 *   <li><b>CTL, chakra control:</b> how cleanly you shape chakra. Jutsu cost less (up to −30%) and
 *       water walking drains less. Grows from walking on water, spending chakra under pressure
 *       (below half your chakra) and holding a mode or the Eight Gates in a fight.</li>
 *   <li><b>WIL, willpower:</b> how much you can endure. The Mangekyō strains less (up to −40%) and
 *       a tailed beast can be held at more tails before it fights you for control. Grows from
 *       surviving being "locked in", fighting much stronger foes, using a strained Mangekyō and
 *       resisting a beast that wants out.</li>
 * </ul>
 *
 * <p>They use the same XP curve as the core stats ({@link TrainingSystem#xpToNext}) and only grow
 * from active play: nothing here ticks up while you stand still. They are kept apart from
 * {@link TrainingSystem.Stat} on purpose: that enum also sizes crew allies' stat arrays and
 * the ally screen, and allies don't have CTL or WIL (yet).
 */
public final class Supplementary {
    public enum Stat {
        CONTROL("Control"), WILLPOWER("Willpower");

        private final String key;

        Stat(String key) {
            this.key = key;
        }

        /** Name used in save data and translation keys. */
        public String key() {
            return key;
        }
    }

    // ------------------------------------------------------------ effects
    /** Jutsu chakra cost: −0.3% per CTL point above 10 … */
    public static final float CTL_COST_CUT_PER_POINT = 0.003f;
    /** … capped at −30% (CTL 110). */
    public static final float CTL_MAX_COST_CUT = 0.30f;
    /** Water walking drain: −0.4% per CTL point above 10, capped at −40%. */
    public static final float CTL_WATER_CUT_PER_POINT = 0.004f;
    public static final float CTL_MAX_WATER_CUT = 0.40f;
    /** Mangekyō strain gained: −0.4% per WIL point above 10, capped at −40% (WIL 110). */
    public static final float WIL_STRAIN_CUT_PER_POINT = 0.004f;
    public static final float WIL_MAX_STRAIN_CUT = 0.40f;
    /** A tailed beast: one more tail held safely per this many WIL points above 10 … */
    public static final float WIL_PER_SAFE_TAIL = 25f;
    /** … at most this many more. */
    public static final int WIL_MAX_EXTRA_TAILS = 2;

    // ------------------------------------------------------------ growth (XP, same curve as the core stats)
    /** Moving on water: 1 CTL XP every {@link #WATER_INTERVAL} ticks (a drill: stops at {@link TrainingSystem#DRILL_CAP}). */
    public static final float CTL_WATER_XP = 1f;
    public static final int WATER_INTERVAL = 40;
    /** CTL XP per point of chakra spent while below half your chakra. */
    public static final float CTL_PRESSURE_XP_PER_CHAKRA = 0.5f;
    /** Spends smaller than this (per tick upkeep: water walking, eyes) don't count as "under pressure". */
    public static final float PRESSURE_MIN_SPEND = 0.25f;
    /** Holding a mode or the Gates in a fight: CTL XP per second. */
    public static final float CTL_MODE_XP = 1f;
    /** Coming out of the "locked in" state alive. */
    public static final float WIL_LOCKED_IN_XP = 40f;
    /** A hit traded (either way) with a foe at least {@link #STRONG_FOE_RATIO}× your strength: this × the ratio (max 3). */
    public static final float WIL_STRONG_FOE_XP = 1.5f;
    public static final float STRONG_FOE_RATIO = 1.5f;
    /** WIL XP per point of Mangekyō strain taken while the eyes are already strained (25+). */
    public static final float WIL_STRAIN_XP = 2f;
    public static final float STRAINED_AT = 25f;
    /** Each second you hold more tails than is safe and the beast fails to take over: this × the extra tails. */
    public static final float WIL_RESIST_BEAST_XP = 2f;
    /** "In a fight": hit or hurt by something within this many ticks. */
    public static final int FIGHTING_TICKS = 200;

    private Supplementary() {}

    // ------------------------------------------------------------ values

    public static float stat(ShinobiData data, Stat stat) {
        return stat == Stat.CONTROL ? data.getControl() : data.getWillpower();
    }

    public static void setStat(ShinobiData data, Stat stat, float value) {
        if (stat == Stat.CONTROL) {
            data.setControl(value);
        } else {
            data.setWillpower(value);
        }
    }

    /** Progress toward the next point, 0..1 (for the HUD). */
    public static float progress(ShinobiData data, Stat stat) {
        return Mth.clamp(data.training().xp(stat) / TrainingSystem.xpToNext(stat(data, stat)), 0f, 1f);
    }

    /** Parses "ctl"/"control" and "wil"/"willpower" (for commands); null if neither. */
    public static Stat byName(String name) {
        return switch (name) {
            case "ctl", "control" -> Stat.CONTROL;
            case "wil", "willpower" -> Stat.WILLPOWER;
            default -> null;
        };
    }

    // ------------------------------------------------------------ effects (one hook each)

    /** The jutsu chakra-cost multiplier from CTL (called from {@code JutsuCaster.cost}). 1 for non-players. */
    public static float costMultiplier(LivingEntity caster) {
        if (!(caster instanceof Player player)) {
            return 1f;
        }
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        return data == null ? 1f : costMultiplier(data.getControl());
    }

    public static float costMultiplier(float control) {
        return 1f - Mth.clamp((control - ShinobiData.DEFAULT_STAT) * CTL_COST_CUT_PER_POINT, 0f, CTL_MAX_COST_CUT);
    }

    /** Water walking drain multiplier from CTL. */
    public static float waterWalkMultiplier(ShinobiData data) {
        return 1f - Mth.clamp((data.getControl() - ShinobiData.DEFAULT_STAT) * CTL_WATER_CUT_PER_POINT, 0f, CTL_MAX_WATER_CUT);
    }

    /** Mangekyō strain multiplier from WIL. */
    public static float strainMultiplier(ShinobiData data) {
        return strainMultiplier(data.getWillpower());
    }

    public static float strainMultiplier(float willpower) {
        return 1f - Mth.clamp((willpower - ShinobiData.DEFAULT_STAT) * WIL_STRAIN_CUT_PER_POINT, 0f, WIL_MAX_STRAIN_CUT);
    }

    /** Extra tails a jinchūriki holds safely thanks to WIL. */
    public static int extraSafeTails(ShinobiData data) {
        return Mth.clamp((int) ((data.getWillpower() - ShinobiData.DEFAULT_STAT) / WIL_PER_SAFE_TAIL), 0, WIL_MAX_EXTRA_TAILS);
    }

    // ------------------------------------------------------------ growth hooks

    /** Every server tick (from {@link TrainingSystem#tick}). Allocation-free. */
    static void tick(Player player, ShinobiData data) {
        TrainingData training = data.training();
        // Chakra spent while below half: control under pressure.
        float pressure = training.takePressureSpent();
        if (pressure > 0f && !player.getAbilities().instabuild) {
            grant(player, data, Stat.CONTROL, pressure * CTL_PRESSURE_XP_PER_CHAKRA);
        }
        // Coming out of "locked in" alive.
        boolean lockedIn = data.awakening().isLockedIn();
        if (training.wasLockedIn() && !lockedIn && player.isAlive()) {
            grant(player, data, Stat.WILLPOWER, WIL_LOCKED_IN_XP);
        }
        training.setWasLockedIn(lockedIn);
        // Holding a mode or the Gates while fighting: once a second.
        if (player.tickCount % 20 == 0 && com.noirm.jutsu.ModeSystem.activeMode(player) != null && fighting(player)) {
            grant(player, data, Stat.CONTROL, CTL_MODE_XP);
        }
    }

    /** A server tick walking (not standing) on water. */
    public static void onWaterWalk(Player player, ShinobiData data, boolean moving) {
        if (moving && data.training().waterTick(WATER_INTERVAL) && data.getControl() < TrainingSystem.DRILL_CAP) {
            grant(player, data, Stat.CONTROL, CTL_WATER_XP);
        }
    }

    /** A hit traded with {@code opponent} (landed or taken): a much stronger foe teaches willpower. */
    public static void onExchange(Player player, Entity opponent) {
        if (!(opponent instanceof LivingEntity living) || opponent == player || player.level().isClientSide()
                || opponent instanceof com.noirm.entity.TrainingDummy
                || !(opponent instanceof net.minecraft.world.entity.monster.Enemy || opponent instanceof Player
                || opponent instanceof com.noirm.combat.CombatStats)) {
            return;
        }
        float ratio = Perception.basePower(living) / Math.max(1f, Perception.basePower(player));
        if (ratio >= STRONG_FOE_RATIO) {
            ShinobiCapability.get(player).ifPresent(data -> grant(player, data, Stat.WILLPOWER, WIL_STRONG_FOE_XP * Math.min(ratio, 3f)));
        }
    }

    /** Mangekyō strain just taken ({@code before} → before + amount): pushing on through strain builds will. */
    public static void onStrain(Player player, ShinobiData data, float before, float amount) {
        if (before >= STRAINED_AT && amount > 0f) {
            grant(player, data, Stat.WILLPOWER, amount * WIL_STRAIN_XP);
        }
    }

    /** A second spent holding {@code extraTails} more tails than is safe, without the beast taking over. */
    public static void onResistBeast(Player player, ShinobiData data, int extraTails) {
        if (extraTails > 0) {
            grant(player, data, Stat.WILLPOWER, WIL_RESIST_BEAST_XP * extraTails);
        }
    }

    /** In a fight: hit something, or got hit, within {@link #FIGHTING_TICKS}. */
    public static boolean fighting(Player player) {
        return player.getLastHurtMob() != null && player.tickCount - player.getLastHurtMobTimestamp() < FIGHTING_TICKS
                || player.getLastHurtByMob() != null && player.tickCount - player.getLastHurtByMobTimestamp() < FIGHTING_TICKS;
    }

    /** Adds XP and raises the stat for every threshold crossed (the core stats' curve). */
    public static void grant(Player player, ShinobiData data, Stat stat, float xp) {
        if (xp <= 0f || player.level().isClientSide()) {
            return;
        }
        float value = stat(data, stat);
        if (value >= ShinobiData.MAX_STAT) {
            return;
        }
        float total = data.training().xp(stat) + xp;
        int gained = 0;
        while (total >= TrainingSystem.xpToNext(value) && value < ShinobiData.MAX_STAT) {
            total -= TrainingSystem.xpToNext(value);
            value = Math.min(ShinobiData.MAX_STAT, (float) Math.floor(value) + 1f);
            gained++;
        }
        data.training().setXp(stat, total);
        if (gained > 0) {
            setStat(data, stat, value);
            String key = "message.noirm.training." + stat.key().toLowerCase(java.util.Locale.ROOT) + "_up";
            player.sendSystemMessage(Component.translatable(key, Math.round(value)).withStyle(ChatFormatting.GOLD));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.3f);
        }
        data.markDirty();
    }
}

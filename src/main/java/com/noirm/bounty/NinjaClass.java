package com.noirm.bounty;

import com.noirm.dojutsu.MangekyoNpcs;
import com.noirm.entity.ShadowClone;
import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.training.TrainingSystem.Stat;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * A fighter's <b>class</b>, D (weakest) to S (strongest): how dangerous they are in a fight. Every
 * ninja and every player has one, worked out from their <b>power</b>.
 *
 * <p><b>Class is not rank.</b> Your rank ({@code mission.PlayerRank}: genin, chūnin…) is your
 * standing in the village, earned with missions and exams. Your class is how strong you are right
 * now. A genin who trained hard can be class B; a lazy chūnin can still be class D. The letters
 * match the mission letters on purpose: an A-rank hunt sends you after an A-class rogue.
 *
 * <p><b>Power</b> ({@link #power}) is a weighted average of the stats (a new player has 10 in every
 * stat, so power 10):
 * <ul>
 *   <li>players: taijutsu 25%, speed 25%, vitality 15%, durability 15%, reserves 10%, chakra control
 *       5%, willpower 5%;</li>
 *   <li>ninjas (no CTL / WIL): taijutsu 30%, speed 30%, vitality 15%, durability 15%, reserves 10%.</li>
 * </ul>
 * then raised by what they carry ({@link #extras}): +4% per Sharingan tomoe, +20% a Mangekyō,
 * +8% the Byakugan, +10% and +3% per tail for a tailed beast, +15% Sage Mode, +1% per jutsu known
 * beyond the first two (at most +10%); all of it together at most +{@link #MAX_EXTRAS}.
 *
 * <p><b>Thresholds</b> (power): D below 15, C from 15, B from 28, A from 45, S from 80. They line up
 * with the rest of the game: a fresh player (10) and the D-rank bandits (9) are D; the genin test
 * opponent (16) and C-rank rogues are C; the chūnin exam (22–30) is C to B; the jōnin trial (48)
 * and A-rank rogues (~48) are A; the S-rank rogue (~88) and every Kage (120+) are S.
 * <b>Legendary</b> ninjas are shown as S+.
 *
 * <p><b>Worth</b> (kill XP): D 10, C 20, B 40, A 80, S 160, legendary {@link #LEGENDARY_WORTH}: each
 * class is worth twice the one below.
 */
public enum NinjaClass {
    D(0f, 10f, 0xFF9AD27A),
    C(15f, 20f, 0xFF7AB8E8),
    B(28f, 40f, 0xFFE8C060),
    A(45f, 80f, 0xFFE8873A),
    S(80f, 160f, 0xFFE05A5A);

    /** Kill-XP worth of a legendary ninja (twice an S). */
    public static final float LEGENDARY_WORTH = 320f;
    /** The gold of a legendary's S+ tag and its bingo page. */
    public static final int LEGENDARY_COLOR = 0xFFF2C94C;
    /** The most the extras can add together (+75%). */
    public static final float MAX_EXTRAS = 0.75f;
    /** Extras: per tomoe, Mangekyō, Byakugan, beast (+ per tail), Sage Mode, per jutsu beyond {@link #FREE_JUTSU} (capped). */
    public static final float PER_TOMOE = 0.04f, MANGEKYO = 0.20f, BYAKUGAN = 0.08f, BEAST = 0.10f, PER_TAIL = 0.03f, SAGE = 0.15f;
    public static final float PER_JUTSU = 0.01f, MAX_JUTSU = 0.10f;
    public static final int FREE_JUTSU = 2;
    /** Synced code for "legendary" (after the five letters). */
    public static final int LEGENDARY_CODE = 5;

    private final float minPower;
    private final float worth;
    private final int color;

    NinjaClass(float minPower, float worth, int color) {
        this.minPower = minPower;
        this.worth = worth;
        this.color = color;
    }

    /** The least power for this class. */
    public float minPower() {
        return minPower;
    }

    /** What beating someone of this class is worth (kill XP). */
    public float worth() {
        return worth;
    }

    /** The letter's colour (the same as the mission letter's). */
    public int color() {
        return color;
    }

    public static NinjaClass byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : D;
    }

    public static NinjaClass byName(String name) {
        for (NinjaClass c : values()) {
            if (c.name().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    /** The class for a power value. */
    public static NinjaClass ofPower(float power) {
        NinjaClass result = D;
        for (NinjaClass c : values()) {
            if (power >= c.minPower) {
                result = c;
            }
        }
        return result;
    }

    // ------------------------------------------------------------ power

    /** The stats part of {@link #power} (before extras). 0 for anything that isn't a player or a ninja. */
    public static float statPower(LivingEntity entity) {
        if (entity instanceof Player player) {
            ShinobiData d = ShinobiCapability.get(player).orElse(null);
            if (d == null) {
                return 10f;
            }
            return 0.25f * d.getTaijutsu() + 0.25f * d.getSpeed() + 0.15f * d.getVitality() + 0.15f * d.getDurability()
                    + 0.10f * d.getReserves() + 0.05f * d.getControl() + 0.05f * d.getWillpower();
        }
        if (entity instanceof SparringNinja ninja) {
            return 0.30f * ninja.stat(Stat.TAIJUTSU) + 0.30f * ninja.stat(Stat.SPEED) + 0.15f * ninja.stat(Stat.VITALITY)
                    + 0.15f * ninja.stat(Stat.DURABILITY) + 0.10f * ninja.stat(Stat.RESERVES);
        }
        return 0f;
    }

    /** What the eyes, a beast, Sage Mode and jutsu add (a share: 0.2 = +20%), capped at {@link #MAX_EXTRAS}. */
    public static float extras(LivingEntity entity) {
        float bonus = 0f;
        if (entity instanceof Player player) {
            ShinobiData d = ShinobiCapability.get(player).orElse(null);
            if (d == null) {
                return 0f;
            }
            bonus += PER_TOMOE * d.eyes().sharinganStage();
            bonus += d.eyes().hasMangekyo() ? MANGEKYO : 0f;
            bonus += d.eyes().hasByakugan() ? BYAKUGAN : 0f;
            bonus += d.biju().isJinchuriki() ? BEAST + PER_TAIL * d.biju().tails() : 0f;
            boolean sage = com.noirm.senjutsu.Sage.learned(d, com.noirm.senjutsu.Sage.LESSON_SAGE)
                    || d.jutsu().knows(com.noirm.senjutsu.Sage.SAGE_MODE);
            bonus += sage ? SAGE : 0f;
            bonus += jutsuBonus(d.jutsu().known().size());
        } else if (entity instanceof SparringNinja ninja && !(entity instanceof ShadowClone)) {
            bonus += PER_TOMOE * ninja.activeSharinganStage();
            bonus += ninja.mangekyoLook() != 0 || MangekyoNpcs.hasMangekyo(ninja) ? MANGEKYO : 0f;
            bonus += ninja.byakuganActive() ? BYAKUGAN : 0f;
            bonus += ninja.tails() > 0 ? BEAST + PER_TAIL * ninja.tails() : 0f;
            bonus += Legendary.isLegendary(ninja) ? SAGE : 0f; // legends carry something special too
            bonus += jutsuBonus(ninja.knownJutsu().size());
        }
        return Math.min(MAX_EXTRAS, bonus);
    }

    private static float jutsuBonus(int known) {
        return Math.min(MAX_JUTSU, Math.max(0, known - FREE_JUTSU) * PER_JUTSU);
    }

    /** Power: the stats, raised by the extras. */
    public static float power(LivingEntity entity) {
        return statPower(entity) * (1f + extras(entity));
    }

    /** The class of a player or ninja (D for anything else). Server side. */
    public static NinjaClass of(LivingEntity entity) {
        return ofPower(power(entity));
    }

    /** The synced code: the class ordinal, or {@link #LEGENDARY_CODE}. */
    public static int code(LivingEntity entity) {
        return Legendary.isLegendary(entity) ? LEGENDARY_CODE : of(entity).ordinal();
    }

    /** What beating {@code entity} is worth (kill XP): the class's worth, or {@link #LEGENDARY_WORTH}. */
    public static float worth(LivingEntity entity) {
        return Legendary.isLegendary(entity) ? LEGENDARY_WORTH : of(entity).worth();
    }

    // ------------------------------------------------------------ text

    /** "B", or "S+" for a legendary code. */
    public static String letter(int code) {
        return code == LEGENDARY_CODE ? "S+" : byOrdinal(code).name();
    }

    public static int color(int code) {
        return code == LEGENDARY_CODE ? LEGENDARY_COLOR : byOrdinal(code).color();
    }

    /** The coloured tag, "[B]" (or a gold "[S+]"). */
    public static MutableComponent tag(int code) {
        return Component.literal("[" + letter(code) + "]").withStyle(s -> s.withColor(color(code) & 0xFFFFFF));
    }

    /** What a name tag shows: "[B] Name". */
    public static MutableComponent nameplate(int code, Component name) {
        return Component.empty().append(tag(code)).append(" ").append(name);
    }

    /** {@code entity}'s name tag (server side: their class worked out now). */
    public static MutableComponent nameplate(LivingEntity entity) {
        return nameplate(code(entity), entity.getDisplayName());
    }
}

package com.noirm.arts;

import com.noirm.clan.Nature;
import com.noirm.clan.NatureSystem;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The solo paths to <b>Yin</b>, <b>Yang</b> and <b>Yin-Yang</b>, as nature drills (they use the drill
 * machinery in {@code NatureDrills}: meditate, one second of study per second, teachers help):
 * <ul>
 *   <li><b>Yin</b>, the mind: needs WIL {@link #YIN_MIN_WIL}. Meditate in darkness (light
 *       {@link #DARK_LIGHT} or less: night, a cave), or within a minute of mind practice (casting a
 *       genjutsu or a Yin jutsu, or breaking one with Kai).</li>
 *   <li><b>Yang</b>, the body: needs Vitality {@link #YANG_MIN_VITALITY}. Meditate in daylight under
 *       the open sky, or within a minute of body training (landing a hit).</li>
 *   <li><b>Yin-Yang</b>: once you hold both (and a jutsu of each at ★{@link NatureSystem#REQUIRED_MASTERY}),
 *       meditate anywhere and bring them into balance.</li>
 * </ul>
 * A strong stat ({@link #STRONG_STAT} WIL for Yin, Vitality for Yang) makes it {@link #STRONG_PACE}× faster.
 */
public final class SpiritPaths {
    public static final float YIN_MIN_WIL = 20f;
    public static final float YANG_MIN_VITALITY = 20f;
    /** The brightest a place may be for Yin's meditation. */
    public static final int DARK_LIGHT = 7;
    /** Practice counts for this long (ticks): a minute. */
    public static final int PRACTICE_WINDOW = 1200;
    public static final float STRONG_STAT = 40f;
    public static final float STRONG_PACE = 1.5f;

    private SpiritPaths() {}

    public static boolean isSpirit(Nature nature) {
        return nature == Nature.YIN || nature == Nature.YANG || nature == Nature.YIN_YANG;
    }

    /** Why this player can't drill this nature at all yet (a translation key), or null. */
    public static String gate(ShinobiData data, Nature nature) {
        return switch (nature) {
            case YIN -> data.getWillpower() < YIN_MIN_WIL ? "message.noirm.spirit.gate.yin" : null;
            case YANG -> data.getVitality() < YANG_MIN_VITALITY ? "message.noirm.spirit.gate.yang" : null;
            case YIN_YANG -> NatureSystem.status(data, Nature.YIN_YANG) == NatureSystem.Status.LOCKED ? "message.noirm.spirit.gate.yin_yang" : null;
            default -> null;
        };
    }

    /** Whether the player is in the drill's setting right now (the gate is checked separately). */
    public static boolean inSetting(Player player, Nature nature) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null) {
            return false;
        }
        Level level = player.level();
        long now = level.getGameTime();
        BlockPos head = BlockPos.containing(player.getEyePosition());
        return switch (nature) {
            case YIN -> level.getMaxLocalRawBrightness(head) <= DARK_LIGHT || now - data.arts().lastMind() <= PRACTICE_WINDOW;
            case YANG -> (level.isDay() && level.canSeeSky(head)) || now - data.arts().lastBody() <= PRACTICE_WINDOW;
            case YIN_YANG -> data.identity().affinity(Nature.YIN) > 0f && data.identity().affinity(Nature.YANG) > 0f;
            default -> false;
        };
    }

    /** Pace bonus from a strong stat. */
    public static float pace(ShinobiData data, Nature nature) {
        return switch (nature) {
            case YIN -> data.getWillpower() >= STRONG_STAT ? STRONG_PACE : 1f;
            case YANG -> data.getVitality() >= STRONG_STAT ? STRONG_PACE : 1f;
            default -> 1f;
        };
    }

    /** Body training happened (a hit landed). */
    public static void noteBody(Player player) {
        ShinobiCapability.get(player).ifPresent(d -> d.arts().noteBody(player.level().getGameTime()));
    }
}

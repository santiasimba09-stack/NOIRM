package com.noirm.mission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * One kind of mission, read from {@code data/<ns>/missions/<id>.json}. Its name and description
 * are the lang keys {@code mission.<ns>.<path>} and {@code mission.<ns>.<path>.desc}.
 *
 * <ul>
 *   <li>{@code type}: hunt, escort, deliver, scout, retrieve or defend ({@link MissionKind}).</li>
 *   <li>{@code rank}: D, C, B, A or S: who may take it ({@link Letter#required}).</li>
 *   <li>{@code reward} ryo and rank {@code points} when you report back.</li>
 *   <li>{@code time_limit}: seconds to finish the objective (0 = none).</li>
 *   <li>{@code min_distance} / {@code max_distance}: how far from the board the place is.</li>
 *   <li>{@code encounter} (a wild-ninja group from {@code data/noirm/encounters/}) and its
 *       {@code strength}: the rogues to hunt, the camp's guards or each raiding wave.</li>
 *   <li>{@code count}: points to scout, or waves to defend.</li>
 *   <li>{@code ambush}: an encounter that springs on an escort halfway, or waits at the last scouting point.</li>
 *   <li>{@code solo}: you must land the final blows yourself (the jōnin trial).</li>
 *   <li>{@code exam}: part of a rank-up exam; never offered on a board.</li>
 *   <li>{@code weight}: how often a board offers it among missions of its rank.</li>
 * </ul>
 */
public record MissionTemplate(MissionKind kind, Letter rank, int reward, int points, int timeLimit, int minDistance, int maxDistance,
                              Optional<ResourceLocation> encounter, float strength, int count, Optional<ResourceLocation> ambush,
                              boolean solo, boolean exam, int weight) {

    /** A mission's rank letter, and the player rank needed to take it. */
    public enum Letter {
        D(PlayerRank.ACADEMY, 0xFF9AD27A),
        C(PlayerRank.GENIN, 0xFF7AB8E8),
        B(PlayerRank.CHUNIN, 0xFFE8C060),
        A(PlayerRank.JONIN, 0xFFE8873A),
        S(PlayerRank.ANBU, 0xFFE05A5A);

        private final PlayerRank required;
        private final int color;

        Letter(PlayerRank required, int color) {
            this.required = required;
            this.color = color;
        }

        public PlayerRank required() {
            return required;
        }

        public int color() {
            return color;
        }

        public static Letter byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : D;
        }
    }

    private static final Codec<MissionKind> KIND = Codec.STRING.comapFlatMap(s -> {
        MissionKind kind = MissionKind.byKey(s);
        return kind == null ? DataResult.error(() -> "Unknown mission type " + s) : DataResult.success(kind);
    }, MissionKind::key);

    private static final Codec<Letter> LETTER = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(Letter.valueOf(s.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Unknown mission rank " + s);
        }
    }, Letter::name);

    public static final Codec<MissionTemplate> CODEC = RecordCodecBuilder.create(i -> i.group(
            KIND.fieldOf("type").forGetter(MissionTemplate::kind),
            LETTER.fieldOf("rank").forGetter(MissionTemplate::rank),
            Codec.INT.optionalFieldOf("reward", 100).forGetter(MissionTemplate::reward),
            Codec.INT.optionalFieldOf("points", 10).forGetter(MissionTemplate::points),
            Codec.INT.optionalFieldOf("time_limit", 0).forGetter(MissionTemplate::timeLimit),
            Codec.INT.optionalFieldOf("min_distance", 100).forGetter(MissionTemplate::minDistance),
            Codec.INT.optionalFieldOf("max_distance", 160).forGetter(MissionTemplate::maxDistance),
            ResourceLocation.CODEC.optionalFieldOf("encounter").forGetter(MissionTemplate::encounter),
            Codec.FLOAT.optionalFieldOf("strength", 12f).forGetter(MissionTemplate::strength),
            Codec.INT.optionalFieldOf("count", 1).forGetter(MissionTemplate::count),
            ResourceLocation.CODEC.optionalFieldOf("ambush").forGetter(MissionTemplate::ambush),
            Codec.BOOL.optionalFieldOf("solo", false).forGetter(MissionTemplate::solo),
            Codec.BOOL.optionalFieldOf("exam", false).forGetter(MissionTemplate::exam),
            Codec.INT.optionalFieldOf("weight", 10).forGetter(MissionTemplate::weight)
    ).apply(i, MissionTemplate::new));

    /** The lang key of a mission's name: {@code mission.<ns>.<path with dots>}. */
    public static String nameKey(ResourceLocation id) {
        return "mission." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }
}

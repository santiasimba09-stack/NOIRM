package com.noirm.mission;

import com.noirm.player.ShinobiCapability;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The player's shinobi rank, from the Academy to Kage candidate.
 *
 * <p>Rank points come from missions. The first three steps need a <b>rank-up exam</b> once you
 * have the points (genin test, chūnin exam, jōnin trial); the last two are invitations: the
 * village names you ANBU or a Kage candidate once you have the points and enough hard missions
 * behind you ({@link #letter} missions, {@link #missions} of them). Becoming Kage itself is left
 * for later (the village would have to choose you).
 *
 * <p>Other systems gate content on the rank with {@link #atLeast(Player, PlayerRank)}.
 */
public enum PlayerRank {
    ACADEMY("academy", 0, false, null, 0),
    GENIN("genin", 30, true, null, 0),
    CHUNIN("chunin", 150, true, null, 0),
    JONIN("jonin", 500, true, null, 0),
    ANBU("anbu", 1000, false, "A", 3),
    KAGE_CANDIDATE("kage_candidate", 2500, false, "S", 2);

    private final String key;
    private final int points;
    private final boolean exam;
    /** The letter's name (MissionTemplate.Letter refers back to this enum, so it's looked up lazily). */
    private final String letter;
    private final int missions;

    PlayerRank(String key, int points, boolean exam, String letter, int missions) {
        this.key = key;
        this.points = points;
        this.exam = exam;
        this.letter = letter;
        this.missions = missions;
    }

    public String key() {
        return key;
    }

    /** Total rank points needed before you can reach this rank. */
    public int points() {
        return points;
    }

    /** Whether reaching this rank takes an exam (otherwise it's an invitation). */
    public boolean exam() {
        return exam;
    }

    /** For invitations: the mission rank that counts, and how many of them you need. */
    public MissionTemplate.Letter letter() {
        return letter == null ? null : MissionTemplate.Letter.valueOf(letter);
    }

    public int missions() {
        return missions;
    }

    public Component title() {
        return Component.translatable("rank.noirm." + key);
    }

    /** The next rank up, or null at the top. */
    public PlayerRank next() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }

    public boolean atLeast(PlayerRank other) {
        return ordinal() >= other.ordinal();
    }

    public static PlayerRank byKey(String key) {
        for (PlayerRank rank : values()) {
            if (rank.key.equalsIgnoreCase(key) || rank.name().equalsIgnoreCase(key)) {
                return rank;
            }
        }
        return null;
    }

    public static PlayerRank byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : ACADEMY;
    }

    /** The player's rank (the Academy if they have no data). */
    public static PlayerRank of(Player player) {
        return ShinobiCapability.get(player).map(d -> d.career().rank()).orElse(ACADEMY);
    }

    /**
     * The rank a jutsu of rank letter {@code jutsuRank} needs to be learned (wired into
     * {@code nature.JutsuLearning.extraRankGate}): B needs chūnin, A jōnin, S ANBU; E–C anyone.
     */
    public static PlayerRank forJutsu(String jutsuRank) {
        return switch (jutsuRank == null ? "" : jutsuRank.toUpperCase(java.util.Locale.ROOT)) {
            case "B" -> CHUNIN;
            case "A" -> JONIN;
            case "S" -> ANBU;
            default -> ACADEMY;
        };
    }

    /** Why {@code player}'s rank is too low for a jutsu of rank {@code jutsuRank} (null if it isn't). */
    public static Component jutsuGate(Player player, String jutsuRank) {
        PlayerRank need = forJutsu(jutsuRank);
        return atLeast(player, need) ? null : Component.translatable("rank.noirm.why.jutsu", jutsuRank, need.title());
    }

    /** Whether {@code player} is {@code rank} or higher: the gate for missions, shop items and masters. */
    public static boolean atLeast(Player player, PlayerRank rank) {
        return of(player).atLeast(rank);
    }
}

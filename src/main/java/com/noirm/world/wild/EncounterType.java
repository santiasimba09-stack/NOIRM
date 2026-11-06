package com.noirm.world.wild;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One kind of group you can meet out in the wild, read from {@code data/<ns>/encounters/<id>.json}.
 *
 * <ul>
 *   <li>{@code weight} / {@code night_weight}: how often it's picked by day and at night (0 = never).</li>
 *   <li>{@code min_size} / {@code max_size}: how many ninjas (the first one leads).</li>
 *   <li>{@code hostile}: rogues attack players on sight; the others only fight back.</li>
 *   <li>{@code spar_chance}: chance each member challenges a player who goes into combat mode nearby.</li>
 *   <li>{@code leader_strength} / {@code member_strength}: multiplies the area's danger level
 *       (see {@link WildSpawner#danger}) to give their speed and taijutsu.</li>
 *   <li>{@code outfit}: {@code clan} (their clan's clothes), {@code village} (their village's
 *       uniform; the leader in the jōnin vest) or {@code rogue} (a rogue's cloak and slashed headband).</li>
 *   <li>{@code same_clan}: everyone in the group shares one clan.</li>
 *   <li>{@code hunts_rogues}: they go after hostile rogues they see.</li>
 *   <li>{@code defends_players}: when a rogue attacks a player near them, they join the fight
 *       against the rogue (see {@link WildDefenders}).</li>
 *   <li>{@code min_distance}: blocks from the world spawn before it can turn up;
 *       {@code min_played_minutes}: how long the player must have played.</li>
 * </ul>
 */
public record EncounterType(int weight, int nightWeight, int minSize, int maxSize, boolean hostile, float sparChance,
                            float leaderStrength, float memberStrength, String outfit, boolean sameClan, boolean huntsRogues,
                            int minDistance, int minPlayedMinutes, boolean defendsPlayers) {
    public static final Codec<EncounterType> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("weight", 10).forGetter(EncounterType::weight),
            Codec.INT.optionalFieldOf("night_weight", -1).forGetter(EncounterType::nightWeight),
            Codec.INT.optionalFieldOf("min_size", 1).forGetter(EncounterType::minSize),
            Codec.INT.optionalFieldOf("max_size", 1).forGetter(EncounterType::maxSize),
            Codec.BOOL.optionalFieldOf("hostile", false).forGetter(EncounterType::hostile),
            Codec.FLOAT.optionalFieldOf("spar_chance", 0f).forGetter(EncounterType::sparChance),
            Codec.FLOAT.optionalFieldOf("leader_strength", 1f).forGetter(EncounterType::leaderStrength),
            Codec.FLOAT.optionalFieldOf("member_strength", 1f).forGetter(EncounterType::memberStrength),
            Codec.STRING.optionalFieldOf("outfit", "clan").forGetter(EncounterType::outfit),
            Codec.BOOL.optionalFieldOf("same_clan", false).forGetter(EncounterType::sameClan),
            Codec.BOOL.optionalFieldOf("hunts_rogues", false).forGetter(EncounterType::huntsRogues),
            Codec.INT.optionalFieldOf("min_distance", 0).forGetter(EncounterType::minDistance),
            Codec.INT.optionalFieldOf("min_played_minutes", 0).forGetter(EncounterType::minPlayedMinutes),
            Codec.BOOL.optionalFieldOf("defends_players", false).forGetter(EncounterType::defendsPlayers)
    ).apply(i, EncounterType::new));

    /** The weight by day or at night (night falls back to the day weight). */
    public int weightAt(boolean night) {
        return Math.max(0, night && nightWeight >= 0 ? nightWeight : weight);
    }

    public int clampedMax() {
        return Math.max(Math.max(1, minSize), maxSize);
    }
}

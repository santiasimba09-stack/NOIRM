package com.noirm.jutsu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.noirm.clan.Nature;

import java.util.Optional;

/**
 * A jutsu, loaded from {@code data/<ns>/jutsu/<path>.json}. What it actually does comes from its
 * {@code behavior} (Java, see {@link JutsuBehaviors}); everything tunable lives here.
 *
 * @param nature     the chakra nature needed (none = anyone can use it)
 * @param chakraCost for CHANNEL jutsu, per second
 * @param cooldown   ticks before it can be used again
 * @param minCharge  CHARGE: ticks of charging needed before it can be released
 * @param maxCharge  CHARGE: ticks to reach full power
 * @param maxDuration CHANNEL: longest it can be held, in ticks
 * @param requires   a dōjutsu requirement, e.g. the Sharingan at 2 tomoe
 * @param starter    given to everyone with its nature (or to everyone, if it has no nature)
 * @param rank       E–S, for display
 */
public record JutsuDefinition(Optional<Nature> nature, CastType cast, String behavior, float chakraCost, int cooldown,
                              int minCharge, int maxCharge, int maxDuration, Optional<Requirement> requires,
                              boolean starter, String rank, Params params) {

    public static final Codec<JutsuDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Nature.CODEC.optionalFieldOf("nature").forGetter(JutsuDefinition::nature),
            CastType.CODEC.optionalFieldOf("cast", CastType.TAP).forGetter(JutsuDefinition::cast),
            Codec.STRING.fieldOf("behavior").forGetter(JutsuDefinition::behavior),
            Codec.floatRange(0, 1000).fieldOf("chakra_cost").forGetter(JutsuDefinition::chakraCost),
            Codec.intRange(0, 72000).optionalFieldOf("cooldown", 40).forGetter(JutsuDefinition::cooldown),
            Codec.intRange(0, 400).optionalFieldOf("min_charge", 0).forGetter(JutsuDefinition::minCharge),
            Codec.intRange(1, 400).optionalFieldOf("max_charge", 20).forGetter(JutsuDefinition::maxCharge),
            Codec.intRange(1, 2400).optionalFieldOf("max_duration", 60).forGetter(JutsuDefinition::maxDuration),
            Requirement.CODEC.optionalFieldOf("requires").forGetter(JutsuDefinition::requires),
            Codec.BOOL.optionalFieldOf("starter", false).forGetter(JutsuDefinition::starter),
            Codec.STRING.optionalFieldOf("rank", "C").forGetter(JutsuDefinition::rank),
            Params.CODEC.optionalFieldOf("params", Params.DEFAULT).forGetter(JutsuDefinition::params)
    ).apply(i, JutsuDefinition::new));

    /** A dōjutsu needed to cast: e.g. {@code {"dojutsu": "sharingan", "stage": 2}} (must be active). */
    public record Requirement(String dojutsu, int stage) {
        public static final Codec<Requirement> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dojutsu").forGetter(Requirement::dojutsu),
                Codec.intRange(1, 10).optionalFieldOf("stage", 1).forGetter(Requirement::stage)
        ).apply(i, Requirement::new));
    }

    /** Numbers the behaviors read. Each behavior uses the ones that make sense for it. */
    public record Params(float damage, float chargeDamage, float speed, float radius, float range, float knockback,
                         float posture, int duration, int stun, int fireSeconds) {
        public static final Params DEFAULT = new Params(0, 0, 1, 1, 6, 0.5f, 0, 20, 0, 0);
        public static final Codec<Params> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("damage", 0f).forGetter(Params::damage),
                Codec.FLOAT.optionalFieldOf("charge_damage", 0f).forGetter(Params::chargeDamage),
                Codec.FLOAT.optionalFieldOf("speed", 1f).forGetter(Params::speed),
                Codec.FLOAT.optionalFieldOf("radius", 1f).forGetter(Params::radius),
                Codec.FLOAT.optionalFieldOf("range", 6f).forGetter(Params::range),
                Codec.FLOAT.optionalFieldOf("knockback", 0.5f).forGetter(Params::knockback),
                Codec.FLOAT.optionalFieldOf("posture", 0f).forGetter(Params::posture),
                Codec.INT.optionalFieldOf("duration", 20).forGetter(Params::duration),
                Codec.INT.optionalFieldOf("stun", 0).forGetter(Params::stun),
                Codec.INT.optionalFieldOf("fire_seconds", 0).forGetter(Params::fireSeconds)
        ).apply(i, Params::new));
    }
}

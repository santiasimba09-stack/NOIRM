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
 * @param unlock     learned automatically once another jutsu reaches a mastery level
 */
public record JutsuDefinition(Optional<Nature> nature, CastType cast, String behavior, float chakraCost, int cooldown,
                              int minCharge, int maxCharge, int maxDuration, Optional<Requirement> requires,
                              boolean starter, String rank, Params params, Optional<Unlock> unlock) {

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
            Params.CODEC.optionalFieldOf("params", Params.DEFAULT).forGetter(JutsuDefinition::params),
            Unlock.CODEC.optionalFieldOf("unlock").forGetter(JutsuDefinition::unlock)
    ).apply(i, JutsuDefinition::new));

    /** {@code {"after": "noirm:katon/great_fireball", "mastery": 2}}: learned when that jutsu reaches ★2. */
    public record Unlock(net.minecraft.resources.ResourceLocation after, int mastery) {
        public static final Codec<Unlock> CODEC = RecordCodecBuilder.create(i -> i.group(
                net.minecraft.resources.ResourceLocation.CODEC.fieldOf("after").forGetter(Unlock::after),
                Codec.intRange(1, 5).optionalFieldOf("mastery", 2).forGetter(Unlock::mastery)
        ).apply(i, Unlock::new));
    }

    /** A dōjutsu needed to cast: e.g. {@code {"dojutsu": "sharingan", "stage": 2}} (must be active). */
    public record Requirement(String dojutsu, int stage) {
        public static final Codec<Requirement> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dojutsu").forGetter(Requirement::dojutsu),
                Codec.intRange(1, 10).optionalFieldOf("stage", 1).forGetter(Requirement::stage)
        ).apply(i, Requirement::new));
    }

    /**
     * Numbers the behaviors read. Each behavior uses the ones that make sense for it.
     *
     * @param count       how many projectiles/beams/strikes
     * @param spread      fan angle in degrees for several projectiles or beams
     * @param block       a block id whose look the jutsu takes (wood, ice, iron sand…)
     * @param effect      effects on whoever is hit (or on the caster, for buffs): {@code "slowness:1,weakness:0"}
     * @param effectTicks how long those effects last
     */
    public record Params(float damage, float chargeDamage, float speed, float radius, float range, float knockback,
                         float posture, int duration, int stun, int fireSeconds,
                         int count, float spread, String block, String effect, int effectTicks) {
        public static final Params DEFAULT = new Params(0, 0, 1, 1, 6, 0.5f, 0, 20, 0, 0, 1, 0, "", "", 60);
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
                Codec.INT.optionalFieldOf("fire_seconds", 0).forGetter(Params::fireSeconds),
                Codec.intRange(1, 32).optionalFieldOf("count", 1).forGetter(Params::count),
                Codec.FLOAT.optionalFieldOf("spread", 0f).forGetter(Params::spread),
                Codec.STRING.optionalFieldOf("block", "").forGetter(Params::block),
                Codec.STRING.optionalFieldOf("effect", "").forGetter(Params::effect),
                Codec.INT.optionalFieldOf("effect_ticks", 60).forGetter(Params::effectTicks)
        ).apply(i, Params::new));
    }
}

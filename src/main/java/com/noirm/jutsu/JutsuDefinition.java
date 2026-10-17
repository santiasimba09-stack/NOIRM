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
                              boolean starter, String rank, Params params, Optional<Unlock> unlock, Optional<Mode> mode) {

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
            Unlock.CODEC.optionalFieldOf("unlock").forGetter(JutsuDefinition::unlock),
            Mode.CODEC.optionalFieldOf("mode").forGetter(JutsuDefinition::mode)
    ).apply(i, JutsuDefinition::new));

    /**
     * A state you switch on (behavior {@code "mode"}), like Lightning Armor: it lasts until you
     * switch it off or run out of chakra, draining {@code drain} chakra per second.
     *
     * @param speed       extra movement speed (+0.6 = 60% faster)
     * @param perception  perception multiplier (opponents' wind-ups look slower)
     * @param damage      taijutsu damage multiplier
     * @param defense     damage taken multiplier (0.75 = 25% less)
     * @param actionSpeed how fast your own moves play (1.35 = 35% faster wind-ups and recoveries)
     * @param drain       chakra per second while it's on
     * @param healthDrain health per second while it's on (the Eight Gates burn the body, not chakra)
     * @param endHealth   the mode ends (and can't start) at or below this fraction of max health
     * @param gate        which tier of the Eight Gates this is (0 = not a gate); jutsu can require one
     * @param aura        the aura's colours, {@code ["#core", "#mid", "#edge", "#accent"]}; empty = the nature's
     */
    public record Mode(float speed, float perception, float damage, float defense, float actionSpeed, float drain,
                       float healthDrain, float endHealth, int gate, java.util.List<String> aura) {
        /** A mode with no effects (for a {@code "mode"} jutsu without a {@code mode} block). */
        public static final Mode NONE = new Mode(0, 1, 1, 1, 1, 1, 0, 0, 0, java.util.List.of());

        public static final Codec<Mode> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("speed", 0f).forGetter(Mode::speed),
                Codec.FLOAT.optionalFieldOf("perception", 1f).forGetter(Mode::perception),
                Codec.FLOAT.optionalFieldOf("damage", 1f).forGetter(Mode::damage),
                Codec.FLOAT.optionalFieldOf("defense", 1f).forGetter(Mode::defense),
                Codec.FLOAT.optionalFieldOf("action_speed", 1f).forGetter(Mode::actionSpeed),
                Codec.FLOAT.optionalFieldOf("drain", 1f).forGetter(Mode::drain),
                Codec.FLOAT.optionalFieldOf("health_drain", 0f).forGetter(Mode::healthDrain),
                Codec.floatRange(0, 1).optionalFieldOf("end_health", 0f).forGetter(Mode::endHealth),
                Codec.intRange(0, 8).optionalFieldOf("gate", 0).forGetter(Mode::gate),
                Codec.STRING.listOf().optionalFieldOf("aura", java.util.List.of()).forGetter(Mode::aura)
        ).apply(i, Mode::new));

        /** The aura's palette, or null to use the nature's. */
        public com.noirm.vfx.Vfx.Palette auraPalette() {
            if (aura.isEmpty()) {
                return null;
            }
            int[] c = new int[4];
            for (int k = 0; k < 4; k++) {
                String hex = aura.get(Math.min(k, aura.size() - 1));
                try {
                    c[k] = Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
                } catch (NumberFormatException e) {
                    c[k] = 0xFFFFFF;
                }
            }
            return new com.noirm.vfx.Vfx.Palette(c[0], c[1], c[2], c[3]);
        }
    }

    /**
     * How a jutsu is learned without a clan: {@code {"after": "noirm:katon/great_fireball",
     * "mastery": 2}} (when that jutsu reaches ★2), or {@code {"stat": "reserves", "value": 20}}
     * (when a trained stat gets there, e.g. chakra control for the Rasengan).
     */
    public record Unlock(Optional<net.minecraft.resources.ResourceLocation> afterJutsu, int mastery, Optional<String> stat, float value) {
        public static final Codec<Unlock> CODEC = RecordCodecBuilder.create(i -> i.group(
                net.minecraft.resources.ResourceLocation.CODEC.optionalFieldOf("after").forGetter(Unlock::afterJutsu),
                Codec.intRange(1, 5).optionalFieldOf("mastery", 2).forGetter(Unlock::mastery),
                Codec.STRING.optionalFieldOf("stat").forGetter(Unlock::stat),
                Codec.FLOAT.optionalFieldOf("value", 0f).forGetter(Unlock::value)
        ).apply(i, Unlock::new));

        /** The jutsu whose mastery unlocks this one, or null. */
        public net.minecraft.resources.ResourceLocation after() {
            return afterJutsu.orElse(null);
        }
    }

    /**
     * A state needed to cast: a dōjutsu, e.g. {@code {"dojutsu": "sharingan", "stage": 2}} (must be
     * active), the beast's {@code "cloak"}, or an open {@code "gate"} of at least that tier.
     */
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
     * @param homing      how hard projectiles steer toward their target each tick (0 = straight)
     */
    public record Params(float damage, float chargeDamage, float speed, float radius, float range, float knockback,
                         float posture, int duration, int stun, int fireSeconds,
                         int count, float spread, String block, String effect, int effectTicks, float homing) {
        public static final Params DEFAULT = new Params(0, 0, 1, 1, 6, 0.5f, 0, 20, 0, 0, 1, 0, "", "", 60, 0);
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
                Codec.INT.optionalFieldOf("effect_ticks", 60).forGetter(Params::effectTicks),
                Codec.FLOAT.optionalFieldOf("homing", 0f).forGetter(Params::homing)
        ).apply(i, Params::new));
    }
}

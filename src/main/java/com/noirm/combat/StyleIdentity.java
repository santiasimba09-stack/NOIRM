package com.noirm.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/**
 * How a style <em>plays</em> (not only how it looks): its footwork and speed, its guard, its dodge
 * and a few small mechanical hooks. Loaded from a style JSON's {@code "identity"} object (see
 * {@code docs/COMBAT_DESIGN.md}, "Fighting styles"); every number is optional and defaults to the
 * plain Academy behaviour, so the old styles play exactly as before.
 *
 * @param footwork  a label for the picker: {@code upright}, {@code low}, {@code wide}, {@code bladed},
 *                  {@code bouncing}, {@code rooted}
 * @param rhythm    a label for the picker: {@code steady}, {@code fast}, {@code heavy}, {@code kicks},
 *                  {@code grapple}, {@code irregular}…
 * @param moveSpeed movement speed in combat mode (×; 1 = normal): strafing, closing in, backing off
 * @param guard     what holding guard does (see {@link Guard})
 * @param dodge     what the dodge key does (see {@link Dodge})
 * @param strike    style-wide bonuses on hits (see {@link Strike})
 * @param feint     if present, pressing guard during your own wind-up cancels it: a feint (see {@link Feint})
 */
public record StyleIdentity(String footwork, String rhythm, float moveSpeed, Guard guard, Dodge dodge, Strike strike,
                            Optional<Feint> feint) {
    public static final StyleIdentity DEFAULT = new StyleIdentity("upright", "steady", 1f, Guard.DEFAULT, Dodge.DEFAULT,
            Strike.DEFAULT, Optional.empty());

    public static final Codec<StyleIdentity> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("footwork", "upright").forGetter(StyleIdentity::footwork),
            Codec.STRING.optionalFieldOf("rhythm", "steady").forGetter(StyleIdentity::rhythm),
            Codec.floatRange(0.3f, 2f).optionalFieldOf("move_speed", 1f).forGetter(StyleIdentity::moveSpeed),
            Guard.CODEC.optionalFieldOf("guard", Guard.DEFAULT).forGetter(StyleIdentity::guard),
            Dodge.CODEC.optionalFieldOf("dodge", Dodge.DEFAULT).forGetter(StyleIdentity::dodge),
            Strike.CODEC.optionalFieldOf("strike", Strike.DEFAULT).forGetter(StyleIdentity::strike),
            Feint.CODEC.optionalFieldOf("feint").forGetter(StyleIdentity::feint)
    ).apply(i, StyleIdentity::new));

    /**
     * The guard.
     *
     * @param type        {@code standard}, {@code high} (keeps more out, slower), {@code parry} (a wider
     *                    parry window), {@code evasive} (no block at all: pressing guard is a quick sway,
     *                    a short dodge), {@code armoured} (shrugs posture off but plods) or {@code counter}
     *                    (a parry answers at once with the style's counter move)
     * @param parryWindow the parry window (× {@link CombatSystem#PARRY_WINDOW})
     * @param keep        share of a blocked hit's damage that still gets through
     *                    ({@link CombatSystem#GUARD_DAMAGE_MULTIPLIER} by default)
     * @param posture     × posture a blocked hit builds
     * @param armour      share of ALL posture damage shrugged off (guarded or not): posture armour
     * @param speed       movement speed while guarding (× ; the default 0.5 is the old guard slow)
     */
    public record Guard(String type, float parryWindow, float keep, float posture, float armour, float speed) {
        public static final Guard DEFAULT = new Guard("standard", 1f, CombatSystem.GUARD_DAMAGE_MULTIPLIER, 1f, 0f, 0.5f);
        public static final Codec<Guard> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("type", "standard").forGetter(Guard::type),
                Codec.floatRange(0.2f, 4f).optionalFieldOf("parry_window", 1f).forGetter(Guard::parryWindow),
                Codec.floatRange(0f, 1f).optionalFieldOf("keep", CombatSystem.GUARD_DAMAGE_MULTIPLIER).forGetter(Guard::keep),
                Codec.floatRange(0f, 4f).optionalFieldOf("posture", 1f).forGetter(Guard::posture),
                Codec.floatRange(0f, 0.9f).optionalFieldOf("armour", 0f).forGetter(Guard::armour),
                Codec.floatRange(0.05f, 1.5f).optionalFieldOf("speed", 0.5f).forGetter(Guard::speed)
        ).apply(i, Guard::new));

        public boolean evasive() {
            return "evasive".equals(type);
        }

        public boolean counter() {
            return "counter".equals(type);
        }
    }

    /**
     * The dodge.
     *
     * @param kind       a label and the default animations: {@code roll} (rolls, side steps), {@code step}
     *                   (short steps), {@code hop} (a light hop), {@code slide} (low and long), {@code flip}
     *                   (back handsprings), {@code sway} (a lean, barely moving)
     * @param speed      how far it carries (× {@link CombatSystem#DODGE_SPEED})
     * @param ticks      how long it lasts ({@link CombatSystem#DODGE_TICKS} by default)
     * @param iframes    the invulnerable start ({@link CombatSystem#DODGE_IFRAMES})
     * @param perfect    a hit in this many first ticks is a perfect dodge ({@link CombatSystem#PERFECT_DODGE_TICKS})
     * @param stamina    × the stamina it costs ({@link CombatSystem#DODGE_STAMINA_COST})
     * @param lift       how high it hops off the ground (blocks/tick; 0.12 by default)
     * @param animations its animation per direction ({@code forward}, {@code back}, {@code left}, {@code right});
     *                   a direction it doesn't list uses the kind's default
     */
    public record Dodge(String kind, float speed, int ticks, int iframes, int perfect, float stamina, float lift,
                        Map<String, ResourceLocation> animations) {
        public static final Dodge DEFAULT = new Dodge("roll", 1f, CombatSystem.DODGE_TICKS, CombatSystem.DODGE_IFRAMES,
                CombatSystem.PERFECT_DODGE_TICKS, 1f, 0.12f, Map.of());
        public static final Codec<Dodge> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("kind", "roll").forGetter(Dodge::kind),
                Codec.floatRange(0f, 3f).optionalFieldOf("speed", 1f).forGetter(Dodge::speed),
                Codec.intRange(3, 30).optionalFieldOf("ticks", CombatSystem.DODGE_TICKS).forGetter(Dodge::ticks),
                Codec.intRange(0, 30).optionalFieldOf("iframes", CombatSystem.DODGE_IFRAMES).forGetter(Dodge::iframes),
                Codec.intRange(0, 15).optionalFieldOf("perfect", CombatSystem.PERFECT_DODGE_TICKS).forGetter(Dodge::perfect),
                Codec.floatRange(0f, 4f).optionalFieldOf("stamina", 1f).forGetter(Dodge::stamina),
                Codec.floatRange(0f, 1.5f).optionalFieldOf("lift", 0.12f).forGetter(Dodge::lift),
                Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC).optionalFieldOf("animations", Map.of()).forGetter(Dodge::animations)
        ).apply(i, Dodge::new));
    }

    /**
     * Style-wide bonuses on hits (a move's own {@code effects} stack on top).
     *
     * @param backstab     × damage when the hit lands from behind the target
     * @param aerial       × damage (and posture) when the attacker is off the ground
     * @param staminaDrain stamina knocked out of a player per hit (an NPC loses half as much posture instead)
     * @param momentum     + damage per hit already in the string (a whirlwind that builds), up to {@code momentumMax}
     * @param momentumMax  the most momentum adds
     * @param jitter       wind-ups vary by up to this many ticks either way: irregular, hard-to-read timing
     * @param read         × the chance an NPC reads (guards or dodges) this style's wind-ups
     */
    public record Strike(float backstab, float aerial, float staminaDrain, float momentum, float momentumMax, int jitter, float read) {
        public static final Strike DEFAULT = new Strike(1f, 1f, 0f, 0f, 0f, 0, 1f);
        public static final Codec<Strike> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0.5f, 4f).optionalFieldOf("backstab", 1f).forGetter(Strike::backstab),
                Codec.floatRange(0.5f, 4f).optionalFieldOf("aerial", 1f).forGetter(Strike::aerial),
                Codec.floatRange(0f, 100f).optionalFieldOf("stamina_drain", 0f).forGetter(Strike::staminaDrain),
                Codec.floatRange(0f, 1f).optionalFieldOf("momentum", 0f).forGetter(Strike::momentum),
                Codec.floatRange(0f, 2f).optionalFieldOf("momentum_max", 0f).forGetter(Strike::momentumMax),
                Codec.intRange(0, 6).optionalFieldOf("jitter", 0).forGetter(Strike::jitter),
                Codec.floatRange(0f, 3f).optionalFieldOf("read", 1f).forGetter(Strike::read)
        ).apply(i, Strike::new));
    }

    /**
     * A feint: guard pressed during your own wind-up cancels the attack at once (no recovery). For
     * {@code window} ticks after, your next attack winds up in at most {@code windup} ticks and, if
     * the enemy is guarding, slips {@code pierce} of its damage through and builds × {@code posture}.
     *
     * @param cost stamina a feint costs a player
     */
    public record Feint(float cost, int window, int windup, float pierce, float posture) {
        public static final Codec<Feint> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0f, 100f).optionalFieldOf("cost", 6f).forGetter(Feint::cost),
                Codec.intRange(1, 60).optionalFieldOf("window", 12).forGetter(Feint::window),
                Codec.intRange(0, 10).optionalFieldOf("windup", 1).forGetter(Feint::windup),
                Codec.floatRange(0f, 1f).optionalFieldOf("pierce", 0.5f).forGetter(Feint::pierce),
                Codec.floatRange(0f, 4f).optionalFieldOf("posture", 1.5f).forGetter(Feint::posture)
        ).apply(i, Feint::new));
    }
}

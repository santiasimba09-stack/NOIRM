package com.noirm.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/**
 * One attack, loaded from {@code data/<namespace>/combat/moves/<path>.json}. Times are in ticks
 * (20 per second).
 *
 * @param animation player animation to play (from {@code assets/<ns>/player_animation/})
 * @param timing    how long each phase lasts
 * @param hit       what happens to whoever is hit
 * @param next      combo routes: which move follows when this one is followed by each input
 */
public record CombatMove(Optional<ResourceLocation> animation, Timing timing, Hit hit, Map<AttackInput, ResourceLocation> next) {

    public static final Codec<CombatMove> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("animation").forGetter(CombatMove::animation),
            Timing.CODEC.fieldOf("timing").forGetter(CombatMove::timing),
            Hit.CODEC.fieldOf("hit").forGetter(CombatMove::hit),
            Codec.unboundedMap(AttackInput.CODEC, ResourceLocation.CODEC).optionalFieldOf("next", Map.of()).forGetter(CombatMove::next)
    ).apply(i, CombatMove::new));

    public int totalTicks() {
        return timing.windup() + timing.active() + timing.recovery();
    }

    /**
     * @param windup      telegraph before the hit lands (stretched by the opponent's perception)
     * @param active      ticks during which the attack can connect
     * @param recovery    ticks after the attack before you're free again
     * @param cancelAfter ticks into recovery after which the next attack (combo) may start
     */
    public record Timing(int windup, int active, int recovery, int cancelAfter) {
        public static final Codec<Timing> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 200).fieldOf("windup").forGetter(Timing::windup),
                Codec.intRange(1, 200).fieldOf("active").forGetter(Timing::active),
                Codec.intRange(0, 200).fieldOf("recovery").forGetter(Timing::recovery),
                Codec.intRange(0, 200).optionalFieldOf("cancel_after", 0).forGetter(Timing::cancelAfter)
        ).apply(i, Timing::new));
    }

    /**
     * @param damage    health damage before stat multipliers
     * @param posture   posture damage (how fast it breaks a guard)
     * @param range     reach in blocks
     * @param arc       width of the swing in degrees, centred on where the attacker faces
     * @param knockback horizontal push
     * @param lift      upward launch speed (launchers)
     * @param lunge     forward step the attacker takes when the hit comes out
     * @param hitstun   ticks the target is stunned (interrupts their attack)
     * @param hitstop   ticks both fighters freeze on impact (the "weight" of a hit)
     * @param shake     camera shake strength, 0–1
     */
    public record Hit(float damage, float posture, float range, float arc, float knockback, float lift,
                      float lunge, int hitstun, int hitstop, float shake) {
        public static final Codec<Hit> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 1000).fieldOf("damage").forGetter(Hit::damage),
                Codec.floatRange(0, 1000).optionalFieldOf("posture", 0f).forGetter(Hit::posture),
                Codec.floatRange(0, 16).optionalFieldOf("range", 2.5f).forGetter(Hit::range),
                Codec.floatRange(1, 360).optionalFieldOf("arc", 90f).forGetter(Hit::arc),
                Codec.floatRange(0, 5).optionalFieldOf("knockback", 0.2f).forGetter(Hit::knockback),
                Codec.floatRange(0, 3).optionalFieldOf("lift", 0f).forGetter(Hit::lift),
                Codec.floatRange(0, 3).optionalFieldOf("lunge", 0f).forGetter(Hit::lunge),
                Codec.intRange(0, 100).optionalFieldOf("hitstun", 6).forGetter(Hit::hitstun),
                Codec.intRange(0, 20).optionalFieldOf("hitstop", 2).forGetter(Hit::hitstop),
                Codec.floatRange(0, 1).optionalFieldOf("shake", 0.3f).forGetter(Hit::shake)
        ).apply(i, Hit::new));

        /** Heavy hits get weightier sounds and particles. */
        public boolean isHeavy() {
            return hitstop >= 4;
        }
    }
}

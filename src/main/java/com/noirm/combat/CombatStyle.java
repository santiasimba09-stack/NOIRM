package com.noirm.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.noirm.Noirm;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/**
 * A fighting style, loaded from {@code data/<namespace>/combat/styles/<path>.json}: the move each
 * input starts a combo with (the follow-ups live in each move's {@code next} routes), and what it
 * takes to fight that way.
 *
 * @param openers     the move each input opens with; {@code light}, {@code heavy} and
 *                    {@code launcher} are required, the directional ones fall back to them
 * @param taijutsu    taijutsu needed to use the style (0 = anyone)
 * @param byakugan    true if it needs the Byakugan (the Gentle Fist)
 * @param npcWeight   how often ninjas who may use it pick it (0 = never)
 * @param playable    false for styles only monsters use (the beasts')
 * @param stances     the style's guard stance per sub-stance ({@code neutral} is the base one); a
 *                    sub-stance the style doesn't list is played as neutral
 * @param counter     the fast counter a well-timed dodge opens (lands behind or beside the enemy);
 *                    styles without one use {@link #DEFAULT_COUNTER}
 */
public record CombatStyle(Map<AttackInput, ResourceLocation> openers, float taijutsu, boolean byakugan, int npcWeight, boolean playable,
                          Map<SubStance, Stance> stances, Optional<ResourceLocation> counter) {
    /** Everyone can grab: styles without their own grab use this one. */
    public static final ResourceLocation DEFAULT_GRAB = Noirm.id("clinch/grab");
    /** The dodge counter for styles without their own. */
    public static final ResourceLocation DEFAULT_COUNTER = Noirm.id("basic/counter_strike");

    public static final Codec<CombatStyle> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(AttackInput.CODEC, ResourceLocation.CODEC).fieldOf("openers").forGetter(CombatStyle::openers),
            Codec.floatRange(0, 100).optionalFieldOf("taijutsu", 0f).forGetter(CombatStyle::taijutsu),
            Codec.BOOL.optionalFieldOf("byakugan", false).forGetter(CombatStyle::byakugan),
            Codec.intRange(0, 100).optionalFieldOf("npc_weight", 1).forGetter(CombatStyle::npcWeight),
            Codec.BOOL.optionalFieldOf("playable", true).forGetter(CombatStyle::playable),
            Codec.unboundedMap(SubStance.CODEC, Stance.CODEC).optionalFieldOf("stances", Map.of()).forGetter(CombatStyle::stances),
            ResourceLocation.CODEC.optionalFieldOf("counter").forGetter(CombatStyle::counter)
    ).apply(i, CombatStyle::new));

    /**
     * One sub-stance's look.
     *
     * @param animation   its looping stance animation
     * @param switchTicks how fast the fighter snaps into it from another sub-stance
     */
    public record Stance(ResourceLocation animation, int switchTicks) {
        public static final Codec<Stance> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("animation").forGetter(Stance::animation),
                Codec.intRange(0, 20).optionalFieldOf("switch", 2).forGetter(Stance::switchTicks)
        ).apply(i, Stance::new));
    }

    public ResourceLocation opener(AttackInput input) {
        ResourceLocation id = openers.get(input);
        if (id == null && input.fallback() != null) {
            id = openers.get(input.fallback());
        }
        if (id == null && input == AttackInput.GRAB) {
            id = DEFAULT_GRAB;
        }
        return id;
    }

    /** The dodge counter this style throws. */
    public ResourceLocation counterMove() {
        return counter.orElse(DEFAULT_COUNTER);
    }

    /** True if the style has its own look for {@code sub} (neutral always counts). */
    public boolean has(SubStance sub) {
        return sub == SubStance.NEUTRAL || stances.containsKey(sub);
    }

    /** The stance for {@code sub}, falling back to neutral; null if the style lists none. */
    public Stance stance(SubStance sub) {
        Stance stance = stances.get(sub);
        return stance != null ? stance : stances.get(SubStance.NEUTRAL);
    }

    /** Needs a style its user's taijutsu and eyes allow. */
    public Optional<String> missingRequirement(float userTaijutsu, boolean hasByakugan) {
        if (byakugan && !hasByakugan) {
            return Optional.of("byakugan");
        }
        if (userTaijutsu < taijutsu) {
            return Optional.of("taijutsu");
        }
        return Optional.empty();
    }
}

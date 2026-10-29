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
 */
public record CombatStyle(Map<AttackInput, ResourceLocation> openers, float taijutsu, boolean byakugan, int npcWeight, boolean playable) {
    /** Everyone can grab: styles without their own grab use this one. */
    public static final ResourceLocation DEFAULT_GRAB = Noirm.id("clinch/grab");

    public static final Codec<CombatStyle> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(AttackInput.CODEC, ResourceLocation.CODEC).fieldOf("openers").forGetter(CombatStyle::openers),
            Codec.floatRange(0, 100).optionalFieldOf("taijutsu", 0f).forGetter(CombatStyle::taijutsu),
            Codec.BOOL.optionalFieldOf("byakugan", false).forGetter(CombatStyle::byakugan),
            Codec.intRange(0, 100).optionalFieldOf("npc_weight", 1).forGetter(CombatStyle::npcWeight),
            Codec.BOOL.optionalFieldOf("playable", true).forGetter(CombatStyle::playable)
    ).apply(i, CombatStyle::new));

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

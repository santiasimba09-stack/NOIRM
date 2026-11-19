package com.noirm.traits;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

import java.util.List;
import java.util.Map;

/**
 * A personal talent ({@code data/noirm/traits/<id>.json}): rolled per character, not per clan.
 * Clans make some likelier ({@code "talent_bias"} in the clan file), but two members of one clan
 * can still be very different. The same format describes a perk package for generated clans
 * ({@code data/noirm/clan_perks/<id>.json}), whose {@code talent_bias} passes on to the clan.
 *
 * @param kind      positive, mixed (a strength with a cost) or negative
 * @param weight    how often it's rolled, relative to the others
 * @param modifiers {@link Perk} key → amount
 * @param excludes  talents (or packages) that can't come with this one
 * @param talentBias packages only: talent id → × likelier for the clan's members
 */
public record Talent(Kind kind, int weight, Map<String, Float> modifiers, List<String> excludes, Map<String, Float> talentBias) {

    public enum Kind implements StringRepresentable {
        POSITIVE, MIXED, NEGATIVE;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** Chat/screen colour: gold, amber, red. */
        public int color() {
            return switch (this) {
                case POSITIVE -> 0x7FD3C8;
                case MIXED -> 0xE0B060;
                case NEGATIVE -> 0xE06A5A;
            };
        }
    }

    public static final Codec<Talent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.optionalFieldOf("kind", Kind.POSITIVE).forGetter(Talent::kind),
            Codec.intRange(0, 1000).optionalFieldOf("weight", 10).forGetter(Talent::weight),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("modifiers", Map.of()).forGetter(Talent::modifiers),
            Codec.STRING.listOf().optionalFieldOf("excludes", List.of()).forGetter(Talent::excludes),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("talent_bias", Map.of()).forGetter(Talent::talentBias)
    ).apply(i, Talent::new));

    public static String translationKey(String id) {
        return "talent.noirm." + id;
    }

    public static String descriptionKey(String id) {
        return "talent.noirm." + id + ".desc";
    }
}

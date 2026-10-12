package com.noirm.clan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A clan: canon ones come from {@code data/noirm/clans/*.json}, custom ones are generated per
 * world ({@link ClanGenerator}) and saved in {@link WorldClans}.
 *
 * @param natures      affinity per nature: {@link #PRIMARY_AFFINITY} or {@link #SECONDARY_AFFINITY}
 * @param kekkeiGenkai bloodline ability, e.g. {@code "sharingan"}
 * @param weight       how likely a canon clan is rolled, relative to other canon clans
 */
public record Clan(String id, String name, boolean canon, Map<Nature, Float> natures, List<ClanTrait> traits,
                   float speedBonus, float taijutsuBonus, Optional<String> kekkeiGenkai, AwakeningPath awakening, int weight) {

    /** A clan's main nature: much stronger and cheaper jutsu. */
    public static final float PRIMARY_AFFINITY = 1.5f;
    public static final float SECONDARY_AFFINITY = 1.0f;
    /** A nature learned through (very hard) training. */
    public static final float TRAINED_AFFINITY = 0.5f;
    /** Casting a copied jutsu of a nature you don't have (Sharingan only). */
    public static final float IMITATION_AFFINITY = 0.4f;

    public static final Codec<Clan> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Clan::id),
            Codec.STRING.fieldOf("name").forGetter(Clan::name),
            Codec.BOOL.optionalFieldOf("canon", false).forGetter(Clan::canon),
            Codec.unboundedMap(Nature.CODEC, Codec.FLOAT).fieldOf("natures").forGetter(Clan::natures),
            ClanTrait.CODEC.listOf().optionalFieldOf("traits", List.of()).forGetter(Clan::traits),
            Codec.FLOAT.optionalFieldOf("speed_bonus", 0f).forGetter(Clan::speedBonus),
            Codec.FLOAT.optionalFieldOf("taijutsu_bonus", 0f).forGetter(Clan::taijutsuBonus),
            Codec.STRING.optionalFieldOf("kekkei_genkai").forGetter(Clan::kekkeiGenkai),
            AwakeningPath.CODEC.optionalFieldOf("awakening", AwakeningPath.WILL).forGetter(Clan::awakening),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(Clan::weight)
    ).apply(i, Clan::new));

    public boolean hasSharingan() {
        return kekkeiGenkai.filter("sharingan"::equals).isPresent();
    }
}

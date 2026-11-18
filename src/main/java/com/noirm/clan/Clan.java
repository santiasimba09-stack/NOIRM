package com.noirm.clan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

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
 * @param perks        what the blood gives: {@code com.noirm.traits.Perk} key → amount (see docs/CLANS.md)
 * @param clanJutsu    the clan's secret techniques: members are born knowing them, and only members can use them
 * @param look         default hair and eye colours for new members
 * @param talentBias   personal talent id → how much likelier members are to roll it (×)
 * @param packages     generated clans: which perk packages ({@code data/noirm/clan_perks/}) made up {@code perks}
 * @param style        a fighting style the clan teaches its members ({@code data/noirm/combat/styles/}), if any
 */
public record Clan(String id, String name, boolean canon, Map<Nature, Float> natures, List<ClanTrait> traits,
                   float speedBonus, float taijutsuBonus, Optional<String> kekkeiGenkai, AwakeningPath awakening, int weight,
                   Map<String, Float> perks, List<ResourceLocation> clanJutsu, Optional<Look> look,
                   Map<String, Float> talentBias, List<String> packages, Optional<String> style) {

    /** A clan's main nature: much stronger and cheaper jutsu. */
    public static final float PRIMARY_AFFINITY = 1.5f;
    public static final float SECONDARY_AFFINITY = 1.0f;
    /** A nature learned through (very hard) training. */
    public static final float TRAINED_AFFINITY = 0.5f;
    /** Casting a copied jutsu of a nature you don't have (Sharingan only). */
    public static final float IMITATION_AFFINITY = 0.4f;

    /** Default colours of the clan's members ({@code "#rrggbb"}): the first hair colour is the creator's default. */
    public record Look(List<String> hair, Optional<String> eyes) {
        public static final Codec<Look> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().optionalFieldOf("hair", List.of()).forGetter(Look::hair),
                Codec.STRING.optionalFieldOf("eyes").forGetter(Look::eyes)
        ).apply(i, Look::new));

        /** {@code "#rrggbb"} as 0xRRGGBB, or -1. */
        public static int rgb(String hex) {
            try {
                return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
            } catch (RuntimeException e) {
                return -1;
            }
        }

        /** The creator's default hair colour (0xRRGGBB), or -1. */
        public int defaultHair() {
            return hair.isEmpty() ? -1 : rgb(hair.get(0));
        }

        /** The creator's default eye colour (0xRRGGBB), or -1. */
        public int defaultEyes() {
            return eyes.map(Look::rgb).orElse(-1);
        }
    }

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
            Codec.INT.optionalFieldOf("weight", 1).forGetter(Clan::weight),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("perks", Map.of()).forGetter(Clan::perks),
            ResourceLocation.CODEC.listOf().optionalFieldOf("clan_jutsu", List.of()).forGetter(Clan::clanJutsu),
            Look.CODEC.optionalFieldOf("look").forGetter(Clan::look),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("talent_bias", Map.of()).forGetter(Clan::talentBias),
            Codec.STRING.listOf().optionalFieldOf("packages", List.of()).forGetter(Clan::packages),
            Codec.STRING.optionalFieldOf("style").forGetter(Clan::style)
    ).apply(i, Clan::new));

    /** A clan without perks, clan jutsu or looks (tests, old code). */
    public Clan(String id, String name, boolean canon, Map<Nature, Float> natures, List<ClanTrait> traits,
                float speedBonus, float taijutsuBonus, Optional<String> kekkeiGenkai, AwakeningPath awakening, int weight) {
        this(id, name, canon, natures, traits, speedBonus, taijutsuBonus, kekkeiGenkai, awakening, weight,
                Map.of(), List.of(), Optional.empty(), Map.of(), List.of(), Optional.empty());
    }

    /** The same clan, marked canon or not. */
    public Clan withCanon(boolean canon) {
        return new Clan(id, name, canon, natures, traits, speedBonus, taijutsuBonus, kekkeiGenkai, awakening, weight,
                perks, clanJutsu, look, talentBias, packages, style);
    }

    /** The same clan with perks (generated clans, and generated clans saved before perks existed). */
    public Clan withPerks(Map<String, Float> perks, Map<String, Float> talentBias, List<String> packages) {
        return new Clan(id, name, canon, natures, traits, speedBonus, taijutsuBonus, kekkeiGenkai, awakening, weight,
                Map.copyOf(perks), clanJutsu, look, Map.copyOf(talentBias), List.copyOf(packages), style);
    }

    public boolean hasSharingan() {
        return kekkeiGenkai.filter("sharingan"::equals).isPresent();
    }

    public boolean hasByakugan() {
        return kekkeiGenkai.filter("byakugan"::equals).isPresent();
    }
}

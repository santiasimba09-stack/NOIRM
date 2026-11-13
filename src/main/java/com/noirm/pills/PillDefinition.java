package com.noirm.pills;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * A food or a pill, loaded from {@code data/<ns>/pills/<id>.json} or {@code data/<ns>/foods/<id>.json}
 * (see {@code docs/PILLS.md} for every field and number). Hunger and saturation of foods are the
 * item's own (in {@link PillItems}); this file holds what NOIRM adds on top:
 *
 * <pre>
 * {
 *   "item": "noirm:soldier_pill",
 *   "cooldown": 60,                 // seconds before another (pills)
 *   "toxicity": 35,                 // what it adds to the body's toxicity (pills)
 *   "instant": { "chakra": 0.6, "stamina": 1.0, "heal": 0, "cure": false },
 *   "effects": [ { "type": "stamina_regen", "amount": 2.0, "seconds": 30 } ],
 *   "crash":   [ { "type": "effect", "effect": "minecraft:slowness", "seconds": 30 } ],
 *   "recipe":  { "ingredients": [ { "item": "noirm:ironmillet", "count": 2 } ], "yield": 2,
 *                "difficulty": 5, "min_pharmacology": 1, "unlock": "starter", "xp": 12 }
 * }
 * </pre>
 * {@code "color": "#C23B22"} tints the swallow puff and the HUD line.
 * {@code instant.chakra} / {@code stamina} are shares of the maximum; {@code heal} is health
 * points; {@code cure} washes out poison, wither, nausea and hunger. The crash starts when the
 * longest effect ends.
 */
public record PillDefinition(ResourceLocation item, int cooldown, float toxicity, Instant instant, List<EffectSpec> effects,
                             List<EffectSpec> crash, Optional<Recipe> recipe, String color) {

    public static final Codec<PillDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(PillDefinition::item),
            Codec.intRange(0, 3600).optionalFieldOf("cooldown", 0).forGetter(PillDefinition::cooldown),
            Codec.floatRange(0, 100).optionalFieldOf("toxicity", 0f).forGetter(PillDefinition::toxicity),
            Instant.CODEC.optionalFieldOf("instant", Instant.NONE).forGetter(PillDefinition::instant),
            EffectSpec.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(PillDefinition::effects),
            EffectSpec.CODEC.listOf().optionalFieldOf("crash", List.of()).forGetter(PillDefinition::crash),
            Recipe.CODEC.optionalFieldOf("recipe").forGetter(PillDefinition::recipe),
            Codec.STRING.optionalFieldOf("color", "#E8E2D4").forGetter(PillDefinition::color)
    ).apply(i, PillDefinition::new));

    /** Its colour ({@code "color": "#RRGGBB"}), for the swallow puff and the HUD: 0xRRGGBB. */
    public int rgb() {
        try {
            return Integer.parseInt(color.startsWith("#") ? color.substring(1) : color, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xE8E2D4;
        }
    }

    /** The longest effect (ticks): when the crash starts. */
    public int effectTicks() {
        int longest = 0;
        for (EffectSpec e : effects) {
            longest = Math.max(longest, e.seconds() * 20);
        }
        return longest;
    }

    /** What happens at once. */
    public record Instant(float chakra, float stamina, float heal, boolean cure) {
        public static final Instant NONE = new Instant(0, 0, 0, false);
        public static final Codec<Instant> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 1).optionalFieldOf("chakra", 0f).forGetter(Instant::chakra),
                Codec.floatRange(0, 1).optionalFieldOf("stamina", 0f).forGetter(Instant::stamina),
                Codec.floatRange(0, 1000).optionalFieldOf("heal", 0f).forGetter(Instant::heal),
                Codec.BOOL.optionalFieldOf("cure", false).forGetter(Instant::cure)
        ).apply(i, Instant::new));
    }

    /**
     * One effect over time: a {@link Boost.Kind} ({@code "type"}), its amount, its length. For
     * {@code "effect"}, {@code effect} names a vanilla status effect and {@code amplifier} its level.
     */
    public record EffectSpec(String type, float amount, int seconds, Optional<ResourceLocation> effect, int amplifier) {
        public static final Codec<EffectSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("type").forGetter(EffectSpec::type),
                Codec.FLOAT.optionalFieldOf("amount", 0f).forGetter(EffectSpec::amount),
                Codec.intRange(1, 3600).fieldOf("seconds").forGetter(EffectSpec::seconds),
                ResourceLocation.CODEC.optionalFieldOf("effect").forGetter(EffectSpec::effect),
                Codec.intRange(0, 4).optionalFieldOf("amplifier", 0).forGetter(EffectSpec::amplifier)
        ).apply(i, EffectSpec::new));

        public Boost.Kind kind() {
            return Boost.Kind.byKey(type);
        }
    }

    /** One ingredient: an item and how many. */
    public record Ingredient(ResourceLocation item, int count) {
        public static final Codec<Ingredient> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(Ingredient::item),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Ingredient::count)
        ).apply(i, Ingredient::new));
    }

    /**
     * How a pill is brewed at the apothecary. {@code difficulty} is the pharmacology at which it
     * comes out "standard" about half the time; {@code min_pharmacology} is the least you need to try.
     * {@code unlock}: {@code "starter"} (everyone knows it), or {@code "perk:<perk>"} (a master's lesson
     * teaches it, e.g. Tsubaki's {@code pill_lore}); every non-starter recipe can also be learned from a
     * medical scroll. {@code xp} is the pharmacology XP a brew gives (a failed one gives a share).
     */
    public record Recipe(List<Ingredient> ingredients, int yield, float difficulty, float minPharmacology, String unlock, float xp) {
        public static final Codec<Recipe> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC.listOf().fieldOf("ingredients").forGetter(Recipe::ingredients),
                Codec.intRange(1, 16).optionalFieldOf("yield", 1).forGetter(Recipe::yield),
                Codec.floatRange(0, 100).optionalFieldOf("difficulty", 1f).forGetter(Recipe::difficulty),
                Codec.floatRange(0, 100).optionalFieldOf("min_pharmacology", 1f).forGetter(Recipe::minPharmacology),
                Codec.STRING.optionalFieldOf("unlock", "starter").forGetter(Recipe::unlock),
                Codec.floatRange(0, 1000).optionalFieldOf("xp", 10f).forGetter(Recipe::xp)
        ).apply(i, Recipe::new));

        public boolean starter() {
            return "starter".equals(unlock);
        }

        /** The master's perk that teaches it, or "" (starter or scroll-only). */
        public String perk() {
            return unlock.startsWith("perk:") ? unlock.substring(5) : "";
        }
    }
}

package com.noirm.pills;

import com.noirm.master.MasterPerks;
import com.noirm.player.ShinobiData;
import com.noirm.progress.PillTrigger;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The craft of pill-making (the rules; the block and screen are {@link ApothecaryBlock} and
 * {@link ApothecaryMenu}). You put a recipe's ingredients in the mortar and grind:
 * <ul>
 *   <li><b>Success</b>: {@value #BASE_SUCCESS} at the recipe's difficulty, ±{@value #SUCCESS_PER_POINT}
 *       per pharmacology point above or below it, between {@value #MIN_SUCCESS} and {@value #MAX_SUCCESS}.
 *       A failed brew uses the ingredients up and leaves one ruined pill.</li>
 *   <li><b>Grade</b>: a score of (pharmacology − difficulty) + a little for chakra control, ± a roll of
 *       {@value #GRADE_SPREAD}: below {@value #STANDARD_AT} crude, then standard, refined from
 *       {@value #REFINED_AT}, perfect from {@value #PERFECT_AT}.</li>
 *   <li><b>Yield</b>: the recipe's, plus one per {@value #YIELD_STEP} points above its difficulty (at most
 *       +{@value #MAX_EXTRA_YIELD}).</li>
 *   <li><b>XP</b>: the recipe's ({@code xp}) × 1.25 per grade above crude; a failure teaches
 *       {@value #FAIL_XP_SHARE} of it; far above a recipe's difficulty it teaches less.</li>
 * </ul>
 * Which recipes you can brew: starters, those a master's lesson taught you ({@code "unlock": "perk:…"}),
 * and those learned from medical scrolls, once your pharmacology reaches their {@code min_pharmacology}.
 */
public final class Apothecary {
    public static final float BASE_SUCCESS = 0.70f;
    public static final float SUCCESS_PER_POINT = 0.025f;
    public static final float MIN_SUCCESS = 0.25f;
    public static final float MAX_SUCCESS = 0.97f;
    public static final float GRADE_SPREAD = 10f;
    public static final float STANDARD_AT = -5f;
    public static final float REFINED_AT = 12f;
    public static final float PERFECT_AT = 30f;
    /** Chakra control steadies the hand: +this per CTL point above 10, at most {@link #MAX_CTL_BONUS}. */
    public static final float GRADE_PER_CTL = 0.1f;
    public static final float MAX_CTL_BONUS = 5f;
    public static final float YIELD_STEP = 20f;
    public static final int MAX_EXTRA_YIELD = 2;
    public static final float FAIL_XP_SHARE = 0.4f;
    public static final float XP_PER_GRADE = 0.25f;
    /** XP falls off past this many points above a recipe's difficulty, down to {@link #MIN_XP_SHARE} at +60. */
    public static final float XP_FALLOFF_FROM = 20f;
    public static final float XP_FALLOFF_RANGE = 40f;
    public static final float MIN_XP_SHARE = 0.2f;

    /** Why a recipe can't be brewed (or {@link #READY}). */
    public enum State { READY, LOW_SKILL, UNKNOWN }

    /** What came out of a brew. */
    public record Outcome(boolean success, Grade grade, int count, ResourceLocation pill, float xp) {}

    private Apothecary() {}

    // ---------------------------------------------------------------- what you know

    public static boolean knows(ShinobiData data, ResourceLocation id, PillDefinition.Recipe recipe) {
        if (recipe.starter() || data.pills().learned().contains(id)) {
            return true;
        }
        String perk = recipe.perk();
        return !perk.isEmpty() && MasterPerks.has(data, perk);
    }

    public static State state(ShinobiData data, ResourceLocation id, PillDefinition.Recipe recipe) {
        if (!knows(data, id, recipe)) {
            return State.UNKNOWN;
        }
        return data.pills().pharmacology() < recipe.minPharmacology() ? State.LOW_SKILL : State.READY;
    }

    // ---------------------------------------------------------------- the numbers

    public static float successChance(ShinobiData data, PillDefinition.Recipe recipe) {
        float edge = data.pills().pharmacology() - recipe.difficulty();
        return Mth.clamp(BASE_SUCCESS + SUCCESS_PER_POINT * edge, MIN_SUCCESS, MAX_SUCCESS);
    }

    /** The grade score before the roll. */
    public static float gradeScore(ShinobiData data, PillDefinition.Recipe recipe) {
        float ctl = Mth.clamp((data.getControl() - ShinobiData.DEFAULT_STAT) * GRADE_PER_CTL, 0f, MAX_CTL_BONUS);
        return data.pills().pharmacology() - recipe.difficulty() + ctl;
    }

    public static Grade gradeFor(float score) {
        if (score < STANDARD_AT) {
            return Grade.CRUDE;
        }
        if (score < REFINED_AT) {
            return Grade.STANDARD;
        }
        return score < PERFECT_AT ? Grade.REFINED : Grade.PERFECT;
    }

    /** The odds of each grade (crude, standard, refined, perfect) for a successful brew. */
    public static float[] gradeOdds(ShinobiData data, PillDefinition.Recipe recipe) {
        float center = gradeScore(data, recipe);
        float lo = center - GRADE_SPREAD, hi = center + GRADE_SPREAD;
        float[] cuts = {Float.NEGATIVE_INFINITY, STANDARD_AT, REFINED_AT, PERFECT_AT, Float.POSITIVE_INFINITY};
        float[] odds = new float[4];
        for (int i = 0; i < 4; i++) {
            float a = Math.max(lo, cuts[i]), b = Math.min(hi, cuts[i + 1]);
            odds[i] = Math.max(0f, b - a) / (hi - lo);
        }
        return odds;
    }

    public static int yieldOf(ShinobiData data, PillDefinition.Recipe recipe) {
        float edge = data.pills().pharmacology() - recipe.difficulty();
        return recipe.yield() + Mth.clamp((int) Math.floor(Math.max(0f, edge) / YIELD_STEP), 0, MAX_EXTRA_YIELD);
    }

    /** The XP a brew gives at {@code grade} (null = failed). */
    public static float xp(ShinobiData data, PillDefinition.Recipe recipe, Grade grade) {
        float edge = data.pills().pharmacology() - recipe.difficulty();
        float falloff = Mth.clamp(1f - (edge - XP_FALLOFF_FROM) / XP_FALLOFF_RANGE, MIN_XP_SHARE, 1f);
        float base = recipe.xp() * falloff;
        return grade == null ? base * FAIL_XP_SHARE : base * (1f + XP_PER_GRADE * grade.ordinal());
    }

    // ---------------------------------------------------------------- the mortar

    /** Whether the items in {@code inputs} (slots 0..n-1) hold the recipe's ingredients, and nothing else. */
    public static boolean matches(Container inputs, int slots, PillDefinition.Recipe recipe) {
        Map<Item, Integer> have = new HashMap<>();
        for (int i = 0; i < slots; i++) {
            ItemStack s = inputs.getItem(i);
            if (!s.isEmpty()) {
                have.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        }
        if (have.isEmpty()) {
            return false;
        }
        int matched = 0;
        for (PillDefinition.Ingredient ing : recipe.ingredients()) {
            Item item = ForgeRegistries.ITEMS.getValue(ing.item());
            if (item == null || have.getOrDefault(item, 0) < ing.count()) {
                return false;
            }
            matched++;
        }
        return matched == have.size();
    }

    /** The recipe the mortar holds: {@code preferred} if it matches, else the first recipe you can brew that does. */
    public static ResourceLocation find(ShinobiData data, Container inputs, int slots, ResourceLocation preferred) {
        List<Map.Entry<ResourceLocation, PillDefinition>> all = PillRegistry.recipes();
        if (preferred != null) {
            PillDefinition def = PillRegistry.pill(preferred);
            if (def != null && def.recipe().isPresent() && matches(inputs, slots, def.recipe().get())) {
                return preferred;
            }
        }
        for (var e : all) {
            if (matches(inputs, slots, e.getValue().recipe().get())) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Takes one brew's ingredients out of the mortar. */
    static void consume(Container inputs, int slots, PillDefinition.Recipe recipe) {
        for (PillDefinition.Ingredient ing : recipe.ingredients()) {
            Item item = ForgeRegistries.ITEMS.getValue(ing.item());
            int need = ing.count();
            for (int i = 0; i < slots && need > 0; i++) {
                ItemStack s = inputs.getItem(i);
                if (s.is(item)) {
                    int take = Math.min(need, s.getCount());
                    s.shrink(take);
                    need -= take;
                    if (s.isEmpty()) {
                        inputs.setItem(i, ItemStack.EMPTY);
                    }
                }
            }
        }
        inputs.setChanged();
    }

    /**
     * Brews recipe {@code id} once: rolls success and grade, uses up the ingredients, grants XP and
     * returns what came out (the caller puts the pills in the output slot). Public so tests and
     * commands can roll brews with a chosen random source.
     */
    public static Outcome roll(Player player, ShinobiData data, ResourceLocation id, PillDefinition.Recipe recipe, RandomSource random) {
        PillDefinition def = PillRegistry.pill(id);
        boolean success = random.nextFloat() < successChance(data, recipe);
        Grade grade = success ? gradeFor(gradeScore(data, recipe) + (random.nextFloat() * 2f - 1f) * GRADE_SPREAD) : null;
        float xp = xp(data, recipe, grade);
        Pharmacology.grant(player, data, xp);
        if (success) {
            data.pills().addBrewed(grade.ordinal());
            if (player != null) {
                PillTrigger.brewed(player, id.getPath(), grade.ordinal());
            }
        }
        data.pills().markDirty();
        return new Outcome(success, grade, success ? yieldOf(data, recipe) : 1, def == null ? id : def.item(), xp);
    }

    /** The stack an outcome makes: graded pills, or one ruined pill. */
    public static ItemStack result(Outcome outcome) {
        if (!outcome.success()) {
            return new ItemStack(PillItems.RUINED_PILL.get());
        }
        Item item = ForgeRegistries.ITEMS.getValue(outcome.pill());
        return item == null ? ItemStack.EMPTY : outcome.grade().apply(new ItemStack(item, outcome.count()));
    }

    /** The chat line after a brew. */
    static Component message(Outcome outcome) {
        if (!outcome.success()) {
            return Component.translatable("message.noirm.apothecary.failed").withStyle(ChatFormatting.DARK_RED);
        }
        Item item = ForgeRegistries.ITEMS.getValue(outcome.pill());
        return Component.translatable("message.noirm.apothecary.brewed", outcome.count(),
                Component.translatable(outcome.grade().translationKey()).withStyle(outcome.grade().format),
                item == null ? Component.literal(outcome.pill().toString()) : Component.translatable(item.getDescriptionId()))
                .withStyle(ChatFormatting.GREEN);
    }
}

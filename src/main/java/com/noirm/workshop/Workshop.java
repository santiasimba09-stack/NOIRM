package com.noirm.workshop;

import com.noirm.arts.ArtsData;
import com.noirm.arts.ChakraShape;
import com.noirm.arts.ShapeTraining;
import com.noirm.clan.Nature;
import com.noirm.clan.NatureSystem;
import com.noirm.jutsu.CastType;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.JutsuSeals;
import com.noirm.nature.JutsuLearning;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.vfx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * <b>The jutsu workshop</b>: making your own jutsu. Pick a nature you own, a shape you've trained
 * ({@link ShapeTraining}) and four trade-offs (size, speed, power, range, each 1–5), and name it.
 * Everything else is worked out here from those choices, the same way on the server and the screen:
 * <ul>
 *   <li><b>Weight</b> = size + speed + 2 × power + range (5–25): how big a jutsu it is. It sets the
 *       <b>rank</b> ({@link #RANK_WEIGHT}), the <b>chakra cost</b> (steeply: {@link #COST_BASE} ×
 *       (weight / 5)^{@link #COST_EXPONENT}, and much more above A rank), the <b>cooldown</b> and the
 *       number of <b>hand seals</b>.</li>
 *   <li><b>Stat requirements:</b> your <b>budget</b> = {@link #BUDGET_BASE} + Reserves, CTL and your best
 *       mastery of the nature ({@link #BUDGET_PER_RESERVES}, {@link #BUDGET_PER_CTL},
 *       {@link #BUDGET_PER_MASTERY}). A jutsu heavier than your budget can't be made or cast.</li>
 *   <li><b>Shape stage:</b> a shape at stage 1/2/3 allows up to C/B/A rank; S rank needs that shape
 *       fused with the nature.</li>
 *   <li><b>Power:</b> a creation can equal or beat canon jutsu. Strong ones cost a lot of chakra and
 *       <b>backlash</b>: past weight {@link #BACKLASH_FROM}, each cast tears {@link #BACKLASH_PER_POINT}
 *       of your max health per point (never the last point).</li>
 *   <li><b>Practice:</b> a new creation <b>misfires</b> often ({@link #MISFIRE}, by mastery ★0–5): the
 *       chakra is spent, nothing comes out, but you learn from it.</li>
 *   <li><b>No forms:</b> armour, a chakra beast you wear or an avatar is never a shape.</li>
 * </ul>
 * A creation is saved per player as its design ({@link CreationBook}) and turned into a normal
 * {@link JutsuDefinition} at runtime ({@link WorkshopRegistry}), cast with an existing behaviour.
 * Share it by teaching a crew mate or writing a scroll; NPC ninjas never get creations.
 */
public final class Workshop {
    // ---------------------------------------------------------------- tuning
    /** Slider values 1..5 → multipliers. */
    public static final float[] SIZE_SCALE = {0.6f, 0.8f, 1.0f, 1.3f, 1.7f};
    public static final float[] SPEED_SCALE = {0.6f, 0.8f, 1.0f, 1.25f, 1.5f};
    public static final float[] POWER_SCALE = {0.6f, 1.0f, 1.4f, 1.9f, 2.5f};
    public static final float[] RANGE_SCALE = {0.6f, 0.8f, 1.0f, 1.3f, 1.6f};
    /** The ranks, and the highest weight of each (D up to 7, C up to 10, B 14, A 19, S 25). */
    public static final String[] RANKS = {"D", "C", "B", "A", "S"};
    public static final int[] RANK_WEIGHT = {7, 10, 14, 19, 25};
    /** Chakra cost = base × (weight / 5)^exponent… */
    public static final float COST_BASE = 6f;
    public static final float COST_EXPONENT = 1.45f;
    /** …and above A rank, +25% per point of weight past 19 (the big cost of a jutsu past canon). */
    public static final int OVERCHARGE_FROM = 19;
    public static final float OVERCHARGE_PER_POINT = 0.25f;
    /** Cooldown (ticks) = (base + per weight²) × (1.15 − 0.075 × speed). */
    public static final float COOLDOWN_BASE = 40f;
    public static final float COOLDOWN_PER_WEIGHT_SQ = 1.2f;
    /** Backlash: from this weight up, each cast hurts the caster this share of max health per point. */
    public static final int BACKLASH_FROM = 17;
    public static final float BACKLASH_PER_POINT = 0.035f;
    /** Seals: 1 + weight / 4, between 2 and 7. */
    public static final int SEALS_MIN = 2;
    public static final int SEALS_MAX = 7;
    /** Budget = base + per Reserves point above 10 + per CTL point above 10 + per ★ of your best jutsu of the nature. */
    public static final float BUDGET_BASE = 6f;
    public static final float BUDGET_PER_RESERVES = 0.15f;
    public static final float BUDGET_PER_CTL = 0.2f;
    public static final float BUDGET_PER_MASTERY = 1.5f;
    /** The heaviest jutsu a shape allows at stage 0..3 (C at 1, B at 2, A at 3); fused with the nature: S. */
    public static final int[] SHAPE_CAP = {0, 10, 14, 19};
    public static final int FUSED_CAP = 25;
    /** Misfire chance by mastery ★0..★5. */
    public static final float[] MISFIRE = {0.55f, 0.40f, 0.25f, 0.12f, 0.05f, 0f};
    /** A misfire still starts this share of the cooldown. */
    public static final float MISFIRE_COOLDOWN = 0.5f;
    /** How many creations of your own you can keep, and in all (with learned ones). */
    public static final int MAX_OWN = 12;
    public static final int MAX_TOTAL = 24;
    /** Writing a scroll or teaching a crew mate needs the creation at this many ★. */
    public static final int WRITE_MASTERY = 2;
    public static final int TEACH_MASTERY = 2;
    /** Writing a scroll costs this share of the jutsu's chakra cost (at least {@link #WRITE_CHAKRA_MIN}). */
    public static final float WRITE_CHAKRA_SHARE = 0.5f;
    public static final float WRITE_CHAKRA_MIN = 10f;

    /** The twelve seals, for building sequences. */
    public static final List<String> ZODIAC = List.of("rat", "ox", "tiger", "hare", "dragon", "snake", "horse", "ram", "monkey", "bird", "dog", "boar");

    /** Tests: a fixed misfire roll (0..1), or negative for a real one. */
    public static float testRoll = -1f;

    private Workshop() {}

    // ================================================================ the numbers

    /** Everything a design comes out as. */
    public record Stats(int weight, String rank, float cost, int cooldown, List<String> seals, CastType cast, String behavior,
                        JutsuDefinition.Params params, int minCharge, int maxCharge, float backlash, float damage) {
        public int rankIndex() {
            return java.util.Arrays.asList(RANKS).indexOf(rank);
        }
    }

    public static int weight(int size, int speed, int power, int range) {
        return size + speed + 2 * power + range;
    }

    public static int weight(JutsuDesign d) {
        return weight(d.size(), d.speed(), d.power(), d.range());
    }

    public static String rank(int weight) {
        for (int i = 0; i < RANKS.length; i++) {
            if (weight <= RANK_WEIGHT[i]) {
                return RANKS[i];
            }
        }
        return "S";
    }

    public static float cost(int weight) {
        float cost = COST_BASE * (float) Math.pow(weight / 5f, COST_EXPONENT);
        if (weight > OVERCHARGE_FROM) {
            cost *= 1f + OVERCHARGE_PER_POINT * (weight - OVERCHARGE_FROM);
        }
        return Math.round(cost * 2f) / 2f;
    }

    public static int cooldown(int weight, int speed) {
        return Math.round((COOLDOWN_BASE + COOLDOWN_PER_WEIGHT_SQ * weight * weight) * (1.15f - 0.075f * speed));
    }

    /** Share of max health a cast tears from its caster (0 for light jutsu). */
    public static float backlash(int weight) {
        return Math.max(0, weight - BACKLASH_FROM) * BACKLASH_PER_POINT;
    }

    /** The seal sequence: the nature's opening seal, seals picked from the choices, the shape's closing seal. */
    public static List<String> seals(JutsuDesign d) {
        int count = Math.max(SEALS_MIN, Math.min(SEALS_MAX, 1 + weight(d) / 4));
        List<String> out = new ArrayList<>();
        out.add(openingSeal(d.nature()));
        int h = 17;
        h = h * 31 + d.nature().ordinal();
        h = h * 31 + d.shape().ordinal();
        h = h * 31 + d.size();
        h = h * 31 + d.speed();
        h = h * 31 + d.power();
        h = h * 31 + d.range();
        String closing = closingSeal(d.shape());
        while (out.size() < count - 1) {
            h = h * 1103515245 + 12345;
            String next = ZODIAC.get(Math.floorMod(h >>> 8, ZODIAC.size()));
            if (!next.equals(out.get(out.size() - 1)) && !next.equals(closing)) {
                out.add(next);
            }
        }
        out.add(closing.equals(out.get(out.size() - 1)) ? "clone".equals(closing) ? "ram" : "clone" : closing);
        return out;
    }

    static String openingSeal(Nature nature) {
        return switch (nature) {
            case FIRE -> "tiger";
            case WATER -> "ox";
            case WIND -> "dragon";
            case EARTH -> "boar";
            case LIGHTNING -> "monkey";
            case WOOD -> "snake";
            case ICE -> "hare";
            case LAVA -> "horse";
            case STORM -> "bird";
            case BOIL -> "dog";
            case EXPLOSION -> "ram";
            case MAGNET -> "rat";
            case SCORCH -> "horse";
            case DUST -> "rat";
            case YIN -> "snake";
            case YANG -> "boar";
            case YIN_YANG -> "ram";
        };
    }

    static String closingSeal(ChakraShape shape) {
        return switch (shape) {
            case BALL -> "ram";
            case BLADE -> "dog";
            case SPEAR -> "monkey";
            case WAVE -> "ox";
            case WALL -> "boar";
            case BEAST -> "dragon";
            case FIELD -> "bird";
            case BIND -> "rat";
        };
    }

    /** Works out the whole jutsu from a design. */
    public static Stats stats(JutsuDesign d) {
        int w = weight(d);
        float sz = SIZE_SCALE[d.size() - 1];
        float sp = SPEED_SCALE[d.speed() - 1];
        float pw = POWER_SCALE[d.power() - 1];
        float rg = RANGE_SCALE[d.range() - 1];
        ChakraShape shape = d.shape();
        Nature nature = d.nature();
        float damage = shape.damage() * pw;
        boolean fire = nature == Nature.FIRE || nature == Nature.LAVA || nature == Nature.SCORCH || nature == Nature.EXPLOSION;
        int fireSeconds = fire ? 2 + d.power() : 0;
        String effect = switch (nature) {
            case ICE -> "slowness:1";
            case MAGNET, STORM -> "slowness:0";
            case BOIL, DUST -> "weakness:0";
            case YIN -> "blindness:0";
            case WOOD -> "slowness:2";
            default -> "";
        };
        int natureStun = nature == Nature.LIGHTNING || nature == Nature.STORM ? 6 : 0;
        float extraKnock = nature == Nature.WIND || nature == Nature.EXPLOSION ? 0.5f : nature == Nature.WATER ? 0.3f : 0f;
        float posture = 15f + 5f * d.power() + (nature == Nature.EARTH ? 10f : 0f);
        CastType cast = CastType.TAP;
        int minCharge = 0;
        int maxCharge = 20;
        // Params(damage, chargeDamage, speed, radius, range, knockback, posture, duration, stun, fireSeconds,
        //        count, spread, block, effect, effectTicks, homing, look)
        JutsuDefinition.Params params = switch (shape) {
            case BALL -> {
                cast = CastType.CHARGE;
                minCharge = Math.max(4, 14 - 2 * d.speed());
                maxCharge = 20 + 4 * d.size() + 4 * (5 - d.speed());
                damage *= 1.4f;
                yield new JutsuDefinition.Params(damage, 0, 1, shape.radius() * sz, shape.range() * rg, 1.2f + 0.4f * d.power() + extraKnock,
                        posture, 20, natureStun, fireSeconds, 1, 0, "", effect, 60, 0, "");
            }
            case BLADE -> new JutsuDefinition.Params(damage, 0, shape.speed() * sp, shape.radius() * sz, shape.range() * rg,
                    0.3f + 0.1f * d.power() + extraKnock, posture, 20, natureStun, fireSeconds, d.size() >= 4 ? 3 : 1, 24, "", effect, 60, 0, "");
            case SPEAR -> new JutsuDefinition.Params(damage, 0, 1, shape.radius() * sz, shape.range() * rg, 0.4f + extraKnock,
                    posture, 20, (d.power() >= 4 ? 10 : 0) + natureStun, fireSeconds, d.size() >= 4 ? 3 : 1, 10, "", effect, 60, 0, "");
            case WAVE -> new JutsuDefinition.Params(damage, 0, shape.speed() * sp, shape.radius() * sz, shape.range() * rg,
                    1f + 0.3f * d.power() + extraKnock, posture, 20, natureStun, fireSeconds, 1, 0, "", effect, 60, 0, "");
            case WALL -> new JutsuDefinition.Params(2f * pw, 0, 1, shape.radius() * sz, 0, 1f + 0.2f * d.power() + extraKnock,
                    10f + 3f * d.power(), Math.round(60 + 30 * rg * 2), 0, fireSeconds, 1, 0, "", "", 60, 0, "");
            case BEAST -> new JutsuDefinition.Params(damage, 0, shape.speed() * sp, shape.radius() * sz, shape.range() * rg,
                    1f + 0.2f * d.power() + extraKnock, posture + 10f, 20, natureStun, fireSeconds, 1, 0, "", effect, 60,
                    0.06f + 0.03f * d.speed(), "dragon");
            case FIELD -> new JutsuDefinition.Params(damage, 0, 1, shape.radius() * (sz + rg) / 2f, 0, 0.8f + 0.2f * d.power() + extraKnock,
                    posture + 10f, 20, 8 + natureStun, fireSeconds, 1, 0, "", effect, 60, 0, "");
            case BIND -> new JutsuDefinition.Params(damage, 0, 1, 1, shape.range() * rg, 0, posture, 20,
                    Math.round(30 * sz) + natureStun, fireSeconds, 1, 0, "", effect, Math.round(40 * sz), 0, "");
        };
        return new Stats(w, rank(w), cost(w), cooldown(w, d.speed()), seals(d), cast, shape.behavior(), params, minCharge, maxCharge,
                backlash(w), damage);
    }

    /** The jutsu as the rest of the game sees it. */
    public static JutsuDefinition definition(JutsuDesign d) {
        Stats s = stats(d);
        return new JutsuDefinition(Optional.of(d.nature()), s.cast(), s.behavior(), s.cost(), s.cooldown(), s.minCharge(), s.maxCharge(),
                60, Optional.empty(), false, s.rank(), s.params(), Optional.empty(), Optional.empty(), false);
    }

    /** Never a form: no shape casts with a form's behaviour. */
    public static boolean isForm(String shapeName) {
        return ChakraShape.isForm(shapeName);
    }

    // ================================================================ stat requirements

    /** How heavy a jutsu of {@code nature} this player can make and cast. */
    public static float budget(float reserves, float control, int natureMastery) {
        return BUDGET_BASE + BUDGET_PER_RESERVES * Math.max(0f, reserves - ShinobiData.DEFAULT_STAT)
                + BUDGET_PER_CTL * Math.max(0f, control - ShinobiData.DEFAULT_STAT) + BUDGET_PER_MASTERY * natureMastery;
    }

    public static float budget(ShinobiData data, Nature nature) {
        return budget(data.getReserves(), data.getControl(), NatureSystem.bestMastery(data).getOrDefault(nature, 0));
    }

    /** The heaviest jutsu a shape allows with this nature: by its stage, or S when the two are fused. */
    public static int shapeCap(int stage, boolean fused) {
        return fused && stage >= ShapeTraining.STAGES ? FUSED_CAP : SHAPE_CAP[Math.max(0, Math.min(SHAPE_CAP.length - 1, stage))];
    }

    /** What the rules look at (the same on the server and the screen). */
    public record Limits(Function<Nature, Boolean> owns, Function<ChakraShape, Integer> stage, BiPredicate<ChakraShape, Nature> fused,
                         Function<Nature, Float> budget, int ownCount) {}

    public static Limits limits(Player player, ShinobiData data) {
        ArtsData arts = data.arts();
        Map<Nature, Integer> best = NatureSystem.bestMastery(data);
        return new Limits(n -> data.identity().affinity(n) > 0f, arts::stage, arts::fused,
                n -> budget(data.getReserves(), data.getControl(), best.getOrDefault(n, 0)),
                data.creations().ownCount(player.getUUID()));
    }

    /** Why a design can't be made (a message), or null if it can. {@code shapeName} may be anything the player typed. */
    public static Component problem(Limits limits, String name, Nature nature, String shapeName, int size, int speed, int power, int range) {
        if (isForm(shapeName)) {
            return Component.translatable("message.noirm.workshop.why.form");
        }
        ChakraShape shape = ChakraShape.byKey(shapeName);
        if (shape == null) {
            return Component.translatable("message.noirm.workshop.why.shape_unknown", shapeName == null ? "" : shapeName);
        }
        if (nature == null || !limits.owns().apply(nature)) {
            return Component.translatable("message.noirm.workshop.why.nature");
        }
        if (JutsuDesign.cleanName(name).isEmpty()) {
            return Component.translatable("message.noirm.workshop.why.name");
        }
        int stage = limits.stage().apply(shape);
        if (stage <= 0) {
            return Component.translatable("message.noirm.workshop.why.shape_untrained", Component.translatable(shape.translationKey()));
        }
        int w = weight(clampSlider(size), clampSlider(speed), clampSlider(power), clampSlider(range));
        int cap = shapeCap(stage, limits.fused().test(shape, nature));
        if (w > cap) {
            return Component.translatable(cap >= SHAPE_CAP[ShapeTraining.STAGES] ? "message.noirm.workshop.why.needs_fusion" : "message.noirm.workshop.why.shape_cap",
                    Component.translatable(shape.translationKey()), rank(cap), rank(w), Component.translatable(nature.translationKey()));
        }
        float budget = limits.budget().apply(nature);
        if (w > budget) {
            return Component.translatable("message.noirm.workshop.why.budget", w, String.format(Locale.ROOT, "%.1f", budget));
        }
        if (limits.ownCount() >= MAX_OWN) {
            return Component.translatable("message.noirm.workshop.why.full", MAX_OWN);
        }
        return null;
    }

    private static int clampSlider(int v) {
        return Math.max(JutsuDesign.MIN, Math.min(JutsuDesign.MAX, v));
    }

    /** What making a jutsu gave: the design, or why not. */
    public record Made(JutsuDesign design, Component error) {}

    /** Makes a jutsu: checks the rules, saves the design, learns it and puts it in a free slot. */
    public static Made create(Player player, ShinobiData data, String name, Nature nature, String shapeName, int size, int speed,
                              int power, int range) {
        Component why = problem(limits(player, data), name, nature, shapeName, size, speed, power, range);
        if (why != null) {
            player.displayClientMessage(why.copy().withStyle(ChatFormatting.GRAY), false);
            return new Made(null, why);
        }
        JutsuDesign design = new JutsuDesign(UUID.randomUUID(), name, nature, ChakraShape.byKey(shapeName), size, speed, power, range,
                player.getUUID(), player.getGameProfile().getName());
        learnDesign(data, design);
        Stats s = stats(design);
        player.sendSystemMessage(Component.translatable("message.noirm.workshop.created", design.name(), s.rank(),
                Math.round(MISFIRE[0] * 100)).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.1f);
        return new Made(design, null);
    }

    /** Adds a design to the player's book and jutsu (no checks). */
    public static void learnDesign(ShinobiData data, JutsuDesign design) {
        WorkshopRegistry.register(design);
        data.creations().add(design);
        ResourceLocation id = design.jutsuId();
        data.jutsu().learn(id, false);
        data.jutsu().equipIfRoom(id);
        data.markPowersDirty();
    }

    /** Forgets one of your creations (scrolls already written still teach it). */
    public static boolean delete(Player player, ShinobiData data, ResourceLocation id) {
        JutsuDesign design = data.creations().get(id);
        if (design == null) {
            return false;
        }
        data.creations().remove(id);
        data.jutsu().forget(id);
        data.markPowersDirty();
        player.displayClientMessage(Component.translatable("message.noirm.workshop.deleted", design.name()).withStyle(ChatFormatting.GRAY), false);
        return true;
    }

    // ================================================================ casting

    /** Why this caster can't cast a creation (too heavy for their stats), or null. Players only; crew mates were taught it. */
    public static Component castBlocker(LivingEntity caster, ResourceLocation id) {
        if (!(caster instanceof Player player) || !JutsuDesign.isCreation(id)) {
            return null;
        }
        JutsuDesign design = WorkshopRegistry.design(id);
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (design == null || data == null) {
            return null;
        }
        float budget = budget(data, design.nature());
        int w = weight(design);
        return w > budget ? Component.translatable("message.noirm.jutsu.too_demanding", w, String.format(Locale.ROOT, "%.1f", budget)) : null;
    }

    /** The misfire chance for this caster's mastery of a creation. */
    public static float misfireChance(int mastery) {
        return MISFIRE[Math.max(0, Math.min(MISFIRE.length - 1, mastery))];
    }

    /** Whether this cast of a creation misfires (rolled now). */
    public static boolean misfires(LivingEntity caster, ResourceLocation id) {
        if (!JutsuDesign.isCreation(id)) {
            return false;
        }
        int mastery = 0;
        if (caster instanceof Player player) {
            mastery = ShinobiCapability.get(player).map(d -> d.jutsu().masteryLevel(id)).orElse(0);
        } else if (caster instanceof com.noirm.entity.SparringNinja ninja) {
            mastery = ninja.jutsuMastery(id);
        }
        float roll = testRoll >= 0f ? testRoll : caster.getRandom().nextFloat();
        return roll < misfireChance(mastery);
    }

    /** A misfire: the chakra is gone, a sputter of the nature and smoke, nothing else. */
    public static void misfire(LivingEntity caster, ResourceLocation id, JutsuDefinition def) {
        Vec3 hands = caster.getEyePosition().add(caster.getViewVector(1f).scale(0.7)).add(0, -0.4, 0);
        if (caster.level() instanceof ServerLevel level) {
            Vfx.Palette p = Vfx.palette(def.nature().orElse(null));
            Vfx.burst(level, Vfx.glow(p.core(), p.mid(), 0.3f, 0.05f, 6), hands, 5, 0.25, 0.08);
            Vfx.burst(level, Vfx.smoke(0x202020, 0.5f, 30), hands, 8, 0.3, 0.03);
        }
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.3f);
        if (caster instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.noirm.workshop.misfire", WorkshopRegistry.nameOr(id, "?")), true);
        }
    }

    /** After a creation goes off: the backlash of a strong one. Returns the health it took. */
    public static float backlash(LivingEntity caster, ResourceLocation id) {
        JutsuDesign design = WorkshopRegistry.design(id);
        if (design == null || (caster instanceof Player p && p.getAbilities().instabuild)) {
            return 0f;
        }
        float share = backlash(weight(design));
        if (share <= 0f) {
            return 0f;
        }
        float hurt = Math.min(caster.getMaxHealth() * share, caster.getHealth() - 1f);
        if (hurt <= 0f) {
            return 0f;
        }
        caster.setHealth(caster.getHealth() - hurt);
        caster.hurtMarked = true;
        if (caster.level() instanceof ServerLevel level) {
            Vec3 at = caster.position().add(0, caster.getBbHeight() * 0.6, 0);
            Vfx.burst(level, Vfx.spark(0xE03030, 0.12f, 12), at, 10, 0.35, 0.1);
            Vfx.burst(level, Vfx.smoke(0x3A0808, 0.5f, 26), at, 6, 0.3, 0.02);
        }
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.8f, 0.7f);
        if (caster instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.noirm.workshop.backlash", Math.round(share * 100))
                    .withStyle(ChatFormatting.RED), true);
        }
        return hurt;
    }

    // ================================================================ scrolls

    /** What writing a scroll gave: the scroll, or why not. */
    public record Written(ItemStack scroll, Component error) {}

    /**
     * Writes one of your own creations on a scroll: a blank scroll (or a sheet of paper), an ink sac
     * and chakra ({@link #WRITE_CHAKRA_SHARE} of its cost). The scroll goes in your inventory.
     */
    public static Written writeScroll(Player player, ShinobiData data, ResourceLocation id) {
        JutsuDesign design = data.creations().get(id);
        if (design == null) {
            return refuseWrite(player, Component.translatable("message.noirm.workshop.why.not_yours"));
        }
        if (!player.getUUID().equals(design.author())) {
            return refuseWrite(player, Component.translatable("message.noirm.workshop.why.not_author", design.name()));
        }
        int mastery = data.jutsu().masteryLevel(id);
        if (mastery < WRITE_MASTERY) {
            return refuseWrite(player, Component.translatable("message.noirm.workshop.why.write_mastery", design.name(), WRITE_MASTERY, mastery));
        }
        float chakra = Math.max(WRITE_CHAKRA_MIN, stats(design).cost() * WRITE_CHAKRA_SHARE);
        boolean free = player.getAbilities().instabuild;
        int surface = free ? -1 : findSlot(player, WorkshopItems.BLANK_SCROLL.get(), Items.PAPER);
        int ink = free ? -1 : findSlot(player, Items.INK_SAC, Items.GLOW_INK_SAC);
        if (!free && (surface < 0 || ink < 0)) {
            return refuseWrite(player, Component.translatable("message.noirm.workshop.why.materials"));
        }
        if (!free && !data.tryConsumeChakra(chakra)) {
            return refuseWrite(player, Component.translatable("message.noirm.workshop.why.write_chakra", Math.round(chakra)));
        }
        if (!free) {
            player.getInventory().getItem(surface).shrink(1);
            player.getInventory().getItem(ink).shrink(1);
        }
        ItemStack scroll = WorkshopItems.creationScroll(design);
        if (!player.getInventory().add(scroll.copy())) {
            player.drop(scroll.copy(), false);
        }
        player.displayClientMessage(Component.translatable("message.noirm.workshop.written", design.name()).withStyle(ChatFormatting.AQUA), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1.1f);
        return new Written(scroll, null);
    }

    private static Written refuseWrite(Player player, Component why) {
        player.displayClientMessage(why.copy().withStyle(ChatFormatting.GRAY), false);
        return new Written(ItemStack.EMPTY, why);
    }

    /** The first inventory slot holding one of {@code items} (in their order of preference), or -1. */
    private static int findSlot(Player player, Item... items) {
        for (Item item : items) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).is(item)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Reads a creation's scroll: you learn that exact jutsu if you own its nature, your rank allows it
     * and you have room. Returns false (and says why) otherwise; you keep the scroll.
     */
    public static boolean readScroll(Player player, ShinobiData data, JutsuDesign design) {
        if (design == null) {
            return refuseRead(player, Component.translatable("message.noirm.jutsu_scroll.unreadable"));
        }
        ResourceLocation id = design.jutsuId();
        if (data.jutsu().knows(id)) {
            return refuseRead(player, Component.translatable("message.noirm.jutsu_scroll.known", design.name()));
        }
        if (data.identity().affinity(design.nature()) <= 0f) {
            return refuseRead(player, Component.translatable("message.noirm.jutsu_scroll.why.nature", design.name(),
                    Component.translatable(design.nature().translationKey())));
        }
        Component rank = JutsuLearning.rankBlocker(player, data, stats(design).rank());
        if (rank != null) {
            return refuseRead(player, rank);
        }
        if (data.creations().all().size() >= MAX_TOTAL) {
            return refuseRead(player, Component.translatable("message.noirm.workshop.why.full_total", MAX_TOTAL));
        }
        learnDesign(data, design);
        player.sendSystemMessage(Component.translatable("message.noirm.workshop.learned", design.name(), design.authorName()).withStyle(ChatFormatting.AQUA));
        float budget = budget(data, design.nature());
        if (weight(design) > budget) {
            player.sendSystemMessage(Component.translatable("message.noirm.workshop.learned_heavy", weight(design),
                    String.format(Locale.ROOT, "%.1f", budget)).withStyle(ChatFormatting.GRAY));
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        return true;
    }

    private static boolean refuseRead(Player player, Component why) {
        player.displayClientMessage(why.copy().withStyle(ChatFormatting.GRAY), true);
        return false;
    }

    /** Teaching a crew mate a creation: you must know it well first. Null if you can, else a translation key. */
    public static String teachBlocker(Player teacher, ResourceLocation id) {
        if (!JutsuDesign.isCreation(id)) {
            return null;
        }
        int mastery = ShinobiCapability.get(teacher).map(d -> d.jutsu().masteryLevel(id)).orElse(0);
        return mastery < TEACH_MASTERY ? "screen.noirm.ally.teach.creation_practice" : null;
    }

    /** The seals as the caster names them. */
    public static JutsuSeals.Sequence sequence(JutsuDesign d) {
        return new JutsuSeals.Sequence(seals(d));
    }
}

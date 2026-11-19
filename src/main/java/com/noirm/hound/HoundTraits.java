package com.noirm.hound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.noirm.Noirm;
import com.noirm.data.CodecJsonLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A hound's own nature: 2–3 traits rolled when you bond, at most one of them a weakness, the way
 * players roll talents. They live in {@code data/noirm/hound_traits/<id>.json} (name and text in
 * the lang files as {@code hound_trait.noirm.<id>}). Every number is a multiplier (1 = no change)
 * or a chance:
 * <ul>
 *   <li>{@code health}, {@code damage}, {@code speed}, {@code smell}, {@code size}: the hound's stats and size;</li>
 *   <li>{@code bond}, {@code xp}: how fast the bond and its XP grow;</li>
 *   <li>{@code food}: how much a meal heals;</li>
 *   <li>{@code disobey}: the chance it ignores an order (Stubborn);</li>
 *   <li>{@code flee_below}: it breaks off a fight under this share of its health (Timid);</li>
 *   <li>{@code down}: how long a knockout lasts; {@code crash}: how long a pill's crash lasts;</li>
 *   <li>{@code begs}: it whines for any food you hold.</li>
 * </ul>
 * {@code excludes} names traits that can't come together (Brave and Timid, Big-Boned and Runt).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class HoundTraits {
    public static final int MIN = 2;
    public static final float THIRD_CHANCE = 0.45f;
    public static final int MAX_WEAKNESSES = 1;

    public record Trait(boolean weakness, int weight, float health, float damage, float speed, float smell, float size,
                        float bond, float xp, float food, float disobey, float fleeBelow, float down, float crash, boolean begs,
                        List<String> excludes) {
        public static final Codec<Trait> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("weakness", false).forGetter(Trait::weakness),
                Codec.INT.optionalFieldOf("weight", 10).forGetter(Trait::weight),
                Codec.FLOAT.optionalFieldOf("health", 1f).forGetter(Trait::health),
                Codec.FLOAT.optionalFieldOf("damage", 1f).forGetter(Trait::damage),
                Codec.FLOAT.optionalFieldOf("speed", 1f).forGetter(Trait::speed),
                Codec.FLOAT.optionalFieldOf("smell", 1f).forGetter(Trait::smell),
                Codec.FLOAT.optionalFieldOf("size", 1f).forGetter(Trait::size),
                Codec.FLOAT.optionalFieldOf("bond", 1f).forGetter(Trait::bond),
                Codec.FLOAT.optionalFieldOf("xp", 1f).forGetter(Trait::xp),
                Codec.FLOAT.optionalFieldOf("food", 1f).forGetter(Trait::food),
                Codec.FLOAT.optionalFieldOf("disobey", 0f).forGetter(Trait::disobey),
                Codec.FLOAT.optionalFieldOf("flee_below", 0f).forGetter(Trait::fleeBelow),
                Codec.FLOAT.optionalFieldOf("down", 1f).forGetter(Trait::down),
                Codec.FLOAT.optionalFieldOf("crash", 1f).forGetter(Trait::crash),
                Codec.BOOL.optionalFieldOf("begs", false).forGetter(Trait::begs),
                Codec.STRING.listOf().optionalFieldOf("excludes", List.of()).forGetter(Trait::excludes)
        ).apply(i, Trait::new));
    }

    private static Map<String, Trait> traits = Map.of();

    private HoundTraits() {}

    public static Map<String, Trait> all() {
        return traits;
    }

    public static Trait get(String id) {
        return traits.get(id);
    }

    @SubscribeEvent
    public static void addHoundTraitListener(AddReloadListenerEvent event) {
        event.addListener(new CodecJsonLoader<>("hound_traits", Trait.CODEC, loaded -> {
            Map<String, Trait> out = new TreeMap<>(); // sorted: the same seed rolls the same traits
            loaded.forEach((id, t) -> out.put(id.getPath(), t));
            traits = Collections.unmodifiableMap(out);
        }));
    }

    /** Rolls 2 (sometimes 3) traits: weighted, no repeats, nothing excluded, at most one weakness. */
    public static List<String> roll(RandomSource random) {
        List<String> picked = new ArrayList<>();
        int count = MIN + (random.nextFloat() < THIRD_CHANCE ? 1 : 0);
        for (int n = 0; n < count; n++) {
            List<String> pool = new ArrayList<>();
            List<Integer> weights = new ArrayList<>();
            int weaknesses = (int) picked.stream().filter(id -> traits.get(id) != null && traits.get(id).weakness()).count();
            traits.forEach((id, t) -> {
                if (picked.contains(id) || t.weakness() && weaknesses >= MAX_WEAKNESSES || clashes(id, t, picked)) {
                    return;
                }
                pool.add(id);
                weights.add(Math.max(1, t.weight()));
            });
            if (pool.isEmpty()) {
                break;
            }
            int total = weights.stream().mapToInt(Integer::intValue).sum();
            int r = random.nextInt(total);
            for (int i = 0; i < pool.size(); i++) {
                r -= weights.get(i);
                if (r < 0) {
                    picked.add(pool.get(i));
                    break;
                }
            }
        }
        return List.copyOf(picked);
    }

    private static boolean clashes(String id, Trait t, List<String> picked) {
        for (String other : picked) {
            Trait o = traits.get(other);
            if (t.excludes().contains(other) || o != null && o.excludes().contains(id)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- combined effects

    public interface Field {
        float of(Trait t);
    }

    /** The product of one multiplier over all of a hound's traits. */
    public static float mult(Collection<String> ids, Field field) {
        float m = 1f;
        for (String id : ids) {
            Trait t = traits.get(id);
            if (t != null) {
                m *= field.of(t);
            }
        }
        return m;
    }

    /** The largest of one chance-like value over a hound's traits. */
    public static float max(Collection<String> ids, Field field) {
        float m = 0f;
        for (String id : ids) {
            Trait t = traits.get(id);
            if (t != null) {
                m = Math.max(m, field.of(t));
            }
        }
        return m;
    }

    public static boolean any(Collection<String> ids, java.util.function.Predicate<Trait> test) {
        for (String id : ids) {
            Trait t = traits.get(id);
            if (t != null && test.test(t)) {
                return true;
            }
        }
        return false;
    }

    public static ResourceLocation id(String path) {
        return Noirm.id(path);
    }
}

package com.noirm.dojutsu;

import com.noirm.Noirm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * The eye abilities a Mangekyō can carry, one per eye (rolled at the awakening). Each one is a
 * jutsu in {@code data/noirm/jutsu/mangekyo/} that the owner learns and can put in a loadout
 * slot. {@code weight}: how often it's rolled; {@code strain}: what one use costs the eyes
 * (Izanagi costs far more when it actually rewrites a death, see {@link MangekyoAbilities}).
 */
public enum MangekyoAbility {
    AMATERASU("amaterasu", 3, 6f),
    TSUKUYOMI("tsukuyomi", 3, 8f),
    KAMUI("kamui", 2, 4f),
    KOTOAMATSUKAMI("kotoamatsukami", 1, 12f),
    IZANAGI("izanagi", 1, 3f);

    private final String key;
    private final int weight;
    private final float strain;

    MangekyoAbility(String key, int weight, float strain) {
        this.key = key;
        this.weight = weight;
        this.strain = strain;
    }

    public String key() {
        return key;
    }

    public float strain() {
        return strain;
    }

    /** The jutsu that uses this ability. */
    public ResourceLocation jutsu() {
        return Noirm.id("mangekyo/" + key);
    }

    public static MangekyoAbility byKey(String key) {
        for (MangekyoAbility ability : values()) {
            if (ability.key.equals(key)) {
                return ability;
            }
        }
        return null;
    }

    /** A weighted roll, never {@code except}. */
    public static MangekyoAbility roll(RandomSource random, MangekyoAbility except) {
        int total = 0;
        for (MangekyoAbility ability : values()) {
            total += ability == except ? 0 : ability.weight;
        }
        int pick = random.nextInt(total);
        for (MangekyoAbility ability : values()) {
            if (ability == except) {
                continue;
            }
            pick -= ability.weight;
            if (pick < 0) {
                return ability;
            }
        }
        return AMATERASU;
    }
}

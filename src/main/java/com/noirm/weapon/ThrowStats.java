package com.noirm.weapon;

import com.noirm.Noirm;
import net.minecraft.resources.ResourceLocation;

/**
 * How a throwable ninja tool flies and hits. One per weapon; the numbers are explained in
 * {@code docs/WEAPONS.md}.
 *
 * @param damage     damage on a hit (before the thrower's stats and Sharpness)
 * @param speed      launch speed in blocks per tick (an arrow from a full bow is 3)
 * @param gravity    how fast it drops, per tick (an arrow drops 0.05)
 * @param cooldown   ticks before the same weapon can be thrown again
 * @param spin       degrees it turns per tick in flight
 * @param flat       true = spins flat like a disc (stars); false = flies point first (kunai, needles)
 * @param pierce     how many bodies it cuts through before it drops (the fūma's big blades)
 * @param fuse       ticks from sticking to the blast, 0 = no explosive tag
 * @param slowTicks  senbon: how long a hit numbs the target (Slowness II)
 * @param animation  the thrower's animation
 * @param scale      how big the renderer draws it
 */
public record ThrowStats(float damage, float speed, float gravity, int cooldown, float spin, boolean flat,
                         int pierce, int fuse, int slowTicks, ResourceLocation animation, float scale) {
    public static final ResourceLocation THROW_KUNAI = Noirm.id("throw_kunai");
    public static final ResourceLocation THROW_SHURIKEN = Noirm.id("throw_shuriken");
    public static final ResourceLocation THROW_FUMA = Noirm.id("throw_fuma");

    public static final ThrowStats KUNAI = new ThrowStats(5f, 2.0f, 0.04f, 10, 0f, false, 0, 0, 0, THROW_KUNAI, 0.75f);
    public static final ThrowStats SHURIKEN = new ThrowStats(3f, 2.4f, 0.025f, 6, 50f, true, 0, 0, 0, THROW_SHURIKEN, 0.55f);
    public static final ThrowStats FUMA_SHURIKEN = new ThrowStats(10f, 1.6f, 0.012f, 30, 35f, true, 3, 0, 0, THROW_FUMA, 1.5f);
    public static final ThrowStats SENBON = new ThrowStats(1.5f, 2.7f, 0.02f, 4, 0f, false, 0, 0, 60, THROW_SHURIKEN, 0.6f);
    public static final ThrowStats EXPLOSIVE_KUNAI = new ThrowStats(3f, 1.8f, 0.045f, 20, 0f, false, 0, 30, 0, THROW_KUNAI, 0.75f);

    /** An explosive tag slapped onto a block: it barely flies, it sticks, it burns for 3 seconds. */
    public static final ThrowStats PLACED_TAG = new ThrowStats(0f, 0.35f, 0.03f, 0, 0f, false, 0, 60, 0, THROW_KUNAI, 0.5f);

    /** How hard an explosive tag goes off (vanilla TNT is 4). */
    public static final float TAG_BLAST = 2.2f;

    /** Animation length (ticks) for the thrower. */
    public int animationTicks() {
        return animation.equals(THROW_FUMA) ? 14 : 10;
    }
}

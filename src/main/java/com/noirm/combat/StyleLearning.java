package com.noirm.combat;

import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Picking a style up by watching it: each time a ninja lands a hit in a style that can be learned
 * this way ({@code "learn": {"watch": <chance>}}), every player close enough to see it may learn it,
 * with that small chance; the one being hit (a spar, or a real fight) twice as likely. Only
 * players who could use the style (its taijutsu and eyes) can pick it up.
 */
public final class StyleLearning {
    /** How close you must be to learn by watching (blocks). */
    public static final double WATCH_RADIUS = 10.0;
    /** The chance is this many times higher for the player on the receiving end (sparring). */
    public static final float SPAR_FACTOR = 2f;
    /** Watchers must be in combat mode (paying attention), or be the one being hit. */
    public static final boolean NEEDS_COMBAT_MODE = true;

    private StyleLearning() {}

    /** A NOIRM hit landed: maybe someone watching learns the attacker's style. */
    static void onStyleHit(LivingEntity attacker, LivingEntity target) {
        if (attacker instanceof Player || !(attacker instanceof CombatStats) || attacker.level().isClientSide()) {
            return;
        }
        ResourceLocation id = CombatSystem.styleFor(attacker);
        CombatStyle style = CombatData.style(id);
        if (style == null || !style.learn().required() || style.learn().watch() <= 0f) {
            return;
        }
        for (Player player : attacker.level().getEntitiesOfClass(Player.class, attacker.getBoundingBox().inflate(WATCH_RADIUS),
                p -> p.isAlive() && !p.isSpectator())) {
            boolean sparring = player == target;
            CombatState state = CombatStates.getIfPresent(player);
            if (!sparring && NEEDS_COMBAT_MODE && (state == null || !state.isCombatMode())) {
                continue;
            }
            witness(player, id, sparring, player.getRandom().nextFloat());
        }
    }

    /**
     * {@code player} saw a hit in style {@code id} ({@code sparring}: it was on them). {@code roll} is a
     * random number in [0, 1); below the style's watch chance (× {@link #SPAR_FACTOR} when sparring),
     * they learn it. Returns true if they learned it now.
     */
    public static boolean witness(Player player, ResourceLocation id, boolean sparring, float roll) {
        CombatStyle style = CombatData.style(id);
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (style == null || data == null || StyleSystem.knows(data, id) || !style.learn().required()) {
            return false;
        }
        if (style.missingRequirement(data.getTaijutsu(), data.identity().hasByakugan()).isPresent()) {
            return false; // you can't copy what your body can't do yet
        }
        float chance = style.learn().watch() * (sparring ? SPAR_FACTOR : 1f);
        return roll < chance && StyleSystem.grant(player, id, StyleSystem.WATCH);
    }
}

package com.noirm.gametest;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hits for tests that must land. NPCs roll their clan like everyone (80% canon since v0.3), and a smoke
 * or liquid body (Iburi, Hōzuki) lets a physical blow pass straight through (traits/PerkEvents). A dodged
 * blow starts no invulnerability, so striking again until one lands is fair.
 */
final class TestHits {
    private static final int TRIES = 40;

    private TestHits() {}

    /** Strikes until the hit lands (or {@value #TRIES} tries). Returns whether it landed. */
    static boolean land(LivingEntity target, DamageSource source, float amount) {
        for (int i = 0; i < TRIES; i++) {
            target.invulnerableTime = 0;
            if (target.hurt(source, amount)) {
                return true;
            }
        }
        return false;
    }
}

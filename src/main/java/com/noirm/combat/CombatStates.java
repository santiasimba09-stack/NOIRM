package com.noirm.combat;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Finds each fighter's {@link CombatState}. Weak keys: when an entity is gone, its state is
 * garbage-collected. Server thread only.
 */
public final class CombatStates {
    private static final Map<LivingEntity, CombatState> STATES = new WeakHashMap<>();

    private CombatStates() {}

    /** The entity's state, created on first use. */
    public static CombatState get(LivingEntity entity) {
        return STATES.computeIfAbsent(entity, e -> new CombatState());
    }

    /** The entity's state, or null if it has never fought. */
    public static CombatState getIfPresent(LivingEntity entity) {
        return STATES.get(entity);
    }
}

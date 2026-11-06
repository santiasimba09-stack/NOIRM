package com.noirm.combat;

import net.minecraft.resources.ResourceLocation;

/**
 * A non-player fighter whose client model plays the same keyframe animations as players do.
 * The combat system calls this (server side) when a move or dodge starts; the entity syncs it
 * to clients, which play {@code animation} stretched to {@code durationTicks}.
 */
public interface CombatAnimated {
    void playCombatAnimation(ResourceLocation animation, int durationTicks);
}

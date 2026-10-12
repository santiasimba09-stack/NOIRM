package com.noirm.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * A fighting style, loaded from {@code data/<namespace>/combat/styles/<path>.json}: the move each
 * input starts a combo with. Masters will teach new styles; the follow-ups live in each move's
 * {@code next} routes.
 */
public record CombatStyle(ResourceLocation light, ResourceLocation heavy, ResourceLocation launcher) {
    public static final Codec<CombatStyle> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("light").forGetter(CombatStyle::light),
            ResourceLocation.CODEC.fieldOf("heavy").forGetter(CombatStyle::heavy),
            ResourceLocation.CODEC.fieldOf("launcher").forGetter(CombatStyle::launcher)
    ).apply(i, CombatStyle::new));

    public ResourceLocation opener(AttackInput input) {
        return switch (input) {
            case LIGHT -> light;
            case HEAVY -> heavy;
            case LAUNCHER -> launcher;
        };
    }
}

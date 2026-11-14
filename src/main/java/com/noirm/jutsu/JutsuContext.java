package com.noirm.jutsu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * One casting of a jutsu.
 *
 * @param power  multiplier from nature affinity, mastery and states (locked in, cloak…)
 * @param charge 0–1 for CHARGE jutsu (how long it was held); 1 for others
 */
public record JutsuContext(LivingEntity caster, ResourceLocation id, JutsuDefinition def, float power, float charge) {
    public ServerLevel level() {
        return (ServerLevel) caster.level();
    }

    public JutsuDefinition.Params params() {
        return def.params();
    }

    public Vec3 look() {
        return caster.getViewVector(1f);
    }

    /** Where the caster faces, ignoring up/down. */
    public Vec3 flatLook() {
        Vec3 look = look();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0, caster.getYRot()) : flat.normalize();
    }
}

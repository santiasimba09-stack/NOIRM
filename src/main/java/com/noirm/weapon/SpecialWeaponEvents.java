package com.noirm.weapon;

import com.noirm.Noirm;
import com.noirm.combat.CombatSystem;
import com.noirm.player.ShinobiCapability;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What the special weapons do (server side): on every melee hit, vanilla swing or combat move,
 * and, for the gunbai, when a projectile meets its guard. Numbers in {@link SpecialWeapons}.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class SpecialWeaponEvents {
    private SpecialWeaponEvents() {}

    @SubscribeEvent
    public static void hurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || event.getSource().getDirectEntity() != attacker
                || attacker.level().isClientSide()) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        if (!(weapon.getItem() instanceof SpecialWeaponItem special)) {
            return;
        }
        onHit(attacker, event.getEntity(), weapon, special.power(), event.getAmount());
    }

    /** One hit's special effect ({@code amount}: the damage before armour). */
    public static void onHit(LivingEntity attacker, LivingEntity target, ItemStack weapon, SpecialWeaponItem.Power power, float amount) {
        ServerLevel level = (ServerLevel) attacker.level();
        Vec3 chest = target.position().add(0, target.getBbHeight() * 0.6, 0);
        switch (power) {
            case LIFESTEAL -> {
                float heal = Math.min(SpecialWeapons.LIFESTEAL_MAX, amount * SpecialWeapons.LIFESTEAL_SHARE);
                attacker.heal(heal);
                if (weapon.isDamaged()) {
                    weapon.setDamageValue(Math.max(0, weapon.getDamageValue() - SpecialWeapons.LIFESTEAL_MEND));
                }
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, chest.x, chest.y, chest.z, 4, 0.2, 0.2, 0.2, 0.05);
            }
            case CHAKRA_DRAIN -> {
                float eaten = drain(target);
                if (attacker instanceof Player player) {
                    ShinobiCapability.get(player).ifPresent(d -> d.setChakra(Math.min(d.getMaxChakra(), d.getChakra() + eaten)));
                } else {
                    attacker.heal(SpecialWeapons.DRAIN_NPC_HEAL);
                }
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, chest.x, chest.y, chest.z, 10, 0.3, 0.3, 0.3, 0.03);
            }
            case THUNDER -> {
                if (!pay(attacker, SpecialWeapons.THUNDER_CHAKRA)) {
                    return;
                }
                CombatSystem.stun(target, SpecialWeapons.THUNDER_STUN);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, chest.x, chest.y, chest.z, 14, 0.3, 0.4, 0.3, 0.15);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.25f, 1.9f);
            }
            case GALE -> {
                Vec3 away = horizontal(target.position().subtract(attacker.position()), attacker);
                target.push(away.x * SpecialWeapons.GALE_PUSH, SpecialWeapons.GALE_LIFT, away.z * SpecialWeapons.GALE_PUSH);
                target.hurtMarked = true;
                level.sendParticles(ParticleTypes.CLOUD, chest.x, chest.y, chest.z, 10, 0.3, 0.3, 0.3, 0.08);
            }
            case CHAIN -> {
                Vec3 toward = attacker.position().subtract(target.position());
                if (toward.horizontalDistance() > SpecialWeapons.CHAIN_MIN_DISTANCE) {
                    Vec3 dir = horizontal(toward, attacker);
                    target.push(dir.x * SpecialWeapons.CHAIN_PULL, 0.15, dir.z * SpecialWeapons.CHAIN_PULL);
                    target.hurtMarked = true;
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.8f, 1f);
                }
            }
            default -> { }
        }
    }

    /** The kusarigama drags its target in, so the hit's usual knockback (away) doesn't apply. */
    @SubscribeEvent
    public static void knockback(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        LivingEntity by = target.getLastHurtByMob();
        if (by != null && target.getLastHurtByMobTimestamp() == target.tickCount
                && by.getMainHandItem().getItem() instanceof SpecialWeaponItem special && special.power() == SpecialWeaponItem.Power.CHAIN
                && by.position().subtract(target.position()).horizontalDistance() > SpecialWeapons.CHAIN_MIN_DISTANCE) {
            event.setCanceled(true);
        }
    }

    /** Unit horizontal vector along {@code v} (or the attacker's facing when they overlap). */
    private static Vec3 horizontal(Vec3 v, LivingEntity attacker) {
        Vec3 flat = new Vec3(v.x, 0, v.z);
        if (flat.lengthSqr() < 1.0e-4) {
            flat = Vec3.directionFromRotation(0, attacker.getYRot());
        }
        return flat.normalize();
    }

    /** Takes chakra from a player target (returns what was taken); ninjas lose posture instead. */
    private static float drain(LivingEntity target) {
        if (target instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> {
                float taken = Math.min(d.getChakra(), SpecialWeapons.DRAIN_CHAKRA);
                d.setChakra(d.getChakra() - taken);
                return taken;
            }).orElse(0f);
        }
        CombatSystem.addPostureDamage(target, SpecialWeapons.DRAIN_POSTURE);
        return SpecialWeapons.DRAIN_CHAKRA;
    }

    /** Players pay chakra for charged hits; ninjas (and creative players) don't. */
    private static boolean pay(LivingEntity attacker, float chakra) {
        if (attacker instanceof Player player) {
            if (player.getAbilities().instabuild) {
                return true;
            }
            return ShinobiCapability.get(player).map(d -> d.tryConsumeChakra(chakra)).orElse(false);
        }
        return true;
    }

    /** A gunbai's guard turns projectiles from the front back where they came from. */
    @SubscribeEvent
    public static void projectile(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide() || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity defender) || !reflects(defender, projectile)) {
            return;
        }
        reflect(defender, projectile);
        event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
    }

    /** Would {@code defender}'s fan turn this projectile away (guarding, and it comes from the front)? */
    public static boolean reflects(LivingEntity defender, Projectile projectile) {
        if (!SpecialWeaponItem.guardsWithFan(defender)) {
            return false;
        }
        Vec3 motion = projectile.getDeltaMovement();
        Vec3 facing = Vec3.directionFromRotation(0, defender.getYRot());
        return motion.x * facing.x + motion.z * facing.z < 0; // flying towards the defender's front
    }

    private static void reflect(LivingEntity defender, Projectile projectile) {
        Vec3 back = projectile.getDeltaMovement().scale(-SpecialWeapons.REFLECT_SPEED);
        projectile.setDeltaMovement(back);
        projectile.setOwner(defender);
        if (projectile instanceof AbstractHurtingProjectile fireball) {
            fireball.xPower = -fireball.xPower;
            fireball.yPower = -fireball.yPower;
            fireball.zPower = -fireball.zPower;
        }
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setYRot(arrow.getYRot() + 180f);
            arrow.yRotO = arrow.getYRot();
        }
        projectile.hurtMarked = true;
        defender.level().playSound(null, defender.getX(), defender.getY(), defender.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 1.3f);
        ((ServerLevel) defender.level()).sendParticles(ParticleTypes.CLOUD, projectile.getX(), projectile.getY(), projectile.getZ(), 6, 0.1, 0.1, 0.1, 0.05);
    }
}

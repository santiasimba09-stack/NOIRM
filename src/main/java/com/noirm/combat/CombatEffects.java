package com.noirm.combat;

import com.noirm.Noirm;
import com.noirm.network.CombatClientPackets.Feedback;
import com.noirm.network.CombatClientPackets.PlayAnimation;
import com.noirm.network.NoirmNetwork;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The sounds, particles, animations and screen feedback of combat — the "feel" layer. Uses only
 * vanilla sounds and particles for now; swapping in custom ones later only touches this class.
 */
final class CombatEffects {
    private CombatEffects() {}

    static void attackStarted(LivingEntity entity, CombatMove move, int totalTicks) {
        if (entity instanceof Player) {
            move.animation().ifPresent(animation ->
                    NoirmNetwork.sendToTrackingAndSelf(entity, new PlayAnimation(entity.getId(), animation, totalTicks)));
        } else {
            // Telegraph so the player can read what's coming: a cloth rustle, and a glint on heavies.
            boolean heavy = move.hit().isHeavy();
            sound(entity, SoundEvents.ARMOR_EQUIP_LEATHER, 0.9f, heavy ? 0.7f : 1.4f);
            if (heavy) {
                particles(entity, ParticleTypes.ENCHANTED_HIT, 12, 0.4);
            }
        }
    }

    static void swing(LivingEntity entity, CombatMove move) {
        sound(entity, move.hit().isHeavy() ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.6f, 1.1f);
    }

    static void hitLanded(LivingEntity attacker, LivingEntity target, CombatMove move, boolean guarded, CombatState targetState) {
        CombatMove.Hit hit = move.hit();
        net.minecraft.world.phys.Vec3 chest = target.position().add(0, target.getBbHeight() * 0.6, 0);
        if (!guarded) {
            sound(target, hit.isHeavy() ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.9f);
            com.noirm.vfx.Vfx.hit(target.level(), chest, com.noirm.vfx.Vfx.HIT, hit.isHeavy());
            if (hit.isHeavy()) {
                particles(target, ParticleTypes.SWEEP_ATTACK, 1, 0);
            }
        } else {
            com.noirm.vfx.Vfx.burst(target.level(), com.noirm.vfx.Vfx.spark(0x9AC8FF, 0.15f, 6), chest, 5, 0.1, 0.25);
        }
        float shake = guarded ? hit.shake() * 0.4f : hit.shake();
        if (attacker instanceof ServerPlayer player) {
            NoirmNetwork.sendToPlayer(player, new Feedback(Feedback.Kind.HIT_DEALT, shake * 0.6f,
                    target.getId(), targetState.posture(), targetState.maxPosture()));
        }
        if (target instanceof ServerPlayer player) {
            NoirmNetwork.sendToPlayer(player, Feedback.simple(Feedback.Kind.HIT_TAKEN, shake));
        }
    }

    static void guarded(LivingEntity target) {
        sound(target, SoundEvents.SHIELD_BLOCK, 0.8f, 1.1f);
        particles(target, ParticleTypes.CRIT, 4, 0.2);
        feedback(target, Feedback.Kind.GUARD, 0.2f);
    }

    static void parry(LivingEntity defender) {
        sound(defender, SoundEvents.ANVIL_LAND, 0.5f, 1.8f);
        particles(defender, ParticleTypes.ELECTRIC_SPARK, 20, 0.4);
        feedback(defender, Feedback.Kind.PARRY, 0.5f);
    }

    static void postureBreak(LivingEntity entity) {
        sound(entity, SoundEvents.SHIELD_BREAK, 1f, 0.8f);
        particles(entity, ParticleTypes.POOF, 10, 0.4);
        feedback(entity, Feedback.Kind.POSTURE_BREAK, 0.7f);
    }

    static void dodge(LivingEntity entity, boolean forward, int ticks) {
        sound(entity, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.5f, 0.6f);
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 0.1, entity.getZ(), 6, 0.3, 0.05, 0.3, 0.02);
        }
        if (entity instanceof Player) {
            NoirmNetwork.sendToTrackingAndSelf(entity, new PlayAnimation(entity.getId(),
                    Noirm.id(forward ? "forward_roll" : "backward_roll"), ticks));
        }
    }

    static void perfectDodge(LivingEntity entity) {
        sound(entity, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 1.5f);
        feedback(entity, Feedback.Kind.PERFECT_DODGE, 0.3f);
    }

    static void focus(LivingEntity center) {
        sound(center, SoundEvents.BEACON_ACTIVATE, 0.7f, 2f);
        feedback(center, Feedback.Kind.FOCUS, 1f);
    }

    static void outOfStamina(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6f, 0.5f);
            NoirmNetwork.sendToPlayer(player, Feedback.simple(Feedback.Kind.NO_STAMINA, 0f));
        }
    }

    private static void feedback(LivingEntity entity, Feedback.Kind kind, float strength) {
        if (entity instanceof ServerPlayer player) {
            NoirmNetwork.sendToPlayer(player, Feedback.simple(kind, strength));
        }
    }

    private static void sound(LivingEntity entity, SoundEvent sound, float volume, float pitch) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound,
                entity instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, volume, pitch);
    }

    private static void particles(LivingEntity entity, ParticleOptions particle, int count, double spread) {
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(particle, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                    count, spread, spread, spread, 0.1);
        }
    }
}

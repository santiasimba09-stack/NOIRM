package com.noirm.combat;

import com.noirm.Noirm;
import com.noirm.network.CombatClientPackets.Feedback;
import com.noirm.network.CombatClientPackets.PlayAnimation;
import com.noirm.network.NoirmNetwork;
import com.noirm.sound.NoirmSounds;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The sounds, particles, animations and screen feedback of combat — the "feel" layer. The sounds
 * are our own ({@link NoirmSounds}, made by tools/soundgen); the particles are vanilla.
 */
final class CombatEffects {
    private CombatEffects() {}

    static void attackStarted(LivingEntity entity, CombatMove move, int totalTicks) {
        if (entity instanceof Player) {
            move.animation().ifPresent(animation ->
                    NoirmNetwork.sendToTrackingAndSelf(entity, new PlayAnimation(entity.getId(), animation, totalTicks)));
        } else {
            if (entity instanceof CombatAnimated animated) {
                // NPCs play the very same animation (synced as entity data, see SparringNinja).
                move.animation().ifPresent(animation -> animated.playCombatAnimation(animation, totalTicks));
            }
            // Telegraph so the player can read what's coming: a cloth rustle, and a glint on heavies.
            boolean heavy = move.hit().isHeavy();
            sound(entity, SoundEvents.ARMOR_EQUIP_LEATHER, 0.9f, heavy ? 0.7f : 1.4f); // vanilla cloth rustle: fits as is
            if (heavy) {
                particles(entity, ParticleTypes.ENCHANTED_HIT, 12, 0.4);
            }
        }
    }

    static void swing(LivingEntity entity, CombatMove move) {
        sound(entity, move.hit().isHeavy() ? NoirmSounds.SWING_HEAVY.get() : NoirmSounds.SWING_LIGHT.get(), 0.7f, 1f);
    }

    /** Both fighters' animations hold still for the hit-stop, on every client that sees them. */
    static void hitstop(LivingEntity attacker, LivingEntity target, int ticks) {
        if (ticks <= 0) {
            return;
        }
        for (LivingEntity entity : new LivingEntity[]{attacker, target}) {
            NoirmNetwork.sendToTrackingAndSelf(entity, new PlayAnimation(entity.getId(), com.noirm.network.CombatClientPackets.HITSTOP, ticks));
        }
    }

    static void hitLanded(LivingEntity attacker, LivingEntity target, CombatMove move, boolean guarded, CombatState targetState) {
        CombatMove.Hit hit = move.hit();
        net.minecraft.world.phys.Vec3 chest = target.position().add(0, target.getBbHeight() * 0.6, 0);
        if (!guarded) {
            sound(target, hit.isHeavy() ? NoirmSounds.HIT_HEAVY.get() : NoirmSounds.HIT_LIGHT.get(), 1f, 1f);
            com.noirm.vfx.Vfx.hit(target.level(), chest, com.noirm.vfx.Vfx.HIT, hit.isHeavy());
            styleImpact(attacker, target, chest, hit.isHeavy());
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

    /** Each style lands differently: Brawler thuds, Strong Fist whips, the Gentle Fist barely touches. */
    private static void styleImpact(LivingEntity attacker, LivingEntity target, net.minecraft.world.phys.Vec3 chest, boolean heavy) {
        net.minecraft.world.level.Level level = target.level();
        switch (CombatSystem.styleFor(attacker).getPath()) {
            case "brawler" -> {
                sound(target, NoirmSounds.BODY_THUD.get(), 0.5f, heavy ? 0.8f : 1.05f);
                com.noirm.vfx.Vfx.burst(level, com.noirm.vfx.Vfx.smoke(0xB8A890, 0.6f, 14), chest, heavy ? 6 : 3, 0.25, 0.04);
            }
            case "strong_fist" -> {
                sound(target, NoirmSounds.SWING_LIGHT.get(), 0.6f, 1.4f);
                net.minecraft.world.phys.Vec3 dir = target.position().subtract(attacker.position()).normalize();
                for (int i = 1; i <= 4; i++) {
                    com.noirm.vfx.Vfx.one(level, com.noirm.vfx.Vfx.spark(0xFFFFFF, 0.12f, 5), chest.subtract(dir.scale(i * 0.45)), dir.scale(0.4));
                }
            }
            case "weapon_kunai", "weapon_sword" -> {
                sound(target, NoirmSounds.WEAPON_IMPACT.get(), 0.6f, heavy ? 0.9f : 1.15f);
                particles(target, ParticleTypes.CRIT, heavy ? 8 : 4, 0.25);
            }
            case "weapon_staff" -> sound(target, SoundEvents.WOOD_HIT, 0.9f, heavy ? 0.7f : 1.0f);
            case "gentle_fist" -> {
                sound(target, NoirmSounds.MEDICAL_CHIME.get(), 0.35f, 1.8f); // Gentle Fist: chakra, not muscle
                com.noirm.vfx.Vfx.burst(level, com.noirm.vfx.Vfx.glow(0xE8FAFF, 0x6FB8FF, 0.25f, 0.05f, 7), chest, 4, 0.1, 0.06);
            }
            default -> { }
        }
    }

    static void guarded(LivingEntity target) {
        sound(target, NoirmSounds.GUARD.get(), 0.9f, 1f);
        particles(target, ParticleTypes.CRIT, 4, 0.2);
        feedback(target, Feedback.Kind.GUARD, 0.2f);
    }

    static void parry(LivingEntity defender) {
        sound(defender, NoirmSounds.PARRY.get(), 0.8f, 1f);
        particles(defender, ParticleTypes.ELECTRIC_SPARK, 20, 0.4);
        feedback(defender, Feedback.Kind.PARRY, 0.5f);
    }

    /** Tells a defending player their perception is slowing an incoming attack (the client draws the focus). */
    static void perceptionFocus(LivingEntity target, LivingEntity attacker, int ticks, float slowdown) {
        if (target instanceof ServerPlayer player) {
            NoirmNetwork.sendToPlayer(player, Feedback.perceptionFocus(attacker.getId(), ticks, slowdown));
        }
    }

    /** A just-frame parry: a bright ring and a clear bell on top of the parry clang. */
    static void justFrame(LivingEntity defender, LivingEntity attacker) {
        sound(defender, NoirmSounds.JUST_FRAME.get(), 1f, 1f);
        if (defender.level() instanceof ServerLevel level) {
            net.minecraft.world.phys.Vec3 mid = defender.position().add(attacker.position()).scale(0.5)
                    .add(0, defender.getBbHeight() * 0.6, 0);
            level.sendParticles(ParticleTypes.FLASH, mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, 16, 0.2, 0.2, 0.2, 0.15);
        }
        feedback(defender, Feedback.Kind.JUST_FRAME, 0.6f);
        if (defender instanceof ServerPlayer player) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.noirm.combat.just_frame")
                    .withStyle(net.minecraft.ChatFormatting.AQUA), true);
        }
    }

    /** The counter after a just-frame parry lands: a heavier crack. */
    static void counterHit(LivingEntity attacker, LivingEntity target) {
        sound(target, NoirmSounds.HIT_HEAVY.get(), 1f, 0.85f);
        particles(target, ParticleTypes.CRIT, 14, 0.35);
        feedback(attacker, Feedback.Kind.SHAKE, 0.6f);
    }

    static void postureBreak(LivingEntity entity) {
        sound(entity, NoirmSounds.POSTURE_BREAK.get(), 1f, 1f);
        particles(entity, ParticleTypes.POOF, 10, 0.4);
        feedback(entity, Feedback.Kind.POSTURE_BREAK, 0.7f);
    }

    static void dodge(LivingEntity entity, boolean forward, int ticks) {
        sound(entity, NoirmSounds.SWING_HEAVY.get(), 0.4f, 1.2f);
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 0.1, entity.getZ(), 6, 0.3, 0.05, 0.3, 0.02);
        }
        ResourceLocation roll = Noirm.id(forward ? "forward_roll" : "backward_roll");
        if (entity instanceof Player) {
            NoirmNetwork.sendToTrackingAndSelf(entity, new PlayAnimation(entity.getId(), roll, ticks));
        } else if (entity instanceof CombatAnimated animated) {
            animated.playCombatAnimation(roll, ticks);
        }
    }

    static void perfectDodge(LivingEntity entity) {
        sound(entity, NoirmSounds.JUST_FRAME.get(), 0.6f, 1.3f);
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

    static void grabbed(LivingEntity holder, LivingEntity target) {
        sound(target, SoundEvents.ARMOR_EQUIP_LEATHER, 1.2f, 0.6f);
        sound(target, NoirmSounds.HIT_LIGHT.get(), 0.6f, 0.8f);
        particles(target, ParticleTypes.CRIT, 6, 0.3);
        feedback(target, Feedback.Kind.HIT_TAKEN, 0.3f);
        feedback(holder, Feedback.Kind.HIT_DEALT, 0.2f);
    }

    static void grabBroken(LivingEntity grabber) {
        sound(grabber, NoirmSounds.SWING_LIGHT.get(), 0.8f, 0.8f);
    }

    static void clinchEscape(LivingEntity entity) {
        sound(entity, NoirmSounds.SWING_LIGHT.get(), 0.8f, 1.1f);
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 0.1, entity.getZ(), 8, 0.3, 0.05, 0.3, 0.03);
        }
    }

    static void knockdown(LivingEntity target) {
        sound(target, NoirmSounds.BODY_THUD.get(), 1f, 1f);
        if (target.level() instanceof ServerLevel level) {
            com.noirm.vfx.Vfx.burst(level, com.noirm.vfx.Vfx.smoke(0xB8A890, 0.7f, 20), target.position().add(0, 0.2, 0), 8, 0.4, 0.04);
        }
    }

    static void wallSplat(LivingEntity target, net.minecraft.world.phys.Vec3 wall) {
        sound(target, NoirmSounds.WALL_SPLAT.get(), 1.2f, 1f);
        if (target.level() instanceof ServerLevel level) {
            com.noirm.vfx.Vfx.hit(level, wall, com.noirm.vfx.Vfx.HIT, true);
            com.noirm.vfx.Vfx.burst(level, com.noirm.vfx.Vfx.smoke(0xA09080, 0.9f, 25), wall, 10, 0.5, 0.05);
        }
        feedback(target, Feedback.Kind.HIT_TAKEN, 1f);
    }

    static void chakraStruck(LivingEntity target) {
        if (target.level() instanceof ServerLevel level) {
            net.minecraft.world.phys.Vec3 chest = target.position().add(0, target.getBbHeight() * 0.6, 0);
            com.noirm.vfx.Vfx.burst(level, com.noirm.vfx.Vfx.glow(0xCFF4FF, 0x4FA8FF, 0.35f, 0.05f, 8), chest, 6, 0.2, 0.08);
        }
    }

    private static void feedback(LivingEntity entity, Feedback.Kind kind, float strength) {
        if (entity instanceof ServerPlayer player) {
            NoirmNetwork.sendToPlayer(player, Feedback.simple(kind, strength));
        }
    }

    private static void sound(LivingEntity entity, SoundEvent sound, float volume, float pitch) {
        float life = 1f + (entity.getRandom().nextFloat() - 0.5f) * 0.06f; // repeats never sound identical
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound,
                entity instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, volume, pitch * life);
    }

    private static void particles(LivingEntity entity, ParticleOptions particle, int count, double spread) {
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(particle, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                    count, spread, spread, spread, 0.1);
        }
    }
}

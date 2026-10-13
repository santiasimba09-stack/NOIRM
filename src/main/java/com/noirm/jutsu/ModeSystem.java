package com.noirm.jutsu;

import com.noirm.clan.Nature;
import com.noirm.player.ShinobiCapability;
import com.noirm.vfx.Vfx;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Modes: states you switch on with a jutsu and keep up with chakra (Lightning Armor now; Sage
 * Mode and the Gates later). Using the jutsu again switches it off. While on: faster movement,
 * sharper perception, faster moves, harder hits, less damage taken, and an aura.
 */
public final class ModeSystem {
    private static final UUID SPEED_ID = UUID.fromString("5d6e7f80-1a2b-4c3d-8e9f-0a1b2c3d4e6f");

    /** {@code strength}: how far the mode's effects go, 1 at first, +25% per mastery level. */
    private record Active(ResourceLocation id, JutsuDefinition def, JutsuDefinition.Mode mode, long until, float strength) {
        float boost(float multiplier) {
            return 1f + (multiplier - 1f) * strength;
        }

        /** Damage taken (below 1): more mastery, less gets through, never below 10%. */
        float guard(float multiplier) {
            return Math.max(0.1f, 1f - (1f - multiplier) * strength);
        }
    }

    /** Each mastery level makes a mode's effects 25% stronger. */
    public static final float MASTERY_STRENGTH = 0.25f;

    private static final Map<LivingEntity, Active> ACTIVE = new WeakHashMap<>();

    private ModeSystem() {}

    /** The {@code mode} behavior: switch it on, or off if it's already on. */
    static void toggle(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        Active current = ACTIVE.get(caster);
        if (current != null && current.id().equals(ctx.id())) {
            end(caster);
            return;
        }
        if (current != null) {
            end(caster);
        }
        JutsuDefinition.Mode mode = ctx.def().mode().orElse(new JutsuDefinition.Mode(0, 1, 1, 1, 1, 1));
        int duration = ctx.params().duration();
        long until = duration > 0 ? caster.level().getGameTime() + duration : Long.MAX_VALUE;
        int mastery = caster instanceof Player player
                ? ShinobiCapability.get(player).map(d -> d.jutsu().masteryLevel(ctx.id())).orElse(0) : 1;
        Active active = new Active(ctx.id(), ctx.def(), mode, until, 1f + MASTERY_STRENGTH * mastery);
        ACTIVE.put(caster, active);
        AttributeInstance speed = caster.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && mode.speed() != 0) {
            speed.removeModifier(SPEED_ID);
            speed.addTransientModifier(new AttributeModifier(SPEED_ID, "noirm_mode", Math.max(-0.9, mode.speed() * active.strength()),
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        Vfx.flash(caster.level(), caster.position().add(0, 1, 0), Vfx.palette(ctx.def().nature().orElse(null)), 2.5f);
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8f, 1.6f);
    }

    public static void end(LivingEntity entity) {
        if (ACTIVE.remove(entity) == null) {
            return;
        }
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_ID);
        }
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.4f);
    }

    public static boolean isActive(LivingEntity entity, ResourceLocation id) {
        Active active = ACTIVE.get(entity);
        return active != null && active.id().equals(id);
    }

    /** Every server tick: drain chakra, end when it runs out or time is up, draw the aura. */
    public static void tick(LivingEntity entity) {
        Active active = ACTIVE.get(entity);
        if (active == null) {
            return;
        }
        if (!entity.isAlive() || entity.level().getGameTime() > active.until()) {
            end(entity);
            return;
        }
        if (entity instanceof Player player && !player.getAbilities().instabuild) {
            boolean paid = ShinobiCapability.get(player).map(d -> d.tryConsumeChakra(active.mode().drain() / 20f)).orElse(false);
            if (!paid) {
                end(entity);
                player.displayClientMessage(Component.translatable("message.noirm.mode.out_of_chakra"), true);
                return;
            }
        }
        Nature nature = active.def().nature().orElse(null);
        if (entity.tickCount % 2 == 0) {
            Vfx.aura(entity.level(), entity.position(), entity.getBbWidth(), entity.getBbHeight(), Vfx.palette(nature), 5);
            if (nature == Nature.LIGHTNING) {
                Vfx.arcs(entity.level(), entity, 2, Vfx.LIGHTNING);
                Vfx.radial(entity.level(), Vfx.spark(0x9AD8FF, 0.18f, 5), entity.position().add(0, entity.getBbHeight() * 0.5, 0), 3, 0.35);
            }
        }
        if (nature == Nature.LIGHTNING && entity.tickCount % 12 == 0) {
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.PLAYERS, 0.25f, 2f);
        }
    }

    public static float perceptionMultiplier(LivingEntity entity) {
        Active active = ACTIVE.get(entity);
        return active == null ? 1f : active.boost(active.mode().perception());
    }

    public static float damageMultiplier(LivingEntity entity) {
        Active active = ACTIVE.get(entity);
        return active == null ? 1f : active.boost(active.mode().damage());
    }

    /** Multiplies damage taken. */
    public static float defenseMultiplier(LivingEntity entity) {
        Active active = ACTIVE.get(entity);
        if (active == null) {
            return 1f;
        }
        float defense = active.mode().defense();
        return defense < 1f ? active.guard(defense) : defense;
    }

    /** How fast your own moves play (wind-up and recovery). */
    public static float actionSpeed(LivingEntity entity) {
        Active active = ACTIVE.get(entity);
        return active == null ? 1f : active.boost(active.mode().actionSpeed());
    }

    /** The active mode's run-speed bonus (e.g. 1.0 = twice as fast), for jumps and leaps. */
    public static float speedBonus(LivingEntity entity) {
        Active active = ACTIVE.get(entity);
        return active == null ? 0f : active.mode().speed() * active.strength();
    }
}

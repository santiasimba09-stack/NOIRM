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

    private record Active(ResourceLocation id, JutsuDefinition def, JutsuDefinition.Mode mode, long until) {}

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
        ACTIVE.put(caster, new Active(ctx.id(), ctx.def(), mode, until));
        AttributeInstance speed = caster.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && mode.speed() != 0) {
            speed.removeModifier(SPEED_ID);
            speed.addTransientModifier(new AttributeModifier(SPEED_ID, "noirm_mode", mode.speed(), AttributeModifier.Operation.MULTIPLY_TOTAL));
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
                Vfx.radial(entity.level(), Vfx.spark(0x9AD8FF, 0.18f, 5), entity.position().add(0, entity.getBbHeight() * 0.5, 0), 3, 0.35);
            }
        }
        if (nature == Nature.LIGHTNING && entity.tickCount % 12 == 0) {
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.PLAYERS, 0.25f, 2f);
        }
    }

    private static JutsuDefinition.Mode mode(LivingEntity entity) {
        Active active = ACTIVE.get(entity);
        return active == null ? null : active.mode();
    }

    public static float perceptionMultiplier(LivingEntity entity) {
        JutsuDefinition.Mode mode = mode(entity);
        return mode == null ? 1f : mode.perception();
    }

    public static float damageMultiplier(LivingEntity entity) {
        JutsuDefinition.Mode mode = mode(entity);
        return mode == null ? 1f : mode.damage();
    }

    public static float defenseMultiplier(LivingEntity entity) {
        JutsuDefinition.Mode mode = mode(entity);
        return mode == null ? 1f : mode.defense();
    }

    /** How fast your own moves play (wind-up and recovery). */
    public static float actionSpeed(LivingEntity entity) {
        JutsuDefinition.Mode mode = mode(entity);
        return mode == null ? 1f : mode.actionSpeed();
    }
}

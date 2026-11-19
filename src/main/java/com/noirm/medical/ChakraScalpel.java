package com.noirm.medical;

import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuEffects;
import com.noirm.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Chakra Scalpel (behavior {@code "scalpel"}): thin blades of chakra over your hands for
 * {@code params.duration} ticks. Every <b>bare-handed or weapon strike</b> you land meanwhile cuts
 * under the skin: {@code params.damage} × jutsu power on top of the hit (it ignores armor, being
 * inside it), and the cut muscles slow and weaken the target ({@code params.effect} for
 * {@code params.effect_ticks}). Jutsu and thrown weapons aren't scalpels.
 */
public final class ChakraScalpel {
    /** Who has the scalpels up: until when, how deep each cut goes and what it does. */
    private record Blades(long until, float damage, String effect, int effectTicks) {}

    private static final Map<LivingEntity, Blades> ACTIVE = new WeakHashMap<>();
    /** The scalpel's colour: a thin medical blue-green. */
    public static final Vfx.Palette PALETTE = new Vfx.Palette(0xE8FFFF, 0x7FF0D8, 0x1A6A60, 0xFFFFFF);

    private ChakraScalpel() {}

    public static void cast(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        var p = ctx.params();
        ACTIVE.put(caster, new Blades(caster.level().getGameTime() + Math.max(20, p.duration()), p.damage() * ctx.power(),
                p.effect(), p.effectTicks()));
        caster.level().playSound(null, caster.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.8f);
        Vfx.aura(caster.level(), caster.position(), caster.getBbWidth(), caster.getBbHeight(), PALETTE, 12);
    }

    public static boolean active(LivingEntity entity) {
        Blades blades = ACTIVE.get(entity);
        if (blades == null) {
            return false;
        }
        if (entity.level().getGameTime() > blades.until() || !entity.isAlive()) {
            ACTIVE.remove(entity);
            return false;
        }
        return true;
    }

    /** Ticks left on the scalpels (0 if none). */
    public static int ticksLeft(LivingEntity entity) {
        return active(entity) ? (int) (ACTIVE.get(entity).until() - entity.level().getGameTime()) : 0;
    }

    public static void end(LivingEntity entity) {
        ACTIVE.remove(entity);
    }

    /** A strike from someone with scalpels up: the extra damage (0 if it isn't a scalpel cut). Applies the cut's effects. */
    public static float cut(LivingEntity attacker, LivingEntity victim, DamageSource source) {
        if (!active(attacker) || source.getDirectEntity() != attacker || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.MAGIC)) {
            return 0f;
        }
        Blades blades = ACTIVE.get(attacker);
        JutsuEffects.applyEffects(victim, blades.effect(), blades.effectTicks());
        if (victim.level() instanceof ServerLevel level) {
            Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
            Vfx.burst(level, Vfx.shard(PALETTE.mid(), 0.12f, 6), at, 6, 0.15, 0.05);
            level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 1.9f);
        }
        return blades.damage();
    }

    /** Every server tick: a faint glow on the hands of everyone with scalpels up; expired ones end. */
    public static void tickAll() {
        for (LivingEntity entity : new java.util.ArrayList<>(ACTIVE.keySet())) {
            tick(entity);
        }
    }

    private static void tick(LivingEntity entity) {
        if (!active(entity) || entity.tickCount % 4 != 0 || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 hand = entity.getEyePosition().add(entity.getViewVector(1f).scale(0.5)).add(0, -0.55, 0);
        Vfx.burst(level, Vfx.glow(PALETTE.core(), PALETTE.mid(), 0.12f, 0.02f, 5), hand, 2, 0.12, 0.005);
    }
}

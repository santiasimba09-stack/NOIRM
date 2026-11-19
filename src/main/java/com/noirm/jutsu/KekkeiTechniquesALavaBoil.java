package com.noirm.jutsu;

import com.noirm.combat.CombatSystem;
import com.noirm.vfx.KekkeiVfxA;
import com.noirm.vfx.Vfx;
import com.noirm.vfx.VfxKind;
import com.noirm.vfx.VfxOptions;
import com.noirm.world.Destruction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Lava and Boil release (the chakra wave, share A; see {@link KekkeiTechniquesA}).
 * <ul>
 *   <li><b>quicklime</b> (Quicklime Congealing): a glob of quicklime arcs to the enemy you look at
 *       (within {@code range}) and sets around them: {@code damage}, held for {@code stun} ticks,
 *       then slowed by {@code effect}.</li>
 *   <li><b>volcano</b> (Volcanic Eruption, charged): the ground at your aim ruptures. Everyone within
 *       {@code radius} is blasted ({@code damage}, burning), the ground turns to magma for a while
 *       (it heals), and the vent erupts for {@code duration} ticks, raining lava bombs.</li>
 *   <li><b>acid_breath</b> (Acid Mist): a cone of acid steam ({@code range}) that scalds, weakens and
 *       eats into armour.</li>
 *   <li><b>geyser</b> (Scalding Geyser): the ground at your aim hisses for {@code duration} ticks, then a
 *       boiling jet throws everyone within {@code radius} up by {@code knockback}.</li>
 *   <li><b>steam_charge</b> (Boiling Vapour Armour): wrapped in boiling steam you charge {@code range}
 *       blocks over {@code duration} ticks, shrugging off blows and bowling through everyone in the way.</li>
 *   <li><b>pressure_dome</b> (Scalding Pressure Dome): a dome of steam of {@code radius} builds over
 *       your aim for {@code duration} ticks, scalding whoever is inside, then bursts.</li>
 * </ul>
 */
public final class KekkeiTechniquesALavaBoil {
    /** Ticks a quicklime glob takes to reach its target. */
    public static final int LIME_FLIGHT = 6;
    /** The volcano's lava bombs: one every this many ticks, landing this many ticks after they leave the vent. */
    public static final int BOMB_INTERVAL = 6;
    public static final int BOMB_FLIGHT = 12;
    /** A lava bomb's share of the volcano's damage. */
    public static final float BOMB_SHARE = 0.25f;
    /** How long the molten ground stays before it heals (share of the terrain regen time). */
    public static final float MAGMA_REGEN = 0.25f;
    /** Acid Mist: durability eaten from each armour piece. */
    public static final int ACID_CORRODE = 6;
    /** The pressure dome scalds whoever is inside every this many ticks, for this share of its damage. */
    public static final int DOME_SCALD_INTERVAL = 10;
    public static final float DOME_SCALD_SHARE = 0.08f;

    private KekkeiTechniquesALavaBoil() {}

    // ================================================================ Quicklime Congealing

    static void quicklime(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        LivingEntity target = JutsuBehaviors.lookTarget(caster, p.range(), 15);
        if (target == null) {
            JutsuBehaviors.message(caster, Component.translatable("message.noirm.jutsu.no_target"));
            return;
        }
        float power = ctx.power();
        Vec3 from = KekkeiTechniquesA.hands(caster);
        int hold = Math.round(p.stun() * Math.min(1.5f, power));
        int[] age = {0};
        KekkeiTechniquesA.playAt(level, from, SoundEvents.SLIME_SQUISH, 1.2f, 0.6f);
        GreatTechniques.later(now -> {
            age[0]++;
            if (!target.isAlive() || target.level() != level) {
                return true;
            }
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
            if (age[0] <= LIME_FLIGHT) {
                // In flight: a lobbed arc of setting cement.
                double t = age[0] / (double) LIME_FLIGHT;
                Vec3 at = from.add(to.subtract(from).scale(t)).add(0, Math.sin(t * Math.PI) * 1.5, 0);
                KekkeiVfxA.limeGlob(level, at, to.subtract(from).scale(1.0 / LIME_FLIGHT), 0.6f);
                if (age[0] == LIME_FLIGHT) {
                    JutsuEffects.hit(caster, target, p.damage() * power, 0, p.posture() * power, 0, Vec3.ZERO);
                    CombatSystem.stun(target, hold);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, hold, 9, false, false));
                    JutsuEffects.applyEffects(target, p.effect(), hold + p.effectTicks());
                    Vfx.burst(level, Vfx.glow(0xFFFFFF, KekkeiVfxA.LIME, 1.6f, 0.5f, 5), to, 1, 0, 0);
                    Vfx.burst(level, new VfxOptions(VfxKind.STEAM, KekkeiVfxA.LIME, KekkeiVfxA.LIME_SHADE, 0.8f, 1.6f, 30, -0.005f, 0.9f), to, 8, 0.6, 0.05);
                    KekkeiTechniquesA.playAt(level, to, SoundEvents.MUD_PLACE, 1.5f, 0.6f);
                }
                return false;
            }
            // Setting: the cement climbs them and hardens, holding them still.
            if (age[0] % 2 == 0) {
                KekkeiVfxA.limeEncase(level, target, (age[0] - LIME_FLIGHT) / 12f);
            }
            target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
            target.hurtMarked = true;
            if (age[0] >= LIME_FLIGHT + hold) {
                Vfx.burst(level, Vfx.shard(KekkeiVfxA.LIME_SHADE, 0.2f, 30), to, 14, 0.5, 0.2);
                KekkeiTechniquesA.playAt(level, to, com.noirm.sound.NoirmSounds.EARTH_CRACK.get(), 1f, 1.1f);
                return true;
            }
            return false;
        });
    }

    // ================================================================ Volcanic Eruption

    static void volcano(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power() * (0.6f + 0.4f * ctx.charge());
        float radius = p.radius() * (float) Math.sqrt(Math.max(0.5f, power));
        Vec3 vent = KekkeiTechniquesA.groundAim(caster, level, p.range());
        // The rupture: a deep orange flash, the ground blown open, magma everywhere.
        KekkeiVfxA.lavaSplash(level, vent.add(0, 0.6, 0), radius * 0.7f);
        KekkeiVfxA.eruption(level, vent, 2f);
        Vfx.burst(level, Vfx.ring(KekkeiVfxA.LAVA_ORANGE, radius * 2f, 14), vent.add(0, 0.1, 0), 1, 0, 0);
        Vfx.quake(level, vent, 1.2f, radius * 6);
        KekkeiTechniquesA.playAt(level, vent, com.noirm.sound.NoirmSounds.BIG_IMPACT.get(), 3f, 0.71f);
        KekkeiTechniquesA.playAt(level, vent, SoundEvents.LAVA_EXTINGUISH, 2f, 0.5f);
        for (LivingEntity victim : KekkeiTechniquesA.around(caster, level, vent, radius)) {
            float falloff = 1f - (float) Math.min(1, victim.position().distanceTo(vent) / (radius + 1));
            JutsuEffects.hit(caster, victim, p.damage() * power * (0.5f + 0.5f * falloff), p.knockback(), p.posture() * power, p.fireSeconds(),
                    victim.position().subtract(vent));
            victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.35 + 0.25 * falloff, 0));
            victim.hurtMarked = true;
        }
        moltenGround(level, vent, radius);
        Destruction.crater(level, vent, radius * 0.3f, power);
        int duration = Math.max(10, p.duration());
        int[] age = {0};
        GreatTechniques.later(now -> {
            age[0]++;
            if (age[0] > duration + BOMB_FLIGHT) {
                return true;
            }
            if (age[0] <= duration) {
                KekkeiVfxA.eruption(level, vent, 1.2f + 0.8f * (1f - age[0] / (float) duration));
                if (age[0] % 2 == 0) {
                    KekkeiVfxA.moltenGround(level, vent, radius);
                }
                if (age[0] % 10 == 0) {
                    KekkeiTechniquesA.playAt(level, vent, com.noirm.sound.NoirmSounds.LAVA_HISS.get(), 2f, 0.71f);
                    Vfx.quake(level, vent, 0.3f, radius * 4);
                }
                if (age[0] % BOMB_INTERVAL == 0) {
                    lobBomb(caster, level, vent, radius, p, power);
                }
            }
            return false;
        });
    }

    /** The surface around the vent turns to magma blocks for a while (they heal back). */
    private static void moltenGround(ServerLevel level, Vec3 vent, float radius) {
        int r = (int) Math.ceil(radius * 0.8f);
        BlockPos origin = BlockPos.containing(vent);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r || level.random.nextFloat() > 0.65f) {
                    continue;
                }
                // The top solid block of the column (within 2 of the vent's height).
                for (int dy = 1; dy >= -2; dy--) {
                    BlockPos at = origin.offset(dx, dy, dz);
                    if (!level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
                        Destruction.temporary(level, at, Blocks.MAGMA_BLOCK.defaultBlockState(), MAGMA_REGEN);
                        break;
                    }
                }
            }
        }
    }

    /** A lava bomb thrown from the vent: you see it arc, then it bursts where it lands. */
    private static void lobBomb(LivingEntity caster, ServerLevel level, Vec3 vent, float radius, JutsuDefinition.Params p, float power) {
        double a = level.random.nextDouble() * Math.PI * 2;
        double d = radius * (0.4 + 0.9 * level.random.nextDouble());
        Vec3 land = ElementalBehaviors.ground(level, vent.add(Math.cos(a) * d, 2, Math.sin(a) * d));
        Vec3 start = vent.add(0, 0.8, 0);
        // The particle falls 0.04 a tick per tick (gravity 1, no drag): pick the throw that lands it on time.
        int t = BOMB_FLIGHT;
        Vec3 delta = land.subtract(start);
        Vec3 velocity = new Vec3(delta.x / t, (delta.y + 0.02 * t * (t - 1)) / t, delta.z / t);
        Vfx.one(level, new VfxOptions(VfxKind.MAGMA, 0xFFFFFF, 0xFFB080, 0.9f, 0.8f, t, 1f, 1f), start, velocity);
        Vfx.one(level, new VfxOptions(VfxKind.GLOW, KekkeiVfxA.LAVA_HOT, KekkeiVfxA.LAVA_ORANGE, 0.9f, 0.6f, t, 1f, 1f), start, velocity);
        int[] wait = {0};
        GreatTechniques.later(now -> {
            if (++wait[0] < t) {
                return false;
            }
            KekkeiVfxA.lavaSplash(level, land.add(0, 0.3, 0), 1.1f);
            for (LivingEntity victim : KekkeiTechniquesA.around(caster, level, land.add(0, 0.6, 0), 1.6)) {
                JutsuEffects.hit(caster, victim, p.damage() * power * BOMB_SHARE, 0.3f, p.posture() * power * BOMB_SHARE, p.fireSeconds(),
                        victim.position().subtract(land));
            }
            Destruction.scorch(level, land, 1.2f);
            KekkeiTechniquesA.playAt(level, land, com.noirm.sound.NoirmSounds.LAVA_HISS.get(), 1.5f, 0.84f);
            return true;
        });
    }

    // ================================================================ Acid Mist

    static void acidBreath(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        Vec3 from = caster.getEyePosition().add(0, -0.25, 0);
        Vec3 dir = ctx.look();
        KekkeiVfxA.steamCone(level, from, dir, p.range(), KekkeiVfxA.ACID);
        for (LivingEntity victim : JutsuBehaviors.inCone(caster, p.range(), 32)) {
            JutsuEffects.hit(caster, victim, p.damage() * power, p.knockback(), p.posture() * power, 0, victim.position().subtract(caster.position()));
            JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
            corrode(victim, Math.round(ACID_CORRODE * power));
            Vfx.burst(level, new VfxOptions(VfxKind.STEAM, KekkeiVfxA.ACID, KekkeiVfxA.STEAM_GREY, 0.5f, 1.2f, 30, -0.01f, 0.9f),
                    victim.position().add(0, victim.getBbHeight() * 0.6, 0), 4, 0.3, 0.02);
        }
        int[] age = {0};
        GreatTechniques.later(now -> {
            if (++age[0] > 8 || !caster.isAlive()) {
                return true;
            }
            if (age[0] % 2 == 0) {
                KekkeiVfxA.steamCone(level, from, dir, p.range() * (0.6 + 0.05 * age[0]), KekkeiVfxA.ACID);
            }
            return false;
        });
        KekkeiTechniquesA.playAt(level, from, com.noirm.sound.NoirmSounds.BOIL_STEAM.get(), 1.2f, 0.89f);
    }

    /** Acid eats into whatever armour a body wears. */
    static void corrode(LivingEntity victim, int amount) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = victim.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.isDamageableItem()) {
                stack.hurtAndBreak(amount, victim, e -> e.broadcastBreakEvent(slot));
            }
        }
    }

    // ================================================================ Scalding Geyser

    static void geyser(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        Vec3 at = KekkeiTechniquesA.groundAim(caster, level, p.range());
        float radius = Math.max(1f, p.radius());
        int delay = Math.max(1, p.duration());
        int[] age = {0};
        KekkeiTechniquesA.playAt(level, at, com.noirm.sound.NoirmSounds.BOIL_STEAM.get(), 1.5f, 0.89f);
        GreatTechniques.later(now -> {
            age[0]++;
            if (age[0] < delay) {
                KekkeiVfxA.hiss(level, at, radius);
                return false;
            }
            if (age[0] == delay) {
                // The jet bursts out: a flash of heat, a column of boiling water and steam.
                KekkeiVfxA.steamBurst(level, at.add(0, 0.5, 0), radius * 1.2f);
                Vfx.quake(level, at, 0.5f, 14);
                KekkeiTechniquesA.playAt(level, at, com.noirm.sound.NoirmSounds.EXPLOSION_BOOM.get(), 1f, 1.18f);
                KekkeiTechniquesA.playAt(level, at, com.noirm.sound.NoirmSounds.BOIL_STEAM.get(), 2f, 0.71f);
                for (LivingEntity victim : KekkeiTechniquesA.around(caster, level, at.add(0, 1, 0), radius + 0.5)) {
                    JutsuEffects.hit(caster, victim, p.damage() * power, 0, p.posture() * power, 0, Vec3.ZERO);
                    JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
                    victim.setDeltaMovement(victim.getDeltaMovement().x * 0.2, p.knockback(), victim.getDeltaMovement().z * 0.2);
                    victim.hurtMarked = true;
                }
                Destruction.douse(level, at, radius + 1);
            }
            KekkeiVfxA.geyser(level, at, 7f * (1f - (age[0] - delay) / 20f), radius);
            return age[0] >= delay + 16;
        });
    }

    // ================================================================ Boiling Vapour Armour

    static void steamCharge(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        int duration = Math.max(4, p.duration());
        double speed = p.range() / duration;
        Vec3 dir = ctx.flatLook();
        Set<LivingEntity> hit = new HashSet<>();
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration + 10, 1, false, false));
        KekkeiVfxA.steamBurst(level, caster.position().add(0, 1, 0), 1.6f);
        Vfx.quake(level, caster.position(), 0.4f, 12);
        KekkeiTechniquesA.playAt(level, caster.position(), com.noirm.sound.NoirmSounds.BOIL_STEAM.get(), 1.5f, 0.77f);
        int[] age = {0};
        GreatTechniques.later(now -> {
            age[0]++;
            if (!caster.isAlive() || caster.level() != level) {
                return true;
            }
            // Nothing stops the charge: no stagger, no knockback, only walls.
            caster.setDeltaMovement(dir.x * speed, Math.min(caster.getDeltaMovement().y, 0), dir.z * speed);
            caster.hurtMarked = true;
            KekkeiVfxA.vapourArmor(level, caster, dir.scale(speed));
            for (LivingEntity victim : KekkeiTechniquesA.around(caster, level, caster.position().add(dir.scale(0.6)).add(0, 0.9, 0), 1.3)) {
                if (!hit.add(victim)) {
                    continue;
                }
                // Bowled aside and scalded.
                Vec3 side = new Vec3(-dir.z, 0, dir.x);
                Vec3 away = victim.position().subtract(caster.position());
                Vec3 push = dir.add(side.scale(side.dot(away) >= 0 ? 1 : -1)).normalize();
                JutsuEffects.hit(caster, victim, p.damage() * power, p.knockback(), p.posture() * power, 0, push);
                JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
                KekkeiVfxA.steamBurst(level, victim.position().add(0, 1, 0), 1.2f);
                KekkeiTechniquesA.playAt(level, victim.position(), com.noirm.sound.NoirmSounds.HIT_HEAVY.get(), 1.2f, 0.84f);
            }
            if (age[0] >= duration || caster.horizontalCollision) {
                caster.setDeltaMovement(Vec3.ZERO);
                caster.hurtMarked = true;
                KekkeiVfxA.steamBurst(level, caster.position().add(0, 1, 0), 1.4f);
                return true;
            }
            return false;
        });
    }

    // ================================================================ Scalding Pressure Dome

    static void pressureDome(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel level = ctx.level();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power();
        float radius = p.radius() * (float) Math.sqrt(Math.max(0.5f, power));
        Vec3 center = KekkeiTechniquesA.groundAim(caster, level, p.range());
        int duration = Math.max(10, p.duration());
        KekkeiVfxA.steamBurst(level, center.add(0, 0.5, 0), 1.5f);
        KekkeiTechniquesA.playAt(level, center, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 2f, 0.6f);
        int[] age = {0};
        GreatTechniques.later(now -> {
            age[0]++;
            float progress = Math.min(1f, age[0] / (float) duration);
            float shell = radius * (1.15f - 0.25f * progress); // the pressure squeezes it in
            KekkeiVfxA.pressureDome(level, center, shell, progress, age[0]);
            if (age[0] % DOME_SCALD_INTERVAL == 0) {
                KekkeiTechniquesA.playAt(level, center, com.noirm.sound.NoirmSounds.BOIL_STEAM.get(), 1.2f, 0.5f + progress * 0.6f);
                for (LivingEntity victim : KekkeiTechniquesA.around(caster, level, center.add(0, 1, 0), shell)) {
                    JutsuEffects.hit(caster, victim, p.damage() * power * DOME_SCALD_SHARE, 0, 0, 0, Vec3.ZERO);
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DOME_SCALD_INTERVAL + 5, 1, false, false));
                }
            }
            if (age[0] < duration) {
                return false;
            }
            // The dome bursts.
            KekkeiVfxA.steamBurst(level, center.add(0, radius * 0.4, 0), radius * 1.3f);
            KekkeiVfxA.steamBurst(level, center.add(0, 0.5, 0), radius);
            KekkeiVfxA.flashCloud(level, center.add(0, radius * 0.4, 0), 0xFFFFFF, KekkeiVfxA.STEAM_HEAT, radius * 1.4f, 6);
            Vfx.quake(level, center, 1.1f, radius * 6);
            KekkeiTechniquesA.playAt(level, center, com.noirm.sound.NoirmSounds.BIG_IMPACT.get(), 2.5f, 0.84f);
            for (LivingEntity victim : KekkeiTechniquesA.around(caster, level, center.add(0, 1, 0), radius * 1.1f)) {
                float falloff = 1f - (float) Math.min(1, victim.position().distanceTo(center) / (radius + 1));
                JutsuEffects.hit(caster, victim, p.damage() * power * (0.5f + 0.5f * falloff), p.knockback(), p.posture() * power, 0,
                        victim.position().subtract(center));
                JutsuEffects.applyEffects(victim, p.effect(), p.effectTicks());
            }
            Destruction.douse(level, center, radius + 1);
            Destruction.shatterFragile(level, BlockPos.containing(center), (int) Math.ceil(radius * 0.6f));
            return true;
        });
    }
}

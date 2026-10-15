package com.noirm.dojutsu;

import com.noirm.Noirm;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuEffects;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.vfx.Vfx;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Susanoo: a giant of chakra built around the user, in their own colour. Summoned with the
 * {@code mangekyo/susanoo} jutsu; it grows a stage every {@link #GROW_TICKS} while kept up:
 * <ol>
 *   <li>a ribcage (damage taken × {@link #DEFENSE}[1])</li>
 *   <li>a torso with arms: less damage, and the jutsu key now <b>swipes</b> with a giant arm</li>
 *   <li>the full warrior: least damage, the widest, hardest swipe (and you're slower)</li>
 * </ol>
 * It drains chakra fast ({@link #DRAIN_PER_SECOND} by stage) and strains the eyes while up; out
 * of chakra it collapses. Crouch + the key dismisses it. The Eternal Mangekyō drains less and
 * grows faster. Drawn as a shell of particles (no model needed).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class Susanoo {
    public static final int MAX_STAGE = 3;
    /** Ticks per stage of growth (Eternal: half). */
    public static final int GROW_TICKS = 60;
    /** Damage taken multiplier by stage (index 0 = none). */
    public static final float[] DEFENSE = {1f, 0.6f, 0.45f, 0.3f};
    /** Chakra per second by stage. */
    public static final float[] DRAIN_PER_SECOND = {0f, 3f, 5f, 7f};
    public static final float ETERNAL_DRAIN = 0.6f;
    /** Chakra to summon it, and per swipe (players). */
    public static final float SUMMON_COST = 25f;
    public static final float SWIPE_COST = 10f;
    /** The swipe: damage (× power) and reach by stage. */
    public static final float[] SWIPE_DAMAGE = {0f, 0f, 12f, 18f};
    public static final float[] SWIPE_RANGE = {0f, 0f, 4.5f, 6.5f};
    public static final float SWIPE_KNOCKBACK = 2.2f;
    public static final float SWIPE_POSTURE = 40f;
    /** Eye strain: summoning, and per second while it stands (× stage). */
    public static final float SUMMON_STRAIN = 4f;
    public static final float STRAIN_PER_SECOND_PER_STAGE = 0.1f;
    /** NPC Susanoo colour. */
    public static final int NPC_COLOR = 0x8A3CFF;

    private static final class State {
        final int color;
        int ticks;
        int stage = 1;

        State(int color) {
            this.color = color;
        }
    }

    private static final Map<LivingEntity, State> ACTIVE = new WeakHashMap<>();

    private Susanoo() {}

    public static int stage(LivingEntity entity) {
        State state = ACTIVE.get(entity);
        return state == null ? 0 : state.stage;
    }

    public static boolean isActive(LivingEntity entity) {
        return ACTIVE.containsKey(entity);
    }

    /** The jutsu: summon it; while up, swipe (stage 2+); crouching, dismiss it. */
    public static void cast(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        State state = ACTIVE.get(caster);
        if (state == null) {
            summon(caster, ctx.power());
        } else if (caster.isShiftKeyDown()) {
            dismiss(caster);
        } else if (state.stage >= 2) {
            swipe(caster, ctx.power() * Mangekyo.powerScale(caster));
        } else {
            MangekyoAbilities.message(caster, Component.translatable("message.noirm.susanoo.not_ready"));
        }
    }

    public static boolean summon(LivingEntity caster, float power) {
        if (caster instanceof Player player && !player.getAbilities().instabuild) {
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            if (data == null || !data.tryConsumeChakra(SUMMON_COST)) {
                MangekyoAbilities.message(caster, Component.translatable("message.noirm.jutsu.no_chakra"));
                return false;
            }
        }
        State state = new State(color(caster));
        ACTIVE.put(caster, state);
        dirty(caster);
        Mangekyo.addStrain(caster, SUMMON_STRAIN);
        if (caster.level() instanceof ServerLevel level) {
            Vfx.Palette palette = palette(state.color);
            Vfx.flash(level, caster.position().add(0, 1.2, 0), palette, 2.5f);
            Vfx.shockwave(level, caster.position(), palette, 4f);
            Vfx.quake(level, caster.position(), 0.4f, 14);
        }
        MangekyoAbilities.sound(caster, SoundEvents.BEACON_ACTIVATE, 1.4f, 0.5f);
        MangekyoAbilities.sound(caster, SoundEvents.WITHER_SPAWN, 0.4f, 1.6f);
        return true;
    }

    public static void dismiss(LivingEntity entity) {
        if (ACTIVE.remove(entity) == null) {
            return;
        }
        dirty(entity);
        entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (entity.level() instanceof ServerLevel level) {
            Vfx.burst(level, Vfx.smoke(0x404050, 0.8f, 20), entity.position().add(0, 1.5, 0), 14, 0.8, 0.03);
        }
        MangekyoAbilities.sound(entity, SoundEvents.BEACON_DEACTIVATE, 1.2f, 0.6f);
    }

    /** A giant arm sweeps the arc in front: everything in reach is hit hard and thrown. Returns how many were hit. */
    public static int swipe(LivingEntity caster, float power) {
        State state = ACTIVE.get(caster);
        if (state == null || state.stage < 2) {
            return 0;
        }
        if (caster instanceof Player player && !player.getAbilities().instabuild) {
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            if (data == null || !data.tryConsumeChakra(SWIPE_COST)) {
                MangekyoAbilities.message(caster, Component.translatable("message.noirm.jutsu.no_chakra"));
                return 0;
            }
        }
        float range = SWIPE_RANGE[state.stage];
        Vec3 look = caster.getViewVector(1f);
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        int hits = 0;
        for (LivingEntity target : caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range, 3, range),
                e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && e.distanceTo(caster) <= range + e.getBbWidth())) {
            Vec3 to = target.position().subtract(caster.position());
            Vec3 toFlat = new Vec3(to.x, 0, to.z);
            if (toFlat.lengthSqr() > 1 && flat.dot(toFlat.normalize()) < Math.cos(Math.toRadians(65))) {
                continue;
            }
            JutsuEffects.hit(caster, target, SWIPE_DAMAGE[state.stage] * power, SWIPE_KNOCKBACK, SWIPE_POSTURE, 0, to);
            hits++;
        }
        if (caster.level() instanceof ServerLevel level) {
            Vfx.Palette palette = palette(state.color);
            Vec3 center = caster.position().add(0, 1.6, 0);
            for (int i = -6; i <= 6; i++) {
                Vec3 dir = flat.yRot(i * 10 * Mth.DEG_TO_RAD);
                Vfx.burst(level, Vfx.glow(palette.core(), palette.mid(), 0.7f, 0.1f, 8), center.add(dir.scale(range * 0.85)), 2, 0.15, 0.02);
            }
            Vfx.cone(level, center, flat, range, palette, 1.5f);
            Vfx.quake(level, caster.position(), 0.35f, 12);
        }
        MangekyoAbilities.sound(caster, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f, 0.4f);
        MangekyoAbilities.sound(caster, SoundEvents.ENDER_DRAGON_FLAP, 1.2f, 0.6f);
        return hits;
    }

    // ---------------------------------------------------------------- ticking

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide() && ACTIVE.containsKey(entity)) {
            tick(entity);
        }
    }

    /** One tick: grow, pay (chakra and strain), collapse if the eyes closed or chakra ran out, draw it. */
    public static void tick(LivingEntity entity) {
        State state = ACTIVE.get(entity);
        if (state == null) {
            return;
        }
        if (!entity.isAlive() || !Mangekyo.canUse(entity, null)) {
            dismiss(entity);
            return;
        }
        boolean eternal = Mangekyo.isEternal(entity);
        state.ticks++;
        int grow = eternal ? GROW_TICKS / 2 : GROW_TICKS;
        int stage = Math.min(MAX_STAGE, 1 + state.ticks / grow);
        if (stage > state.stage) {
            state.stage = stage;
            dirty(entity);
            MangekyoAbilities.sound(entity, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.2f, 0.5f);
            MangekyoAbilities.message(entity, Component.translatable("message.noirm.susanoo.stage." + stage));
        }
        float drain = DRAIN_PER_SECOND[state.stage] * (eternal ? ETERNAL_DRAIN : 1f) / 20f;
        boolean paid = true;
        if (entity instanceof Player player) {
            paid = player.getAbilities().instabuild || ShinobiCapability.get(player).map(d -> d.tryConsumeChakra(drain)).orElse(false);
        } else if (entity instanceof SparringNinja ninja) {
            paid = ninja.tryUseChakra(drain);
        }
        if (!paid) {
            MangekyoAbilities.message(entity, Component.translatable("message.noirm.susanoo.collapses"));
            dismiss(entity);
            return;
        }
        if (state.ticks % 20 == 0) {
            Mangekyo.addStrain(entity, STRAIN_PER_SECOND_PER_STAGE * state.stage);
        }
        if (state.stage >= MAX_STAGE && state.ticks % 20 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, false));
        }
        if (entity.tickCount % 2 == 0 && entity.level() instanceof ServerLevel level) {
            draw(level, entity, state);
        }
    }

    /** Damage taken while it stands (runs after armour, before the guard). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void hurt(LivingHurtEvent event) {
        int stage = stage(event.getEntity());
        if (stage > 0 && !event.getEntity().level().isClientSide()) {
            event.setAmount(event.getAmount() * DEFENSE[stage]);
            if (event.getEntity().level() instanceof ServerLevel level) {
                Vfx.burst(level, Vfx.spark(ACTIVE.get(event.getEntity()).color, 0.3f, 8), event.getEntity().position().add(0, 1.2, 0), 6, 0.6, 0.1);
            }
        }
    }

    /** The defense multiplier for {@code entity} (1 without a Susanoo). */
    public static float defenseMultiplier(LivingEntity entity) {
        return DEFENSE[stage(entity)];
    }

    // ---------------------------------------------------------------- the look: a shell of light

    private static void draw(ServerLevel level, LivingEntity entity, State state) {
        int color = state.color;
        var glow = Vfx.glow(lighten(color), color, 0.32f, 0.12f, 5);
        float yaw = entity.getYRot() * Mth.DEG_TO_RAD;
        Vec3 base = entity.position();
        float scale = state.stage == 1 ? 1f : state.stage == 2 ? 1.5f : 2.2f;
        // Ribs: arcs around the chest, open at the front.
        for (int rib = 0; rib < 4; rib++) {
            double y = (0.7 + rib * 0.3) * scale;
            double radius = (0.75 - Math.abs(rib - 1.5) * 0.08) * scale;
            for (int a = 30; a <= 330; a += 30) {
                point(level, glow, base, yaw, Math.sin(Math.toRadians(a)) * radius, y, -Math.cos(Math.toRadians(a)) * radius * 0.7);
            }
        }
        // Spine.
        for (double y = 0.4; y <= 1.9; y += 0.25) {
            point(level, glow, base, yaw, 0, y * scale, 0.55 * scale);
        }
        if (state.stage >= 2) {
            // Shoulders and two great arms hanging forward.
            for (int side = -1; side <= 1; side += 2) {
                for (double t = 0; t <= 1; t += 0.12) {
                    point(level, glow, base, yaw, side * (0.9 + 0.5 * t) * scale, (2.0 - 1.1 * t) * scale, (-0.2 - 0.6 * t) * scale);
                }
            }
            // Head: a ring above the shoulders.
            for (int a = 0; a < 360; a += 40) {
                point(level, glow, base, yaw, Math.sin(Math.toRadians(a)) * 0.35 * scale, 2.35 * scale + Math.cos(Math.toRadians(a)) * 0.3 * scale, 0);
            }
        }
        if (state.stage >= 3) {
            // The full warrior: a mantle down to the ground and a blade in the right hand.
            for (int a = 0; a < 360; a += 24) {
                point(level, glow, base, yaw, Math.sin(Math.toRadians(a)) * 1.2 * scale * 0.6, 0.15, Math.cos(Math.toRadians(a)) * 0.9 * scale * 0.6);
            }
            for (double t = 0; t <= 1; t += 0.1) {
                point(level, glow, base, yaw, -(1.4 + 0.3 * t) * scale, (0.9 + 1.6 * t) * scale, (-0.8 - 0.8 * t) * scale);
            }
            Vfx.aura(level, base, entity.getBbWidth() * scale * 1.4f, entity.getBbHeight() * scale, palette(color), 3);
        }
    }

    /** A point of the shell in the entity's own frame (x right, z back), turned to its facing. */
    private static void point(ServerLevel level, net.minecraft.core.particles.ParticleOptions particle, Vec3 base, float yaw, double x, double y, double z) {
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        // Forward is (-sin, cos), the right hand is (-cos, -sin); local z points backward.
        double wx = -cos * x + sin * z;
        double wz = -sin * x - cos * z;
        Vfx.one(level, particle, base.add(wx, y, wz), Vec3.ZERO);
    }

    private static void dirty(LivingEntity entity) {
        if (entity instanceof Player player) {
            ShinobiCapability.get(player).ifPresent(ShinobiData::markPowersDirty);
        }
    }

    private static int color(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> d.eyes().susanooColor()).orElse(NPC_COLOR);
        }
        return entity instanceof SparringNinja ninja ? MangekyoNpcs.susanooColor(ninja) : NPC_COLOR;
    }

    static Vfx.Palette palette(int color) {
        return new Vfx.Palette(lighten(color), color, darken(color), lighten(color));
    }

    private static int lighten(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return ((r + 255) / 2 << 16) | ((g + 255) / 2 << 8) | (b + 255) / 2;
    }

    private static int darken(int color) {
        return ((color >> 16 & 0xFF) / 5 << 16) | ((color >> 8 & 0xFF) / 5 << 8) | (color & 0xFF) / 5;
    }
}

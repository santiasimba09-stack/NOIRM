package com.noirm.dojutsu;

import com.noirm.Noirm;
import com.noirm.ally.Crew;
import com.noirm.combat.CombatSystem;
import com.noirm.jutsu.JutsuContext;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.jutsu.JutsuEffects;
import com.noirm.network.MangekyoPackets;
import com.noirm.network.NoirmNetwork;
import com.noirm.vfx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What the Mangekyō's eye abilities do (the jutsu behaviors {@code amaterasu}, {@code tsukuyomi},
 * {@code kamui}, {@code kotoamatsukami}, {@code izanagi}; Susanoo is {@link Susanoo}). Every use
 * strains the eyes ({@link Mangekyo#addStrain}) and comes out weaker as the strain grows.
 * Server side; the numbers are in each jutsu's JSON and the constants below.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class MangekyoAbilities {
    /** Black flames: damage per second (× power) comes from the JSON; they burn this long at least once lit. */
    public static final int AMATERASU_MIN_TICKS = 60;
    /** Black flames left on the ground last this long (and burn whoever walks in). */
    public static final int FLAME_PATCH_TICKS = 160;
    public static final int MAX_FLAME_PATCHES = 16;
    /** Tsukuyomi: share of the target's max health taken as mental damage (× power, at most 2×), capped. */
    public static final float TSUKUYOMI_HEALTH_SHARE = 0.25f;
    public static final float TSUKUYOMI_MAX_DAMAGE = 60f;
    /** A player must be looking within this angle of the caster to be caught (eye contact). */
    public static final double EYE_CONTACT_DEGREES = 60;
    /** Kamui's warp sends a target this far away (blocks, random direction). */
    public static final double KAMUI_WARP_MIN = 8;
    public static final double KAMUI_WARP_MAX = 14;
    /** Izanagi: extra strain when it actually rewrites a death (the eye pays for it). */
    public static final float IZANAGI_TRIGGER_STRAIN = 20f;
    /** Izanagi restores at least this share of max health. */
    public static final float IZANAGI_MIN_HEALTH = 0.3f;

    private static final Vfx.Palette BLACK_FLAME = new Vfx.Palette(0x40204A, 0x14001C, 0x000000, 0x5A0A6A);
    private static final Vfx.Palette ILLUSION = new Vfx.Palette(0xFFE0E0, 0xD00010, 0x050000, 0x200000);
    private static final Vfx.Palette WARP = new Vfx.Palette(0xF0F0FF, 0x2A2A40, 0x050510, 0x9090C0);

    private record Burn(Entity owner, float dps, long until) {}

    private record Patch(ServerLevel level, Vec3 center, float radius, long until, float dps, Entity owner) {}

    private record Control(LivingEntity owner, long until) {}

    private record Armed(long until, float health, Vec3 pos) {}

    private static final Map<LivingEntity, Burn> BURNING = new WeakHashMap<>();
    private static final List<Patch> PATCHES = new ArrayList<>();
    private static final Map<LivingEntity, Long> PHASED = new WeakHashMap<>();
    private static final Map<Mob, Control> CONTROLLED = new WeakHashMap<>();
    private static final Map<LivingEntity, Armed> IZANAGI = new WeakHashMap<>();

    private MangekyoAbilities() {}

    // ---------------------------------------------------------------- Amaterasu

    /** Black flames where you look: on the creature you're looking at, or on the ground there. */
    public static void amaterasu(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        JutsuDefinition.Params p = ctx.params();
        float dps = p.damage() * ctx.power() * Mangekyo.powerScale(caster);
        LivingEntity target = lookTarget(caster, p.range(), 8);
        Vec3 eye = caster.getEyePosition();
        Vec3 at;
        if (target != null) {
            ignite(target, caster, dps, p.duration());
            at = target.position().add(0, target.getBbHeight() * 0.5, 0);
        } else {
            HitResult hit = ctx.level().clip(new ClipContext(eye, eye.add(ctx.look().scale(p.range())),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            at = hit.getLocation();
            addPatch(ctx.level(), at, p.radius(), dps, caster);
        }
        // A black streak from the eye, then the flames bloom.
        Vfx.line(ctx.level(), eye, at, BLACK_FLAME, 0.12f);
        Vfx.burst(ctx.level(), Vfx.smoke(0x050008, 0.9f, 30), at, 16, 0.5, 0.03);
        Vfx.burst(ctx.level(), Vfx.glow(0x5A0A6A, 0x000000, 0.8f, 0.2f, 14), at, 6, 0.4, 0.02);
        bleed(caster);
        sound(caster, SoundEvents.BLAZE_SHOOT, 1f, 0.4f);
        sound(caster, SoundEvents.SOUL_ESCAPE, 1.2f, 0.5f);
        Mangekyo.addStrain(caster, MangekyoAbility.AMATERASU.strain());
    }

    /** Sets {@code target} ablaze in black flames (water doesn't put them out). */
    public static void ignite(LivingEntity target, Entity owner, float dps, int ticks) {
        long until = target.level().getGameTime() + Math.max(AMATERASU_MIN_TICKS, ticks);
        Burn old = BURNING.get(target);
        if (old == null || old.until() < until || old.dps() < dps) {
            BURNING.put(target, new Burn(owner, Math.max(dps, old == null ? 0 : old.dps()), Math.max(until, old == null ? 0 : old.until())));
        }
    }

    public static boolean isBurning(LivingEntity entity) {
        Burn burn = BURNING.get(entity);
        return burn != null && burn.until() > entity.level().getGameTime();
    }

    private static void addPatch(ServerLevel level, Vec3 at, float radius, float dps, Entity owner) {
        if (PATCHES.size() >= MAX_FLAME_PATCHES) {
            PATCHES.remove(0);
        }
        PATCHES.add(new Patch(level, at, Math.max(1f, radius), level.getGameTime() + FLAME_PATCH_TICKS, dps, owner));
    }

    public static int flamePatches() {
        return PATCHES.size();
    }

    private static void tickBurn(LivingEntity entity) {
        Burn burn = BURNING.get(entity);
        if (burn == null) {
            return;
        }
        long now = entity.level().getGameTime();
        if (now > burn.until() || !entity.isAlive()) {
            BURNING.remove(entity);
            return;
        }
        if (entity.level() instanceof ServerLevel level) {
            if (entity.tickCount % 2 == 0) {
                Vec3 at = entity.position().add(0, entity.getBbHeight() * 0.4, 0);
                Vfx.burst(level, Vfx.smoke(0x030005, 0.55f, 18), at, 3, entity.getBbWidth() * 0.4, 0.04);
                Vfx.burst(level, Vfx.glow(0x3A0A4A, 0x000000, 0.35f, 0.1f, 8), at, 1, entity.getBbWidth() * 0.3, 0.02);
            }
            if ((now - burn.until()) % 20 == 0) {
                Entity owner = burn.owner();
                DamageSource source = owner != null ? level.damageSources().indirectMagic(owner, owner) : level.damageSources().magic();
                entity.invulnerableTime = 0;
                entity.hurt(source, burn.dps());
                if (entity.tickCount % 40 == 0) {
                    level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.HOSTILE, 1f, 0.5f);
                }
            }
        }
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PATCHES.isEmpty()) {
            return;
        }
        PATCHES.removeIf(patch -> patch.level().getGameTime() > patch.until());
        for (Patch patch : List.copyOf(PATCHES)) {
            ServerLevel level = patch.level();
            if (level.getGameTime() % 3 == 0) {
                Vfx.burst(level, Vfx.smoke(0x030005, 0.7f, 22), patch.center().add(0, 0.3, 0), 4, patch.radius() * 0.5, 0.03);
                Vfx.burst(level, Vfx.glow(0x3A0A4A, 0x000000, 0.4f, 0.1f, 10), patch.center().add(0, 0.2, 0), 2, patch.radius() * 0.4, 0.02);
            }
            if (level.getGameTime() % 10 == 0) {
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(patch.center(), patch.center()).inflate(patch.radius(), 1.5, patch.radius()),
                        e -> e.isAlive() && !(patch.owner() instanceof LivingEntity o && JutsuEffects.isFriendly(o, e)))) {
                    ignite(entity, patch.owner(), patch.dps(), AMATERASU_MIN_TICKS);
                }
            }
        }
    }

    // ---------------------------------------------------------------- Tsukuyomi

    /**
     * Eye contact traps the target in a genjutsu: frozen for {@code stun} ticks, a share of their
     * health torn from the mind, weakened for {@code duration}. A player who looks away isn't
     * caught; another open Mangekyō resists.
     */
    public static void tsukuyomi(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        JutsuDefinition.Params p = ctx.params();
        LivingEntity target = lookTarget(caster, p.range(), 8);
        if (target == null) {
            message(caster, Component.translatable("message.noirm.genjutsu.no_target"));
            return;
        }
        if (target instanceof Player && !facing(target, caster)) {
            message(caster, Component.translatable("message.noirm.mangekyo.tsukuyomi.looked_away", target.getDisplayName()));
            return;
        }
        if (Mangekyo.isActive(target)) {
            Vfx.burst(ctx.level(), Vfx.ring(0xD00010, 1.5f, 10), target.getEyePosition(), 1, 0, 0);
            message(caster, Component.translatable("message.noirm.genjutsu.resisted", target.getDisplayName()));
            Mangekyo.addStrain(caster, MangekyoAbility.TSUKUYOMI.strain() * 0.5f);
            return;
        }
        float power = Mth.clamp(ctx.power() * Mangekyo.powerScale(caster), 0.25f, 2f);
        catchInTsukuyomi(caster, target, p.stun(), p.duration(), power);
        Mangekyo.addStrain(caster, MangekyoAbility.TSUKUYOMI.strain());
    }

    /** The Tsukuyomi's effect on {@code target} (public for tests). Returns the damage dealt. */
    public static float catchInTsukuyomi(LivingEntity caster, LivingEntity target, int stun, int duration, float power) {
        float damage = Math.min(TSUKUYOMI_MAX_DAMAGE, target.getMaxHealth() * TSUKUYOMI_HEALTH_SHARE * power);
        CombatSystem.stun(target, stun);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 6, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 1, false, false));
        if (target instanceof Mob mob) {
            mob.setTarget(null);
        }
        target.invulnerableTime = 0;
        target.hurt(caster.damageSources().indirectMagic(caster, caster), damage);
        if (target instanceof ServerPlayer player) {
            NoirmNetwork.sendToPlayer(player, new MangekyoPackets.Tsukuyomi(stun + 40));
            player.displayClientMessage(Component.translatable("message.noirm.mangekyo.tsukuyomi.caught").withStyle(ChatFormatting.DARK_RED), false);
        }
        if (caster.level() instanceof ServerLevel level) {
            Vfx.spiral(level, target.position(), ILLUSION, target.getBbWidth() + 0.6f, target.getBbHeight() + 0.6f, 4f);
            Vfx.burst(level, Vfx.ring(0xD00010, 3f, 20), target.position().add(0, target.getBbHeight() * 0.8, 0), 1, 0, 0);
            Vfx.burst(level, Vfx.ring(0x100000, 4.5f, 26), target.position().add(0, 0.1, 0), 1, 0, 0);
            Vfx.line(level, caster.getEyePosition(), target.getEyePosition(), ILLUSION, 0.06f);
        }
        bleed(caster);
        sound(target, SoundEvents.ILLUSIONER_CAST_SPELL, 1f, 0.5f);
        sound(target, SoundEvents.WARDEN_HEARTBEAT, 1.5f, 0.5f);
        message(caster, Component.translatable("message.noirm.genjutsu.caught", target.getDisplayName()));
        return damage;
    }

    // ---------------------------------------------------------------- Kamui

    /**
     * Looking at someone: they're warped away (hurt and thrown {@link #KAMUI_WARP_MIN}–
     * {@link #KAMUI_WARP_MAX} blocks). Looking at a projectile coming in: it's swallowed. Neither
     * (or crouching): you turn intangible for {@code duration} ticks; hits and projectiles pass.
     */
    public static void kamui(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        JutsuDefinition.Params p = ctx.params();
        float power = ctx.power() * Mangekyo.powerScale(caster);
        LivingEntity target = caster.isShiftKeyDown() ? null : lookTarget(caster, p.range(), 8);
        Projectile projectile = caster.isShiftKeyDown() ? null : incomingProjectile(caster, p.range());
        if (target != null) {
            Vec3 from = target.position();
            JutsuEffects.hit(caster, target, p.damage() * power, 0, p.posture() * power, 0, Vec3.ZERO);
            warpAway(target);
            Vfx.spiral(ctx.level(), from, WARP, 1.2f, target.getBbHeight() + 0.4f, 3f);
            Vfx.burst(ctx.level(), Vfx.ring(0x2A2A40, 2.2f, 14), from.add(0, 1, 0), 1, 0, 0);
            Mangekyo.addStrain(caster, MangekyoAbility.KAMUI.strain() + 1f);
        } else if (projectile != null) {
            Vfx.spiral(ctx.level(), projectile.position().add(0, -0.5, 0), WARP, 0.6f, 1f, 2f);
            projectile.discard();
            Mangekyo.addStrain(caster, MangekyoAbility.KAMUI.strain() * 0.75f);
        } else {
            phase(caster, p.duration());
            Mangekyo.addStrain(caster, MangekyoAbility.KAMUI.strain());
        }
        sound(caster, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.5f);
    }

    /** Intangible for {@code ticks}: hits and projectiles pass through. */
    public static void phase(LivingEntity entity, int ticks) {
        PHASED.put(entity, entity.level().getGameTime() + ticks);
        if (entity.level() instanceof ServerLevel level) {
            Vfx.spiral(level, entity.position(), WARP, entity.getBbWidth() + 0.3f, entity.getBbHeight(), 2f);
        }
    }

    public static boolean isPhased(Entity entity) {
        Long until = entity instanceof LivingEntity living ? PHASED.get(living) : null;
        return until != null && until >= entity.level().getGameTime();
    }

    private static void warpAway(LivingEntity target) {
        var random = target.getRandom();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = KAMUI_WARP_MIN + random.nextDouble() * (KAMUI_WARP_MAX - KAMUI_WARP_MIN);
            if (target.randomTeleport(target.getX() + Math.cos(angle) * distance, target.getY() + random.nextInt(5) - 2,
                    target.getZ() + Math.sin(angle) * distance, true)) {
                return;
            }
        }
    }

    private static Projectile incomingProjectile(LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1f);
        return caster.level().getEntitiesOfClass(Projectile.class, caster.getBoundingBox().inflate(range),
                        e -> e.getOwner() != caster && look.dot(e.position().subtract(eye).normalize()) > Math.cos(Math.toRadians(20)))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(caster))).orElse(null);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attack(LivingAttackEvent event) {
        if (!event.getEntity().level().isClientSide() && isPhased(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void projectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && isPhased(hit.getEntity())) {
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        }
    }

    // ---------------------------------------------------------------- Kotoamatsukami

    /** Rewrites a creature's will: for {@code duration} ticks it fights on your side. Not players. */
    public static void kotoamatsukami(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        LivingEntity target = lookTarget(caster, ctx.params().range(), 8);
        if (!(target instanceof Mob mob)) {
            message(caster, Component.translatable(target == null ? "message.noirm.genjutsu.no_target" : "message.noirm.mangekyo.koto.not_a_mind"));
            return;
        }
        int ticks = Math.round(ctx.params().duration() * Mth.clamp(Mangekyo.powerScale(caster), 0.5f, 1.5f));
        control(mob, caster, ticks);
        Mangekyo.addStrain(caster, MangekyoAbility.KOTOAMATSUKAMI.strain());
    }

    /** {@code mob} serves {@code owner} for {@code ticks} (public for tests). */
    public static void control(Mob mob, LivingEntity owner, int ticks) {
        CONTROLLED.put(mob, new Control(owner, mob.level().getGameTime() + ticks));
        mob.setTarget(null);
        if (mob.level() instanceof ServerLevel level) {
            Vfx.burst(level, Vfx.ring(0xFFFFFF, 2f, 16), mob.getEyePosition(), 1, 0, 0);
            Vfx.radial(level, Vfx.smoke(0x101010, 0.4f, 30), mob.getEyePosition(), 14, 0.15); // black feathers
            Vfx.line(level, owner.getEyePosition(), mob.getEyePosition(), ILLUSION, 0.05f);
        }
        sound(mob, SoundEvents.EVOKER_CAST_SPELL, 1f, 0.7f);
        message(owner, Component.translatable("message.noirm.mangekyo.koto.controlled", mob.getDisplayName()));
    }

    /** Who {@code mob} serves right now, or null. */
    public static LivingEntity controller(Mob mob) {
        Control control = CONTROLLED.get(mob);
        return control != null && control.until() >= mob.level().getGameTime() && control.owner().isAlive() ? control.owner() : null;
    }

    private static void tickControl(Mob mob) {
        Control control = CONTROLLED.get(mob);
        if (control == null) {
            return;
        }
        if (controller(mob) == null) {
            CONTROLLED.remove(mob);
            mob.setTarget(null);
            message(control.owner(), Component.translatable("message.noirm.mangekyo.koto.fades", mob.getDisplayName()));
            return;
        }
        if (mob.tickCount % 10 != 0) {
            return;
        }
        LivingEntity owner = control.owner();
        LivingEntity current = mob.getTarget();
        if (current == null || !current.isAlive() || Crew.sameSide(owner, current)) {
            LivingEntity foe = mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(16),
                            e -> e != mob && e.isAlive() && !Crew.sameSide(owner, e) && !(e instanceof Mob m && controller(m) == owner)
                                    && (e instanceof Enemy || (e instanceof Mob m2 && m2.getTarget() != null && Crew.sameSide(owner, m2.getTarget()))))
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(mob))).orElse(null);
            mob.setTarget(foe);
        }
        if (mob.level() instanceof ServerLevel level) {
            Vfx.burst(level, Vfx.glow(0xFF2020, 0x400000, 0.25f, 0.05f, 10), mob.getEyePosition(), 1, 0.1, 0);
        }
    }

    @SubscribeEvent
    public static void changeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && event.getNewTarget() != null) {
            LivingEntity owner = controller(mob);
            if (owner != null && Crew.sameSide(owner, event.getNewTarget())) {
                event.setCanceled(true);
            }
        }
    }

    // ---------------------------------------------------------------- Izanagi

    /**
     * Rewrites reality for {@code duration} ticks: a death in that window doesn't happen. You're
     * back where you cast it, with the health you had then (at least 30%). The eye pays dearly.
     */
    public static void izanagi(JutsuContext ctx) {
        LivingEntity caster = ctx.caster();
        arm(caster, ctx.params().duration());
        Mangekyo.addStrain(caster, MangekyoAbility.IZANAGI.strain());
        sound(caster, SoundEvents.BEACON_POWER_SELECT, 1f, 0.5f);
    }

    public static void arm(LivingEntity entity, int ticks) {
        IZANAGI.put(entity, new Armed(entity.level().getGameTime() + ticks, entity.getHealth(), entity.position()));
        if (entity.level() instanceof ServerLevel level) {
            Vfx.burst(level, Vfx.ring(0xFFFFFF, 1.6f, 20), entity.getEyePosition(), 1, 0, 0);
            Vfx.burst(level, Vfx.ring(0xD00010, 2.4f, 24), entity.position().add(0, 0.05, 0), 1, 0, 0);
        }
    }

    public static boolean isArmed(LivingEntity entity) {
        Armed armed = IZANAGI.get(entity);
        return armed != null && armed.until() >= entity.level().getGameTime();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void death(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !isArmed(entity)) {
            return;
        }
        Armed armed = IZANAGI.remove(entity);
        event.setCanceled(true);
        Vec3 from = entity.position();
        entity.setHealth(Math.max(armed.health(), entity.getMaxHealth() * IZANAGI_MIN_HEALTH));
        entity.clearFire();
        BURNING.remove(entity);
        entity.teleportTo(armed.pos().x, armed.pos().y, armed.pos().z);
        entity.setDeltaMovement(Vec3.ZERO);
        if (entity.level() instanceof ServerLevel level) {
            Vfx.flash(level, from.add(0, 1, 0), ILLUSION, 1.5f);
            Vfx.burst(level, Vfx.smoke(0xF0F0F0, 0.8f, 24), from.add(0, 1, 0), 12, 0.4, 0.03);
            Vfx.burst(level, Vfx.ring(0xFFFFFF, 3f, 20), armed.pos().add(0, 0.05, 0), 1, 0, 0);
        }
        sound(entity, SoundEvents.TOTEM_USE, 0.8f, 0.5f);
        message(entity, Component.translatable("message.noirm.mangekyo.izanagi.rewritten"));
        Mangekyo.addStrain(entity, IZANAGI_TRIGGER_STRAIN);
    }

    // ---------------------------------------------------------------- ticking

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (!BURNING.isEmpty()) {
            tickBurn(entity);
        }
        if (entity instanceof Mob mob && !CONTROLLED.isEmpty()) {
            tickControl(mob);
        }
        if (entity.tickCount % 4 == 0 && entity.level() instanceof ServerLevel level) {
            if (isPhased(entity)) {
                Vfx.burst(level, Vfx.glow(0xB0B0E0, 0x202040, 0.3f, 0.05f, 8), entity.position().add(0, entity.getBbHeight() / 2, 0), 3,
                        entity.getBbWidth() * 0.5, 0.01);
            }
            if (isArmed(entity)) {
                Vfx.burst(level, Vfx.glow(0xFFFFFF, 0xD00010, 0.2f, 0.05f, 10), entity.getEyePosition(), 1, 0.2, 0);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Blood from the eye: the Mangekyō's price, visible to everyone. */
    static void bleed(LivingEntity caster) {
        if (caster.level() instanceof ServerLevel level) {
            Vfx.burst(level, new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.6f, 0f, 0f), 0.6f),
                    caster.getEyePosition().add(0, -0.1, 0), 4, 0.08, 0);
        }
    }

    /** Is {@code who} looking toward {@code at} (eye contact)? */
    static boolean facing(LivingEntity who, LivingEntity at) {
        Vec3 to = at.getEyePosition().subtract(who.getEyePosition()).normalize();
        return who.getViewVector(1f).dot(to) >= Math.cos(Math.toRadians(EYE_CONTACT_DEGREES));
    }

    /** The creature closest to the caster's line of sight, within {@code maxAngle}°. */
    static LivingEntity lookTarget(LivingEntity caster, double range, double maxAngle) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1f);
        if (caster instanceof Mob mob && mob.getTarget() != null && mob.distanceTo(mob.getTarget()) <= range
                && JutsuEffects.isTarget(caster, mob.getTarget()) && mob.hasLineOfSight(mob.getTarget())) {
            return mob.getTarget(); // NPCs: whoever they're fighting
        }
        return caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range),
                        e -> JutsuEffects.isTarget(caster, e) && e.isAlive() && !e.isSpectator() && caster.hasLineOfSight(e)
                                && e.distanceTo(caster) <= range)
                .stream()
                .filter(e -> angle(eye, look, e) <= maxAngle + Math.toDegrees(Math.atan2(e.getBbHeight() * 0.5,
                        Math.max(0.5, eye.distanceTo(e.position().add(0, e.getBbHeight() * 0.5, 0))))))
                .min(Comparator.comparingDouble(e -> angle(eye, look, e)))
                .orElse(null);
    }

    private static double angle(Vec3 eye, Vec3 look, LivingEntity entity) {
        Vec3 to = entity.position().add(0, entity.getBbHeight() * 0.5, 0).subtract(eye).normalize();
        return Math.toDegrees(Math.acos(Mth.clamp(look.dot(to), -1, 1)));
    }

    static void sound(Entity at, SoundEvent sound, float volume, float pitch) {
        at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    static void message(LivingEntity to, Component text) {
        if (to instanceof Player player) {
            player.displayClientMessage(text, true);
        }
    }

    /** Clears every lingering effect (tests). */
    public static void reset(LivingEntity entity) {
        BURNING.remove(entity);
        PHASED.remove(entity);
        IZANAGI.remove(entity);
        if (entity instanceof Mob mob) {
            CONTROLLED.remove(mob);
        }
    }
}

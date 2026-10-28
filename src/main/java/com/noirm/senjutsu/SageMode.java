package com.noirm.senjutsu;

import com.noirm.jutsu.JutsuBehavior;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuContext;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.vfx.Vfx;
import com.noirm.vfx.VfxKind;
import com.noirm.vfx.VfxOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

/**
 * Sage Mode: nature energy, balanced with your own chakra, floods the body. A toggle (the Sage
 * Mode jutsu in a loadout slot, or {@code /noirm sage on|off}) that needs at least
 * {@value #MIN_ENERGY} nature energy to start and burns {@value #DRAIN_PER_SECOND}/s while on
 * (twice as fast while you hold more than your safe limit: the body burns the excess).
 *
 * <p>While it's on: hits land ×{@value #DAMAGE} harder (fists and weapons), you take
 * ×{@value #DEFENSE} damage (30% less), perception ×{@value #PERCEPTION}, jutsu ×{@value #JUTSU_POWER}
 * stronger, an aura of nature energy that reaches {@value #REACH} blocks past your fists, and
 * +{@value #SPEED} movement. The look: pigment marks around the eyes and sage eyes (client
 * {@code SageLayer}), a moss-and-bone aura, a low hum.
 *
 * <p>When the energy runs out it ends with exhaustion: {@value #EXHAUSTION_SECONDS} s of slowness
 * and weakness, and no Sage Mode until it passes. Switching it off yourself costs nothing.
 *
 * <p>Also the two other sage jutsu: <b>Nature Gathering</b> (channel: draw nature energy standing,
 * twice the meditation rate, and it can overdraw) and <b>Nature Purge</b> (vent everything above
 * your limit in a blast of stone dust; petrification −{@value #PURGE_PETRIFY}).
 */
public final class SageMode {
    // ---------------------------------------------------------------- tuning
    public static final float MIN_ENERGY = 20f;
    public static final float DRAIN_PER_SECOND = 0.5f;
    public static final float OVER_LIMIT_DRAIN = 2f;
    public static final float DAMAGE = 1.5f;
    public static final float DEFENSE = 0.7f;
    public static final float PERCEPTION = 1.3f;
    public static final float JUTSU_POWER = 1.3f;
    /** Extra melee reach (blocks): the natural-energy aura strikes a little past the fist. */
    public static final float REACH = 0.75f;
    public static final float SPEED = 0.1f;
    public static final int EXHAUSTION_SECONDS = 8;
    /** Nature Gathering draws this many times the base meditation rate (before nature and lessons). */
    public static final float GATHER_RATE = 2f;
    /** Nature Purge: petrification removed, and push/damage per point of energy vented. */
    public static final float PURGE_PETRIFY = 40f;
    public static final float PURGE_DAMAGE_PER_ENERGY = 0.25f;

    /** The aura's colours: bone core, moss body, deep shadow, ochre-red accent (docs/ART_STYLE.md). */
    public static final Vfx.Palette PALETTE = new Vfx.Palette(0xF2E8C8, 0x8FBF5A, 0x1E2A12, 0xC0502A);

    private static final UUID SPEED_ID = UUID.fromString("2f4b6d8e-1a3c-4e5f-8a9b-0c1d2e3f4a5c");
    private static final UUID REACH_ID = UUID.fromString("3a5c7e9f-2b4d-4f6a-9b0c-1d2e3f4a5b6d");

    private SageMode() {}

    // ---------------------------------------------------------------- state

    public static boolean isActive(LivingEntity entity) {
        return entity instanceof Player player && ShinobiCapability.get(player).map(d -> d.senjutsu().sage().active()).orElse(false);
    }

    /** Channelling Nature Gathering right now? */
    public static boolean gathering(Player player) {
        JutsuCaster.Casting casting = JutsuCaster.casting(player);
        return casting != null && Sage.NATURE_GATHERING.equals(casting.id()) && !casting.sealing();
    }

    // ---------------------------------------------------------------- on and off

    /** Why Sage Mode can't start (a translation key), or null if it can. */
    public static String blocker(Player player, ShinobiData data) {
        SageData sage = data.senjutsu().sage();
        if (!Sage.learned(data, Sage.LESSON_SAGE) && !data.jutsu().knows(Sage.SAGE_MODE)) {
            return "message.noirm.sage.not_learned";
        }
        if (sage.isStatue()) {
            return "message.noirm.sage.stone";
        }
        if (sage.exhaustionTicks() > 0) {
            return "message.noirm.sage.exhausted";
        }
        if (data.senjutsu().natureEnergy() < MIN_ENERGY) {
            return "message.noirm.sage.need_energy";
        }
        return null;
    }

    /** Switches Sage Mode on. Returns whether it's on afterwards (the player is told why not). */
    public static boolean start(Player player) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null || player.level().isClientSide()) {
            return false;
        }
        SageData sage = data.senjutsu().sage();
        if (sage.active()) {
            return true;
        }
        String blocker = blocker(player, data);
        if (blocker != null) {
            Sage.notify(player, Component.translatable(blocker, Math.round(MIN_ENERGY)).withStyle(ChatFormatting.RED));
            return false;
        }
        Meditation.stop(player);
        sage.setActive(true);
        applyAttributes(player, true);
        data.markDirty();
        data.markPowersDirty();
        // The flood: nature energy rushes in from all around, then a ring of moss-green light.
        var level = player.level();
        Vfx.gather(level, player.position().add(0, 1, 0), PALETTE, 4f, 40);
        Vfx.shockwave(level, player.position().add(0, 0.1, 0), PALETTE, 3.5f);
        Vfx.flash(level, player.position().add(0, 1.6, 0), PALETTE, 1.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 0.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1f, 0.5f);
        Sage.notify(player, Component.translatable("message.noirm.sage.on").withStyle(ChatFormatting.GREEN));
        Sage.sync(player, data);
        return true;
    }

    /** Switches it off ({@code exhausted}: it ran dry, so the exhaustion follows). */
    public static void end(Player player, boolean exhausted) {
        ShinobiCapability.get(player).ifPresent(data -> {
            SageData sage = data.senjutsu().sage();
            if (!sage.active()) {
                return;
            }
            sage.setActive(false);
            applyAttributes(player, false);
            if (exhausted) {
                sage.setExhaustionTicks(EXHAUSTION_SECONDS * 20);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EXHAUSTION_SECONDS * 20, 1));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EXHAUSTION_SECONDS * 20, 0));
                player.causeFoodExhaustion(6f);
                Sage.notify(player, Component.translatable("message.noirm.sage.ran_dry", EXHAUSTION_SECONDS).withStyle(ChatFormatting.GRAY));
            } else {
                Sage.notify(player, Component.translatable("message.noirm.sage.off").withStyle(ChatFormatting.GRAY));
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7f, 0.8f);
            data.markDirty();
            data.markPowersDirty();
            Sage.sync(player, data);
        });
    }

    public static boolean toggle(Player player) {
        if (isActive(player)) {
            end(player, false);
            return false;
        }
        return start(player);
    }

    /** Movement and the vanilla reach follow the mode (transient: re-applied after a load by {@link #tick}). */
    static void applyAttributes(Player player, boolean on) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_ID);
            if (on) {
                speed.addTransientModifier(new AttributeModifier(SPEED_ID, "noirm_sage_mode", SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        AttributeInstance reach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (reach != null) {
            reach.removeModifier(REACH_ID);
            if (on) {
                reach.addTransientModifier(new AttributeModifier(REACH_ID, "noirm_sage_reach", REACH, AttributeModifier.Operation.ADDITION));
            }
        }
    }

    // ---------------------------------------------------------------- every server tick

    /** Drains nature energy, ends the mode when it's gone, ticks the exhaustion, draws the aura. */
    public static void tick(Player player, ShinobiData data) {
        SageData sage = data.senjutsu().sage();
        if (sage.exhaustionTicks() > 0) {
            sage.setExhaustionTicks(sage.exhaustionTicks() - 1);
            if (sage.exhaustionTicks() == 0) {
                Sage.sync(player, data);
            }
        }
        if (!sage.active()) {
            return;
        }
        if (!player.isAlive()) {
            end(player, false);
            return;
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED_ID) == null) {
            applyAttributes(player, true); // after a reload
        }
        MeditationData m = data.senjutsu();
        if (!player.getAbilities().instabuild) {
            float drain = DRAIN_PER_SECOND * (m.natureEnergy() > Sage.safeLimit(data) ? OVER_LIMIT_DRAIN : 1f);
            m.setNatureEnergy(m.natureEnergy() - drain / 20f);
            if (m.energyChangedForSync()) {
                data.markPowersDirty();
            }
        }
        if (m.natureEnergy() <= 0f) {
            end(player, true);
            return;
        }
        aura(player);
    }

    /** Moss-green motes rising off the body, a ring at the feet now and then, a low hum. */
    private static void aura(Player player) {
        var level = player.level();
        if (player.tickCount % 2 == 0) {
            Vfx.aura(level, player.position(), player.getBbWidth(), player.getBbHeight(), PALETTE, 3);
            var random = player.getRandom();
            double angle = random.nextDouble() * Math.PI * 2;
            Vec3 at = player.position().add(Math.cos(angle) * 0.45, 0.2 + random.nextDouble() * 1.4, Math.sin(angle) * 0.45);
            Vfx.one(level, new VfxOptions(VfxKind.SMOKE, 0x6E8A4A, 0x1E2A12, 0.18f, 0.32f, 26, -0.002f, 0.95f), at, new Vec3(0, 0.03, 0));
        }
        if (player.tickCount % 20 == 0) {
            Vfx.burst(level, Vfx.ring(PALETTE.mid(), 1.1f, 18), player.position().add(0, 0.05, 0), 1, 0, 0);
        }
        if (player.tickCount % 80 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.CONDUIT_AMBIENT, SoundSource.PLAYERS, 0.35f, 0.7f);
        }
    }

    // ---------------------------------------------------------------- what it boosts (hooks)

    public static float damageMultiplier(LivingEntity entity) {
        return isActive(entity) ? DAMAGE : 1f;
    }

    public static float defenseMultiplier(LivingEntity entity) {
        return isActive(entity) ? DEFENSE : 1f;
    }

    public static float perceptionMultiplier(LivingEntity entity) {
        return isActive(entity) ? PERCEPTION : 1f;
    }

    public static float jutsuPowerMultiplier(LivingEntity entity) {
        return isActive(entity) ? JUTSU_POWER : 1f;
    }

    /** Extra reach for combat-mode swings. */
    public static float reachBonus(LivingEntity entity) {
        return isActive(entity) ? REACH : 0f;
    }

    // ---------------------------------------------------------------- the sage jutsu (JutsuBehaviors)

    /** {@code sage_mode}: switch Sage Mode on (switching off is free, in {@code JutsuCaster}). */
    public static void cast(JutsuContext ctx) {
        if (ctx.caster() instanceof Player player) {
            toggle(player);
        }
    }

    /** {@code nature_gathering}: stand and draw nature energy (channel). */
    public static final JutsuBehavior GATHERING = new JutsuBehavior() {
        @Override
        public void cast(JutsuContext ctx) {
            ctx.caster().level().playSound(null, ctx.caster().blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 0.5f);
        }

        @Override
        public void channel(JutsuContext ctx, int tick) {
            if (!(ctx.caster() instanceof Player player)) {
                return;
            }
            ShinobiCapability.get(player).ifPresent(data -> gather(player, data, tick));
        }
    };

    /** One tick of Nature Gathering (public so tests can drive it). */
    public static void gather(Player player, ShinobiData data, int tick) {
        MeditationData m = data.senjutsu();
        if (tick == 1 || tick % Meditation.NATURE_SCAN_INTERVAL == 0) {
            m.setNatureFactor(Meditation.natureFactor(player.level(), player.blockPosition()));
        }
        // An untrained body still stops at its limit; a student can overdraw.
        float cap = Sage.opened(data) ? Meditation.NATURE_MAX : Sage.safeLimit(data);
        if (m.natureEnergy() < cap) {
            float gain = Meditation.gainPerTick(player.level(), m.natureFactor()) * GATHER_RATE * Sage.gainMultiplier(data);
            m.setNatureEnergy(Math.min(cap, m.natureEnergy() + gain));
            if (m.energyChangedForSync()) {
                data.markPowersDirty();
            }
        }
        if (tick % 3 == 0) {
            Vfx.gather(player.level(), player.position().add(0, 1, 0), PALETTE, 2.5f, 3);
        }
    }

    /** {@code nature_purge}: vent everything above the limit in a blast of stone dust. */
    public static void purge(JutsuContext ctx) {
        if (!(ctx.caster() instanceof Player player)) {
            return;
        }
        ShinobiCapability.get(player).ifPresent(data -> purge(player, data));
    }

    /** Returns the nature energy vented. */
    public static float purge(Player player, ShinobiData data) {
        MeditationData m = data.senjutsu();
        float limit = Sage.safeLimit(data);
        float vented = Math.max(0f, m.natureEnergy() - limit);
        m.setNatureEnergy(Math.min(m.natureEnergy(), limit));
        SageData sage = data.senjutsu().sage();
        sage.setPetrification(sage.petrification() - PURGE_PETRIFY);
        var level = player.level();
        Vfx.shockwave(level, player.position().add(0, 0.1, 0), new Vfx.Palette(0xE8E2D4, 0x8C8A84, 0x2A2826), 2.5f + vented * 0.08f);
        Vfx.burst(level, Vfx.smoke(0x8C8A84, 0.7f, 30), player.position().add(0, 0.6, 0), 10 + Math.round(vented), 0.6, 0.12);
        level.playSound(null, player.blockPosition(), SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.PLAYERS, 1f, 0.6f);
        if (vented > 0f) {
            float radius = 2.5f + vented * 0.05f;
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                    t -> t != player && t.isAlive() && !com.noirm.ally.Crew.sameSide(player, t))) {
                Vec3 push = target.position().subtract(player.position()).multiply(1, 0, 1).normalize().scale(0.6 + vented * 0.02);
                target.push(push.x, 0.25, push.z);
                target.hurtMarked = true;
                target.hurt(level.damageSources().playerAttack(player), vented * PURGE_DAMAGE_PER_ENERGY);
            }
        }
        data.markPowersDirty();
        Sage.sync(player, data);
        return vented;
    }
}

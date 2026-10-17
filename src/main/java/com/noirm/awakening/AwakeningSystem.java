package com.noirm.awakening;

import com.noirm.biju.BijuSystem;
import com.noirm.clan.AwakeningPath;
import com.noirm.clan.ClanTrait;
import com.noirm.combat.Perception;
import com.noirm.dojutsu.Sharingan;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The near-death awakening (the owner's idea): when a hit would leave you below 20% health,
 * your <b>Resolve</b> gives a chance to lock in instead — a surge of power, and something that
 * depends on who you are. Afterwards you're exhausted for a while.
 */
public final class AwakeningSystem {
    /** Health fraction below which an awakening can trigger. */
    public static final float THRESHOLD = 0.2f;
    /** Below this much Resolve, there's no chance at all. */
    public static final float MIN_RESOLVE = 20f;
    /** Chance at full Resolve (plus Iron Will's bonus). */
    public static final float MAX_CHANCE = 0.85f;
    public static final int LOCKED_IN_TICKS = 400;
    public static final int EXHAUSTION_TICKS = 600;
    /** At most one awakening every 5 minutes. */
    public static final int COOLDOWN_TICKS = 6000;

    private AwakeningSystem() {}

    // ------------------------------------------------------------ Resolve

    /** Dealt damage: Resolve builds faster against stronger opponents. */
    public static void onDamageDealt(Player player, LivingEntity target, float damage) {
        float strength = Math.min(2f, Math.max(0.5f, Perception.perception(target) / Perception.perception(player)));
        addResolve(player, damage * 0.6f * strength);
    }

    public static void onDamageTaken(Player player, float damage) {
        addResolve(player, damage);
    }

    /** A perfect dodge or parry: the biggest Resolve gain. */
    public static void onClutch(LivingEntity entity) {
        if (entity instanceof Player player) {
            addResolve(player, 5f);
            if (!player.level().isClientSide()) {
                // The Sharingan grows (more if it foresaw the attack); a dormant one builds pressure.
                ShinobiCapability.get(player).ifPresent(d -> com.noirm.dojutsu.SharinganAwakening.onClutch(player, d));
            }
        }
    }

    public static void addResolve(Player player, float amount) {
        ShinobiCapability.get(player).ifPresent(data -> {
            if (data.awakening().isLockedIn()) {
                return;
            }
            float multiplier = data.identity().has(ClanTrait.IRON_WILL) ? 1.5f : 1f;
            data.awakening().setResolve(data.awakening().resolve() + amount * multiplier);
            data.markPowersDirty();
        });
    }

    // ------------------------------------------------------------ the trigger

    /** The chance an awakening triggers right now (0 if not possible). */
    public static float chance(Player player, ShinobiData data) {
        AwakeningData awakening = data.awakening();
        if (awakening.isLockedIn() || awakening.resolve() < MIN_RESOLVE) {
            return 0f;
        }
        long last = awakening.lastAwakening();
        if (last >= 0 && player.level().getGameTime() - last < COOLDOWN_TICKS) {
            return 0f;
        }
        float chance = awakening.resolve() / AwakeningData.MAX_RESOLVE * MAX_CHANCE;
        if (data.identity().has(ClanTrait.IRON_WILL)) {
            chance += 0.1f;
        }
        return Math.min(chance, 0.95f);
    }

    /**
     * A hit of {@code damage} is about to land. If it would leave the player under 20% health and
     * the roll succeeds, they awaken. Returns the damage to apply (reduced so a lethal hit
     * leaves them at 1 health).
     */
    public static float onIncomingDamage(Player player, float damage, RandomSource random) {
        return onIncomingDamage(player, damage, random.nextFloat());
    }

    /** Same, with the dice roll given (0–1; it succeeds when below the chance). */
    public static float onIncomingDamage(Player player, float damage, float roll) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null || damage <= 0) {
            return damage;
        }
        float after = player.getHealth() - damage;
        if (after > player.getMaxHealth() * THRESHOLD || roll >= chance(player, data)) {
            return damage;
        }
        awaken(player, data);
        return after < 1f ? Math.max(0f, player.getHealth() - 1f) : damage;
    }

    /** Locks in now (also used by the test command). */
    public static void awaken(Player player, ShinobiData data) {
        AwakeningData awakening = data.awakening();
        float resolveBefore = awakening.resolve();
        awakening.setResolve(0f);
        awakening.setLastAwakening(player.level().getGameTime());
        awakening.setLockedInTicks(LOCKED_IN_TICKS);
        awakening.setExhaustionTicks(0);
        awakening.setNatureSurge(false);
        data.setChakra(Math.max(data.getChakra(), data.getMaxChakra() * 0.5f));
        data.refillStamina();
        player.heal(player.getMaxHealth() * 0.25f);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 4, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, LOCKED_IN_TICKS, 1, false, false));

        AwakeningPath path = data.biju().isJinchuriki() ? AwakeningPath.BEAST : data.identity().awakening();
        String subtitle = switch (path) {
            case SHARINGAN -> {
                if (com.noirm.dojutsu.Mangekyo.onResolveAwakening(player, data, resolveBefore)) {
                    yield "title.noirm.awakening.mangekyo";
                }
                boolean first = data.eyes().sharinganStage() == 0;
                Sharingan.advance(player, data, first ? "message.noirm.sharingan.awakened" : "message.noirm.sharingan.tomoe_awakening");
                data.eyes().setActive(true);
                data.eyes().setPressure(0f); // whatever was building has broken through
                yield first ? "title.noirm.awakening.sharingan" : "title.noirm.awakening.tomoe";
            }
            case NATURE_SURGE -> {
                awakening.setNatureSurge(true);
                yield "title.noirm.awakening.nature_surge";
            }
            case BEAST -> {
                BijuSystem.beastTakesOver(player, data, LOCKED_IN_TICKS);
                yield "title.noirm.awakening.beast";
            }
            case WILL -> "title.noirm.awakening.will";
        };
        data.markPowersDirty();
        effects(player, subtitle);
    }

    /** Every server tick: count down being locked in, then exhaustion. */
    public static void tick(Player player, ShinobiData data) {
        AwakeningData awakening = data.awakening();
        if (awakening.isLockedIn()) {
            awakening.setLockedInTicks(awakening.lockedInTicks() - 1);
            if (player.tickCount % 4 == 0 && player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 2, 0.4, 0.6, 0.4, 0.02);
            }
            if (!awakening.isLockedIn()) {
                awakening.setNatureSurge(false);
                awakening.setExhaustionTicks(EXHAUSTION_TICKS);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EXHAUSTION_TICKS, 0, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EXHAUSTION_TICKS, 0, false, true));
                player.displayClientMessage(Component.translatable("message.noirm.awakening.exhausted"), true);
                data.markPowersDirty();
            }
        } else if (awakening.isExhausted()) {
            awakening.setExhaustionTicks(awakening.exhaustionTicks() - 1);
            if (!awakening.isExhausted()) {
                data.markPowersDirty();
            }
        }
    }

    /** While locked in, every hit is stronger. */
    public static float damageMultiplier(LivingEntity entity) {
        if (entity instanceof Player player) {
            return ShinobiCapability.get(player).map(d -> d.awakening().isLockedIn() ? 1.3f : 1f).orElse(1f);
        }
        return 1f;
    }

    public static float perceptionMultiplier(LivingEntity entity) {
        return damageMultiplier(entity);
    }

    private static void effects(Player player, String subtitleKey) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 0.7f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 0.6f);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 60, 0.6, 1, 0.6, 0.4);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
            serverPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.noirm.awakening")));
            serverPlayer.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(subtitleKey)));
        }
    }
}

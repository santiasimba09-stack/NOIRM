package com.noirm.training;

import com.noirm.combat.CombatStats;
import com.noirm.combat.Perception;
import com.noirm.entity.TrainingDummy;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Practice makes you stronger. Landing hits and parrying train <b>taijutsu</b>; dodging and
 * sprinting train <b>speed</b>; taking hits and surviving trains <b>vitality</b> (max health);
 * spending chakra trains <b>reserves</b> (max chakra and its regen). Each point costs more
 * experience than the last.
 *
 * <p>Solo drills (the training dummy, running, dodging in the air) only take you to
 * {@link #DRILL_CAP}. Past that you need real opponents, and stronger ones teach more. Speed
 * also makes you move faster ({@link #MOVE_BONUS_PER_POINT}).
 */
public final class TrainingSystem {
    public enum Stat {
        TAIJUTSU("Taijutsu"), SPEED("Speed"), VITALITY("Vitality"), RESERVES("Reserves");

        private final String key;

        Stat(String key) {
            this.key = key;
        }

        /** Name used in save data and translation keys. */
        public String key() {
            return key;
        }
    }

    /** Solo drills stop teaching at this stat value. */
    public static final float DRILL_CAP = 30f;
    /** XP for a landed light hit; heavies add {@link #HEAVY_HIT_BONUS}, a blocked hit gives half. */
    public static final float HIT_XP = 2f;
    public static final float HEAVY_HIT_BONUS = 1f;
    public static final float GUARDED_FACTOR = 0.5f;
    public static final float PARRY_XP = 6f;
    public static final float DODGE_XP = 1f;
    public static final float PERFECT_DODGE_XP = 6f;
    /** Running: 1 speed XP every 2 seconds of sprinting. */
    public static final float SPRINT_XP = 1f;
    public static final int SPRINT_INTERVAL = 40;
    /** A real fight teaches more than a drill, scaled by how strong the opponent is compared to you. */
    public static final float REAL_FIGHT_BONUS = 1.5f;
    public static final float MIN_OPPONENT_RATIO = 0.25f;
    public static final float MAX_OPPONENT_RATIO = 3f;
    /** +0.4% movement speed per speed point above 10 (speed 100 = +36%). */
    public static final double MOVE_BONUS_PER_POINT = 0.004;

    /** Vitality: XP per point of damage taken (and survived). */
    public static final float VITALITY_XP_PER_DAMAGE = 2f;
    /** +0.5 max health per vitality point above 10 (+5 hearts at 30). */
    public static final double HEALTH_PER_VITALITY = 0.5;
    /** Reserves: XP per point of chakra spent (jutsu, eyes…). */
    public static final float RESERVES_XP_PER_CHAKRA = 0.25f;
    /** +5 max chakra per reserves point above 10 (double at 30). */
    public static final float CHAKRA_PER_RESERVES = 5f;
    /** +2% chakra regen per reserves point above 10. */
    public static final float REGEN_PER_RESERVES = 0.02f;

    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("8b1e2c3d-4f5a-4b6c-9d7e-0a1b2c3d4e5f");
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("3c1f8e52-6a0b-4d8e-9b7f-2e5a4c6d8f10");

    private TrainingSystem() {}

    /** XP needed to go from {@code stat} to the next point: 100 at 10, 260 at 30, 500 at 60. */
    public static float xpToNext(float stat) {
        return 20f + 8f * (float) Math.floor(stat);
    }

    public static float stat(ShinobiData data, Stat stat) {
        return switch (stat) {
            case TAIJUTSU -> data.getTaijutsu();
            case SPEED -> data.getSpeed();
            case VITALITY -> data.getVitality();
            case RESERVES -> data.getReserves();
        };
    }

    public static void setStat(ShinobiData data, Stat stat, float value) {
        switch (stat) {
            case TAIJUTSU -> data.setTaijutsu(value);
            case SPEED -> data.setSpeed(value);
            case VITALITY -> data.setVitality(value);
            case RESERVES -> data.setReserves(value);
        }
    }

    /** Progress toward the next point, 0..1 (for the HUD). */
    public static float progress(ShinobiData data, Stat stat) {
        return Mth.clamp(data.training().xp(stat) / xpToNext(stat(data, stat)), 0f, 1f);
    }

    // ---------------------------------------------------------------- hooks from combat

    /** A NOIRM melee hit landed (possibly on a guard). */
    public static void onHitLanded(LivingEntity attacker, LivingEntity target, boolean heavy, boolean guarded) {
        if (attacker instanceof Player player && !player.level().isClientSide()) {
            float xp = (HIT_XP + (heavy ? HEAVY_HIT_BONUS : 0f)) * (guarded ? GUARDED_FACTOR : 1f);
            grant(player, Stat.TAIJUTSU, xp * opponentFactor(player, target, Stat.TAIJUTSU));
        } else if (attacker instanceof com.noirm.entity.ShadowClone clone) {
            clone.storeXp((HIT_XP + (heavy ? HEAVY_HIT_BONUS : 0f)) * (guarded ? GUARDED_FACTOR : 1f));
        }
    }

    /** {@code defender} parried {@code attacker}. */
    public static void onParry(LivingEntity defender, Entity attacker) {
        if (defender instanceof Player player && !player.level().isClientSide()) {
            grant(player, Stat.TAIJUTSU, PARRY_XP * opponentFactor(player, attacker, Stat.TAIJUTSU));
        }
    }

    /** Any dodge: a drill, so it only teaches below the cap. */
    public static void onDodge(LivingEntity entity) {
        if (entity instanceof Player player && !player.level().isClientSide()) {
            grant(player, Stat.SPEED, drillFactor(player, Stat.SPEED) * DODGE_XP);
        }
    }

    /** A dodge that slipped a real attack at the last moment. */
    public static void onPerfectDodge(LivingEntity entity) {
        if (entity instanceof Player player && !player.level().isClientSide()) {
            grant(player, Stat.SPEED, PERFECT_DODGE_XP);
        }
    }

    /**
     * Damage taken (after armor). Surviving real fights trains vitality; falls, fire and the like
     * are drills.
     */
    public static void onDamageTaken(Player player, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (player.level().isClientSide() || amount <= 0 || player.getAbilities().invulnerable) {
            return;
        }
        Entity attacker = source.getEntity();
        float factor = attacker != null && attacker != player ? opponentFactor(player, attacker, Stat.VITALITY) : drillFactor(player, Stat.VITALITY);
        grant(player, Stat.VITALITY, amount * VITALITY_XP_PER_DAMAGE * factor);
    }

    /**
     * Every server tick: running trains speed, chakra spent trains reserves, and the stats set
     * movement speed, max health and max chakra.
     */
    public static void tick(Player player, ShinobiData data) {
        if (player.isSprinting() && player.onGround() && data.training().sprintTick(SPRINT_INTERVAL)) {
            grant(player, Stat.SPEED, drillFactor(player, Stat.SPEED) * SPRINT_XP);
        }
        float spent = data.training().takeChakraSpent();
        if (spent > 0 && !player.getAbilities().instabuild) {
            grant(player, Stat.RESERVES, spent * RESERVES_XP_PER_CHAKRA);
        }
        applyMovementBonus(player, data.getSpeed());
        applyVitality(player, data.getVitality());
        applyReserves(data);
        if (player.tickCount % 40 == 0) {
            com.noirm.clan.NatureSystem.checkStatUnlocks(player, data);
        }
    }

    /** Max health from vitality. */
    public static void applyVitality(Player player, float vitality) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return;
        }
        double amount = (vitality - ShinobiData.DEFAULT_STAT) * HEALTH_PER_VITALITY;
        AttributeModifier current = health.getModifier(HEALTH_MODIFIER_ID);
        if (current != null && Math.abs(current.getAmount() - amount) < 1e-6) {
            return;
        }
        health.removeModifier(HEALTH_MODIFIER_ID);
        if (amount != 0) {
            health.addTransientModifier(new AttributeModifier(HEALTH_MODIFIER_ID, "noirm_vitality", amount, AttributeModifier.Operation.ADDITION));
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** Max chakra from reserves: adjusts the max by the change in bonus since last time. */
    public static void applyReserves(ShinobiData data) {
        float bonus = (data.getReserves() - ShinobiData.DEFAULT_STAT) * CHAKRA_PER_RESERVES;
        float applied = data.training().appliedChakraBonus();
        if (Math.abs(bonus - applied) > 1e-4) {
            data.setMaxChakra(data.getMaxChakra() - applied + bonus);
            data.training().setAppliedChakraBonus(bonus);
        }
    }

    // ---------------------------------------------------------------- rules (public for tests)

    /**
     * How much a fight against {@code target} teaches: the dummy is a drill; real opponents
     * (hostile mobs, ninjas, players) teach more the stronger they are; animals teach nothing.
     */
    public static float opponentFactor(Player player, Entity target, Stat stat) {
        if (target instanceof TrainingDummy) {
            return drillFactor(player, stat);
        }
        if (!(target instanceof LivingEntity living) || !(target instanceof Enemy || target instanceof Player || target instanceof CombatStats)) {
            return 0f;
        }
        float ratio = Perception.basePower(living) / Math.max(1f, Perception.basePower(player));
        return Mth.clamp(ratio, MIN_OPPONENT_RATIO, MAX_OPPONENT_RATIO) * REAL_FIGHT_BONUS;
    }

    /** 1 below {@link #DRILL_CAP}, 0 at or above it. */
    public static float drillFactor(Player player, Stat stat) {
        return ShinobiCapability.get(player).map(d -> stat(d, stat) < DRILL_CAP ? 1f : 0f).orElse(0f);
    }

    /** Adds XP and raises the stat for every threshold crossed. */
    public static void grant(Player player, Stat stat, float xp) {
        if (xp <= 0f) {
            return;
        }
        ShinobiCapability.get(player).ifPresent(data -> {
            float value = stat(data, stat);
            if (value >= ShinobiData.MAX_STAT) {
                return;
            }
            float total = data.training().xp(stat) + xp;
            int gained = 0;
            while (total >= xpToNext(value) && value < ShinobiData.MAX_STAT) {
                total -= xpToNext(value);
                value = Math.min(ShinobiData.MAX_STAT, (float) Math.floor(value) + 1f);
                gained++;
            }
            data.training().setXp(stat, total);
            if (gained > 0) {
                setStat(data, stat, value);
                announce(player, stat, value);
            }
            data.markDirty();
        });
    }

    private static void announce(Player player, Stat stat, float value) {
        String key = "message.noirm.training." + stat.key().toLowerCase(java.util.Locale.ROOT) + "_up";
        player.sendSystemMessage(Component.translatable(key, Math.round(value)).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.3f);
        if (value >= DRILL_CAP && value - 1 < DRILL_CAP) {
            player.sendSystemMessage(Component.translatable("message.noirm.training.drill_cap").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Keeps the movement-speed modifier in line with the speed stat. */
    public static void applyMovementBonus(Player player, float speed) {
        AttributeInstance movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) {
            return;
        }
        double amount = (speed - ShinobiData.DEFAULT_STAT) * MOVE_BONUS_PER_POINT;
        AttributeModifier current = movement.getModifier(SPEED_MODIFIER_ID);
        if (current != null && Math.abs(current.getAmount() - amount) < 1e-6) {
            return;
        }
        movement.removeModifier(SPEED_MODIFIER_ID);
        if (amount != 0) {
            movement.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID, "noirm_speed_stat", amount, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }
}

package com.noirm.ally;

import com.noirm.entity.ShadowClone;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.TrainingDummy;
import com.noirm.player.ShinobiData;
import com.noirm.training.TrainingSystem;
import com.noirm.training.TrainingSystem.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Crew mates grow like you do, on the same XP curve: hits they land train taijutsu, hits they
 * take train vitality, jutsu they cast train reserves, dodges train speed. Drills (the training
 * dummy, meditating) only teach up to {@link TrainingSystem#DRILL_CAP}; past that, only real
 * fights do. Ninjas outside a crew don't grow.
 */
public final class AllyTraining {
    public static final float HIT_XP = 2f;
    public static final float VITALITY_XP_PER_DAMAGE = 2f;
    public static final float RESERVES_XP_PER_CHAKRA = 0.25f;
    public static final float DODGE_XP = 1f;
    /** Meditating: reserves XP per second. */
    public static final float MEDITATE_XP = 1.5f;
    /** Real fights teach more than drills. */
    public static final float REAL_FIGHT_BONUS = 1.5f;

    private AllyTraining() {}

    private static boolean grows(SparringNinja ninja) {
        return ninja.leaderId() != null && !(ninja instanceof ShadowClone) && !ninja.level().isClientSide();
    }

    public static void onHitLanded(SparringNinja ninja, Entity target, boolean heavy, boolean guarded) {
        if (!grows(ninja)) {
            return;
        }
        float xp = (HIT_XP + (heavy ? 1f : 0f)) * (guarded ? 0.5f : 1f);
        if (target instanceof TrainingDummy) {
            drill(ninja, Stat.TAIJUTSU, xp);
        } else {
            grant(ninja, Stat.TAIJUTSU, xp * REAL_FIGHT_BONUS);
        }
    }

    public static void onDamageTaken(SparringNinja ninja, Entity attacker, float amount) {
        if (grows(ninja) && attacker != null && amount > 0) {
            grant(ninja, Stat.VITALITY, amount * VITALITY_XP_PER_DAMAGE);
        }
    }

    public static void onJutsuCast(SparringNinja ninja, float chakraCost) {
        if (grows(ninja)) {
            grant(ninja, Stat.RESERVES, chakraCost * RESERVES_XP_PER_CHAKRA);
        }
    }

    public static void onDodge(SparringNinja ninja) {
        if (grows(ninja)) {
            drill(ninja, Stat.SPEED, DODGE_XP);
        }
    }

    public static void onMeditate(SparringNinja ninja) {
        if (grows(ninja)) {
            drill(ninja, Stat.RESERVES, MEDITATE_XP);
        }
    }

    /** XP from a drill: nothing once the stat is past the drill cap. */
    private static void drill(SparringNinja ninja, Stat stat, float xp) {
        if (ninja.stat(stat) < TrainingSystem.DRILL_CAP) {
            grant(ninja, stat, xp);
        }
    }

    public static void grant(SparringNinja ninja, Stat stat, float xp) {
        if (xp <= 0) {
            return;
        }
        float value = ninja.stat(stat);
        float total = ninja.xp(stat) + xp;
        int gained = 0;
        while (total >= TrainingSystem.xpToNext(value) && value < ShinobiData.MAX_STAT) {
            total -= TrainingSystem.xpToNext(value);
            value = (float) Math.floor(value) + 1f;
            gained++;
        }
        ninja.setXp(stat, total);
        if (gained > 0) {
            ninja.setStat(stat, value);
            Player leader = ninja.leaderId() == null ? null : ninja.level().getPlayerByUUID(ninja.leaderId());
            if (leader != null) {
                leader.sendSystemMessage(Component.translatable("message.noirm.ally.stat_up", ninja.getDisplayName(),
                        Component.translatable("stat.noirm." + stat.key().toLowerCase(java.util.Locale.ROOT)), Math.round(value))
                        .withStyle(ChatFormatting.GOLD));
            }
            ninja.level().playSound(null, ninja.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.5f, 1.5f);
        }
    }

    /** Reserves needed before an ally can learn a jutsu of this rank. */
    public static float reservesFor(String rank) {
        return switch (rank) {
            case "S" -> 60f;
            case "A" -> 35f;
            case "B" -> 20f;
            default -> 0f;
        };
    }
}

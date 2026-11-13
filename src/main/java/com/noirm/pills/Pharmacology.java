package com.noirm.pills;

import com.noirm.player.ShinobiData;
import com.noirm.progress.PillTrigger;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * <b>Pharmacology</b>: the medic's craft, a skill from {@value PillData#START_PHARMACOLOGY} to
 * {@value PillData#MAX_PHARMACOLOGY}, trained by brewing at the apothecary ({@link Apothecary}) and
 * by the medic's lessons. It raises a brew's success chance, the grade it comes out at and its yield,
 * and opens harder recipes. Its XP curve is gentler than the fighting stats': {@link #xpToNext}.
 */
public final class Pharmacology {
    /** XP to the next level: {@value #XP_BASE} + {@value #XP_PER_LEVEL} × level (19 at 1, 95 at 20, 175 at 40). */
    public static final float XP_BASE = 15f;
    public static final float XP_PER_LEVEL = 4f;

    private Pharmacology() {}

    public static float xpToNext(float level) {
        return XP_BASE + XP_PER_LEVEL * (float) Math.floor(level);
    }

    /** Progress toward the next level, 0..1 (for the HUD and the apothecary). */
    public static float progress(PillData pills) {
        return Mth.clamp(pills.pharmacologyXp() / xpToNext(pills.pharmacology()), 0f, 1f);
    }

    /** Adds XP and raises the level for every threshold crossed. Returns the levels gained. */
    public static int grant(Player player, ShinobiData data, float xp) {
        PillData pills = data.pills();
        if (xp <= 0f || pills.pharmacology() >= PillData.MAX_PHARMACOLOGY) {
            return 0;
        }
        float level = pills.pharmacology();
        float total = pills.pharmacologyXp() + xp;
        int gained = 0;
        while (total >= xpToNext(level) && level < PillData.MAX_PHARMACOLOGY) {
            total -= xpToNext(level);
            level = Math.min(PillData.MAX_PHARMACOLOGY, (float) Math.floor(level) + 1f);
            gained++;
        }
        pills.setPharmacologyXp(level >= PillData.MAX_PHARMACOLOGY ? 0f : total);
        if (gained > 0) {
            pills.setPharmacology(level);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.noirm.pharmacology.up", Math.round(level)).withStyle(ChatFormatting.GOLD));
                player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.5f);
                PillTrigger.pharmacology(player, Math.round(level));
            }
        }
        data.markDirty();
        return gained;
    }

    /** Raises the level straight away by {@code levels} (a master's lesson). */
    public static void raise(Player player, ShinobiData data, int levels) {
        PillData pills = data.pills();
        pills.setPharmacology(pills.pharmacology() + levels);
        if (player != null) {
            PillTrigger.pharmacology(player, Math.round(pills.pharmacology()));
        }
    }
}

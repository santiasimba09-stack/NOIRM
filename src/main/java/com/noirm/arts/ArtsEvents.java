package com.noirm.arts;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ticks the solo transformations while you meditate (shape drills, shape + nature fusions, the fusion
 * trial), hears about jutsu cast (the trial's proof, the Power stage, mind practice for Yin) and keeps
 * the client's copy fresh (through the powers sync).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class ArtsEvents {
    private ArtsEvents() {}

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        ShinobiCapability.get(player).ifPresent(data -> {
            tick(player, data);
            if (data.arts().consumeDirty()) {
                data.markPowersDirty();
            }
        });
    }

    /** One server tick of a player's solo training. */
    public static void tick(Player player, ShinobiData data) {
        ArtsData arts = data.arts();
        if (!data.senjutsu().isMeditating()) {
            arts.setMoving(-1, -1, false);
            return;
        }
        int ticks = data.senjutsu().ticks();
        if (ticks <= 0 || ticks % ShapeTraining.INTERVAL != 0) {
            return;
        }
        float seconds = ShapeTraining.INTERVAL / 20f;
        ShapeTraining.Moved moved = ShapeTraining.study(player, data, seconds);
        boolean trial = arts.trialNature() != null && FusionTrial.study(player, data, seconds, player.getRandom().nextFloat());
        arts.setMoving(moved.shape(), moved.pair(), trial);
    }

    /** A player's jutsu went off (from {@code JutsuCaster}). */
    public static void onJutsuCast(Player player, ShinobiData data, ResourceLocation id, JutsuDefinition def) {
        Nature nature = def.nature().orElse(null);
        long now = player.level().getGameTime();
        if (nature != null) {
            data.arts().noteNatureCast(nature, now);
        }
        if (nature == Nature.YIN || "genjutsu".equals(def.behavior()) || "genjutsu_release".equals(def.behavior())
                || "mirage".equals(def.behavior()) || "tsukuyomi".equals(def.behavior())) {
            data.arts().noteMind(now); // mind practice: Yin's drill
        }
        FusionTrial.onCast(player, data, def);
        ShapeTraining.onCast(player, data, def);
    }
}

package com.noirm.progress;

import com.noirm.Noirm;
import com.noirm.clan.Nature;
import com.noirm.creator.CreatorData;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.training.TrainingSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * Once a second, looks at each player's state and fires the "state" advancement triggers (see
 * {@link NoirmTriggers}): character made, Handbook opened, stats, jutsu known and their ★, natures,
 * rank, ryo, crew size, masters' tests, eyes, Sage Mode, jinchūriki. Advancements already earned
 * don't listen any more, so this costs next to nothing once they're done.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class ProgressWatcher {
    /** Ticks between checks. */
    public static final int INTERVAL = 20;

    private ProgressWatcher() {}

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % INTERVAL != 11) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            check(player);
        }
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            check(player);
        }
    }

    /** Fires every state trigger for {@code player} (public for tests). */
    public static void check(ServerPlayer player) {
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (data == null) {
            return;
        }
        // Beginnings
        if (data.creator().mode() == CreatorData.Mode.NONE) {
            BeginningsTrigger.created(player);
        }
        if (data.guide().openedHandbook()) {
            BeginningsTrigger.handbook(player);
        }
        // Stats
        for (TrainingSystem.Stat stat : TrainingSystem.Stat.values()) {
            // A pill's borrowed points don't count: only what you trained.
            StatTrigger.reached(player, stat, TrainingSystem.stat(data, stat) - com.noirm.pills.PillEffects.statBonus(data, stat));
        }
        // Jutsu
        int stars = 0;
        for (ResourceLocation id : data.jutsu().known()) {
            JutsuDefinition def = JutsuData.get(id);
            if (def != null) {
                JutsuTrigger.learned(player, def.rank());
            }
            stars = Math.max(stars, data.jutsu().masteryLevel(id));
        }
        if (stars > 0) {
            JutsuTrigger.mastery(player, stars);
        }
        // Natures
        int owned = 0, combined = 0;
        for (Map.Entry<Nature, Float> e : data.identity().natures().entrySet()) {
            if (e.getValue() > 0f) {
                owned++;
                if (!e.getKey().isBasic()) {
                    combined++;
                }
            }
        }
        NatureTrigger.owned(player, owned);
        if (combined > 0) {
            NatureTrigger.combined(player, combined);
        }
        if (data.natureTraining().revealed() && !data.natureTraining().hidden().isEmpty()) {
            NatureTrigger.revealed(player);
        }
        // Rank and ryo
        RankTrigger.reached(player, data.career().rank());
        RyoTrigger.balance(player, data.career().ryo());
        for (com.noirm.mission.MissionTemplate.Letter letter : com.noirm.mission.MissionTemplate.Letter.values()) {
            int done = data.career().completed(letter);
            if (done > 0) {
                MissionTrigger.completed(player, letter.name(), done);
            }
        }
        // Crew and masters
        if (!data.crew().isEmpty()) {
            CrewTrigger.size(player, data.crew().size());
        }
        for (String master : data.masters().passedTests()) {
            MasterTrigger.passed(player, master);
        }
        // Eyes, sage, beasts
        if (data.eyes().sharinganStage() > 0) {
            EyesTrigger.sharingan(player, data.eyes().sharinganStage());
        }
        if (data.eyes().hasMangekyo()) {
            EyesTrigger.mangekyo(player);
        }
        if (data.eyes().isByakuganActive()) {
            EyesTrigger.byakugan(player);
        }
        if (data.senjutsu().sage().active()) {
            SageTrigger.mode(player);
        }
        if (data.biju().isJinchuriki()) {
            BeastTrigger.jinchuriki(player, data.biju().tails());
        }
    }
}

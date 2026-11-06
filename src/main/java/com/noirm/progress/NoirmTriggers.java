package com.noirm.progress;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.List;

/**
 * Every NOIRM advancement trigger, registered with the game once while the mod loads.
 *
 * <p><b>How the "Path of the Shinobi" tab works.</b> Each advancement in
 * {@code data/noirm/advancements/shinobi/} has a criterion naming one of these triggers (for
 * example {@code noirm:rank} with {@code "key": "chunin"}). Two kinds of places fire them:
 * <ul>
 *   <li><b>Moments</b>, fired right where they happen in the game code: a hit on a training dummy,
 *       a jutsu cast, a drill finished, a mission reported, a ninja defeated, a just-frame parry, a
 *       bounty paid, a nature lesson finished, a tailed beast subdued or sealed.</li>
 *   <li><b>States</b>, checked once a second by {@link ProgressWatcher}: your rank, ryo, stats,
 *       natures, the jutsu you know and their ★, your eyes, crew size, masters' tests passed, Sage
 *       Mode, being a jinchūriki. Checking the state (not the moment) means every route counts: a
 *       scroll, a master, a mastery chain, a command.</li>
 * </ul>
 * The game only asks a trigger about advancements the player hasn't finished yet, so firing is
 * cheap. Workshop placeholders: {@link CreateJutsuTrigger}, {@link FuseNaturesTrigger}.
 */
public final class NoirmTriggers {
    private NoirmTriggers() {}

    /** All of them, in tree order. */
    public static final List<NoirmTrigger> ALL = List.of(
            BeginningsTrigger.INSTANCE, StatTrigger.INSTANCE, JutsuTrigger.INSTANCE, NatureTrigger.INSTANCE,
            RankTrigger.INSTANCE, MissionTrigger.INSTANCE, RyoTrigger.INSTANCE, DefeatTrigger.INSTANCE,
            CombatFeatTrigger.INSTANCE, CrewTrigger.INSTANCE, MasterTrigger.INSTANCE, EyesTrigger.INSTANCE,
            SageTrigger.INSTANCE, BeastTrigger.INSTANCE, CreateJutsuTrigger.INSTANCE, FuseNaturesTrigger.INSTANCE);

    /** Called from common setup (on the main thread: the game's trigger list is a plain map). */
    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ALL.forEach(CriteriaTriggers::register));
    }
}

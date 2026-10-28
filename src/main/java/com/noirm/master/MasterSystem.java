package com.noirm.master;

import com.noirm.Noirm;
import com.noirm.combat.CombatSystem;
import com.noirm.combat.StyleSystem;
import com.noirm.entity.MasterNinja;
import com.noirm.jutsu.JutsuData;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.registry.NoirmEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The rules of masters: starting a spar, how it ends (nobody dies), passing, and learning lessons.
 *
 * <p><b>The spar:</b> the master fights at a fraction of their strength (never weaker than you).
 * You pass by landing enough clean hits, breaking their guard, or wearing them down to their
 * {@code end_health}. You fail if time runs out, you walk away, or you drop to {@code end_health}
 * yourself: the master stops short of really hurting you. Outside a spar, masters never hurt anyone.
 *
 * <p><b>Lessons</b> need the test passed, trained stats, earlier lessons and practice (mastery of
 * a technique you already know). Passing a test also grants the master's style, whatever your
 * taijutsu (Guy would never turn away a student over a number).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class MasterSystem {
    /** You need at least this share of your health to start a spar. */
    public static final float SPAR_MIN_HEALTH = 0.5f;

    private MasterSystem() {}

    /** Spawns master {@code id} at {@code at}, facing {@code yaw}. Returns null if there's no such master. */
    public static MasterNinja spawn(ServerLevel level, ResourceLocation id, Vec3 at, float yaw) {
        if (MasterData.get(id) == null) {
            return null;
        }
        MasterNinja master = NoirmEntities.MASTER.get().create(level);
        if (master == null) {
            return null;
        }
        master.moveTo(at.x, at.y, at.z, yaw, 0f);
        master.setYHeadRot(yaw);
        master.finalizeSpawn(level, level.getCurrentDifficultyAt(master.blockPosition()), MobSpawnType.COMMAND, null, null);
        master.setMaster(id);
        level.addFreshEntity(master);
        return master;
    }

    // ---------------------------------------------------------------- the spar

    /** Why {@code player} can't spar with {@code master} right now, or null if they can. */
    public static Component sparBlocker(Player player, MasterNinja master) {
        MasterDefinition def = master.definition();
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (def == null || data == null) {
            return Component.translatable("screen.noirm.master.why.unknown");
        }
        if (master.isSparring() || com.noirm.senjutsu.SageTrial.trial(master) != null || com.noirm.medical.MedicTrial.trial(master) != null) {
            return Component.translatable("screen.noirm.master.why.busy");
        }
        if (data.getTaijutsu() < def.test().minTaijutsu()) {
            return Component.translatable("screen.noirm.master.why.taijutsu", fmt(data.getTaijutsu()), fmt(def.test().minTaijutsu()));
        }
        if (player.getHealth() < player.getMaxHealth() * SPAR_MIN_HEALTH) {
            return Component.translatable("screen.noirm.master.why.rest");
        }
        return null;
    }

    /** Starts the test. Returns whether it started (if not, the player is told why). */
    public static boolean startSpar(Player player, MasterNinja master) {
        Component blocker = sparBlocker(player, master);
        if (blocker != null) {
            player.displayClientMessage(blocker.copy().withStyle(ChatFormatting.RED), true);
            return false;
        }
        MasterDefinition def = master.definition();
        if (com.noirm.senjutsu.SageTrial.isTrialMaster(master)) {
            return com.noirm.senjutsu.SageTrial.start(player, master); // a test of stillness, not a spar
        }
        if (com.noirm.medical.MedicTrial.isTrialMaster(master)) {
            return com.noirm.medical.MedicTrial.start(player, master); // keep a patient alive, not a spar
        }
        CombatSystem.setCombatMode(player, true); // a spar is a fight: hands up
        master.beginSpar(player);
        player.sendSystemMessage(Component.translatable("message.noirm.master.spar_start", master.getDisplayName(),
                def.test().hits(), def.test().postureBreaks(), def.test().seconds()).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, master.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 0.4f, 1.6f);
        return true;
    }

    /** Called by the master when a spar ends. */
    public static void onSparEnded(Player student, MasterNinja master, boolean passed, String messageKey) {
        student.sendSystemMessage(Component.translatable(messageKey, master.getDisplayName())
                .withStyle(passed ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        if (passed) {
            passTest(student, master.masterId());
            student.level().playSound(null, master.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.8f, 1.2f);
        }
    }

    /** Records a passed test, and grants the master's style. */
    public static void passTest(Player player, ResourceLocation masterId) {
        MasterDefinition def = MasterData.get(masterId);
        ShinobiCapability.get(player).ifPresent(data -> {
            if (data.masters().pass(masterId.toString())) {
                player.sendSystemMessage(Component.translatable("message.noirm.master.test_passed",
                        Component.translatable(def == null ? masterId.toString() : def.name())).withStyle(ChatFormatting.GOLD));
            }
            if (def != null && def.grantsStyle() && def.style().isPresent() && data.masters().grantStyle(def.style().get().toString())) {
                player.sendSystemMessage(Component.translatable("message.noirm.style.unlocked",
                        Component.translatable(StyleSystem.translationKey(def.style().get()))).withStyle(ChatFormatting.AQUA));
            }
            data.markDirty();
            data.markPowersDirty();
        });
    }

    /**
     * Nobody dies in a spar: whoever would drop below the test's {@code end_health} stops there and
     * the spar ends. Outside their spar, masters never hurt anyone. Runs last, after guard and armor.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.isCanceled()) {
            return;
        }
        if (event.getSource().getEntity() instanceof MasterNinja master && victim instanceof Player player) {
            if (master.sparPartner() != player) {
                event.setCanceled(true);
                return;
            }
            float floor = floor(player, master.definition());
            if (player.getHealth() - event.getAmount() <= floor) {
                event.setAmount(Math.max(0f, player.getHealth() - floor));
                master.endSpar(false, "message.noirm.master.you_fell");
            }
        } else if (victim instanceof MasterNinja master && master.isSparring() && event.getSource().getEntity() == master.sparPartner()) {
            float floor = floor(master, master.definition());
            if (master.getHealth() - event.getAmount() <= floor) {
                event.setAmount(Math.max(0f, master.getHealth() - floor));
                master.endSpar(true, "message.noirm.master.passed_down");
            }
        }
    }

    /** The jutsu duel's trial kind. */
    public static final String JUTSU_TRIAL = "jutsu";

    /** Whether a hit from the student counts toward the test: in a jutsu duel ({@code "trial": "jutsu"}) only jutsu hits do. */
    public static boolean countsAsHit(MasterDefinition def, net.minecraft.world.damagesource.DamageSource source) {
        return def == null || !JUTSU_TRIAL.equals(def.trial()) || source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC);
    }

    private static float floor(LivingEntity entity, MasterDefinition def) {
        return Math.max(1f, entity.getMaxHealth() * (def == null ? 0.2f : def.test().endHealth()));
    }

    // ---------------------------------------------------------------- lessons

    /** Why {@code player} can't learn this lesson yet (null if they can, or already did). */
    public static Component lessonBlocker(ShinobiData data, ResourceLocation masterId, MasterDefinition def, MasterDefinition.Lesson lesson) {
        String key = masterId.toString();
        if (lesson.teachesJutsu() ? JutsuData.get(lesson.jutsu()) == null : !MasterPerks.known(lesson)) {
            return Component.translatable("screen.noirm.master.why.unknown");
        }
        if (lesson.test() && !data.masters().passed(key)) {
            return Component.translatable("screen.noirm.master.why.test");
        }
        for (String before : lesson.after()) {
            if (!data.masters().learned(key, before)) {
                Component name = def.lesson(before).map(l -> (Component) Component.translatable(l.titleKey()))
                        .orElse(Component.literal(before));
                return Component.translatable("screen.noirm.master.why.after", name);
            }
        }
        com.noirm.mission.PlayerRank rank = lesson.neededRank();
        if (rank != null && !data.career().rank().atLeast(rank)) {
            return Component.translatable("screen.noirm.master.why.rank", rank.title(), data.career().rank().title());
        }
        if (lesson.teachesJutsu()) {
            var nature = JutsuData.get(lesson.jutsu()).nature();
            if (nature.isPresent() && data.identity().affinity(nature.get()) <= 0f) {
                return Component.translatable("screen.noirm.master.why.nature",
                        Component.translatable(nature.get().translationKey()));
            }
        }
        if (data.getTaijutsu() < lesson.taijutsu()) {
            return Component.translatable("screen.noirm.master.why.taijutsu", fmt(data.getTaijutsu()), fmt(lesson.taijutsu()));
        }
        if (data.getSpeed() < lesson.speed()) {
            return Component.translatable("screen.noirm.master.why.speed", fmt(data.getSpeed()), fmt(lesson.speed()));
        }
        if (data.getControl() < lesson.control()) {
            return Component.translatable("screen.noirm.master.why.control", fmt(data.getControl()), fmt(lesson.control()));
        }
        for (var practice : lesson.mastery().entrySet()) {
            if (data.jutsu().masteryLevel(practice.getKey()) < practice.getValue()) {
                return Component.translatable("screen.noirm.master.why.practice", Component.translatable(jutsuKey(practice.getKey())),
                        practice.getValue());
            }
        }
        Component perk = MasterPerks.blocker(data, lesson);
        if (perk != null) {
            return perk;
        }
        if (lesson.ryo() > 0 && data.career().ryo() < lesson.ryo()) {
            return Component.translatable("screen.noirm.master.why.ryo", com.noirm.economy.Ryo.format(lesson.ryo()),
                    com.noirm.economy.Ryo.format(data.career().ryo()));
        }
        return null;
    }

    /** As the other {@code lessonBlocker}, plus what a service needs from the player (an eye to transplant...). */
    public static Component lessonBlocker(Player player, ShinobiData data, ResourceLocation masterId, MasterDefinition def, MasterDefinition.Lesson lesson) {
        Component blocker = lessonBlocker(data, masterId, def, lesson);
        return blocker != null || !lesson.isService() ? blocker : MasterPerks.serviceBlocker(player, data, lesson);
    }

    /** {@code player} asks to learn a lesson (or for a service). Returns whether they learned it (or it was done). */
    public static boolean learn(Player player, ResourceLocation masterId, String lessonId) {
        MasterDefinition def = MasterData.get(masterId);
        ShinobiData data = ShinobiCapability.get(player).orElse(null);
        if (def == null || data == null) {
            return false;
        }
        MasterDefinition.Lesson lesson = def.lesson(lessonId).orElse(null);
        if (lesson == null || !lesson.isService() && data.masters().learned(masterId.toString(), lessonId)) {
            return false;
        }
        Component blocker = lessonBlocker(player, data, masterId, def, lesson);
        if (blocker != null) {
            player.displayClientMessage(blocker.copy().withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (lesson.isService()) {
            if (!MasterPerks.perform(player, data, def, lesson)) {
                return false;
            }
            com.noirm.economy.Ryo.take(player, lesson.ryo());
            data.markDirty();
            data.markPowersDirty();
            return true;
        }
        if (lesson.ryo() > 0 && !com.noirm.economy.Ryo.take(player, lesson.ryo())) {
            return false;
        }
        data.masters().learn(masterId.toString(), lessonId);
        if (lesson.teachesJutsu()) {
            data.jutsu().learn(lesson.jutsu(), false);
            data.jutsu().equipIfRoom(lesson.jutsu());
        } else {
            MasterPerks.granted(player, data, lesson);
        }
        data.markDirty();
        data.markPowersDirty();
        player.sendSystemMessage(Component.translatable("message.noirm.master.learned", Component.translatable(def.name()),
                Component.translatable(lesson.titleKey())).withStyle(ChatFormatting.GREEN));
        if (lesson.ryo() > 0) {
            player.sendSystemMessage(Component.translatable("message.noirm.master.paid", com.noirm.economy.Ryo.text(lesson.ryo()))
                    .withStyle(ChatFormatting.GRAY));
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f);
        return true;
    }

    /** The lang key of a jutsu's name: {@code jutsu.<ns>.<path with dots>}. */
    public static String jutsuKey(ResourceLocation id) {
        return com.noirm.dojutsu.Sharingan.jutsuKey(id);
    }

    static String fmt(float value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}

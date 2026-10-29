package com.noirm.mission;

import com.noirm.Noirm;
import com.noirm.clan.WorldClans;
import com.noirm.combat.CombatSystem;
import com.noirm.entity.SparringNinja;
import com.noirm.gear.NoirmGear;
import com.noirm.registry.NoirmEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Rank-up exams. With enough rank points, a mission board offers the next rank's exam:
 * <ul>
 *   <li><b>Genin test:</b> a spar with a chūnin examiner.</li>
 *   <li><b>Chūnin exam:</b> a two-round tournament against rival genin, one after the other
 *       (you're healed between rounds).</li>
 *   <li><b>Jōnin trial:</b> a hard solo mission (a dangerous rogue; the final blow must be
 *       yours), then, back at a board, a fight with a jōnin.</li>
 * </ul>
 * Nobody dies in an exam fight: you win a round by wearing your opponent down to
 * {@link #WIN_HEALTH} of their health; you lose if you drop to {@link #LOSE_HEALTH} of yours, run
 * out of time ({@link #FIGHT_SECONDS}) or walk away ({@link #LEAVE_RANGE}). Only you and your
 * opponent can hurt each other. A failed exam can be retaken after {@link #COOLDOWN_TICKS}.
 *
 * <p>ANBU and Kage candidate have no exam: the village invites you once you have the points and
 * enough A / S missions ({@link #afterPoints}).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class Exams {
    public static final String EXAMINER_TAG = "noirm_examiner";
    public static final int FIGHT_SECONDS = 180;
    public static final float WIN_HEALTH = 0.3f;
    public static final float LOSE_HEALTH = 0.15f;
    public static final int COOLDOWN_TICKS = 20 * 60 * 5;
    public static final double LEAVE_RANGE = 40;
    /** You need this share of your health to start an exam. */
    public static final float START_HEALTH = 0.5f;

    private Exams() {}

    /** The strength (speed and taijutsu) of each round's opponent. */
    public static float[] rounds(PlayerRank target) {
        return switch (target) {
            case GENIN -> new float[] {16f};
            case CHUNIN -> new float[] {22f, 30f};
            case JONIN -> new float[] {48f};
            default -> new float[0];
        };
    }

    /** Who you face: {@code exam.noirm.opponent.<key>}. */
    static String opponentKey(PlayerRank target) {
        return switch (target) {
            case GENIN -> "chunin_examiner";
            case CHUNIN -> "rival";
            default -> "jonin_examiner";
        };
    }

    /** The next rank's exam, if it has one (null at the top, or for invitations). */
    public static PlayerRank nextExam(Player player) {
        PlayerRank next = PlayerRank.of(player).next();
        return next != null && next.exam() ? next : null;
    }

    /** Why {@code player} can't start their exam now (null if they can). */
    public static Component blocker(Player player) {
        CareerData career = Missions.career(player).orElse(null);
        if (career == null) {
            return Component.translatable("mission.noirm.why.unknown");
        }
        PlayerRank next = career.rank().next();
        if (next == null) {
            return Component.translatable("exam.noirm.why.top");
        }
        if (!next.exam()) {
            return Component.translatable("exam.noirm.why.invitation", next.title(), next.points(), next.missions(), next.letter().name());
        }
        if (career.points() < next.points()) {
            return Component.translatable("exam.noirm.why.points", career.points(), next.points());
        }
        if (career.exam() != null) {
            return Component.translatable("exam.noirm.why.running");
        }
        if (career.mission() != null) {
            return Component.translatable("mission.noirm.why.busy");
        }
        long wait = career.examCooldownUntil() - player.level().getGameTime();
        if (wait > 0) {
            return Component.translatable("exam.noirm.why.cooldown", (wait + 19) / 20);
        }
        if (player.getHealth() < player.getMaxHealth() * START_HEALTH) {
            return Component.translatable("exam.noirm.why.rest");
        }
        return null;
    }

    /** Starts the next rank's exam at the board at {@code board}. Returns whether it started (if not, the player is told why). */
    public static boolean start(Player player, BlockPos board) {
        Component why = blocker(player);
        if (why != null) {
            player.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
            return false;
        }
        CareerData career = Missions.career(player).orElseThrow();
        PlayerRank target = career.rank().next();
        Missions.remember(player);
        player.sendSystemMessage(Component.translatable("exam.noirm.start." + target.key()).withStyle(ChatFormatting.GOLD));
        if (target == PlayerRank.JONIN) {
            career.setExam(new CareerData.Exam(target, board.immutable(), CareerData.Exam.Stage.TRIAL));
            Component trouble = Missions.accept(player, MissionTemplates.JONIN_TRIAL, board);
            if (trouble != null) {
                career.setExam(null);
                player.displayClientMessage(trouble.copy().withStyle(ChatFormatting.RED), true);
                return false;
            }
            return true;
        }
        CareerData.Exam exam = new CareerData.Exam(target, board.immutable(), CareerData.Exam.Stage.FIGHT);
        career.setExam(exam);
        spawnRound(player, exam);
        return true;
    }

    /** The jōnin trial mission is done: on to the fight. */
    static void trialDone(Player player, BlockPos board) {
        CareerData career = Missions.career(player).orElse(null);
        CareerData.Exam exam = career == null ? null : career.exam();
        if (exam == null || exam.stage != CareerData.Exam.Stage.TRIAL) {
            return;
        }
        exam.stage = CareerData.Exam.Stage.FIGHT;
        exam.round = 0;
        career.markDirty();
        player.sendSystemMessage(Component.translatable("exam.noirm.trial_done").withStyle(ChatFormatting.GOLD));
        spawnRound(player, exam);
    }

    /** Brings this round's opponent, five blocks in front of the player, and starts the clock. */
    static void spawnRound(Player player, CareerData.Exam exam) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        float[] rounds = rounds(exam.target);
        float strength = rounds.length == 0 ? 20f : rounds[Math.min(exam.round, rounds.length - 1)];
        SparringNinja opponent = NoirmEntities.SPARRING_NINJA.get().create(level);
        if (opponent == null) {
            return;
        }
        Vec3 look = player.getViewVector(1f).multiply(1, 0, 1);
        Vec3 at = player.position().add(look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 5) : look.normalize().scale(5));
        opponent.moveTo(at.x, player.getY(), at.z, player.getYRot() + 180f, 0f);
        opponent.setCombatStats(strength, strength);
        opponent.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null, null);
        if (level.getServer() != null) {
            opponent.joinClan(WorldClans.get(level.getServer()).rollForNewcomer(level.getRandom()));
        }
        dress(opponent, exam.target);
        opponent.setCustomName(Component.translatable("exam.noirm.opponent." + opponentKey(exam.target)));
        opponent.setPersistenceRequired();
        opponent.addTag(EXAMINER_TAG);
        Missions.tagFor(opponent, player);
        level.addFreshEntity(opponent);
        opponent.setTarget(player);
        exam.examiner = opponent.getUUID();
        exam.deadline = level.getGameTime() + FIGHT_SECONDS * 20L;
        Missions.career(player).ifPresent(CareerData::markDirty);
        CombatSystem.setCombatMode(player, true); // an exam fight is a fight: hands up
        player.sendSystemMessage(Component.translatable("exam.noirm.round", exam.round + 1, Math.max(1, rounds.length),
                opponent.getDisplayName(), FIGHT_SECONDS).withStyle(ChatFormatting.YELLOW));
        level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 0.4f, 1.6f);
    }

    private static void dress(SparringNinja opponent, PlayerRank target) {
        Item head = NoirmGear.FOREHEAD_PROTECTOR_NAVY.get();
        Item chest = switch (target) {
            case GENIN -> NoirmGear.FLAK_VEST.get();
            case CHUNIN -> NoirmGear.TRAINING_SHIRT.get();
            default -> NoirmGear.JONIN_VEST.get();
        };
        opponent.setGearRolled(true);
        opponent.setItemSlot(EquipmentSlot.HEAD, new ItemStack(head));
        opponent.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chest));
        opponent.setItemSlot(EquipmentSlot.LEGS, new ItemStack(NoirmGear.SHINOBI_PANTS.get()));
        opponent.setItemSlot(EquipmentSlot.FEET, new ItemStack(NoirmGear.SHINOBI_SANDALS.get()));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            opponent.setDropChance(slot, 0f);
        }
    }

    /** The exam's clock, the opponent's whereabouts and focus (every half second). */
    public static void update(Player player) {
        CareerData career = Missions.career(player).orElse(null);
        CareerData.Exam exam = career == null ? null : career.exam();
        if (exam == null || exam.stage != CareerData.Exam.Stage.FIGHT || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Missions.remember(player);
        Entity found = exam.examiner == null ? null : level.getEntity(exam.examiner);
        if (!(found instanceof SparringNinja opponent) || !opponent.isAlive()) {
            if (found == null && exam.examiner != null && Missions.flat(player, exam.board) > LEAVE_RANGE * 2) {
                fail(player, "exam.noirm.failed.left");
            } else if (found == null) {
                spawnRound(player, exam); // the opponent got lost (unloaded): a fresh one steps up
            }
            return;
        }
        if (level.getGameTime() > exam.deadline) {
            fail(player, "exam.noirm.failed.time");
        } else if (opponent.distanceTo(player) > LEAVE_RANGE) {
            fail(player, "exam.noirm.failed.left");
        } else if (opponent.getTarget() != player) {
            opponent.setTarget(player);
        }
    }

    /** You wore this round's opponent down: next round, or the new rank. */
    static void roundWon(Player player, SparringNinja opponent) {
        CareerData career = Missions.career(player).orElse(null);
        CareerData.Exam exam = career == null ? null : career.exam();
        if (exam == null || !opponent.getUUID().equals(exam.examiner)) {
            return;
        }
        if (opponent.level() instanceof ServerLevel level) {
            Missions.poof(level, opponent);
        }
        opponent.discard();
        exam.examiner = null;
        exam.round++;
        if (exam.round < rounds(exam.target).length) {
            player.heal(player.getMaxHealth());
            com.noirm.player.ShinobiCapability.get(player).ifPresent(com.noirm.player.ShinobiData::refill);
            player.sendSystemMessage(Component.translatable("exam.noirm.round_won").withStyle(ChatFormatting.GREEN));
            career.markDirty();
            spawnRound(player, exam);
            return;
        }
        career.setExam(null);
        player.sendSystemMessage(Component.translatable("exam.noirm.passed." + exam.target.key()).withStyle(ChatFormatting.GREEN));
        promote(player, exam.target);
    }

    /** The exam fails: the opponent leaves, and it can be retaken after a while. */
    public static void fail(Player player, String reasonKey) {
        CareerData career = Missions.career(player).orElse(null);
        CareerData.Exam exam = career == null ? null : career.exam();
        if (exam == null) {
            return;
        }
        career.setExam(null);
        if (exam.examiner != null && player.level() instanceof ServerLevel level) {
            Entity opponent = level.getEntity(exam.examiner);
            if (opponent != null) {
                Missions.poof(level, opponent);
                opponent.discard();
            }
        }
        Mission trial = career.mission();
        if (trial != null && trial.definition() != null && trial.definition().exam()) {
            Missions.cleanUp(player, trial);
            career.setMission(null);
        }
        career.setExamCooldownUntil(player.level().getGameTime() + COOLDOWN_TICKS);
        player.sendSystemMessage(Component.translatable("exam.noirm.failed", exam.target.title(), Component.translatable(reasonKey),
                COOLDOWN_TICKS / 1200).withStyle(ChatFormatting.RED));
    }

    /** Makes {@code player} {@code rank}: a title, a sound, the rank's gift, their name updated. */
    public static void promote(Player player, PlayerRank rank) {
        CareerData career = Missions.career(player).orElse(null);
        if (career == null) {
            return;
        }
        career.setRank(rank);
        Component title = Component.translatable("rank.noirm.promoted", rank.title());
        player.sendSystemMessage(title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (player instanceof ServerPlayer sp && sp.connection != null && sp.connection.connection.channel() != null) {
            sp.connection.send(new ClientboundSetTitleTextPacket(rank.title().copy().withStyle(ChatFormatting.GOLD)));
            sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("rank.noirm.promoted.sub")));
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
        Item gift = switch (rank) {
            case GENIN -> NoirmGear.FOREHEAD_PROTECTOR_NAVY.get();
            case CHUNIN -> NoirmGear.FLAK_VEST.get();
            case JONIN -> NoirmGear.JONIN_VEST.get();
            case ANBU -> NoirmGear.ANBU_MASK.get();
            case KAGE_CANDIDATE -> NoirmGear.KAGE_HAT_RED.get();
            default -> null;
        };
        if (gift != null) {
            Missions.give(player, new ItemStack(gift));
            player.sendSystemMessage(Component.translatable("rank.noirm.gift", new ItemStack(gift).getHoverName()).withStyle(ChatFormatting.GRAY));
        }
        player.refreshDisplayName();
        afterPoints(player);
    }

    /**
     * After rank points change: tells the player when their next exam opens, and promotes them
     * when an invitation rank's conditions are met.
     */
    public static void afterPoints(Player player) {
        CareerData career = Missions.career(player).orElse(null);
        PlayerRank next = career == null ? null : career.rank().next();
        if (next == null || career.points() < next.points()) {
            return;
        }
        if (next.exam()) {
            if (career.exam() == null) {
                player.sendSystemMessage(Component.translatable("exam.noirm.ready", next.title()).withStyle(ChatFormatting.AQUA));
            }
        } else if (career.completed(next.letter()) >= next.missions()) {
            player.sendSystemMessage(Component.translatable("rank.noirm.invited", next.title()).withStyle(ChatFormatting.AQUA));
            promote(player, next);
        }
    }

    // ---------------------------------------------------------------- nobody dies in an exam

    /**
     * Exam fights: only the examinee and their opponent hurt each other, and whoever would drop to
     * their floor stops there and the round ends. Runs last, after guard and armor.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.isCanceled()) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (victim instanceof SparringNinja opponent && opponent.getTags().contains(EXAMINER_TAG)) {
            Player owner = Missions.owner(opponent);
            if (owner == null || attacker != owner) {
                event.setCanceled(true); // only the examinee's blows count
                return;
            }
            float floor = opponent.getMaxHealth() * WIN_HEALTH;
            if (opponent.getHealth() - event.getAmount() <= floor) {
                event.setAmount(Math.max(0f, opponent.getHealth() - floor));
                roundWon(owner, opponent);
            }
            return;
        }
        if (attacker != null && attacker.getTags().contains(EXAMINER_TAG)) {
            Player owner = Missions.owner(attacker);
            if (victim != owner || !(victim instanceof Player player)) {
                event.setCanceled(true); // examiners only fight their examinee
                return;
            }
            float floor = Math.max(1f, player.getMaxHealth() * LOSE_HEALTH);
            if (player.getHealth() - event.getAmount() <= floor) {
                event.setAmount(Math.max(0f, player.getHealth() - floor));
                fail(player, "exam.noirm.failed.fell");
            }
        }
    }
}

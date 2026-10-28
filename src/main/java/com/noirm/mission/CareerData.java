package com.noirm.mission;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A player's shinobi career, saved in {@code ShinobiData}: the ryo wallet, rank and rank points,
 * missions completed by rank letter, the mission they're on, and any rank-up exam under way.
 */
public final class CareerData {
    /** No wallet holds more (keeps sums inside an int). */
    public static final int MAX_RYO = 1_000_000_000;

    private int ryo;
    private PlayerRank rank = PlayerRank.ACADEMY;
    private int points;
    private final int[] completed = new int[MissionTemplate.Letter.values().length];
    private Mission mission;
    private Exam exam;
    /** Game time before which a failed exam can't be retaken. */
    private long examCooldownUntil;
    /** Board offers already taken today ("x,y,z#index"), and which day that is. */
    private final Set<String> takenOffers = new HashSet<>();
    private long offersDay = -1;
    private boolean dirty = true;

    /** A rank-up exam under way. */
    public static final class Exam {
        public enum Stage {
            /** The jōnin's solo trial mission (the active mission). */
            TRIAL,
            /** A fight with an examiner or a rival. */
            FIGHT
        }

        public final PlayerRank target;
        public final BlockPos board;
        public Stage stage;
        /** The fight's round (0-based) and the examiner fighting it. */
        public int round;
        public UUID examiner;
        public long deadline;

        public Exam(PlayerRank target, BlockPos board, Stage stage) {
            this.target = target;
            this.board = board;
            this.stage = stage;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Target", target.name());
            tag.putLong("Board", board.asLong());
            tag.putString("Stage", stage.name());
            tag.putInt("Round", round);
            if (examiner != null) {
                tag.putUUID("Examiner", examiner);
            }
            tag.putLong("Deadline", deadline);
            return tag;
        }

        static Exam load(CompoundTag tag) {
            PlayerRank target = PlayerRank.byKey(tag.getString("Target"));
            if (target == null) {
                return null;
            }
            Stage stage;
            try {
                stage = Stage.valueOf(tag.getString("Stage"));
            } catch (IllegalArgumentException e) {
                return null;
            }
            Exam exam = new Exam(target, BlockPos.of(tag.getLong("Board")), stage);
            exam.round = tag.getInt("Round");
            exam.examiner = tag.hasUUID("Examiner") ? tag.getUUID("Examiner") : null;
            exam.deadline = tag.getLong("Deadline");
            return exam;
        }
    }

    // ---------------------------------------------------------------- ryo

    public int ryo() {
        return ryo;
    }

    public void setRyo(int amount) {
        int clamped = Math.max(0, Math.min(MAX_RYO, amount));
        if (clamped != ryo) {
            ryo = clamped;
            dirty = true;
        }
    }

    // ---------------------------------------------------------------- rank

    public PlayerRank rank() {
        return rank;
    }

    public void setRank(PlayerRank rank) {
        this.rank = rank == null ? PlayerRank.ACADEMY : rank;
        dirty = true;
    }

    public int points() {
        return points;
    }

    public void setPoints(int points) {
        this.points = Math.max(0, points);
        dirty = true;
    }

    public int completed(MissionTemplate.Letter letter) {
        return completed[letter.ordinal()];
    }

    public void addCompleted(MissionTemplate.Letter letter) {
        completed[letter.ordinal()]++;
        dirty = true;
    }

    public int totalCompleted() {
        int total = 0;
        for (int n : completed) {
            total += n;
        }
        return total;
    }

    // ---------------------------------------------------------------- mission and exam

    public Mission mission() {
        return mission;
    }

    public void setMission(Mission mission) {
        this.mission = mission;
        dirty = true;
    }

    public Exam exam() {
        return exam;
    }

    public void setExam(Exam exam) {
        this.exam = exam;
        dirty = true;
    }

    public long examCooldownUntil() {
        return examCooldownUntil;
    }

    public void setExamCooldownUntil(long time) {
        examCooldownUntil = time;
        dirty = true;
    }

    /** Whether the board offer {@code key} was already taken on {@code day}. */
    public boolean offerTaken(String key, long day) {
        return day == offersDay && takenOffers.contains(key);
    }

    public void takeOffer(String key, long day) {
        if (day != offersDay) {
            takenOffers.clear();
            offersDay = day;
        }
        takenOffers.add(key);
    }

    // ---------------------------------------------------------------- syncing and saving

    public void markDirty() {
        dirty = true;
    }

    /** Whether the client's copy (HUD) needs refreshing; clears the flag. */
    public boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Ryo", ryo);
        tag.putString("Rank", rank.name());
        tag.putInt("Points", points);
        tag.putIntArray("Completed", completed.clone());
        if (mission != null) {
            tag.put("Mission", mission.save());
        }
        if (exam != null) {
            tag.put("Exam", exam.save());
        }
        tag.putLong("ExamCooldown", examCooldownUntil);
        ListTag taken = new ListTag();
        takenOffers.forEach(k -> taken.add(StringTag.valueOf(k)));
        tag.put("Taken", taken);
        tag.putLong("OffersDay", offersDay);
        return tag;
    }

    public void load(CompoundTag tag) {
        ryo = Math.max(0, tag.getInt("Ryo"));
        PlayerRank saved = PlayerRank.byKey(tag.getString("Rank"));
        rank = saved == null ? PlayerRank.ACADEMY : saved;
        points = Math.max(0, tag.getInt("Points"));
        int[] counts = tag.getIntArray("Completed");
        java.util.Arrays.fill(completed, 0);
        System.arraycopy(counts, 0, completed, 0, Math.min(counts.length, completed.length));
        mission = tag.contains("Mission") ? Mission.load(tag.getCompound("Mission")) : null;
        exam = tag.contains("Exam") ? Exam.load(tag.getCompound("Exam")) : null;
        examCooldownUntil = tag.getLong("ExamCooldown");
        takenOffers.clear();
        tag.getList("Taken", Tag.TAG_STRING).forEach(t -> takenOffers.add(t.getAsString()));
        offersDay = tag.contains("OffersDay") ? tag.getLong("OffersDay") : -1;
        dirty = true;
    }
}

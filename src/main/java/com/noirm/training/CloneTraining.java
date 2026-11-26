package com.noirm.training;

import com.noirm.entity.ShadowClone;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shadow clone training, the canon trick (wave "clones", 2026-11-26).
 *
 * <p><b>Memories.</b> Everything a shadow clone learns is kept in its {@link Memories}: XP from its own hits (on
 * enemies or a training dummy), the chakra it spent casting, the jutsu it used, and what it saw. When the clone is
 * dispelled (popped by a hit, timed out, or released), all of it returns to you.
 *
 * <p><b>Training with clones.</b> Clones near you that aren't fighting train alongside you: whatever stat XP you earn,
 * each of them banks {@link #CLONE_TRAIN_SHARE} of it too, and hands it over when it pops. Train with four clones and
 * you learn about four times as fast.
 *
 * <p><b>The price: mental fatigue.</b> Every set of memories coming back tires the mind:
 * {@link #FATIGUE_PER_CLONE} per clone plus {@link #FATIGUE_PER_XP} per point of XP returned. Fatigue fades slowly
 * (faster while meditating or sleeping). Above {@link #TIRED_AT} you're tired: chakra regen and returned memories
 * drop; above {@link #EXHAUSTED_AT} you're exhausted: slow, weak and learning little from clones; at
 * {@link #NO_CLONES_AT} you can't make clones at all until you rest.
 *
 * <p>Numbers at the top (docs/TUNING.md).
 */
public final class CloneTraining {
    /** Each idle clone near you banks this share of the stat XP you earn. */
    public static final float CLONE_TRAIN_SHARE = 0.8f;
    /** Clones within this many blocks of you train with you. */
    public static final double TRAIN_RANGE = 32;
    /** Fatigue per clone whose memories come back, and per point of XP they bring. */
    public static final float FATIGUE_PER_CLONE = 3f;
    public static final float FATIGUE_PER_XP = 0.05f;
    public static final float MAX_FATIGUE = 100f;
    /** Fatigue lost per second, and how much faster while meditating or sleeping. */
    public static final float FATIGUE_DECAY_PER_SECOND = 0.15f;
    public static final float REST_DECAY_FACTOR = 5f;
    /** Tired: chakra regen ×{@link #TIRED_REGEN}, returned memories ×{@link #TIRED_MEMORY}. */
    public static final float TIRED_AT = 40f;
    public static final float TIRED_REGEN = 0.75f;
    public static final float TIRED_MEMORY = 0.75f;
    /** Exhausted: slow and weak, regen ×{@link #EXHAUSTED_REGEN}, memories ×{@link #EXHAUSTED_MEMORY}. */
    public static final float EXHAUSTED_AT = 75f;
    public static final float EXHAUSTED_REGEN = 0.5f;
    public static final float EXHAUSTED_MEMORY = 0.4f;
    /** At this fatigue the mind can't hold another clone. */
    public static final float NO_CLONES_AT = 95f;
    /** A clone that saw an enemy this far from you tells you where, when it pops (scouting). */
    public static final double SCOUT_DISTANCE = 16;

    /** True while memories are coming back, so returned XP isn't banked by other clones again. */
    private static boolean returning;

    private CloneTraining() {}

    /** What a clone learned, returned to its owner when it pops. */
    public static final class Memories {
        private final Map<TrainingSystem.Stat, Float> stats = new EnumMap<>(TrainingSystem.Stat.class);
        private final Map<Supplementary.Stat, Float> supplementary = new EnumMap<>(Supplementary.Stat.class);
        private final Map<ResourceLocation, Integer> jutsu = new HashMap<>();
        private Component sawName;
        private net.minecraft.core.BlockPos sawAt;

        public void add(TrainingSystem.Stat stat, float xp) {
            if (xp > 0) {
                stats.merge(stat, xp, Float::sum);
            }
        }

        public void add(Supplementary.Stat stat, float xp) {
            if (xp > 0) {
                supplementary.merge(stat, xp, Float::sum);
            }
        }

        public void usedJutsu(ResourceLocation id) {
            jutsu.merge(id, 1, Integer::sum);
        }

        public void saw(Component name, net.minecraft.core.BlockPos at) {
            sawName = name;
            sawAt = at;
        }

        public float xp(TrainingSystem.Stat stat) {
            return stats.getOrDefault(stat, 0f);
        }

        public float total() {
            float total = 0f;
            for (float v : stats.values()) {
                total += v;
            }
            for (float v : supplementary.values()) {
                total += v;
            }
            return total;
        }

        public boolean isEmpty() {
            return stats.isEmpty() && supplementary.isEmpty() && jutsu.isEmpty() && sawName == null;
        }
    }

    // ---------------------------------------------------------------- training alongside you

    /** You earned {@code xp} in {@code stat}: every idle clone near you banks its share. */
    public static void onOwnerXp(Player player, TrainingSystem.Stat stat, float xp) {
        if (returning || xp <= 0 || player.level().isClientSide()) {
            return;
        }
        for (ShadowClone clone : idleClones(player)) {
            clone.memories().add(stat, xp * CLONE_TRAIN_SHARE);
        }
    }

    public static void onOwnerXp(Player player, Supplementary.Stat stat, float xp) {
        if (returning || xp <= 0 || player.level().isClientSide()) {
            return;
        }
        for (ShadowClone clone : idleClones(player)) {
            clone.memories().add(stat, xp * CLONE_TRAIN_SHARE);
        }
    }

    private static List<ShadowClone> idleClones(Player player) {
        return player.level().getEntitiesOfClass(ShadowClone.class, player.getBoundingBox().inflate(TRAIN_RANGE),
                c -> c.isAlive() && c.isOwnedBy(player) && c.isTrainingIdle());
    }

    // ---------------------------------------------------------------- memories coming back

    /** A clone popped: its memories (XP, jutsu practice, what it saw) return, and tire the mind. */
    public static void returnMemories(Player owner, ShadowClone clone, Memories memories) {
        if (memories.isEmpty()) {
            addFatigue(owner, FATIGUE_PER_CLONE * 0.5f); // even an empty clone's return is felt
            return;
        }
        float factor = memoryFactor(owner);
        float total = memories.total() * factor;
        returning = true;
        try {
            memories.stats.forEach((stat, xp) -> TrainingSystem.grant(owner, stat, xp * factor));
            ShinobiCapability.get(owner).ifPresent(data -> {
                memories.supplementary.forEach((stat, xp) -> Supplementary.grant(owner, data, stat, xp * factor));
                memories.jutsu.forEach((id, uses) -> data.jutsu().addXp(id, Math.max(1, Math.round(uses * factor))));
                data.markDirty();
            });
        } finally {
            returning = false;
        }
        addFatigue(owner, FATIGUE_PER_CLONE + total * FATIGUE_PER_XP);
        if (total >= 0.5f) {
            owner.displayClientMessage(Component.translatable("message.noirm.clone.memories", Math.round(total))
                    .withStyle(ChatFormatting.AQUA), true);
        }
        if (memories.sawName != null && memories.sawAt != null
                && owner.blockPosition().distSqr(memories.sawAt) > SCOUT_DISTANCE * SCOUT_DISTANCE) {
            owner.sendSystemMessage(Component.translatable("message.noirm.clone.scouted", memories.sawName,
                    memories.sawAt.getX(), memories.sawAt.getY(), memories.sawAt.getZ()).withStyle(ChatFormatting.GRAY));
        }
    }

    /** How much of a clone's memories sink in, given the mind's fatigue. */
    public static float memoryFactor(Player player) {
        float fatigue = fatigue(player);
        return fatigue >= EXHAUSTED_AT ? EXHAUSTED_MEMORY : fatigue >= TIRED_AT ? TIRED_MEMORY : 1f;
    }

    // ---------------------------------------------------------------- fatigue

    public static float fatigue(Player player) {
        return ShinobiCapability.get(player).map(d -> d.training().cloneFatigue()).orElse(0f);
    }

    public static void setFatigue(Player player, float value) {
        ShinobiCapability.get(player).ifPresent(d -> d.training().setCloneFatigue(Math.max(0f, Math.min(MAX_FATIGUE, value))));
    }

    public static void addFatigue(Player player, float amount) {
        float before = fatigue(player);
        setFatigue(player, before + amount);
        float after = fatigue(player);
        if (before < TIRED_AT && after >= TIRED_AT) {
            player.displayClientMessage(Component.translatable("message.noirm.clone.tired").withStyle(ChatFormatting.YELLOW), false);
        } else if (before < EXHAUSTED_AT && after >= EXHAUSTED_AT) {
            player.displayClientMessage(Component.translatable("message.noirm.clone.exhausted").withStyle(ChatFormatting.RED), false);
        }
    }

    /** True when the mind is too tired to make another clone. */
    public static boolean tooTiredForClones(Player player) {
        return fatigue(player) >= NO_CLONES_AT;
    }

    /** Chakra regen multiplier from fatigue. */
    public static float regenMultiplier(ShinobiData data) {
        float fatigue = data.training().cloneFatigue();
        return fatigue >= EXHAUSTED_AT ? EXHAUSTED_REGEN : fatigue >= TIRED_AT ? TIRED_REGEN : 1f;
    }

    /** Once a second for each player: fatigue fades (faster at rest), and exhaustion slows the body. */
    public static void tick(Player player, ShinobiData data) {
        if (player.tickCount % 20 != 0) {
            return;
        }
        float fatigue = data.training().cloneFatigue();
        if (fatigue <= 0f) {
            return;
        }
        boolean resting = player.isSleeping() || com.noirm.senjutsu.Meditation.isMeditating(player);
        data.training().setCloneFatigue(Math.max(0f, fatigue - FATIGUE_DECAY_PER_SECOND * (resting ? REST_DECAY_FACTOR : 1f)));
        if (fatigue >= EXHAUSTED_AT) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, false, true));
        }
    }
}

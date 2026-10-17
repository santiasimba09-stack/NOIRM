package com.noirm.clan;

import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.EnumMap;
import java.util.Map;

/**
 * The nature tree. You <b>own</b> a nature when your clan gave it to you (or you trained or
 * unlocked it). A combined nature becomes <b>available</b> once you own every nature it mixes
 * <i>and</i> have mastered ({@link #REQUIRED_MASTERY}) at least one jutsu of each; then you can
 * unlock it from the jutsu screen. Basic natures you lack stay <b>locked</b> (only very hard
 * training will open them, later).
 */
public final class NatureSystem {
    public enum Status { OWNED, AVAILABLE, LOCKED }

    /** Mastery (★) needed in a jutsu of each component nature. */
    public static final int REQUIRED_MASTERY = 2;

    private NatureSystem() {}

    /**
     * Works on both sides: the client calls it with the synced natures and the best mastery per
     * nature to draw the tree; the server with the player's real data.
     */
    public static Status status(Nature nature, Map<Nature, Float> owned, Map<Nature, Integer> bestMastery) {
        if (owned.getOrDefault(nature, 0f) > 0f) {
            return Status.OWNED;
        }
        if (nature.isBasic()) {
            return Status.LOCKED;
        }
        for (Nature part : nature.components()) {
            if (owned.getOrDefault(part, 0f) <= 0f || bestMastery.getOrDefault(part, 0) < REQUIRED_MASTERY) {
                return Status.LOCKED;
            }
        }
        return Status.AVAILABLE;
    }

    public static Status status(ShinobiData data, Nature nature) {
        return status(nature, data.identity().natures(), bestMastery(data));
    }

    /** The highest mastery among the jutsu you know of each nature. */
    public static Map<Nature, Integer> bestMastery(ShinobiData data) {
        Map<Nature, Integer> best = new EnumMap<>(Nature.class);
        for (ResourceLocation id : data.jutsu().known()) {
            JutsuDefinition def = JutsuData.get(id);
            if (def != null && def.nature().isPresent()) {
                best.merge(def.nature().get(), data.jutsu().masteryLevel(id), Math::max);
            }
        }
        return best;
    }

    /**
     * Unlocks an available combined nature: its affinity is your weakest component's (at most
     * natural, 1.0), and you learn its starting jutsu. Returns false if it isn't available.
     */
    public static boolean unlock(Player player, ShinobiData data, Nature nature) {
        if (status(data, nature) != Status.AVAILABLE) {
            return false;
        }
        float affinity = Clan.SECONDARY_AFFINITY;
        for (Nature part : nature.components()) {
            affinity = Math.min(affinity, data.identity().affinity(part));
        }
        grant(data, nature, affinity);
        player.sendSystemMessage(Component.translatable("message.noirm.nature.unlocked",
                Component.translatable(nature.translationKey()).withStyle(s -> s.withColor(nature.color()))).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7f, 1.2f);
        return true;
    }

    /** Gives a nature and its starting jutsu (filling free loadout slots). */
    public static void grant(ShinobiData data, Nature nature, float affinity) {
        data.identity().addNature(nature, affinity);
        for (ResourceLocation id : JutsuData.startersFor(nature)) {
            if (data.jutsu().learn(id, false)) {
                data.jutsu().equipIfRoom(id);
            }
        }
        data.markPowersDirty();
    }

    /** Learns every jutsu unlocked by a trained stat you've now reached (the Rasengan at Reserves 20). */
    public static int checkStatUnlocks(Player player, ShinobiData data) {
        int learned = 0;
        for (Map.Entry<ResourceLocation, JutsuDefinition> entry : JutsuData.all().entrySet()) {
            JutsuDefinition.Unlock unlock = entry.getValue().unlock().orElse(null);
            if (unlock == null || unlock.stat().isEmpty() || data.jutsu().knows(entry.getKey())) {
                continue;
            }
            com.noirm.training.TrainingSystem.Stat stat = null;
            for (com.noirm.training.TrainingSystem.Stat s : com.noirm.training.TrainingSystem.Stat.values()) {
                if (s.key().equalsIgnoreCase(unlock.stat().get())) {
                    stat = s;
                }
            }
            if (stat == null || com.noirm.training.TrainingSystem.stat(data, stat) < unlock.value()) {
                continue;
            }
            JutsuDefinition def = entry.getValue();
            if (def.nature().isPresent() && data.identity().affinity(def.nature().get()) <= 0f) {
                continue;
            }
            data.jutsu().learn(entry.getKey(), false);
            data.jutsu().equipIfRoom(entry.getKey());
            player.sendSystemMessage(Component.translatable("message.noirm.jutsu.unlocked_stat",
                    Component.translatable(com.noirm.dojutsu.Sharingan.jutsuKey(entry.getKey()))).withStyle(ChatFormatting.AQUA));
            learned++;
        }
        if (learned > 0) {
            data.markPowersDirty();
        }
        return learned;
    }

    /**
     * After {@code id} reaches a new mastery level: learn every jutsu that unlocks from it (when
     * you have its nature). Returns how many were learned.
     */
    public static int checkMasteryUnlocks(Player player, ShinobiData data, ResourceLocation id) {
        int level = data.jutsu().masteryLevel(id);
        int learned = 0;
        for (Map.Entry<ResourceLocation, JutsuDefinition> entry : JutsuData.all().entrySet()) {
            JutsuDefinition def = entry.getValue();
            if (def.unlock().isEmpty() || !id.equals(def.unlock().get().after()) || level < def.unlock().get().mastery()
                    || data.jutsu().knows(entry.getKey())) {
                continue;
            }
            if (def.nature().isPresent() && data.identity().affinity(def.nature().get()) <= 0f) {
                continue;
            }
            data.jutsu().learn(entry.getKey(), false);
            data.jutsu().equipIfRoom(entry.getKey());
            player.sendSystemMessage(Component.translatable("message.noirm.jutsu.unlocked",
                    Component.translatable(com.noirm.dojutsu.Sharingan.jutsuKey(entry.getKey()))).withStyle(ChatFormatting.AQUA));
            learned++;
        }
        if (learned > 0) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
            data.markPowersDirty();
        }
        return learned;
    }
}

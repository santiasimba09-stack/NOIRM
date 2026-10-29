package com.noirm.nature;

import com.noirm.dojutsu.Sharingan;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;

/**
 * Learning jutsu from <b>jutsu scrolls</b> (besides mastery chains and masters' lessons).
 *
 * <p>A scroll holds a rank (C, B, A or S) and, sometimes, one named jutsu. Reading it teaches a
 * jutsu of that rank (or the named one) <i>of a nature you own</i>, if your rank allows it
 * ({@link #rankBlocker}); otherwise you keep the scroll for later. Dōjutsu, beast, Mangekyō and
 * Eight Gates techniques never come from scrolls (eyes, beasts and masters give those).
 */
public final class JutsuLearning {
    /** Ranks from weakest to strongest. */
    public static final List<String> RANKS = List.of("E", "D", "C", "B", "A", "S");
    /** Reserves needed to read a scroll of each rank (index as {@link #RANKS}): your chakra must hold the jutsu. */
    public static final float[] RESERVES_FOR_RANK = {0, 0, 0, 15, 25, 40};
    /** Folders whose techniques no scroll teaches. */
    private static final List<String> NOT_FROM_SCROLLS = List.of("taijutsu/", "mangekyo/", "biju/", "byakugan/", "sharingan/");

    /**
     * An extra gate other systems can plug in (the player rank: genin, chūnin, jōnin…): given the
     * player and a jutsu rank, return why they can't learn it yet, or null if they can. The default
     * allows everything, so only the Reserves check applies.
     */
    public static BiFunction<Player, String, Component> extraRankGate = (player, rank) -> null;

    private JutsuLearning() {}

    /** 0 (E) to 5 (S); unknown ranks count as C. */
    public static int rankIndex(String rank) {
        int i = RANKS.indexOf(rank == null ? "" : rank.toUpperCase(Locale.ROOT));
        return i < 0 ? 2 : i;
    }

    /** Why {@code player} can't learn a jutsu of this rank yet, or null if they can. */
    public static Component rankBlocker(Player player, ShinobiData data, String rank) {
        float need = RESERVES_FOR_RANK[rankIndex(rank)];
        if (data.getReserves() < need) {
            return Component.translatable("message.noirm.jutsu_scroll.why.reserves", rank, Math.round(need), Math.round(data.getReserves()));
        }
        return extraRankGate.apply(player, rank);
    }

    /** Whether a scroll could ever teach this jutsu (its kind, not your natures). */
    public static boolean scrollTeachable(ResourceLocation id, JutsuDefinition def) {
        if (def == null || def.requires().isPresent() || com.noirm.master.MasterData.taughtByMaster(def)) {
            return false; // a master's own jutsu are only learned from them
        }
        for (String folder : NOT_FROM_SCROLLS) {
            if (id.getPath().startsWith(folder)) {
                return false;
            }
        }
        return def.nature().isPresent() || id.getPath().startsWith("universal/");
    }

    /** Whether this jutsu's nature is one you own (or it needs none). */
    public static boolean natureAllows(ShinobiData data, JutsuDefinition def) {
        return def.nature().isEmpty() || data.identity().affinity(def.nature().get()) > 0f;
    }

    /** The jutsu a scroll of this rank could teach you right now (not known, your natures, that rank). */
    public static List<ResourceLocation> candidates(ShinobiData data, String rank) {
        List<ResourceLocation> out = new ArrayList<>();
        int r = rankIndex(rank);
        JutsuData.all().forEach((id, def) -> {
            if (!data.jutsu().knows(id) && scrollTeachable(id, def) && natureAllows(data, def) && rankIndex(def.rank()) == r) {
                out.add(id);
            }
        });
        out.sort(java.util.Comparator.comparing(ResourceLocation::toString));
        return out;
    }

    /**
     * Reads a jutsu scroll of {@code rank} (holding {@code named}, if not null). Returns the jutsu
     * learned, or null (the player is told why and keeps the scroll).
     */
    public static ResourceLocation readScroll(Player player, ShinobiData data, String rank, ResourceLocation named) {
        ResourceLocation chosen;
        if (named != null) {
            JutsuDefinition def = JutsuData.get(named);
            if (def == null || !scrollTeachable(named, def)) {
                return refuse(player, Component.translatable("message.noirm.jutsu_scroll.unreadable"));
            }
            if (data.jutsu().knows(named)) {
                return refuse(player, Component.translatable("message.noirm.jutsu_scroll.known", jutsuName(named)));
            }
            if (!natureAllows(data, def)) {
                return refuse(player, Component.translatable("message.noirm.jutsu_scroll.why.nature", jutsuName(named),
                        NatureDrills.name(def.nature().get())));
            }
            Component why = rankBlocker(player, data, def.rank());
            if (why != null) {
                return refuse(player, why);
            }
            chosen = named;
        } else {
            Component why = rankBlocker(player, data, rank);
            if (why != null) {
                return refuse(player, why);
            }
            List<ResourceLocation> pool = candidates(data, rank);
            if (pool.isEmpty()) {
                return refuse(player, Component.translatable("message.noirm.jutsu_scroll.why.none", rank));
            }
            chosen = pool.get(player.getRandom().nextInt(pool.size()));
        }
        data.jutsu().learn(chosen, false);
        data.jutsu().equipIfRoom(chosen);
        data.markPowersDirty();
        player.sendSystemMessage(Component.translatable("message.noirm.jutsu_scroll.learned", jutsuName(chosen)).withStyle(ChatFormatting.AQUA));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        return chosen;
    }

    private static ResourceLocation refuse(Player player, Component why) {
        if (player instanceof ServerPlayer server) {
            server.displayClientMessage(why.copy().withStyle(ChatFormatting.GRAY), true);
        }
        return null;
    }

    static Component jutsuName(ResourceLocation id) {
        return Component.translatable(Sharingan.jutsuKey(id));
    }
}

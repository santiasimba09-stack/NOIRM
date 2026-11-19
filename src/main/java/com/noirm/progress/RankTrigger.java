package com.noirm.progress;

import com.noirm.mission.PlayerRank;
import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:rank}: you are this rank or higher (key: {@code genin}, {@code chunin}, {@code jonin},
 * {@code anbu}, {@code kage_candidate}). {@link #reached} fires for every rank up to yours, so a
 * rank set by command still counts the steps below it.
 */
public final class RankTrigger extends NoirmTrigger {
    public static final RankTrigger INSTANCE = new RankTrigger();

    private RankTrigger() {
        super("rank");
    }

    public static void reached(Player player, PlayerRank rank) {
        for (PlayerRank r : PlayerRank.values()) {
            if (rank.atLeast(r)) {
                INSTANCE.fire(player, "reached", r.key(), r.ordinal());
            }
        }
    }
}

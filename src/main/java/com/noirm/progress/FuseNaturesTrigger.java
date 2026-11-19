package com.noirm.progress;

import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:fuse_natures}: you fused two natures into a kekkei genkai on your own (no clan, no
 * teacher).
 *
 * <p><b>Placeholder for chakra wave part 2:</b> nothing calls {@link #fire} yet. When a solo fusion
 * succeeds, call {@code FuseNaturesTrigger.fire(player, nature)} (nature: the new nature's name,
 * e.g. {@code "ice"}). The advancement {@code noirm:shinobi/natures/fuse_alone} waits for it.
 */
public final class FuseNaturesTrigger extends NoirmTrigger {
    public static final FuseNaturesTrigger INSTANCE = new FuseNaturesTrigger();

    private FuseNaturesTrigger() {
        super("fuse_natures");
    }

    public static void fire(Player player, String nature) {
        INSTANCE.fire(player, "fused", nature, 1);
    }
}

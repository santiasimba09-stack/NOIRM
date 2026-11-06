package com.noirm.progress;

import net.minecraft.world.entity.player.Player;

/**
 * {@code noirm:combat_feat}: a move done right. Events: {@code just_frame_parry}.
 */
public final class CombatFeatTrigger extends NoirmTrigger {
    public static final CombatFeatTrigger INSTANCE = new CombatFeatTrigger();

    private CombatFeatTrigger() {
        super("combat_feat");
    }

    public static void justFrameParry(Player player) {
        INSTANCE.fire(player, "just_frame_parry", null, 1);
    }
}

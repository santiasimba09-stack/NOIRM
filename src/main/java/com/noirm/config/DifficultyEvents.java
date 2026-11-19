package com.noirm.config;

import com.noirm.Noirm;
import com.noirm.ally.Crew;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.TailedBeast;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code difficulty.npcDamage}: damage a ninja (not on your side) or a tailed beast deals to a
 * player is multiplied by the config value, before armor and Durability. Their jutsu and thrown
 * weapons count (the game names the thrower as the source). At 1.0 nothing changes.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class DifficultyEvents {
    private DifficultyEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void hurt(LivingHurtEvent event) {
        double scale = NoirmConfig.NPC_DAMAGE.get();
        if (scale == 1.0 || !(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (isEnemyNpc(event.getSource().getEntity(), player)) {
            event.setAmount((float) (event.getAmount() * scale));
        }
    }

    /** A NOIRM fighter that isn't on {@code player}'s side. */
    public static boolean isEnemyNpc(Entity source, Player player) {
        if (source instanceof TailedBeast) {
            return true;
        }
        return source instanceof SparringNinja ninja && !Crew.sameSide(ninja, player);
    }
}

package com.noirm.jutsu;

import com.noirm.Noirm;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hooks the seal phase into the game: hits taken while weaving seals can pause, slow or break
 * them, and death or logging out always ends the cast (and its weaving slow).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class CastingEvents {
    private CastingEvents() {}

    /** After armor, guard and every other change: the damage that actually lands. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damaged(LivingDamageEvent event) {
        if (!event.isCanceled() && !event.getEntity().level().isClientSide()) {
            JutsuCaster.onHurt(event.getEntity(), event.getAmount());
        }
    }

    @SubscribeEvent
    public static void died(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            JutsuCaster.cancel(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        JutsuCaster.cancel(event.getEntity());
    }
}

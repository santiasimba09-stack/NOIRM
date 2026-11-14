package com.noirm.senjutsu;

import com.noirm.Noirm;
import com.noirm.player.ShinobiCapability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Hooks {@link Meditation} into the game: ticking it, and ending it when you act. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class MeditationEvents {
    private MeditationEvents() {}

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            ShinobiCapability.get(player).ifPresent(data -> Meditation.tick(player, data));
        }
    }

    /** Any damage that gets through ends meditation and counts as a fight. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damaged(LivingDamageEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide() && event.getAmount() > 0 && !event.isCanceled()) {
            Meditation.onHurt(player);
        }
    }

    @SubscribeEvent
    public static void jumped(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            Meditation.stop(player);
        }
    }

    @SubscribeEvent
    public static void attacked(AttackEntityEvent event) {
        stopOnServer(event.getEntity());
    }

    @SubscribeEvent
    public static void leftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        stopOnServer(event.getEntity());
    }

    @SubscribeEvent
    public static void rightClickItem(PlayerInteractEvent.RightClickItem event) {
        stopOnServer(event.getEntity());
    }

    @SubscribeEvent
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        stopOnServer(event.getEntity());
    }

    @SubscribeEvent
    public static void interactEntity(PlayerInteractEvent.EntityInteract event) {
        stopOnServer(event.getEntity());
    }

    @SubscribeEvent
    public static void useItem(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player) {
            stopOnServer(player);
        }
    }

    private static void stopOnServer(Player player) {
        if (player != null && !player.level().isClientSide()) {
            Meditation.stop(player);
        }
    }
}

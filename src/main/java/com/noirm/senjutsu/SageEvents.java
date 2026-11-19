package com.noirm.senjutsu;

import com.noirm.Noirm;
import com.noirm.player.ShinobiCapability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Hooks the sage path into the game: ticking, Sage Mode's toughness, the statue, the trial, syncing. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class SageEvents {
    private SageEvents() {}

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            ShinobiCapability.get(player).ifPresent(data -> {
                SageMode.tick(player, data);
                Sage.tick(player, data);
            });
        }
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            SageTrial.tickAll();
        }
    }

    /** Sage Mode: nature energy hardens the body (before guard and armor, so it simply scales the hit). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void hurt(LivingHurtEvent event) {
        if (!event.getEntity().level().isClientSide() && SageMode.isActive(event.getEntity())) {
            event.setAmount(event.getAmount() * SageMode.DEFENSE);
        }
    }

    /** Nobody dies in the hermit's test: the spirits stop at the floor, and the test is failed. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || !(event.getSource().getEntity() instanceof NatureSpirit)) {
            return;
        }
        var master = SageTrial.masterOf(player);
        if (master == null) {
            return;
        }
        float floor = SageTrial.floor(player);
        if (player.getHealth() - event.getAmount() <= floor) {
            event.setAmount(Math.max(0f, player.getHealth() - floor));
            SageTrial.end(master, false, "message.noirm.sage.trial_worn_down");
        }
    }

    /** Dying ends Sage Mode and the stone (what's carried over to the respawned player is clean). */
    @SubscribeEvent
    public static void died(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShinobiCapability.get(player).ifPresent(data -> {
                SageMode.end(player, false);
                data.senjutsu().sage().setExhaustionTicks(0);
                Sage.clearStone(player, data);
            });
        }
    }

    // ---------------------------------------------------------------- a statue can't act

    @SubscribeEvent
    public static void attack(AttackEntityEvent event) {
        if (statue(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void useItem(PlayerInteractEvent.RightClickItem event) {
        if (statue(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (statue(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (statue(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    private static boolean statue(Player player) {
        return player != null && !player.level().isClientSide() && Sage.isStatue(player);
    }

    // ---------------------------------------------------------------- what others see

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof Player target) {
            SagePackets.sendTo(viewer, target);
        }
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        resend(event.getEntity());
    }

    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        resend(event.getEntity());
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        resend(event.getEntity());
    }

    private static void resend(Player player) {
        ShinobiCapability.get(player).ifPresent(data -> data.senjutsu().sage().resend());
    }
}

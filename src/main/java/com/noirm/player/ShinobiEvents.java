package com.noirm.player;

import com.noirm.Noirm;
import com.noirm.network.NoirmNetwork;
import com.noirm.network.SyncShinobiDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Game events that give players {@link ShinobiData} and keep it ticking and in sync. */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class ShinobiEvents {
    private ShinobiEvents() {}

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(ShinobiCapability.ID, new ShinobiCapability.Provider());
        }
    }

    /** Respawning or returning from the End creates a new player object: carry the data over. */
    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        ShinobiCapability.get(original).ifPresent(oldData ->
                ShinobiCapability.get(event.getEntity()).ifPresent(newData -> {
                    newData.copyFrom(oldData);
                    if (event.isWasDeath()) {
                        newData.refill();
                    }
                }));
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        ShinobiCapability.get(player).ifPresent(data -> {
            data.tick();
            if (data.consumeDirty()) {
                NoirmNetwork.sendToPlayer(player, SyncShinobiDataPacket.of(data));
            }
        });
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        requestSync(event.getEntity());
    }

    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        requestSync(event.getEntity());
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        requestSync(event.getEntity());
    }

    private static void requestSync(Player player) {
        ShinobiCapability.get(player).ifPresent(ShinobiData::markDirty);
    }

    /** Mod-bus events: these fire once while the game loads. */
    @Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {
            event.register(ShinobiData.class);
        }
    }
}

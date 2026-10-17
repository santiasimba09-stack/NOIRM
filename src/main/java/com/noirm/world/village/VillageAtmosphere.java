package com.noirm.world.village;

import com.noirm.Noirm;
import com.noirm.network.NoirmNetwork;
import com.noirm.network.VillagePackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells each player's game which village they're standing in (every two seconds, and only when it
 * changes), so the client can set the air to match: mist in the Mist village, a haze in the Cloud
 * village ({@code com.noirm.client.VillageFog}).
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillageAtmosphere {
    private static final Map<UUID, Integer> LAST = new ConcurrentHashMap<>();
    /** Every kind of ninja village ({@code data/noirm/tags/worldgen/structure/ninja_villages.json}). */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> VILLAGES =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.STRUCTURE, Noirm.id("ninja_villages"));

    private VillageAtmosphere() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 40 != 7) {
            return;
        }
        int palette = paletteAt(player.serverLevel(), player);
        Integer last = LAST.put(player.getUUID(), palette);
        if (last == null || last != palette) {
            NoirmNetwork.sendToPlayer(player, new VillagePackets.Atmosphere(palette));
        }
    }

    /** Plans and sites are cached while a world runs; a new world (or the same one reloaded) starts fresh. */
    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        LAST.clear();
        VillagePlan.clearCache();
        NinjaVillageStructure.clearCache();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }

    /** The look (palette ordinal) of the village piece the player stands in, or -1. */
    public static int paletteAt(ServerLevel level, ServerPlayer player) {
        var start = level.structureManager().getStructureWithPieceAt(player.blockPosition(), VILLAGES);
        if (!start.isValid()) {
            // Not a world-generated village: maybe one built with /noirm village.
            BuiltVillages.Entry built = BuiltVillages.of(level).at(player.blockPosition());
            return built == null ? -1 : built.palette().ordinal();
        }
        for (var piece : start.getPieces()) {
            if (piece instanceof VillagePiece village) {
                return village.palette().ordinal();
            }
        }
        return -1;
    }
}

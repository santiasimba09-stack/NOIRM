package com.noirm.network;

import com.noirm.Noirm;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** NOIRM's network channel: every packet between server and client is registered here. */
public final class NoirmNetwork {
    /** Bump this when packets change, so mismatched client/server versions refuse to connect. */
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Noirm.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private NoirmNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SyncShinobiDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncShinobiDataPacket::encode)
                .decoder(SyncShinobiDataPacket::decode)
                .consumerMainThread(SyncShinobiDataPacket::handle)
                .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}

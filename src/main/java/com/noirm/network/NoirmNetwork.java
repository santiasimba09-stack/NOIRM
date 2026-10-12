package com.noirm.network;

import com.noirm.Noirm;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** NOIRM's network channel: every packet between server and client is registered here. */
public final class NoirmNetwork {
    /** Bump this when packets change, so mismatched client/server versions refuse to connect. */
    private static final String PROTOCOL_VERSION = "2";

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

        // Combat: client → server
        CHANNEL.messageBuilder(CombatInputPackets.SetCombatMode.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CombatInputPackets.SetCombatMode::encode).decoder(CombatInputPackets.SetCombatMode::decode)
                .consumerMainThread(CombatInputPackets.SetCombatMode::handle).add();
        CHANNEL.messageBuilder(CombatInputPackets.Attack.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CombatInputPackets.Attack::encode).decoder(CombatInputPackets.Attack::decode)
                .consumerMainThread(CombatInputPackets.Attack::handle).add();
        CHANNEL.messageBuilder(CombatInputPackets.Dodge.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CombatInputPackets.Dodge::encode).decoder(CombatInputPackets.Dodge::decode)
                .consumerMainThread(CombatInputPackets.Dodge::handle).add();
        CHANNEL.messageBuilder(CombatInputPackets.Guard.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CombatInputPackets.Guard::encode).decoder(CombatInputPackets.Guard::decode)
                .consumerMainThread(CombatInputPackets.Guard::handle).add();

        // Combat: server → client
        CHANNEL.messageBuilder(CombatClientPackets.PlayAnimation.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CombatClientPackets.PlayAnimation::encode).decoder(CombatClientPackets.PlayAnimation::decode)
                .consumerMainThread(CombatClientPackets.PlayAnimation::handle).add();
        CHANNEL.messageBuilder(CombatClientPackets.SyncState.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CombatClientPackets.SyncState::encode).decoder(CombatClientPackets.SyncState::decode)
                .consumerMainThread(CombatClientPackets.SyncState::handle).add();
        CHANNEL.messageBuilder(CombatClientPackets.Feedback.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CombatClientPackets.Feedback::encode).decoder(CombatClientPackets.Feedback::decode)
                .consumerMainThread(CombatClientPackets.Feedback::handle).add();
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Sends to everyone who can see {@code entity}, and to the entity itself if it's a player. */
    public static void sendToTrackingAndSelf(Entity entity, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}

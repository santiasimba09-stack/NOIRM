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
    private static final String PROTOCOL_VERSION = "17";

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
        CHANNEL.messageBuilder(CombatInputPackets.CycleStyle.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CombatInputPackets.CycleStyle::encode).decoder(CombatInputPackets.CycleStyle::decode)
                .consumerMainThread(CombatInputPackets.CycleStyle::handle).add();
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

        // Powers: client → server
        CHANNEL.messageBuilder(PowerPackets.JutsuKey.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PowerPackets.JutsuKey::encode).decoder(PowerPackets.JutsuKey::decode)
                .consumerMainThread(PowerPackets.JutsuKey::handle).add();
        CHANNEL.messageBuilder(PowerPackets.EquipJutsu.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PowerPackets.EquipJutsu::encode).decoder(PowerPackets.EquipJutsu::decode)
                .consumerMainThread(PowerPackets.EquipJutsu::handle).add();
        CHANNEL.messageBuilder(PowerPackets.TogglePower.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PowerPackets.TogglePower::encode).decoder(PowerPackets.TogglePower::decode)
                .consumerMainThread(PowerPackets.TogglePower::handle).add();
        CHANNEL.messageBuilder(PowerPackets.UnlockNature.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PowerPackets.UnlockNature::encode).decoder(PowerPackets.UnlockNature::decode)
                .consumerMainThread(PowerPackets.UnlockNature::handle).add();

        // Powers: server → client
        CHANNEL.messageBuilder(PowerPackets.SyncPowers.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PowerPackets.SyncPowers::encode).decoder(PowerPackets.SyncPowers::decode)
                .consumerMainThread(PowerPackets.SyncPowers::handle).add();
        CHANNEL.messageBuilder(PowerPackets.Visuals.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PowerPackets.Visuals::encode).decoder(PowerPackets.Visuals::decode)
                .consumerMainThread(PowerPackets.Visuals::handle).add();
        CHANNEL.messageBuilder(JutsuCatalogPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(JutsuCatalogPacket::encode).decoder(JutsuCatalogPacket::decode)
                .consumerMainThread(JutsuCatalogPacket::handle).add();
        CHANNEL.messageBuilder(PowerPackets.Precognition.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PowerPackets.Precognition::encode).decoder(PowerPackets.Precognition::decode)
                .consumerMainThread(PowerPackets.Precognition::handle).add();
        CHANNEL.messageBuilder(com.noirm.ally.AllyPackets.Info.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.noirm.ally.AllyPackets.Info::encode).decoder(com.noirm.ally.AllyPackets.Info::decode)
                .consumerMainThread(com.noirm.ally.AllyPackets.Info::handle).add();
        CHANNEL.messageBuilder(com.noirm.ally.AllyPackets.Command.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.noirm.ally.AllyPackets.Command::encode).decoder(com.noirm.ally.AllyPackets.Command::decode)
                .consumerMainThread(com.noirm.ally.AllyPackets.Command::handle).add();
        CHANNEL.messageBuilder(com.noirm.ally.CrewPackets.Orders.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.noirm.ally.CrewPackets.Orders::encode).decoder(com.noirm.ally.CrewPackets.Orders::decode)
                .consumerMainThread(com.noirm.ally.CrewPackets.Orders::handle).add();
        CHANNEL.messageBuilder(VfxPackets.Bolt.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VfxPackets.Bolt::encode).decoder(VfxPackets.Bolt::decode)
                .consumerMainThread(VfxPackets.Bolt::handle).add();
        CHANNEL.messageBuilder(VillagePackets.Atmosphere.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VillagePackets.Atmosphere::encode).decoder(VillagePackets.Atmosphere::decode)
                .consumerMainThread(VillagePackets.Atmosphere::handle).add();
        CHANNEL.messageBuilder(MangekyoPackets.Tsukuyomi.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MangekyoPackets.Tsukuyomi::encode).decoder(MangekyoPackets.Tsukuyomi::decode)
                .consumerMainThread(MangekyoPackets.Tsukuyomi::handle).add();
        CHANNEL.messageBuilder(com.noirm.master.MasterPackets.Info.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.noirm.master.MasterPackets.Info::encode).decoder(com.noirm.master.MasterPackets.Info::decode)
                .consumerMainThread(com.noirm.master.MasterPackets.Info::handle).add();
        CHANNEL.messageBuilder(com.noirm.master.MasterPackets.Command.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.noirm.master.MasterPackets.Command::encode).decoder(com.noirm.master.MasterPackets.Command::decode)
                .consumerMainThread(com.noirm.master.MasterPackets.Command::handle).add();
        CHANNEL.messageBuilder(com.noirm.master.MasterPackets.Close.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.noirm.master.MasterPackets.Close::encode).decoder(com.noirm.master.MasterPackets.Close::decode)
                .consumerMainThread(com.noirm.master.MasterPackets.Close::handle).add();
        // Movement feel and the seal lock: server → client
        CHANNEL.messageBuilder(MovementPackets.FeelState.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MovementPackets.FeelState::encode).decoder(MovementPackets.FeelState::decode)
                .consumerMainThread(MovementPackets.FeelState::handle).add();
        CHANNEL.messageBuilder(MovementPackets.SealState.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MovementPackets.SealState::encode).decoder(MovementPackets.SealState::decode)
                .consumerMainThread(MovementPackets.SealState::handle).add();
        // Gore: marks and severed parts (blueprint §7).
        CHANNEL.messageBuilder(com.noirm.gore.GorePackets.Sync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.noirm.gore.GorePackets.Sync::encode).decoder(com.noirm.gore.GorePackets.Sync::decode)
                .consumerMainThread(com.noirm.gore.GorePackets.Sync::handle).add();
        id = com.noirm.creator.CreatorPackets.register(CHANNEL, id); // character creation
        id = com.noirm.mission.CareerPackets.register(CHANNEL, id); // ryo, missions, rank, shops
        id = com.noirm.nature.NaturePackets.register(CHANNEL, id); // hidden natures and nature drills
    }

    /** Sends to every player within {@code radius} blocks of {@code at}. */
    public static void sendNear(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 at, double radius, Object packet) {
        CHANNEL.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(at.x, at.y, at.z, radius, level.dimension())), packet);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        if (isConnected(player)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    /** Sends to everyone who can see {@code entity}, and to the entity itself if it's a player. */
    public static void sendToTrackingAndSelf(Entity entity, Object packet) {
        if (entity instanceof ServerPlayer player && !isConnected(player)) {
            CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
            return;
        }
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), packet);
    }

    /** Fake players (game tests, other mods' machines) have no network connection to send to. */
    private static boolean isConnected(ServerPlayer player) {
        return player.connection != null && player.connection.connection.channel() != null;
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}

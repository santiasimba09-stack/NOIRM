package com.noirm.gametest;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import javax.annotation.Nullable;

/**
 * A network handler that sends nothing, for a plain {@link ServerPlayer} made in a test. A Forge
 * FakePlayer can't earn advancements and the game's mock player trips over Forge's network setup,
 * so the advancement tests make their own player with this (see {@code ProgressGameTests.player}).
 */
final class TestNetHandler extends ServerGamePacketListenerImpl {
    TestNetHandler(MinecraftServer server, ServerPlayer player) {
        super(server, new Connection(PacketFlow.CLIENTBOUND), player);
    }

    @Override
    public void tick() {}

    @Override
    public void send(Packet<?> packet) {}

    @Override
    public void send(Packet<?> packet, @Nullable PacketSendListener listener) {}

    @Override
    public void disconnect(Component reason) {}
}

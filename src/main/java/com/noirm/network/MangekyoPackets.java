package com.noirm.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Packets for the Mangekyō Sharingan. */
public final class MangekyoPackets {
    private MangekyoPackets() {}

    /** You've been caught in a Tsukuyomi: the world turns red and black for {@code ticks}. */
    public record Tsukuyomi(int ticks) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(ticks);
        }

        public static Tsukuyomi decode(FriendlyByteBuf buf) {
            return new Tsukuyomi(buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.MangekyoEffects.onTsukuyomi(ticks));
            context.get().setPacketHandled(true);
        }
    }
}

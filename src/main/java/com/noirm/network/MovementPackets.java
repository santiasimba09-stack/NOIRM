package com.noirm.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: the movement-feel switch, and whether your hands are busy forming seals. */
public final class MovementPackets {
    private MovementPackets() {}

    /** The {@code noirmMovementFeel} game rule (sent on login and whenever it changes). */
    public record FeelState(boolean enabled) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(enabled);
        }

        public static FeelState decode(FriendlyByteBuf buf) {
            return new FeelState(buf.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.movement.MovementFeel.clientEnabled = enabled);
            context.get().setPacketHandled(true);
        }
    }

    /** Your seal phase started or ended: no melee in between (the client skips the swing). */
    public record SealState(boolean forming) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(forming);
        }

        public static SealState decode(FriendlyByteBuf buf) {
            return new SealState(buf.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.SealLockInput.setForming(forming));
            context.get().setPacketHandled(true);
        }
    }
}

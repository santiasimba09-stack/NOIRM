package com.noirm.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client effects that particles can't draw well. */
public final class VfxPackets {
    private VfxPackets() {}

    /**
     * A lightning bolt from one point to another: {@code color} is its glow, {@code width} the
     * core's thickness in blocks, {@code life} ticks, {@code branches} how many side forks (low
     * byte) plus style flags in the next byte ({@code BoltRenderer.ARC}, {@code GROUND}, {@code IMPACT},
     * {@code FORKY}; same wire format, older senders simply set none).
     */
    public record Bolt(double x1, double y1, double z1, double x2, double y2, double z2,
                       int color, float width, int life, int branches) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeDouble(x1);
            buf.writeDouble(y1);
            buf.writeDouble(z1);
            buf.writeDouble(x2);
            buf.writeDouble(y2);
            buf.writeDouble(z2);
            buf.writeInt(color);
            buf.writeFloat(width);
            buf.writeVarInt(life);
            buf.writeVarInt(branches);
        }

        public static Bolt decode(FriendlyByteBuf buf) {
            return new Bolt(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readInt(), Math.min(4f, buf.readFloat()), Math.min(100, buf.readVarInt()), clampBranches(buf.readVarInt()));
        }

        /** At most 8 forks; the style flags above them pass through. */
        private static int clampBranches(int raw) {
            return Math.min(8, raw & 0xFF) | (raw & 0xFF00);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.BoltRenderer.add(this));
            context.get().setPacketHandled(true);
        }
    }
}

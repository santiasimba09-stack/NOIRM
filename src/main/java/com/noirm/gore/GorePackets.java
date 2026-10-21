package com.noirm.gore;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client gore: one small packet per visible change (about 7 bytes). */
public final class GorePackets {
    private GorePackets() {}

    /**
     * A fighter's marks: bruise, dirt, soot and cut packed as four bytes (0–255 each) into
     * {@code marks}, and the parts their dying body has lost ({@link SeveredLimb.Part} bits).
     */
    public record Sync(int entityId, int marks, int severed) {
        static Sync of(int entityId, int[] levels, int severed) {
            int packed = 0;
            for (int i = 0; i < 4; i++) {
                packed |= (Math.max(0, Math.min(255, levels[i])) & 0xFF) << (8 * i);
            }
            return new Sync(entityId, packed, severed);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeInt(marks);
            buf.writeByte(severed);
        }

        public static Sync decode(FriendlyByteBuf buf) {
            return new Sync(buf.readVarInt(), buf.readInt(), buf.readByte() & 0x1F);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.GoreClient.receive(entityId, marks, severed));
            context.get().setPacketHandled(true);
        }
    }
}

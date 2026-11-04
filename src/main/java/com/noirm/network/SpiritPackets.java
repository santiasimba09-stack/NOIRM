package com.noirm.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Packets for the spirit natures and medical ninjutsu (jutsu/SpiritTechniques, jutsu/MedicalTechniques). */
public final class SpiritPackets {
    /** Caught in a Yin genjutsu: the world tints violet, ink closes in from the edges. */
    public static final int GENJUTSU = 0;
    /** Body Pathway Derangement: your movement keys are scrambled (forward is back, left is right). */
    public static final int DERANGE = 1;
    /** A genjutsu released (Kai): the violet clears at once. */
    public static final int RELEASE = 2;

    private SpiritPackets() {}

    /** What happens to the local player's view (and keys) for {@code ticks}. */
    public record Vision(int kind, int ticks) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(kind);
            buf.writeVarInt(ticks);
        }

        public static Vision decode(FriendlyByteBuf buf) {
            return new Vision(buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.SpiritVision.apply(kind, ticks));
            context.get().setPacketHandled(true);
        }
    }
}

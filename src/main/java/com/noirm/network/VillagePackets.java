package com.noirm.network;

import com.noirm.client.VillageFog;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client village messages. */
public final class VillagePackets {
    private VillagePackets() {}

    /**
     * The look of the village the player is standing in ({@link com.noirm.world.village.VillagePalette}
     * ordinal), or -1 outside any village: the client fades its fog to match (mist in the Mist village).
     */
    public record Atmosphere(int palette) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(palette + 1);
        }

        public static Atmosphere decode(FriendlyByteBuf buf) {
            return new Atmosphere(buf.readVarInt() - 1);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VillageFog.setVillage(palette));
            context.get().setPacketHandled(true);
        }
    }
}

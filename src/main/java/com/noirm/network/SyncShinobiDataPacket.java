package com.noirm.network;

import com.noirm.client.ClientShinobiData;
import com.noirm.player.ShinobiData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: the player's current chakra and stamina, for the HUD. */
public record SyncShinobiDataPacket(float chakra, float maxChakra, float stamina, float maxStamina) {

    public static SyncShinobiDataPacket of(ShinobiData data) {
        return new SyncShinobiDataPacket(data.getChakra(), data.getMaxChakra(), data.getStamina(), data.getMaxStamina());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(chakra);
        buf.writeFloat(maxChakra);
        buf.writeFloat(stamina);
        buf.writeFloat(maxStamina);
    }

    public static SyncShinobiDataPacket decode(FriendlyByteBuf buf) {
        return new SyncShinobiDataPacket(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ClientShinobiData.update(this);
        context.get().setPacketHandled(true);
    }
}

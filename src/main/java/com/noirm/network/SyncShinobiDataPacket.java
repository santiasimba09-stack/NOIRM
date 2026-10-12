package com.noirm.network;

import com.noirm.client.ClientShinobiData;
import com.noirm.player.ShinobiData;
import com.noirm.training.TrainingSystem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: the player's chakra, stamina and trained stats (with progress toward the
 * next point, 0..1), for the HUD.
 */
public record SyncShinobiDataPacket(float chakra, float maxChakra, float stamina, float maxStamina,
                                    float taijutsu, float taijutsuProgress, float speed, float speedProgress) {

    public static SyncShinobiDataPacket of(ShinobiData data) {
        return new SyncShinobiDataPacket(data.getChakra(), data.getMaxChakra(), data.getStamina(), data.getMaxStamina(),
                data.getTaijutsu(), TrainingSystem.progress(data, TrainingSystem.Stat.TAIJUTSU),
                data.getSpeed(), TrainingSystem.progress(data, TrainingSystem.Stat.SPEED));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(chakra);
        buf.writeFloat(maxChakra);
        buf.writeFloat(stamina);
        buf.writeFloat(maxStamina);
        buf.writeFloat(taijutsu);
        buf.writeFloat(taijutsuProgress);
        buf.writeFloat(speed);
        buf.writeFloat(speedProgress);
    }

    public static SyncShinobiDataPacket decode(FriendlyByteBuf buf) {
        return new SyncShinobiDataPacket(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ClientShinobiData.update(this);
        context.get().setPacketHandled(true);
    }
}

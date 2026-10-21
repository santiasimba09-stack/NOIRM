package com.noirm.network;

import com.noirm.client.ClientShinobiData;
import com.noirm.player.ShinobiData;
import com.noirm.training.Supplementary;
import com.noirm.training.TrainingSystem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: the player's chakra, stamina and the six trained stats (the core four plus CTL and WIL, with progress toward the
 * next point, 0..1), for the HUD.
 */
public record SyncShinobiDataPacket(float chakra, float maxChakra, float stamina, float maxStamina,
                                    float taijutsu, float taijutsuProgress, float speed, float speedProgress,
                                    float vitality, float vitalityProgress, float reserves, float reservesProgress,
                                    float control, float controlProgress, float willpower, float willpowerProgress, String style) {

    public static SyncShinobiDataPacket of(ShinobiData data) {
        return new SyncShinobiDataPacket(data.getChakra(), data.getMaxChakra(), data.getStamina(), data.getMaxStamina(),
                data.getTaijutsu(), TrainingSystem.progress(data, TrainingSystem.Stat.TAIJUTSU),
                data.getSpeed(), TrainingSystem.progress(data, TrainingSystem.Stat.SPEED),
                data.getVitality(), TrainingSystem.progress(data, TrainingSystem.Stat.VITALITY),
                data.getReserves(), TrainingSystem.progress(data, TrainingSystem.Stat.RESERVES),
                data.getControl(), Supplementary.progress(data, Supplementary.Stat.CONTROL),
                data.getWillpower(), Supplementary.progress(data, Supplementary.Stat.WILLPOWER),
                com.noirm.combat.StyleSystem.styleOf(data).getPath());
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
        buf.writeFloat(vitality);
        buf.writeFloat(vitalityProgress);
        buf.writeFloat(reserves);
        buf.writeFloat(reservesProgress);
        buf.writeFloat(control);
        buf.writeFloat(controlProgress);
        buf.writeFloat(willpower);
        buf.writeFloat(willpowerProgress);
        buf.writeUtf(style, 64);
    }

    public static SyncShinobiDataPacket decode(FriendlyByteBuf buf) {
        return new SyncShinobiDataPacket(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUtf(64));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ClientShinobiData.update(this);
        context.get().setPacketHandled(true);
    }
}

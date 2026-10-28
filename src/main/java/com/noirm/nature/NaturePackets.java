package com.noirm.nature;

import com.noirm.clan.Nature;
import com.noirm.network.NoirmNetwork;
import com.noirm.player.ShinobiData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Server → client: the local player's hidden natures (once revealed) and drill progress, for the HUD and the J screen. */
public final class NaturePackets {
    private NaturePackets() {}

    public static int register(SimpleChannel channel, int id) {
        channel.messageBuilder(Sync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Sync::encode).decoder(Sync::decode).consumerMainThread(Sync::handle).add();
        return id;
    }

    /**
     * @param hiddenMask bit per {@link Nature} ordinal: the hidden natures (0 until a chakra paper shows them)
     * @param active     the drill being worked on (ordinal), or -1
     * @param pace       how fast it moves now (0 = not moving)
     * @param teacher    0 none, 1 crew mate, 2 master
     * @param shares     drill progress (0..1) per basic nature, in {@link NatureDrills#TRAINABLE} order
     */
    public record Sync(int hiddenMask, boolean revealed, int active, float pace, int teacher, float[] shares) {
        public static Sync of(ShinobiData data) {
            NatureTraining t = data.natureTraining();
            int mask = 0;
            if (t.revealed()) {
                for (Nature n : t.hidden()) {
                    mask |= 1 << n.ordinal();
                }
            }
            float[] shares = new float[NatureDrills.TRAINABLE.size()];
            for (int i = 0; i < shares.length; i++) {
                shares[i] = NatureDrills.share(data, NatureDrills.TRAINABLE.get(i));
            }
            return new Sync(mask, t.revealed(), t.active() == null ? -1 : t.active().ordinal(), t.pace(), t.teacher(), shares);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(hiddenMask);
            buf.writeBoolean(revealed);
            buf.writeVarInt(active + 1);
            buf.writeFloat(pace);
            buf.writeByte(teacher);
            buf.writeVarInt(shares.length);
            for (float s : shares) {
                buf.writeFloat(s);
            }
        }

        public static Sync decode(FriendlyByteBuf buf) {
            int mask = buf.readVarInt();
            boolean revealed = buf.readBoolean();
            int active = buf.readVarInt() - 1;
            float pace = buf.readFloat();
            int teacher = buf.readByte();
            float[] shares = new float[Math.min(16, buf.readVarInt())];
            for (int i = 0; i < shares.length; i++) {
                shares[i] = buf.readFloat();
            }
            return new Sync(mask, revealed, active, pace, teacher, shares);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.ClientNatureTraining.sync(this));
            context.get().setPacketHandled(true);
        }
    }

    static void send(ServerPlayer player, ShinobiData data) {
        NoirmNetwork.sendToPlayer(player, Sync.of(data));
    }
}

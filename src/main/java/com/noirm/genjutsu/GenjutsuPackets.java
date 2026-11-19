package com.noirm.genjutsu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Genjutsu scenes: server to the victim's client (protocol 28). */
public final class GenjutsuPackets {
    /** The scene ended by itself: it fades. */
    public static final int END_FADE = 0;
    /** The scene was broken (Kai, a friend, a strong mind, the Sharingan): it shatters like glass. */
    public static final int END_SHATTER = 1;

    private GenjutsuPackets() {}

    public static int register(SimpleChannel channel, int id) {
        channel.messageBuilder(Scene.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Scene::encode).decoder(Scene::decode).consumerMainThread(Scene::handle).add();
        channel.messageBuilder(End.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(End::encode).decoder(End::decode).consumerMainThread(End::handle).add();
        return id;
    }

    /**
     * You are caught: draw scene {@code id} ({@code json} is its definition) for {@code ticks}.
     * {@code casterId} is who cast it; ({@code ax, ay, az}) where you stood; ({@code dx, dy, dz}) a
     * second spot the scene uses (where the false image stands), NaN if none; {@code allies} the ids
     * of your allies close by (Kotoamatsukami shows them as enemies).
     */
    public record Scene(String id, String json, int ticks, int casterId, double ax, double ay, double az,
                        double dx, double dy, double dz, int[] allies) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeUtf(json, 32000);
            buf.writeVarInt(ticks);
            buf.writeVarInt(casterId);
            buf.writeDouble(ax);
            buf.writeDouble(ay);
            buf.writeDouble(az);
            buf.writeDouble(dx);
            buf.writeDouble(dy);
            buf.writeDouble(dz);
            buf.writeVarIntArray(allies);
        }

        public static Scene decode(FriendlyByteBuf buf) {
            return new Scene(buf.readUtf(), buf.readUtf(32000), buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarIntArray(64));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.GenjutsuSceneRenderer.begin(this));
            context.get().setPacketHandled(true);
        }
    }

    /** The scene is over: {@link #END_FADE} or {@link #END_SHATTER}. */
    public record End(int how) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(how);
        }

        public static End decode(FriendlyByteBuf buf) {
            return new End(buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.GenjutsuSceneRenderer.end(how));
            context.get().setPacketHandled(true);
        }
    }
}

package com.noirm.hiraishin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Space-time packets: the Flying Raijin, its marks, and the Body Flicker's look (protocol 28). */
public final class HiraishinPackets {
    /** Client → server actions. */
    public static final int MARK = 0;
    public static final int RECALL = 1;
    public static final int JUMP = 2;
    public static final int CYCLE = 3;
    /** The player's lock-on target changed ({@code arg} = its entity id, or -1). */
    public static final int LOCK = 4;

    private HiraishinPackets() {}

    public static int register(SimpleChannel channel, int id) {
        channel.messageBuilder(Action.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Action::encode).decoder(Action::decode).consumerMainThread(Action::handle).add();
        channel.messageBuilder(Marks.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Marks::encode).decoder(Marks::decode).consumerMainThread(Marks::handle).add();
        channel.messageBuilder(Fx.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Fx::encode).decoder(Fx::decode).consumerMainThread(Fx::handle).add();
        return id;
    }

    /** A space-time key: {@link #MARK}, {@link #RECALL}, {@link #JUMP}, {@link #CYCLE}, {@link #LOCK}. */
    public record Action(int action, int arg) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(action);
            buf.writeVarInt(arg);
        }

        public static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                SpaceTimeInput.handle(player, action, arg);
            }
            context.get().setPacketHandled(true);
        }
    }

    /** One of your marks, as your client sees it. {@code entityId}: the kunai or body carrying it, or -1. */
    public record MarkInfo(int id, int kind, double x, double y, double z, int face, int entityId, int ticksLeft) {}

    /** Your marks (for the glyphs and the HUD), which is selected, your cap, and whether you can travel. */
    public record Marks(List<MarkInfo> marks, int selected, int cap, boolean canJump) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(marks.size());
            for (MarkInfo m : marks) {
                buf.writeVarInt(m.id());
                buf.writeByte(m.kind());
                buf.writeDouble(m.x());
                buf.writeDouble(m.y());
                buf.writeDouble(m.z());
                buf.writeByte(m.face());
                buf.writeVarInt(m.entityId());
                buf.writeVarInt(m.ticksLeft());
            }
            buf.writeVarInt(selected);
            buf.writeVarInt(cap);
            buf.writeBoolean(canJump);
        }

        public static Marks decode(FriendlyByteBuf buf) {
            int n = Math.min(64, buf.readVarInt());
            List<MarkInfo> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(new MarkInfo(buf.readVarInt(), buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readByte(),
                        buf.readVarInt(), buf.readVarInt()));
            }
            return new Marks(list, buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.HiraishinClient.onMarks(this));
            context.get().setPacketHandled(true);
        }
    }

    /**
     * A space-time effect to draw ({@link HiraishinFx} kinds): who moved ({@code entityId}), from where
     * to where.
     */
    public record Fx(int kind, int entityId, double fx, double fy, double fz, double tx, double ty, double tz) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(kind);
            buf.writeVarInt(entityId);
            buf.writeDouble(fx);
            buf.writeDouble(fy);
            buf.writeDouble(fz);
            buf.writeDouble(tx);
            buf.writeDouble(ty);
            buf.writeDouble(tz);
        }

        public static Fx decode(FriendlyByteBuf buf) {
            return new Fx(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.SpaceTimeFx.onFx(this));
            context.get().setPacketHandled(true);
        }
    }
}

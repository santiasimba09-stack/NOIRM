package com.noirm.senjutsu;

import com.noirm.network.NoirmNetwork;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * How a player's sage state looks, for everyone who can see them (the marks, the stone skin) and
 * for their own HUD (the safe limit, petrification). Sent when it changes.
 */
public final class SagePackets {
    /** {@link Visuals#flags} bits. */
    public static final int SAGE = 1, STATUE = 2, EXHAUSTED = 4;

    private SagePackets() {}

    /** Registers the sage packets on {@code channel} from {@code id}; returns the next free id. */
    public static int register(SimpleChannel channel, int id) {
        channel.messageBuilder(Visuals.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Visuals::encode).decoder(Visuals::decode).consumerMainThread(Visuals::handle).add();
        return id;
    }

    public static Visuals of(Player player, ShinobiData data) {
        SageData sage = data.senjutsu().sage();
        return new Visuals(player.getId(), sage.flags(), sage.petrification(), Sage.safeLimit(data));
    }

    public static void broadcast(ServerPlayer player, ShinobiData data) {
        NoirmNetwork.sendToTrackingAndSelf(player, of(player, data));
    }

    /** When {@code viewer} starts seeing {@code player}. */
    public static void sendTo(ServerPlayer viewer, Player player) {
        ShinobiCapability.get(player).ifPresent(data -> NoirmNetwork.sendToPlayer(viewer, of(player, data)));
    }

    /**
     * @param petrification 0–100
     * @param limit         the safe nature energy limit (for the owner's HUD)
     */
    public record Visuals(int entityId, int flags, float petrification, float limit) {
        public boolean sage() {
            return (flags & SAGE) != 0;
        }

        public boolean statue() {
            return (flags & STATUE) != 0;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeByte(flags);
            buf.writeFloat(petrification);
            buf.writeFloat(limit);
        }

        public static Visuals decode(FriendlyByteBuf buf) {
            return new Visuals(buf.readVarInt(), buf.readByte(), buf.readFloat(), buf.readFloat());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.ClientSage.receive(this));
            context.get().setPacketHandled(true);
        }
    }
}

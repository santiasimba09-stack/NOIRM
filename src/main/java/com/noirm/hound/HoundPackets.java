package com.noirm.hound;

import com.noirm.combat.Perception;
import com.noirm.player.ShinobiData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** The hound panel's orders (client → server) and what the panel shows (part of {@code SyncPowers}). */
public final class HoundPackets {
    /** The coat colour can still be changed while bonding: up to this level. */
    public static final int COLOUR_PICK_LEVEL = 5;

    private HoundPackets() {}

    /** An order or action from the hound panel: see {@link Hounds#command}; also {@code colour:<n>} while it's young. */
    public record Command(String action) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(action, 32);
        }

        public static Command decode(FriendlyByteBuf buf) {
            return new Command(buf.readUtf(32));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null || !player.isAlive()) {
                return;
            }
            if (action.startsWith("colour:")) {
                setColour(player, action.substring(7));
                return;
            }
            Hounds.command(player, action);
        }
    }

    /** The base coat colour can be picked while bonding (the first few levels). */
    static boolean setColour(ServerPlayer player, String value) {
        NinjaHound hound = Hounds.find(player);
        if (hound == null || hound.houndLevel() > COLOUR_PICK_LEVEL) {
            return false;
        }
        try {
            hound.setLook(hound.look().withBase(Integer.parseInt(value)));
        } catch (NumberFormatException e) {
            return false;
        }
        Hounds.snapshot(hound);
        com.noirm.player.ShinobiCapability.get(player).ifPresent(ShinobiData::markPowersDirty);
        return true;
    }

    /**
     * What the hound panel and HUD show. {@code has} false = no hound (everything else empty).
     * {@code blood}: 0 none, 1 Inuzuka, 2 a contract line (see {@link Hounds#bloodHound}).
     */
    public record HoundSync(boolean has, String name, int look, int level, float bond, float xp, int order, boolean out,
                            boolean contract, int blood, float ownerPower, UUID entity, List<String> traits) {
        public static final HoundSync NONE = new HoundSync(false, "", HoundLook.DEFAULT.pack(), 1, 0f, 0f, 0, false, false, 0, 0f,
                new UUID(0, 0), List.of());

        public static HoundSync of(ServerPlayer player, ShinobiData data) {
            HoundData hd = data.hound();
            int blood = Hounds.bloodHound(data);
            if (!hd.has()) {
                return new HoundSync(false, "", HoundLook.DEFAULT.pack(), 1, 0f, 0f, 0, false, false, blood, Perception.basePower(player),
                        new UUID(0, 0), List.of());
            }
            return new HoundSync(true, hd.name(), hd.lookBits(), hd.level(), hd.bond(), hd.xp(), hd.order().ordinal(), hd.isOut(),
                    hd.byContract(), blood, Perception.basePower(player), hd.entityId() == null ? new UUID(0, 0) : hd.entityId(),
                    hd.traits());
        }

        public HoundStage stageEnum() {
            return HoundStage.forLevel(level);
        }

        public HoundLook lookValue() {
            return HoundLook.unpack(look);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(has);
            buf.writeUtf(name, 64);
            buf.writeInt(look);
            buf.writeVarInt(level);
            buf.writeFloat(bond);
            buf.writeFloat(xp);
            buf.writeByte(order);
            buf.writeBoolean(out);
            buf.writeBoolean(contract);
            buf.writeByte(blood);
            buf.writeFloat(ownerPower);
            buf.writeUUID(entity);
            buf.writeCollection(traits, (b, s) -> b.writeUtf(s, 64));
        }

        public static HoundSync decode(FriendlyByteBuf buf) {
            return new HoundSync(buf.readBoolean(), buf.readUtf(64), buf.readInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                    buf.readByte(), buf.readBoolean(), buf.readBoolean(), buf.readByte(), buf.readFloat(), buf.readUUID(),
                    buf.readList(b -> b.readUtf(64)));
        }
    }
}

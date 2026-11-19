package com.noirm.workshop;

import com.noirm.arts.ArtsData;
import com.noirm.arts.ChakraShape;
import com.noirm.arts.FusionTrial;
import com.noirm.clan.Nature;
import com.noirm.player.ShinobiCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** The workshop and training screens talking to the server (and the server opening them, for commands). */
public final class WorkshopPackets {
    private WorkshopPackets() {}

    public static int register(SimpleChannel channel, int id) {
        channel.messageBuilder(Create.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Create::encode).decoder(Create::decode).consumerMainThread(Create::handle).add();
        channel.messageBuilder(Act.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Act::encode).decoder(Act::decode).consumerMainThread(Act::handle).add();
        channel.messageBuilder(Open.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Open::encode).decoder(Open::decode).consumerMainThread(Open::handle).add();
        return id;
    }

    /** Make a jutsu. The shape travels as its name so the server can refuse a form by name. */
    public record Create(String name, int nature, String shape, int size, int speed, int power, int range) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(name, 64);
            buf.writeVarInt(nature);
            buf.writeUtf(shape, 32);
            buf.writeByte(size);
            buf.writeByte(speed);
            buf.writeByte(power);
            buf.writeByte(range);
        }

        public static Create decode(FriendlyByteBuf buf) {
            return new Create(buf.readUtf(64), buf.readVarInt(), buf.readUtf(32), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null && nature >= 0 && nature < Nature.values().length) {
                ShinobiCapability.get(player).ifPresent(data ->
                        Workshop.create(player, data, name, Nature.values()[nature], shape, size, speed, power, range));
            }
            context.get().setPacketHandled(true);
        }
    }

    public enum Action { DELETE, WRITE_SCROLL, PICK_SHAPE, PICK_PAIR, START_FUSION, ABANDON_FUSION, START_DRILL }

    /** Something done from a screen: on a creation (by jutsu id), a shape (by key), a pair or a nature (by ordinal). */
    public record Act(Action action, String target, int value) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeEnum(action);
            buf.writeUtf(target, 128);
            buf.writeVarInt(value + 1);
        }

        public static Act decode(FriendlyByteBuf buf) {
            return new Act(buf.readEnum(Action.class), buf.readUtf(128), buf.readVarInt() - 1);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                ShinobiCapability.get(player).ifPresent(data -> {
                    switch (action) {
                        case DELETE -> {
                            ResourceLocation id = ResourceLocation.tryParse(target);
                            if (id != null) {
                                Workshop.delete(player, data, id);
                            }
                        }
                        case WRITE_SCROLL -> {
                            ResourceLocation id = ResourceLocation.tryParse(target);
                            if (id != null) {
                                Workshop.writeScroll(player, data, id);
                            }
                        }
                        case PICK_SHAPE -> data.arts().setActiveShape(ChakraShape.byKey(target));
                        case PICK_PAIR -> {
                            if (value < 0 || (ArtsData.pairShape(value) != null && ArtsData.pairNature(value) != null)) {
                                data.arts().setActivePair(value);
                            }
                        }
                        case START_FUSION -> {
                            if (value >= 0 && value < Nature.values().length) {
                                FusionTrial.start(player, data, Nature.values()[value]);
                            }
                        }
                        case ABANDON_FUSION -> FusionTrial.abandon(player, data);
                        case START_DRILL -> {
                            if (value >= 0 && value < Nature.values().length) {
                                com.noirm.nature.NatureDrills.choose(player, data, Nature.values()[value]);
                            }
                        }
                    }
                    data.markPowersDirty();
                });
            }
            context.get().setPacketHandled(true);
        }
    }

    /** Server → client: open the workshop (0) or the training screen (1). */
    public record Open(int screen) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(screen);
        }

        public static Open decode(FriendlyByteBuf buf) {
            return new Open(buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.ArtsScreens.open(screen));
            context.get().setPacketHandled(true);
        }
    }
}

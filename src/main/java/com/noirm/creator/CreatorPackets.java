package com.noirm.creator;

import com.noirm.clan.Clan;
import com.noirm.clan.ClanIdentity;
import com.noirm.clan.ClanTrait;
import com.noirm.clan.Nature;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** The character creation packets: the screen's state, the player's choices, and looks for everyone. */
public final class CreatorPackets {
    private CreatorPackets() {}

    /** Registers the packets after the others ({@code NoirmNetwork.register}); returns the next free id. */
    public static int register(SimpleChannel channel, int id) {
        channel.messageBuilder(State.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(State::encode).decoder(State::decode).consumerMainThread(State::handle).add();
        channel.messageBuilder(LookSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LookSync::encode).decoder(LookSync::decode).consumerMainThread(LookSync::handle).add();
        channel.messageBuilder(Action.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Action::encode).decoder(Action::decode).consumerMainThread(Action::handle).add();
        channel.messageBuilder(Finish.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Finish::encode).decoder(Finish::decode).consumerMainThread(Finish::handle).add();
        return id;
    }

    /**
     * What one genetics roll gives, for the screen: the clan's name, natures (ordinal → affinity),
     * traits, kekkei genkai, and the stats (RES, TAI, SPD, CTL, WIL) before and after traits.
     */
    public record RollView(String clanName, boolean canon, String kekkeiGenkai, List<Map.Entry<Integer, Float>> natures,
                           List<Integer> traits, int[] base, float[] stats) {
        static RollView of(GeneticRoll roll, Clan clan) {
            List<Map.Entry<Integer, Float>> natures = new ArrayList<>();
            List<Integer> traits = new ArrayList<>();
            if (clan != null) {
                clan.natures().entrySet().stream()
                        .sorted(Map.Entry.<Nature, Float>comparingByValue().reversed())
                        .forEach(e -> natures.add(Map.entry(e.getKey().ordinal(), e.getValue())));
                clan.traits().forEach(t -> traits.add(t.ordinal()));
            }
            return new RollView(clan == null ? roll.clanId() : clan.name(), clan != null && clan.canon(),
                    clan == null ? "" : clan.kekkeiGenkai().orElse(""), natures, traits, roll.stats(),
                    CharacterCreator.finalStats(roll, clan));
        }

        /** The player's current clan and stats (appearance-only mode). */
        static RollView current(ShinobiData data) {
            ClanIdentity identity = data.identity();
            List<Map.Entry<Integer, Float>> natures = new ArrayList<>();
            identity.natures().forEach((n, a) -> natures.add(Map.entry(n.ordinal(), a)));
            List<Integer> traits = new ArrayList<>();
            identity.traits().forEach(t -> traits.add(t.ordinal()));
            String kekkei = identity.hasSharingan() ? "sharingan" : identity.hasByakugan() ? "byakugan" : "";
            float[] stats = {data.getReserves(), data.getTaijutsu(), data.getSpeed(), data.getControl(), data.getWillpower()};
            int[] base = new int[5];
            for (int i = 0; i < 5; i++) {
                base[i] = Math.round(stats[i]);
            }
            return new RollView(identity.clanName(), identity.isCanon(), kekkei, natures, traits, base, stats);
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(clanName, 64);
            buf.writeBoolean(canon);
            buf.writeUtf(kekkeiGenkai, 32);
            buf.writeVarInt(natures.size());
            natures.forEach(e -> {
                buf.writeVarInt(e.getKey());
                buf.writeFloat(e.getValue());
            });
            buf.writeVarInt(traits.size());
            traits.forEach(buf::writeVarInt);
            for (int i = 0; i < 5; i++) {
                buf.writeVarInt(base[i]);
                buf.writeFloat(stats[i]);
            }
        }

        static RollView decode(FriendlyByteBuf buf) {
            String name = buf.readUtf(64);
            boolean canon = buf.readBoolean();
            String kekkei = buf.readUtf(32);
            int n = Math.min(buf.readVarInt(), 32);
            List<Map.Entry<Integer, Float>> natures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                natures.add(Map.entry(buf.readVarInt(), buf.readFloat()));
            }
            int t = Math.min(buf.readVarInt(), 32);
            List<Integer> traits = new ArrayList<>();
            for (int i = 0; i < t; i++) {
                traits.add(buf.readVarInt());
            }
            int[] base = new int[5];
            float[] stats = new float[5];
            for (int i = 0; i < 5; i++) {
                base[i] = buf.readVarInt();
                stats[i] = buf.readFloat();
            }
            return new RollView(name, canon, kekkei, natures, traits, base, stats);
        }

        public List<Nature> natureList() {
            List<Nature> out = new ArrayList<>();
            natures.forEach(e -> {
                if (e.getKey() >= 0 && e.getKey() < Nature.values().length) {
                    out.add(Nature.values()[e.getKey()]);
                }
            });
            return out;
        }

        public List<ClanTrait> traitList() {
            List<ClanTrait> out = new ArrayList<>();
            traits.forEach(i -> {
                if (i >= 0 && i < ClanTrait.values().length) {
                    out.add(ClanTrait.values()[i]);
                }
            });
            return out;
        }
    }

    /**
     * Server → the player: the creation screen's state. Mode NONE closes the screen. {@code rolls}
     * holds every roll seen (genetics modes) or the current clan (appearance mode).
     */
    public record State(int mode, boolean held, int rerollsLeft, int chosen, List<RollView> rolls, PlayerLook look) {
        public static State of(ServerPlayer player, ShinobiData data) {
            CreatorData creator = data.creator();
            List<RollView> rolls = new ArrayList<>();
            if (creator.mode().genetics()) {
                for (GeneticRoll roll : creator.rolls()) {
                    rolls.add(RollView.of(roll, CharacterCreator.clanOf(player.server, roll).orElse(null)));
                }
            } else if (creator.mode() != CreatorData.Mode.NONE) {
                rolls.add(RollView.current(data));
            }
            PlayerLook look = creator.hasLook() ? creator.look() : PlayerLook.random(player.getRandom());
            return new State(creator.mode().ordinal(), creator.held(), creator.rerollsLeft(), creator.chosen(), rolls, look);
        }

        public CreatorData.Mode modeEnum() {
            return CreatorData.Mode.byId(mode);
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeByte(mode);
            buf.writeBoolean(held);
            buf.writeByte(rerollsLeft);
            buf.writeByte(chosen);
            buf.writeVarInt(rolls.size());
            rolls.forEach(r -> r.encode(buf));
            look.encode(buf);
        }

        static State decode(FriendlyByteBuf buf) {
            int mode = buf.readByte();
            boolean held = buf.readBoolean();
            int left = buf.readByte();
            int chosen = buf.readByte();
            int n = Math.min(buf.readVarInt(), 16);
            List<RollView> rolls = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                rolls.add(RollView.decode(buf));
            }
            return new State(mode, held, left, chosen, rolls, PlayerLook.decode(buf));
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.ClientCreator.state(this));
            context.get().setPacketHandled(true);
        }
    }

    /** Server → everyone who sees a player: how that player looks ({@code look} null = their own skin). */
    public record LookSync(int entityId, PlayerLook look) {
        public static LookSync of(Player player) {
            PlayerLook look = ShinobiCapability.get(player).map(d -> d.creator().look()).orElse(null);
            return new LookSync(player.getId(), look);
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeBoolean(look != null);
            if (look != null) {
                look.encode(buf);
            }
        }

        static LookSync decode(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            return new LookSync(id, buf.readBoolean() ? PlayerLook.decode(buf) : null);
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.ClientCreator.look(this));
            context.get().setPacketHandled(true);
        }
    }

    /** Client → server: a button on the creation screen. */
    public record Action(Kind kind, int arg) {
        public enum Kind { REROLL, CHOOSE, CANCEL }

        void encode(FriendlyByteBuf buf) {
            buf.writeEnum(kind);
            buf.writeVarInt(arg);
        }

        static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readEnum(Kind.class), buf.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                ShinobiCapability.get(player).ifPresent(data -> {
                    switch (kind) {
                        case REROLL -> CharacterCreator.reroll(player, data);
                        case CHOOSE -> CharacterCreator.choose(player, data, arg);
                        case CANCEL -> CharacterCreator.cancel(player, data);
                    }
                });
            }
            context.get().setPacketHandled(true);
        }
    }

    /** Client → server: "done", with the look made on the screen. */
    public record Finish(PlayerLook look) {
        void encode(FriendlyByteBuf buf) {
            look.encode(buf);
        }

        static Finish decode(FriendlyByteBuf buf) {
            return new Finish(PlayerLook.decode(buf));
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                ShinobiCapability.get(player).ifPresent(data -> CharacterCreator.finish(player, data, look));
            }
            context.get().setPacketHandled(true);
        }
    }
}

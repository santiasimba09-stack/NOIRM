package com.noirm.bounty;

import com.noirm.biju.BijuSystem;
import com.noirm.clan.Clan;
import com.noirm.clan.Nature;
import com.noirm.clan.WorldClans;
import com.noirm.network.NoirmNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Bounty packets: a fighter's class for name tags and the HUD ({@link ClassInfo}), and the bingo
 * book's pages to read ({@link BookInfo}).
 */
public final class BountyPackets {
    private static final String[] DIRECTIONS = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};

    private BountyPackets() {}

    /** Registers the packets after the others ({@code NoirmNetwork.register}); returns the next free id. */
    public static int register(SimpleChannel channel, int id) {
        channel.messageBuilder(ClassInfo.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClassInfo::encode).decoder(ClassInfo::decode).consumerMainThread(ClassInfo::handle).add();
        channel.messageBuilder(BookInfo.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BookInfo::encode).decoder(BookInfo::decode).consumerMainThread(BookInfo::handle).add();
        return id;
    }

    // ================================================================ class

    /** Entity {@code entityId}'s class code (0–4 = D–S, 5 = legendary). */
    public record ClassInfo(int entityId, int code) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeByte(code);
        }

        public static ClassInfo decode(FriendlyByteBuf buf) {
            return new ClassInfo(buf.readVarInt(), buf.readByte());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ClientClasses.set(entityId, code);
            context.get().setPacketHandled(true);
        }
    }

    // ================================================================ the book

    /** One page. {@code seen} is already worded for the reader ("about 1,200 blocks north-east, in a Taiga"). */
    public record Page(int id, Component name, String clan, int code, String kind, List<Component> traits, Component seen, int bounty,
                       boolean claimed, String claimedBy) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeComponent(name);
            buf.writeUtf(clan, 128);
            buf.writeByte(code);
            buf.writeUtf(kind, 32);
            buf.writeVarInt(traits.size());
            traits.forEach(buf::writeComponent);
            buf.writeComponent(seen);
            buf.writeVarInt(bounty);
            buf.writeBoolean(claimed);
            buf.writeUtf(claimedBy, 64);
        }

        static Page decode(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            Component name = buf.readComponent();
            String clan = buf.readUtf(128);
            int code = buf.readByte();
            String kind = buf.readUtf(32);
            int n = Math.min(16, buf.readVarInt());
            List<Component> traits = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                traits.add(buf.readComponent());
            }
            return new Page(id, name, clan, code, kind, traits, buf.readComponent(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(64));
        }

        public boolean legendary() {
            return code == NinjaClass.LEGENDARY_CODE;
        }
    }

    /** The whole book, and the reader's own class. */
    public record BookInfo(int readerCode, List<Page> pages) {
        public static BookInfo of(ServerPlayer player) {
            BingoBook book = BingoBook.get(player.getServer());
            List<Page> pages = new ArrayList<>();
            for (BingoEntry e : book.pages()) {
                pages.add(page(player, e));
            }
            return new BookInfo(NinjaClass.code(player), pages);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByte(readerCode);
            buf.writeVarInt(pages.size());
            pages.forEach(p -> p.encode(buf));
        }

        public static BookInfo decode(FriendlyByteBuf buf) {
            int code = buf.readByte();
            int n = Math.min(64, buf.readVarInt());
            List<Page> pages = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                pages.add(Page.decode(buf));
            }
            return new BookInfo(code, pages);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.BingoBookScreen.receive(this));
            context.get().setPacketHandled(true);
        }
    }

    public static void openBook(ServerPlayer player) {
        NoirmNetwork.sendToPlayer(player, BookInfo.of(player));
    }

    /** A page as {@code reader} sees it. */
    public static Page page(ServerPlayer reader, BingoEntry e) {
        return new Page(e.id, e.name(), e.clanName, e.code(), e.legendary ? e.kind.key() : "", traits(reader.getServer(), e),
                seen(reader, e), e.bounty, !e.active(), e.claimedBy);
    }

    /** Natures, eyes, beast, and for a legend what they're known for. */
    public static List<Component> traits(MinecraftServer server, BingoEntry e) {
        List<Component> traits = new ArrayList<>();
        List<Nature> natures = BingoBook.natures(server, e);
        if (!natures.isEmpty()) {
            MutableComponent line = Component.empty();
            for (int i = 0; i < natures.size(); i++) {
                Nature n = natures.get(i);
                if (i > 0) {
                    line.append(", ");
                }
                line.append(Component.translatable(n.translationKey()).withStyle(s -> s.withColor(n.color())));
            }
            traits.add(Component.translatable("bingo.noirm.trait.natures", line));
        }
        Clan clan = WorldClans.get(server).find(e.clanId).orElse(null);
        if (e.mangekyo) {
            traits.add(Component.translatable("bingo.noirm.trait.mangekyo"));
        } else if (clan != null && clan.hasSharingan()) {
            traits.add(Component.translatable("bingo.noirm.trait.sharingan"));
        }
        if (clan != null && clan.hasByakugan()) {
            traits.add(Component.translatable("bingo.noirm.trait.byakugan"));
        }
        if (e.tails > 0) {
            traits.add(Component.translatable("bingo.noirm.trait.beast", BijuSystem.beastName(e.tails)));
        }
        if (e.legendary) {
            traits.add(Component.translatable("bingo.noirm.trait.kind." + e.kind.key()));
        }
        return traits;
    }

    /**
     * Where they were last seen, vaguely, from where {@code reader} stands: the distance to the
     * nearest hundred, one of eight directions, and the land there ("about 1,200 blocks north-east,
     * in a taiga"). Close by, just "nearby".
     */
    public static Component seen(ServerPlayer reader, BingoEntry e) {
        BlockPos at = e.area;
        double dx = at.getX() - reader.getX(), dz = at.getZ() - reader.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        Component land = biomeName(reader.serverLevel().getBiome(at));
        if (distance < 150) {
            return Component.translatable("bingo.noirm.seen.near", land);
        }
        return Component.translatable("bingo.noirm.seen", com.noirm.economy.Ryo.format((int) (Math.round(distance / 100.0) * 100)),
                Component.translatable("bingo.noirm.dir." + direction(dx, dz)), land);
    }

    /** One of eight compass directions for a flat offset (north is −z). */
    public static String direction(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(-dx, dz)); // 0 = south (+z), 90 = west, 180 = north, -90 = east
        int index = Math.floorMod(Math.round((float) angle / 45f), 8);
        return DIRECTIONS[index];
    }

    static Component biomeName(Holder<Biome> biome) {
        return biome.unwrapKey().map(k -> (Component) Component.translatable("biome." + k.location().getNamespace() + "." + k.location().getPath()))
                .orElse(Component.translatable("bingo.noirm.seen.wilds"));
    }
}

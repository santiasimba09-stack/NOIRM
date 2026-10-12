package com.noirm.network;

import com.noirm.client.ClientJutsuCatalog;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client: every jutsu that exists (not just the ones you know), so the jutsu screen
 * can show locked jutsu and how to unlock them. Jutsu are data-pack content, so the client
 * only knows them through this. Sent on login and after {@code /reload}.
 */
public record JutsuCatalogPacket(List<Entry> entries) {
    /** What the screen shows about one jutsu. */
    public record Entry(ResourceLocation id, int nature, String rank, int cast, String behavior, float chakraCost, int cooldown,
                        String unlockAfter, int unlockMastery, String requires, boolean starter) {
        void encode(FriendlyByteBuf buf) {
            buf.writeResourceLocation(id);
            buf.writeVarInt(nature + 1);
            buf.writeUtf(rank);
            buf.writeVarInt(cast);
            buf.writeUtf(behavior);
            buf.writeFloat(chakraCost);
            buf.writeVarInt(cooldown);
            buf.writeUtf(unlockAfter);
            buf.writeVarInt(unlockMastery);
            buf.writeUtf(requires);
            buf.writeBoolean(starter);
        }

        static Entry decode(FriendlyByteBuf buf) {
            return new Entry(buf.readResourceLocation(), buf.readVarInt() - 1, buf.readUtf(8), buf.readVarInt(), buf.readUtf(64),
                    buf.readFloat(), buf.readVarInt(), buf.readUtf(256), buf.readVarInt(), buf.readUtf(64), buf.readBoolean());
        }
    }

    public static JutsuCatalogPacket fromData() {
        List<Entry> entries = new ArrayList<>();
        JutsuData.all().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(e -> {
                    JutsuDefinition def = e.getValue();
                    entries.add(new Entry(e.getKey(), def.nature().map(Enum::ordinal).orElse(-1), def.rank(), def.cast().ordinal(),
                            def.behavior(), def.chakraCost(), def.cooldown(),
                            def.unlock().map(u -> u.after().toString()).orElse(""), def.unlock().map(JutsuDefinition.Unlock::mastery).orElse(0),
                            def.requires().map(JutsuDefinition.Requirement::dojutsu).orElse(""), def.starter()));
                });
        return new JutsuCatalogPacket(entries);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(entries, (b, e) -> e.encode(b));
    }

    public static JutsuCatalogPacket decode(FriendlyByteBuf buf) {
        return new JutsuCatalogPacket(buf.readList(Entry::decode));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ClientJutsuCatalog.set(entries);
        context.get().setPacketHandled(true);
    }
}

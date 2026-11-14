package com.noirm.pills;

import com.noirm.player.ShinobiData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Server → client: the player's foods and pills ({@link Sync}: pharmacology, toxicity, running
 * effects, cooldowns) for the HUD, and the apothecary's recipe rows ({@link Station}) while its
 * screen is open. Both land in {@link PillClientState} (plain data, no client classes).
 */
public final class PillPackets {
    private PillPackets() {}

    public static int register(SimpleChannel channel, int id) {
        channel.messageBuilder(Sync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Sync::encode).decoder(Sync::decode).consumerMainThread(Sync::handle).add();
        channel.messageBuilder(Station.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Station::encode).decoder(Station::decode).consumerMainThread(Station::handle).add();
        return id;
    }

    /** One running effect, for the HUD. {@code delay} > 0: a crash still waiting. */
    public record Line(ResourceLocation source, int kind, float amount, int left, int total, int delay, boolean crash, int color) {
        void encode(FriendlyByteBuf buf) {
            buf.writeResourceLocation(source);
            buf.writeByte(kind);
            buf.writeFloat(amount);
            buf.writeVarInt(left);
            buf.writeVarInt(total);
            buf.writeVarInt(delay);
            buf.writeBoolean(crash);
            buf.writeInt(color);
        }

        static Line decode(FriendlyByteBuf buf) {
            return new Line(buf.readResourceLocation(), buf.readByte(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readInt());
        }

        public Boost.Kind kindEnum() {
            return Boost.Kind.values()[Math.floorMod(kind, Boost.Kind.values().length)];
        }
    }

    /** A pill's cooldown. */
    public record Timer(ResourceLocation source, int left, int total) {
        void encode(FriendlyByteBuf buf) {
            buf.writeResourceLocation(source);
            buf.writeVarInt(left);
            buf.writeVarInt(total);
        }

        static Timer decode(FriendlyByteBuf buf) {
            return new Timer(buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt());
        }
    }

    public record Sync(float pharmacology, float progress, float toxicity, List<Line> lines, List<Timer> cooldowns, int brewed) {
        public static Sync of(ShinobiData data) {
            PillData pills = data.pills();
            List<Line> lines = new ArrayList<>();
            for (Boost b : pills.boosts()) {
                lines.add(new Line(b.source, b.kind.ordinal(), b.amount, b.ticksLeft, b.total, b.delay, b.crash, color(b.source)));
            }
            List<Timer> timers = new ArrayList<>();
            for (Map.Entry<ResourceLocation, Integer> e : pills.cooldowns().entrySet()) {
                timers.add(new Timer(e.getKey(), e.getValue(), pills.cooldownTotal(e.getKey())));
            }
            return new Sync(pills.pharmacology(), Pharmacology.progress(pills), pills.toxicity(), lines, timers, pills.brewed());
        }

        private static int color(ResourceLocation source) {
            PillDefinition def = PillRegistry.pill(source);
            if (def == null) {
                def = PillRegistry.foods().get(source);
            }
            return def == null ? 0xE8E2D4 : def.rgb();
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeFloat(pharmacology);
            buf.writeFloat(progress);
            buf.writeFloat(toxicity);
            buf.writeVarInt(lines.size());
            lines.forEach(l -> l.encode(buf));
            buf.writeVarInt(cooldowns.size());
            cooldowns.forEach(t -> t.encode(buf));
            buf.writeVarInt(brewed);
        }

        static Sync decode(FriendlyByteBuf buf) {
            float pharm = buf.readFloat(), progress = buf.readFloat(), tox = buf.readFloat();
            int n = Math.min(buf.readVarInt(), 64);
            List<Line> lines = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                lines.add(Line.decode(buf));
            }
            int m = Math.min(buf.readVarInt(), 64);
            List<Timer> timers = new ArrayList<>();
            for (int i = 0; i < m; i++) {
                timers.add(Timer.decode(buf));
            }
            return new Sync(pharm, progress, tox, lines, timers, buf.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            PillClientState.receive(this);
            context.get().setPacketHandled(true);
        }
    }

    /** One ingredient of a recipe row. */
    public record Need(ResourceLocation item, int count) {}

    /**
     * One recipe at the apothecary, as this player sees it: what it makes, what it needs, whether you
     * can brew it ({@link Apothecary.State}) and, if not, why ({@code hint}: a lang key, with
     * {@code minPharmacology}), the odds and the yield.
     */
    public record Row(ResourceLocation pill, ResourceLocation item, List<Need> needs, float minPharmacology, float difficulty, int state,
                      String hint, float success, float[] odds, int yield, int color) {
        void encode(FriendlyByteBuf buf) {
            buf.writeResourceLocation(pill);
            buf.writeResourceLocation(item);
            buf.writeVarInt(needs.size());
            for (Need n : needs) {
                buf.writeResourceLocation(n.item());
                buf.writeVarInt(n.count());
            }
            buf.writeFloat(minPharmacology);
            buf.writeFloat(difficulty);
            buf.writeByte(state);
            buf.writeUtf(hint, 128);
            buf.writeFloat(success);
            for (int i = 0; i < 4; i++) {
                buf.writeFloat(odds[i]);
            }
            buf.writeVarInt(yield);
            buf.writeInt(color);
        }

        static Row decode(FriendlyByteBuf buf) {
            ResourceLocation pill = buf.readResourceLocation(), item = buf.readResourceLocation();
            int n = Math.min(buf.readVarInt(), 8);
            List<Need> needs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                needs.add(new Need(buf.readResourceLocation(), buf.readVarInt()));
            }
            float min = buf.readFloat(), diff = buf.readFloat();
            int state = buf.readByte();
            String hint = buf.readUtf(128);
            float success = buf.readFloat();
            float[] odds = new float[4];
            for (int i = 0; i < 4; i++) {
                odds[i] = buf.readFloat();
            }
            return new Row(pill, item, needs, min, diff, state, hint, success, odds, buf.readVarInt(), buf.readInt());
        }

        public Apothecary.State stateEnum() {
            return Apothecary.State.values()[Math.floorMod(state, Apothecary.State.values().length)];
        }
    }

    /** The apothecary's rows for this player, and the recipe in the mortar now (-1 = none). */
    public record Station(List<Row> rows, int selected, int matched) {
        public static Station of(ShinobiData data, ResourceLocation selected, ResourceLocation matched) {
            List<Row> rows = new ArrayList<>();
            int sel = -1, match = -1;
            for (var e : PillRegistry.recipes()) {
                PillDefinition def = e.getValue();
                PillDefinition.Recipe r = def.recipe().get();
                Apothecary.State state = Apothecary.state(data, e.getKey(), r);
                List<Need> needs = r.ingredients().stream().map(i -> new Need(i.item(), i.count())).toList();
                String hint = switch (state) {
                    case READY -> "";
                    case LOW_SKILL -> "screen.noirm.apothecary.why.skill";
                    case UNKNOWN -> r.perk().isEmpty() ? "screen.noirm.apothecary.why.scroll"
                            : "screen.noirm.apothecary.why.perk." + r.perk();
                };
                if (e.getKey().equals(selected)) {
                    sel = rows.size();
                }
                if (e.getKey().equals(matched)) {
                    match = rows.size();
                }
                rows.add(new Row(e.getKey(), def.item(), needs, r.minPharmacology(), r.difficulty(), state.ordinal(), hint,
                        Apothecary.successChance(data, r), Apothecary.gradeOdds(data, r), Apothecary.yieldOf(data, r), def.rgb()));
            }
            return new Station(rows, sel, match);
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(rows.size());
            rows.forEach(r -> r.encode(buf));
            buf.writeVarInt(selected + 1);
            buf.writeVarInt(matched + 1);
        }

        static Station decode(FriendlyByteBuf buf) {
            int n = Math.min(buf.readVarInt(), 128);
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                rows.add(Row.decode(buf));
            }
            return new Station(rows, buf.readVarInt() - 1, buf.readVarInt() - 1);
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            PillClientState.receive(this);
            context.get().setPacketHandled(true);
        }
    }
}

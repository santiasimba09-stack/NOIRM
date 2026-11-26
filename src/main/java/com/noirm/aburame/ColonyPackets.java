package com.noirm.aburame;

import com.noirm.player.ShinobiData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The colony on the wire (protocol 36): the Swarm key and panel's orders (client → server), and what
 * the HUD line and the colony panel show (part of {@code SyncPowers}).
 */
public final class ColonyPackets {
    private ColonyPackets() {}

    /**
     * An order from the Swarm key or a screen: {@code recall}, {@code send:<entity id>},
     * {@code park:<x>,<y>,<z>}, {@code jar:<x>,<y>,<z>:<action>} (a Breeding Jar's buttons).
     */
    public record Command(String action) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(action, 96);
        }

        public static Command decode(FriendlyByteBuf buf) {
            return new Command(buf.readUtf(96));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player != null && player.isAlive()) {
                command(player, action);
            }
        }
    }

    /** Carries out a Swarm key or screen order. Returns whether anything happened. */
    public static boolean command(ServerPlayer player, String action) {
        if (Colony.of(player) == null) {
            return false;
        }
        try {
            if (action.equals("recall")) {
                int n = Swarms.recall(player);
                player.displayClientMessage(Component.translatable("message.noirm.colony.recalled", n), true);
                return n > 0;
            }
            if (action.startsWith("send:")) {
                Entity e = player.level().getEntity(Integer.parseInt(action.substring(5)));
                if (e instanceof LivingEntity target && e != player && e.distanceTo(player) < 64) {
                    int n = Swarms.sendAll(player, target);
                    player.displayClientMessage(Component.translatable("message.noirm.colony.sent", n, target.getName()), true);
                    return n > 0;
                }
                return false;
            }
            if (action.startsWith("park:")) {
                String[] p = action.substring(5).split(",");
                Vec3 at = new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                if (at.distanceTo(player.position()) > 64) {
                    return false;
                }
                int n = Swarms.parkAll(player, at);
                player.displayClientMessage(Component.translatable("message.noirm.colony.parked", n), true);
                return n > 0;
            }
            if (action.startsWith("jar:")) {
                String[] parts = action.substring(4).split(":", 2);
                String[] p = parts[0].split(",");
                BlockPos pos = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                if (pos.distToCenterSqr(player.position()) < 64 && player.level().getBlockEntity(pos) instanceof BreedingJarBlockEntity jar) {
                    String msg = jar.action(player, parts[1]);
                    if (msg != null) {
                        player.displayClientMessage(Component.translatable(msg), true);
                    }
                    return true;
                }
            }
        } catch (RuntimeException e) {
            return false; // a malformed order: ignore it
        }
        return false;
    }

    /** A mark as the HUD shows it: who, the entity id (-1 if out of sight), distance and seconds left. */
    public record MarkView(String name, int entityId, int distance, int seconds) {}

    /** A hive as the panel shows it. */
    public record HiveView(String where, int count) {}

    /** What the HUD line and the colony panel show. {@code has} false = no colony (all else empty). */
    public record ColonySync(boolean has, List<Integer> body, int stored, int storeCap, int field, int capacity, float vigour,
                             int generation, float upkeep, float growthPerMin, int rank, int total, boolean deedDone, float drained,
                             List<Float> resist, List<String> strains, List<String> caught, List<MarkView> marks, List<HiveView> hives,
                             int flags, float radar, int swarms, float banked, int temperament, int bikochu) {
        public static final int RINKAICHU = 1, ANTIVENOM = 2, RESTLESS = 4, LIVING_HIVE = 8, QUEEN = 16;
        public static final ColonySync NONE = new ColonySync(false, List.of(), 0, 0, 0, 0, 0f, 0, 0f, 0f, 0, 0, false, 0f, List.of(),
                List.of(), List.of(), List.of(), List.of(), 0, 0f, 0, 0f, 0, 0);

        public static ColonySync of(ServerPlayer player, ShinobiData data) {
            ColonyData c = data.colony();
            if (!c.has()) {
                return c.caught().isEmpty() ? NONE : new ColonySync(false, List.of(), 0, 0, 0, 0, 0f, 0, 0f, 0f, 0, 0, false, 0f, List.of(),
                        List.of(), List.copyOf(c.caught()), List.of(), List.of(), 0, 0f, 0, 0f, 0, c.bikochuQuest());
            }
            List<Integer> body = new ArrayList<>();
            for (Species s : Species.values()) {
                body.add(c.body(s));
            }
            List<Float> resist = new ArrayList<>();
            for (Harm h : Harm.values()) {
                resist.add(c.resist(h));
            }
            List<MarkView> marks = new ArrayList<>();
            long now = player.level().getGameTime();
            for (ColonyData.Mark m : c.marks()) {
                Entity e = ((ServerLevel) player.level()).getEntity(m.target());
                int dist = e == null ? -1 : (int) e.distanceTo(player);
                marks.add(new MarkView(m.name(), e != null && dist <= 256 ? e.getId() : -1, dist, (int) Math.max(0, (m.until() - now) / 20)));
            }
            List<HiveView> hives = new ArrayList<>();
            for (ColonyData.HiveRef h : c.hives()) {
                hives.add(new HiveView(h.pos().toShortString(), h.count()));
            }
            int cap = Colony.capacity(player);
            float growth = c.bodyTotal() >= cap ? 0f : ColonyGrowth.breedPerSecond(c.body(Species.KIKAICHU)) * 60f
                    * ColonyGrowth.vigourBreeding(c.vigour()) * (c.temperament() == 1 ? NestRoll.RESTLESS_BREEDING : 1f);
            HiveRank next = c.rank().next();
            int flags = (c.rinkaichuBorn() ? RINKAICHU : 0) | (c.antivenom() ? ANTIVENOM : 0) | (c.restless() ? RESTLESS : 0)
                    | (c.livingHive() ? LIVING_HIVE : 0) | (c.queen() ? QUEEN : 0);
            return new ColonySync(true, body, Colony.stored(player), Colony.storeCapacity(player), c.field(), cap, c.vigour(),
                    c.generation(), ColonyGrowth.upkeep(c.bodyTotal()) * (c.livingHive() ? 2 : 1), growth, c.rank().ordinal(), Colony.total(player),
                    next != null && Colony.deedDone(player, c, next), c.drainedTotal(), resist, List.copyOf(c.strains()), List.copyOf(c.caught()),
                    marks, hives, flags, ColonyGrowth.radar(c.field()), Swarms.owned(player).size(), c.banked(), c.temperament(), c.bikochuQuest());
        }

        public int bodyTotal() {
            int s = 0;
            for (int n : body) {
                s += n;
            }
            return s;
        }

        public int body(Species species) {
            return species.ordinal() < body.size() ? body.get(species.ordinal()) : 0;
        }

        public float resist(Harm harm) {
            return harm.ordinal() < resist.size() ? resist.get(harm.ordinal()) : 0f;
        }

        public HiveRank rankEnum() {
            return HiveRank.of(rank);
        }

        public boolean flag(int bit) {
            return (flags & bit) != 0;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(has);
            buf.writeCollection(body, FriendlyByteBuf::writeVarInt);
            buf.writeVarInt(stored);
            buf.writeVarInt(storeCap);
            buf.writeVarInt(field);
            buf.writeVarInt(capacity);
            buf.writeFloat(vigour);
            buf.writeVarInt(generation);
            buf.writeFloat(upkeep);
            buf.writeFloat(growthPerMin);
            buf.writeByte(rank);
            buf.writeVarInt(total);
            buf.writeBoolean(deedDone);
            buf.writeFloat(drained);
            buf.writeCollection(resist, FriendlyByteBuf::writeFloat);
            buf.writeCollection(strains, (b, s) -> b.writeUtf(s, 32));
            buf.writeCollection(caught, (b, s) -> b.writeUtf(s, 32));
            buf.writeCollection(marks, (b, m) -> {
                b.writeUtf(m.name(), 64);
                b.writeVarInt(m.entityId() + 1);
                b.writeVarInt(m.distance() + 1);
                b.writeVarInt(m.seconds());
            });
            buf.writeCollection(hives, (b, h) -> {
                b.writeUtf(h.where(), 48);
                b.writeVarInt(h.count());
            });
            buf.writeVarInt(flags);
            buf.writeFloat(radar);
            buf.writeVarInt(swarms);
            buf.writeFloat(banked);
            buf.writeByte(temperament);
            buf.writeByte(bikochu);
        }

        public static ColonySync decode(FriendlyByteBuf buf) {
            return new ColonySync(buf.readBoolean(), buf.readList(FriendlyByteBuf::readVarInt), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readVarInt(),
                    buf.readBoolean(), buf.readFloat(), buf.readList(FriendlyByteBuf::readFloat), buf.readList(b -> b.readUtf(32)),
                    buf.readList(b -> b.readUtf(32)),
                    buf.readList(b -> new MarkView(b.readUtf(64), b.readVarInt() - 1, b.readVarInt() - 1, b.readVarInt())),
                    buf.readList(b -> new HiveView(b.readUtf(48), b.readVarInt())),
                    buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readFloat(), buf.readByte(), buf.readByte());
        }
    }
}

package com.noirm.master;

import com.noirm.combat.StyleSystem;
import com.noirm.entity.MasterNinja;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.network.NoirmNetwork;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The master screen: what it shows (server → client, everything already worked out, including
 * why a lesson is locked) and what its buttons do (client → server).
 */
public final class MasterPackets {
    /** How close you must be to talk to a master. */
    public static final double TALK_RANGE = 8;

    private MasterPackets() {}

    /** Right-click on a master: open (or refresh) their screen. */
    public static void open(ServerPlayer player, MasterNinja master) {
        NoirmNetwork.sendToPlayer(player, Info.of(player, master));
    }

    /**
     * One lesson as the screen shows it.
     *
     * @param status   {@link #LEARNED}, {@link #READY} or {@link #LOCKED}
     * @param blocker  why it's locked (empty if it isn't)
     * @param needs    what it asks for, in one line
     * @param title    what it teaches: a jutsu's name, a perk or a service
     * @param rank     the jutsu's rank letter ("" for a perk or a service)
     * @param ryo      its price
     * @param service  a service (asked for each time; never "learned")
     */
    public record LessonRow(String id, Component title, String rank, int status, Component blocker, Component needs, int ryo, boolean service) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id, 64);
            buf.writeComponent(title);
            buf.writeUtf(rank, 4);
            buf.writeVarInt(status);
            buf.writeComponent(blocker);
            buf.writeComponent(needs);
            buf.writeVarInt(ryo);
            buf.writeBoolean(service);
        }

        static LessonRow decode(FriendlyByteBuf buf) {
            return new LessonRow(buf.readUtf(64), buf.readComponent(), buf.readUtf(4), buf.readVarInt(), buf.readComponent(), buf.readComponent(),
                    buf.readVarInt(), buf.readBoolean());
        }
    }

    public static final int LEARNED = 0, READY = 1, LOCKED = 2;

    /** Everything the master screen shows. {@code sparBlocker} is empty if you can spar now. */
    public record Info(int entityId, String nameKey, String greetingKey, String styleKey, boolean passed, boolean styleGranted,
                       int hits, int postureBreaks, int seconds, Component sparBlocker, List<LessonRow> lessons) {
        public static Info of(ServerPlayer player, MasterNinja master) {
            MasterDefinition def = master.definition();
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            ResourceLocation id = master.masterId();
            if (def == null || data == null || id == null) {
                return new Info(master.getId(), "entity.noirm.master", "", "", false, false, 0, 0, 0,
                        Component.translatable("screen.noirm.master.why.unknown"), List.of());
            }
            List<LessonRow> rows = new ArrayList<>();
            for (MasterDefinition.Lesson lesson : def.lessons()) {
                JutsuDefinition jutsu = JutsuData.get(lesson.jutsu());
                boolean learned = !lesson.isService() && data.masters().learned(id.toString(), lesson.id());
                Component blocker = learned ? null : MasterSystem.lessonBlocker(player, data, id, def, lesson);
                rows.add(new LessonRow(lesson.id(), Component.translatable(lesson.titleKey()),
                        !lesson.teachesJutsu() ? "" : jutsu == null ? "?" : jutsu.rank(),
                        learned ? LEARNED : blocker == null ? READY : LOCKED,
                        blocker == null ? Component.empty() : blocker, needs(def, lesson), lesson.ryo(), lesson.isService()));
            }
            Component blocker = MasterSystem.sparBlocker(player, master);
            String style = def.style().map(StyleSystem::translationKey).orElse("");
            return new Info(master.getId(), def.name(), def.greeting(), style, data.masters().passed(id.toString()),
                    def.style().map(s -> data.masters().hasStyle(s.toString())).orElse(false),
                    def.test().hits(), def.test().postureBreaks(), def.test().seconds(),
                    blocker == null ? Component.empty() : blocker, rows);
        }

        /** "Taijutsu 20 · after Gate of Opening · Gate of Opening ★1", or "Pass the test". */
        private static Component needs(MasterDefinition def, MasterDefinition.Lesson lesson) {
            List<Component> parts = new ArrayList<>();
            if (lesson.neededRank() != null) {
                parts.add(lesson.neededRank().title());
            }
            if (lesson.ryo() > 0) {
                parts.add(com.noirm.economy.Ryo.text(lesson.ryo()));
            }
            if (lesson.taijutsu() > 0) {
                parts.add(Component.translatable("screen.noirm.master.need.taijutsu", MasterSystem.fmt(lesson.taijutsu())));
            }
            if (lesson.speed() > 0) {
                parts.add(Component.translatable("screen.noirm.master.need.speed", MasterSystem.fmt(lesson.speed())));
            }
            if (lesson.control() > 0) {
                parts.add(Component.translatable("screen.noirm.master.need.control", MasterSystem.fmt(lesson.control())));
            }
            for (String before : lesson.after()) {
                def.lesson(before).ifPresent(l -> parts.add(Component.translatable("screen.noirm.master.need.after",
                        Component.translatable(l.titleKey()))));
            }
            for (var practice : lesson.mastery().entrySet()) {
                parts.add(Component.translatable("screen.noirm.master.need.practice",
                        Component.translatable(MasterSystem.jutsuKey(practice.getKey())), practice.getValue()));
            }
            if (parts.isEmpty()) {
                return Component.translatable(lesson.test() ? "screen.noirm.master.need.test" : "screen.noirm.master.need.nothing");
            }
            var line = Component.empty();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) {
                    line.append(" · ");
                }
                line.append(parts.get(i));
            }
            return line;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeUtf(nameKey, 128);
            buf.writeUtf(greetingKey, 128);
            buf.writeUtf(styleKey, 128);
            buf.writeBoolean(passed);
            buf.writeBoolean(styleGranted);
            buf.writeVarInt(hits);
            buf.writeVarInt(postureBreaks);
            buf.writeVarInt(seconds);
            buf.writeComponent(sparBlocker);
            buf.writeCollection(lessons, (b, row) -> row.encode(b));
        }

        public static Info decode(FriendlyByteBuf buf) {
            return new Info(buf.readVarInt(), buf.readUtf(128), buf.readUtf(128), buf.readUtf(128), buf.readBoolean(), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readComponent(), buf.readList(LessonRow::decode));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.MasterScreen.receive(this));
            context.get().setPacketHandled(true);
        }
    }

    public enum Action { SPAR, LEARN }

    /** A button on the master screen. */
    public record Command(int entityId, Action action, String arg) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeEnum(action);
            buf.writeUtf(arg, 64);
        }

        public static Command decode(FriendlyByteBuf buf) {
            return new Command(buf.readVarInt(), buf.readEnum(Action.class), buf.readUtf(64));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null || !(player.level().getEntity(entityId) instanceof MasterNinja master) || player.distanceTo(master) > TALK_RANGE) {
                return;
            }
            switch (action) {
                case SPAR -> {
                    if (MasterSystem.startSpar(player, master)) {
                        NoirmNetwork.sendToPlayer(player, new Close());
                        return;
                    }
                }
                case LEARN -> MasterSystem.learn(player, master.masterId(), arg);
            }
            open(player, master); // refresh the screen
        }
    }

    /** Closes the master screen (the spar begins). */
    public record Close() {
        public void encode(FriendlyByteBuf buf) {}

        public static Close decode(FriendlyByteBuf buf) {
            return new Close();
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.noirm.client.MasterScreen::closeIfOpen);
            context.get().setPacketHandled(true);
        }
    }
}

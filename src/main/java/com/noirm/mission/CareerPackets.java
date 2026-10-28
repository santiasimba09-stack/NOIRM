package com.noirm.mission;

import com.noirm.economy.Shop;
import com.noirm.entity.VillageMerchant;
import com.noirm.network.NoirmNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The career packets: the HUD's copy of your ryo, rank and mission ({@link Sync}), the mission
 * board screen ({@link BoardInfo} / {@link BoardAction}) and the shop screen ({@link ShopInfo} /
 * {@link ShopAction}).
 */
public final class CareerPackets {
    /** How close you must be to a board or a shopkeeper to use their screen. */
    public static final double USE_RANGE = 8;

    private CareerPackets() {}

    /** Registers the packets after the others ({@code NoirmNetwork.register}); returns the next free id. */
    public static int register(SimpleChannel channel, int id) {
        channel.messageBuilder(Sync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Sync::encode).decoder(Sync::decode).consumerMainThread(Sync::handle).add();
        channel.messageBuilder(BoardInfo.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BoardInfo::encode).decoder(BoardInfo::decode).consumerMainThread(BoardInfo::handle).add();
        channel.messageBuilder(BoardAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BoardAction::encode).decoder(BoardAction::decode).consumerMainThread(BoardAction::handle).add();
        channel.messageBuilder(ShopInfo.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ShopInfo::encode).decoder(ShopInfo::decode).consumerMainThread(ShopInfo::handle).add();
        channel.messageBuilder(ShopAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ShopAction::encode).decoder(ShopAction::decode).consumerMainThread(ShopAction::handle).add();
        return id;
    }

    // ================================================================ the HUD

    /**
     * Your career as the HUD shows it. {@code nextPoints} is -1 at the top rank; deadlines are game
     * times (0 = none); the target is where the mission arrow points.
     */
    public record Sync(int ryo, int rank, int points, int nextPoints, boolean examReady,
                       boolean onMission, String mission, int letter, Component objective, BlockPos target, long deadline, boolean report,
                       boolean examFight, int examTarget, int round, int rounds, long examDeadline) {
        public static Sync of(ServerPlayer player, CareerData career) {
            PlayerRank next = career.rank().next();
            boolean ready = next != null && next.exam() && career.points() >= next.points() && career.exam() == null;
            Mission m = career.mission();
            MissionTemplate def = m == null ? null : m.definition();
            CareerData.Exam exam = career.exam();
            boolean fight = exam != null && exam.stage == CareerData.Exam.Stage.FIGHT;
            return new Sync(career.ryo(), career.rank().ordinal(), career.points(), next == null ? -1 : next.points(), ready,
                    def != null, def == null ? "" : m.template.toString(), def == null ? 0 : def.rank().ordinal(),
                    def == null ? Component.empty() : Missions.objective(player, m), m == null ? BlockPos.ZERO : m.target(),
                    m == null || m.stage == Mission.Stage.RETURN ? 0 : m.deadline, m != null && m.stage == Mission.Stage.RETURN,
                    fight, exam == null ? 0 : exam.target.ordinal(), exam == null ? 0 : exam.round,
                    exam == null ? 0 : Exams.rounds(exam.target).length, fight ? exam.deadline : 0);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(ryo);
            buf.writeByte(rank);
            buf.writeVarInt(points);
            buf.writeVarInt(nextPoints);
            buf.writeBoolean(examReady);
            buf.writeBoolean(onMission);
            buf.writeUtf(mission, 256);
            buf.writeByte(letter);
            buf.writeComponent(objective);
            buf.writeBlockPos(target);
            buf.writeLong(deadline);
            buf.writeBoolean(report);
            buf.writeBoolean(examFight);
            buf.writeByte(examTarget);
            buf.writeByte(round);
            buf.writeByte(rounds);
            buf.writeLong(examDeadline);
        }

        public static Sync decode(FriendlyByteBuf buf) {
            return new Sync(buf.readVarInt(), buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    buf.readBoolean(), buf.readUtf(256), buf.readByte(), buf.readComponent(), buf.readBlockPos(), buf.readLong(), buf.readBoolean(),
                    buf.readBoolean(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readLong());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            com.noirm.client.ClientCareer.update(this);
            context.get().setPacketHandled(true);
        }
    }

    public static void sync(ServerPlayer player, CareerData career) {
        NoirmNetwork.sendToPlayer(player, Sync.of(player, career));
    }

    // ================================================================ the mission board

    public static final int READY = 0, LOCKED = 1, TAKEN = 2;

    /** One mission on offer. */
    public record OfferRow(int index, ResourceLocation id, int letter, int kind, int reward, int points, int timeLimit, int status,
                           Component why) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(index);
            buf.writeResourceLocation(id);
            buf.writeByte(letter);
            buf.writeByte(kind);
            buf.writeVarInt(reward);
            buf.writeVarInt(points);
            buf.writeVarInt(timeLimit);
            buf.writeByte(status);
            buf.writeComponent(why);
        }

        static OfferRow decode(FriendlyByteBuf buf) {
            return new OfferRow(buf.readVarInt(), buf.readResourceLocation(), buf.readByte(), buf.readByte(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readComponent());
        }
    }

    /** Everything the board screen shows. {@code examWhy} is empty when the exam can start now. */
    public record BoardInfo(BlockPos pos, int rank, int points, int nextPoints, int ryo, List<OfferRow> offers,
                            boolean onMission, String mission, Component objective, boolean report,
                            Component examLine, boolean examButton, Component examWhy) {
        public static BoardInfo of(ServerPlayer player, BlockPos pos) {
            CareerData career = Missions.career(player).orElse(new CareerData());
            List<OfferRow> rows = new ArrayList<>();
            List<ResourceLocation> offers = Missions.offers(player.level(), pos);
            long day = Missions.day(player.level());
            for (int i = 0; i < offers.size(); i++) {
                MissionTemplate def = MissionTemplates.get(offers.get(i));
                Component why = Missions.blocker(player, def);
                boolean taken = career.offerTaken(Missions.offerKey(pos, i), day);
                boolean rankLocked = def != null && !career.rank().atLeast(def.rank().required());
                rows.add(new OfferRow(i, offers.get(i), def.rank().ordinal(), def.kind().ordinal(), def.reward(), def.points(), def.timeLimit(),
                        taken ? TAKEN : rankLocked || why != null ? LOCKED : READY,
                        taken ? Component.translatable("mission.noirm.why.taken") : why == null ? Component.empty() : why));
            }
            Mission m = career.mission();
            PlayerRank next = career.rank().next();
            Component examLine;
            boolean examButton = false;
            CareerData.Exam exam = career.exam();
            if (exam != null) {
                examLine = Component.translatable("board.noirm.exam.running", exam.target.title());
            } else if (next == null) {
                examLine = Component.translatable("board.noirm.exam.top");
            } else if (next.exam()) {
                examLine = Component.translatable("board.noirm.exam.next", next.title(), career.points(), next.points());
                examButton = true;
            } else {
                examLine = Component.translatable("board.noirm.exam.invitation", next.title(), career.points(), next.points(),
                        career.completed(next.letter()), next.missions(), next.letter().name());
            }
            Component why = examButton ? Exams.blocker(player) : null;
            return new BoardInfo(pos, career.rank().ordinal(), career.points(), next == null ? -1 : next.points(), career.ryo(), rows,
                    m != null, m == null ? "" : m.template.toString(), m == null ? Component.empty() : Missions.objective(player, m),
                    m != null && m.stage == Mission.Stage.RETURN, examLine, examButton, why == null ? Component.empty() : why);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeByte(rank);
            buf.writeVarInt(points);
            buf.writeVarInt(nextPoints);
            buf.writeVarInt(ryo);
            buf.writeCollection(offers, (b, row) -> row.encode(b));
            buf.writeBoolean(onMission);
            buf.writeUtf(mission, 256);
            buf.writeComponent(objective);
            buf.writeBoolean(report);
            buf.writeComponent(examLine);
            buf.writeBoolean(examButton);
            buf.writeComponent(examWhy);
        }

        public static BoardInfo decode(FriendlyByteBuf buf) {
            return new BoardInfo(buf.readBlockPos(), buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readList(OfferRow::decode), buf.readBoolean(), buf.readUtf(256), buf.readComponent(), buf.readBoolean(),
                    buf.readComponent(), buf.readBoolean(), buf.readComponent());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.MissionBoardScreen.receive(this));
            context.get().setPacketHandled(true);
        }
    }

    public static void openBoard(ServerPlayer player, BlockPos pos) {
        NoirmNetwork.sendToPlayer(player, BoardInfo.of(player, pos));
    }

    public enum Action { ACCEPT, ABANDON, EXAM, CLOSE, BINGO_BOOK }

    /** A button on the board screen. */
    public record BoardAction(BlockPos pos, Action action, int index) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeEnum(action);
            buf.writeVarInt(index);
        }

        public static BoardAction decode(FriendlyByteBuf buf) {
            return new BoardAction(buf.readBlockPos(), buf.readEnum(Action.class), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null || !player.level().getBlockState(pos).is(CareerItems.MISSION_BOARD.get())
                    || player.distanceToSqr(pos.getCenter()) > USE_RANGE * USE_RANGE) {
                return;
            }
            Missions.remember(player);
            switch (action) {
                case ACCEPT -> {
                    Component why = Missions.acceptOffer(player, pos, index);
                    if (why != null) {
                        player.displayClientMessage(why.copy().withStyle(net.minecraft.ChatFormatting.RED), true);
                    }
                }
                case ABANDON -> Missions.abandon(player);
                case EXAM -> {
                    if (Exams.start(player, pos)) {
                        NoirmNetwork.sendToPlayer(player, new ShopInfo(-1, Component.empty(), 0, 0, List.of())); // closes the screen
                        return;
                    }
                }
                case CLOSE -> {
                    return;
                }
                case BINGO_BOOK -> com.noirm.bounty.BingoEvents.buyAtBoard(player); // from the board's clerk
            }
            openBoard(player, pos);
        }
    }

    // ================================================================ shops

    /** One line of a shop as the screen shows it. */
    public record ShopRowView(ItemStack stack, int price, boolean sell, int need, int have) {
        void encode(FriendlyByteBuf buf) {
            buf.writeItem(stack);
            buf.writeVarInt(price);
            buf.writeBoolean(sell);
            buf.writeByte(need);
            buf.writeVarInt(have);
        }

        static ShopRowView decode(FriendlyByteBuf buf) {
            return new ShopRowView(buf.readItem(), buf.readVarInt(), buf.readBoolean(), buf.readByte(), buf.readVarInt());
        }
    }

    /** A shop's screen ({@code entityId} -1 closes whatever career screen is open). */
    public record ShopInfo(int entityId, Component name, int ryo, int rank, List<ShopRowView> rows) {
        public static ShopInfo of(ServerPlayer player, VillageMerchant merchant) {
            List<ShopRowView> rows = new ArrayList<>();
            for (Shop.Row row : Shop.rows(merchant, player)) {
                rows.add(new ShopRowView(row.stack(), row.price(), row.sell(), row.need().ordinal(), Shop.count(player, row.stack().getItem())));
            }
            return new ShopInfo(merchant.getId(), merchant.getDisplayName(), com.noirm.economy.Ryo.balance(player),
                    PlayerRank.of(player).ordinal(), rows);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeComponent(name);
            buf.writeVarInt(ryo);
            buf.writeByte(rank);
            buf.writeCollection(rows, (b, row) -> row.encode(b));
        }

        public static ShopInfo decode(FriendlyByteBuf buf) {
            return new ShopInfo(buf.readVarInt(), buf.readComponent(), buf.readVarInt(), buf.readByte(), buf.readList(ShopRowView::decode));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.ShopScreen.receive(this));
            context.get().setPacketHandled(true);
        }
    }

    public static void openShop(ServerPlayer player, VillageMerchant merchant) {
        NoirmNetwork.sendToPlayer(player, ShopInfo.of(player, merchant));
    }

    /** Buy or sell a row of the shop screen. */
    public record ShopAction(int entityId, int index) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeVarInt(index);
        }

        public static ShopAction decode(FriendlyByteBuf buf) {
            return new ShopAction(buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null || !(player.level().getEntity(entityId) instanceof VillageMerchant merchant) || !merchant.isAlive()
                    || player.distanceTo(merchant) > USE_RANGE) {
                return;
            }
            Shop.trade(player, merchant, index);
            openShop(player, merchant);
        }
    }
}

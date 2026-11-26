package com.noirm.combat;

import com.noirm.network.NoirmNetwork;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import com.noirm.weapon.Moveset;
import com.noirm.weapon.WeaponMoves;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The style picker ({@code client.StylePickerScreen}): the client asks for the list ({@link Open}),
 * the server sends every playable style with whether you know it and how it plays ({@link Info}), and
 * a click picks one ({@link Choose}). Added in protocol 28.
 */
public final class StylePackets {
    private StylePackets() {}

    public static int register(SimpleChannel channel, int id) {
        channel.messageBuilder(Open.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Open::encode).decoder(Open::decode).consumerMainThread(Open::handle).add();
        channel.messageBuilder(Info.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Info::encode).decoder(Info::decode).consumerMainThread(Info::handle).add();
        channel.messageBuilder(Choose.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Choose::encode).decoder(Choose::decode).consumerMainThread(Choose::handle).add();
        return id;
    }

    /** Client -> server: open the picker (send me the list). */
    public record Open() {
        public void encode(FriendlyByteBuf buf) {
        }

        public static Open decode(FriendlyByteBuf buf) {
            return new Open();
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                NoirmNetwork.sendToPlayer(player, Info.of(player, true));
            }
            context.get().setPacketHandled(true);
        }
    }

    /**
     * One style as the picker shows it.
     *
     * @param blocker  "" if you may use it, else {@code learn}, {@code taijutsu} or {@code byakugan}
     * @param how      how you learned it ({@code StyleSystem.MASTER}…), or ""
     * @param weapons  the weapon movesets it fights with (empty: an unarmed style)
     * @param flags    bit 0 feints, 1 backstab, 2 aerial, 3 stamina drain, 4 momentum, 5 irregular timing
     */
    public record Row(String id, String blocker, String how, float taijutsu, List<String> weapons, String footwork, String rhythm,
                      String guard, String dodge, float moveSpeed, float parryWindow, int flags, float backstab, float aerial) {
        static Row of(ShinobiData data, ResourceLocation id, CombatStyle style) {
            StyleIdentity i = style.identity();
            int flags = (i.feint().isPresent() ? 1 : 0) | (i.strike().backstab() > 1f ? 2 : 0) | (i.strike().aerial() > 1f ? 4 : 0)
                    | (i.strike().staminaDrain() > 0f ? 8 : 0) | (i.strike().momentum() > 0f ? 16 : 0) | (i.strike().jitter() > 0 ? 32 : 0);
            return new Row(id.toString(), StyleSystem.blocker(data, id, style), data.styles().howLearned(id.toString()), style.taijutsu(),
                    style.weapons(), i.footwork(), i.rhythm(), i.guard().type(), i.dodge().kind(), i.moveSpeed(), i.guard().parryWindow(),
                    flags, i.strike().backstab(), i.strike().aerial());
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeUtf(blocker);
            buf.writeUtf(how);
            buf.writeFloat(taijutsu);
            buf.writeCollection(weapons, FriendlyByteBuf::writeUtf);
            buf.writeUtf(footwork);
            buf.writeUtf(rhythm);
            buf.writeUtf(guard);
            buf.writeUtf(dodge);
            buf.writeFloat(moveSpeed);
            buf.writeFloat(parryWindow);
            buf.writeVarInt(flags);
            buf.writeFloat(backstab);
            buf.writeFloat(aerial);
        }

        static Row decode(FriendlyByteBuf buf) {
            return new Row(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readFloat(), buf.readList(FriendlyByteBuf::readUtf), buf.readUtf(),
                    buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readFloat(), buf.readFloat());
        }

        public boolean usable() {
            return blocker.isEmpty();
        }
    }

    /**
     * Server -> client: every playable style (unarmed and weapon), the active unarmed style, the weapon
     * picks ({@code "katana" -> "noirm:blade_iai"}) and the weapon in hand ("" for none).
     *
     * @param open true to open the picker (false: refresh it if it's open)
     */
    public record Info(boolean open, String active, Map<String, String> picks, String held, List<Row> rows) {
        public static Info of(ServerPlayer player, boolean open) {
            ShinobiData data = ShinobiCapability.get(player).orElse(null);
            if (data == null) {
                return new Info(open, "", Map.of(), "", List.of());
            }
            List<Row> rows = new ArrayList<>();
            CombatData.styles().entrySet().stream()
                    .filter(e -> e.getValue().playable())
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> rows.add(Row.of(data, e.getKey(), e.getValue())));
            Moveset held = WeaponMoves.movesetFor(player.getMainHandItem());
            return new Info(open, StyleSystem.styleOf(data).toString(), Map.copyOf(data.styles().weaponPicks()),
                    held == null ? "" : held.key(), rows);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(open);
            buf.writeUtf(active);
            buf.writeMap(picks, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeUtf);
            buf.writeUtf(held);
            buf.writeCollection(rows, (b, r) -> r.encode(b));
        }

        public static Info decode(FriendlyByteBuf buf) {
            return new Info(buf.readBoolean(), buf.readUtf(), buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readUtf), buf.readUtf(),
                    buf.readList(Row::decode));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.StylePickerScreen.receive(this));
            context.get().setPacketHandled(true);
        }
    }

    /** Client -> server: use this style ({@code "own:<moveset>"} = back to that weapon's own moves). */
    public record Choose(String style) {
        public static final String OWN = "own:";

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(style, 128);
        }

        public static Choose decode(FriendlyByteBuf buf) {
            return new Choose(buf.readUtf(128));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                if (style.startsWith(OWN)) {
                    StyleSystem.clearWeaponPick(player, style.substring(OWN.length()));
                } else {
                    StyleSystem.choose(player, ResourceLocation.tryParse(style));
                }
                NoirmNetwork.sendToPlayer(player, Info.of(player, false));
            }
            context.get().setPacketHandled(true);
        }
    }
}

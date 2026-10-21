package com.noirm.network;

import com.noirm.awakening.AwakeningData;
import com.noirm.biju.BijuSystem;
import com.noirm.client.ClientPowers;
import com.noirm.clan.ClanTrait;
import com.noirm.clan.Nature;
import com.noirm.dojutsu.EyeData;
import com.noirm.dojutsu.Sharingan;
import com.noirm.jutsu.JutsuBook;
import com.noirm.jutsu.JutsuCaster;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.player.ShinobiData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Packets for clans, jutsu, the Sharingan, awakening and the tailed-beast cloak. */
public final class PowerPackets {
    private PowerPackets() {}

    // ================================================================ client → server

    /** A jutsu key (1–4 in combat mode) went down or up. */
    public record JutsuKey(int slot, boolean pressed) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeByte(slot);
            buf.writeBoolean(pressed);
        }

        public static JutsuKey decode(FriendlyByteBuf buf) {
            return new JutsuKey(buf.readByte(), buf.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null && player.isAlive() && slot >= 0 && slot < JutsuBook.SLOTS
                    && (!pressed || com.noirm.combat.CombatStates.get(player).isCombatMode())) {
                JutsuCaster.onKey(player, slot, pressed);
            }
            context.get().setPacketHandled(true);
        }
    }

    /** Put a known jutsu (or nothing, if empty) in a loadout slot. */
    public record EquipJutsu(int slot, String jutsu) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeByte(slot);
            buf.writeUtf(jutsu);
        }

        public static EquipJutsu decode(FriendlyByteBuf buf) {
            return new EquipJutsu(buf.readByte(), buf.readUtf(256));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                com.noirm.player.ShinobiCapability.get(player).ifPresent(data -> {
                    ResourceLocation id = jutsu.isEmpty() ? null : ResourceLocation.tryParse(jutsu);
                    if (data.jutsu().equip(slot, id)) {
                        data.markPowersDirty();
                    }
                });
            }
            context.get().setPacketHandled(true);
        }
    }

    /** The jutsu screen's nature tree: unlock an available combined nature. */
    public record UnlockNature(int nature) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(nature);
        }

        public static UnlockNature decode(FriendlyByteBuf buf) {
            return new UnlockNature(buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null && nature >= 0 && nature < com.noirm.clan.Nature.values().length) {
                com.noirm.player.ShinobiCapability.get(player).ifPresent(data ->
                        com.noirm.clan.NatureSystem.unlock(player, data, com.noirm.clan.Nature.values()[nature]));
            }
            context.get().setPacketHandled(true);
        }
    }

    public enum Toggle { SHARINGAN, CLOAK }

    /** Sharingan or chakra-cloak key. */
    public record TogglePower(Toggle which) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeEnum(which);
        }

        public static TogglePower decode(FriendlyByteBuf buf) {
            return new TogglePower(buf.readEnum(Toggle.class));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null && player.isAlive()) {
                switch (which) {
                    case SHARINGAN -> Sharingan.toggle(player);
                    case CLOAK -> BijuSystem.toggleCloak(player);
                }
            }
            context.get().setPacketHandled(true);
        }
    }

    // ================================================================ server → client

    /** One learned jutsu, as the HUD and loadout screen need it. */
    public record JutsuEntry(ResourceLocation id, String rank, int nature, int mastery, boolean copied, int maxCooldown) {
        void encode(FriendlyByteBuf buf) {
            buf.writeResourceLocation(id);
            buf.writeUtf(rank);
            buf.writeByte(nature);
            buf.writeByte(mastery);
            buf.writeBoolean(copied);
            buf.writeVarInt(maxCooldown);
        }

        static JutsuEntry decode(FriendlyByteBuf buf) {
            return new JutsuEntry(buf.readResourceLocation(), buf.readUtf(8), buf.readByte(), buf.readByte(), buf.readBoolean(), buf.readVarInt());
        }
    }

    /** Everything about the local player's powers, for the HUD and screens. Sent when it changes. */
    public record SyncPowers(String clanName, boolean canonClan, List<Integer> natures, List<Float> affinities,
                             List<Integer> traits, List<JutsuEntry> learned, List<String> loadout, List<Integer> cooldowns,
                             int castingSlot, int castingTicks, int castingMax, boolean castingIsCharge,
                             int sharinganStage, boolean eyesActive, boolean byakugan, int sharinganXp, int sharinganNextXp,
                             float resolve, int lockedInTicks, int exhaustionTicks,
                             int tails, int cloakStage, int cloakTicks, int cloakCooldown,
                             int cloakTails, int unlockedTails, int bond,
                             int sharinganPressure, int sharinganDrillXp,
                             int mangekyoFlags, int mangekyoPattern, int mangekyoDonorPattern,
                             int mangekyoLeft, int mangekyoRight, float mangekyoStrain, int susanooStage) {
        /** {@link #mangekyoFlags} bits. */
        public static final int MS = 1, MS_ETERNAL = 2, MS_TRANSPLANTED = 4, MS_BLIND = 8;

        public boolean mangekyo() {
            return (mangekyoFlags & MS) != 0;
        }

        public boolean eternal() {
            return (mangekyoFlags & MS_ETERNAL) != 0;
        }

        public boolean transplanted() {
            return (mangekyoFlags & MS_TRANSPLANTED) != 0;
        }

        public boolean blind() {
            return (mangekyoFlags & MS_BLIND) != 0;
        }


        public static SyncPowers of(ServerPlayer player, ShinobiData data) {
            List<Integer> natures = new ArrayList<>();
            List<Float> affinities = new ArrayList<>();
            data.identity().natures().forEach((n, a) -> {
                natures.add(n.ordinal());
                affinities.add(a);
            });
            List<Integer> traits = data.identity().traits().stream().map(ClanTrait::ordinal).toList();
            JutsuBook book = data.jutsu();
            List<JutsuEntry> learned = new ArrayList<>();
            for (ResourceLocation id : book.known()) {
                JutsuDefinition def = JutsuData.get(id);
                if (def != null) {
                    learned.add(new JutsuEntry(id, def.rank(), def.nature().map(Nature::ordinal).orElse(-1),
                            book.masteryLevel(id), book.isCopied(id), JutsuCaster.cooldown(player, id, def)));
                }
            }
            List<String> loadout = new ArrayList<>();
            List<Integer> cooldowns = new ArrayList<>();
            for (int i = 0; i < JutsuBook.SLOTS; i++) {
                ResourceLocation id = book.slot(i);
                loadout.add(id == null ? "" : id.toString());
                cooldowns.add(id == null ? 0 : book.cooldown(id));
            }
            JutsuCaster.Casting casting = JutsuCaster.casting(player);
            int castingMax = casting == null ? 0 : casting.barMax();
            EyeData eyes = data.eyes();
            int nextXp = eyes.sharinganStage() > 0 && eyes.sharinganStage() < EyeData.MAX_STAGE ? Sharingan.STAGE_XP[eyes.sharinganStage() + 1] : 0;
            AwakeningData awakening = data.awakening();
            return new SyncPowers(data.identity().clanName(), data.identity().isCanon(), natures, affinities, traits, learned,
                    loadout, cooldowns, casting == null ? -1 : casting.slot(), casting == null ? 0 : casting.ticks(), castingMax,
                    casting != null && casting.def().cast() == com.noirm.jutsu.CastType.CHARGE,
                    eyes.sharinganStage(), eyes.isActive(), eyes.hasByakugan(), eyes.sharinganXp(), nextXp,
                    awakening.resolve(), awakening.lockedInTicks(), awakening.exhaustionTicks(),
                    data.biju().tails(), data.biju().cloakStage(), data.biju().cloakTicks(), data.biju().cooldownTicks(),
                    data.biju().cloakTails(), data.biju().unlockedTails(), data.biju().bond(),
                    com.noirm.dojutsu.SharinganAwakening.isDormant(data) ? Math.round(eyes.pressure()) : -1, eyes.drillXp(),
                    (eyes.hasMangekyo() ? MS : 0) | (eyes.isEternal() ? MS_ETERNAL : 0) | (eyes.isTransplanted() ? MS_TRANSPLANTED : 0)
                            | (eyes.isBlind() ? MS_BLIND : 0),
                    eyes.pattern(), eyes.donorPattern(),
                    eyes.left() == null ? -1 : eyes.left().ordinal(), eyes.right() == null ? -1 : eyes.right().ordinal(),
                    eyes.strain(), com.noirm.dojutsu.Susanoo.stage(player));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(clanName);
            buf.writeBoolean(canonClan);
            buf.writeCollection(natures, FriendlyByteBuf::writeVarInt);
            buf.writeCollection(affinities, FriendlyByteBuf::writeFloat);
            buf.writeCollection(traits, FriendlyByteBuf::writeVarInt);
            buf.writeCollection(learned, (b, e) -> e.encode(b));
            buf.writeCollection(loadout, FriendlyByteBuf::writeUtf);
            buf.writeCollection(cooldowns, FriendlyByteBuf::writeVarInt);
            buf.writeVarInt(castingSlot + 1);
            buf.writeVarInt(castingTicks);
            buf.writeVarInt(castingMax);
            buf.writeBoolean(castingIsCharge);
            buf.writeByte(sharinganStage);
            buf.writeBoolean(eyesActive);
            buf.writeBoolean(byakugan);
            buf.writeVarInt(sharinganXp);
            buf.writeVarInt(sharinganNextXp);
            buf.writeFloat(resolve);
            buf.writeVarInt(lockedInTicks);
            buf.writeVarInt(exhaustionTicks);
            buf.writeByte(tails);
            buf.writeByte(cloakStage);
            buf.writeVarInt(cloakTicks);
            buf.writeVarInt(cloakCooldown);
            buf.writeByte(cloakTails);
            buf.writeByte(unlockedTails);
            buf.writeVarInt(bond + 100);
            buf.writeVarInt(sharinganPressure + 1);
            buf.writeVarInt(sharinganDrillXp);
            buf.writeVarInt(mangekyoFlags);
            buf.writeByte(mangekyoPattern);
            buf.writeByte(mangekyoDonorPattern);
            buf.writeByte(mangekyoLeft);
            buf.writeByte(mangekyoRight);
            buf.writeFloat(mangekyoStrain);
            buf.writeByte(susanooStage);
        }

        public static SyncPowers decode(FriendlyByteBuf buf) {
            return new SyncPowers(buf.readUtf(64), buf.readBoolean(),
                    buf.readList(FriendlyByteBuf::readVarInt), buf.readList(FriendlyByteBuf::readFloat),
                    buf.readList(FriendlyByteBuf::readVarInt), buf.readList(JutsuEntry::decode),
                    buf.readList(b -> b.readUtf(256)), buf.readList(FriendlyByteBuf::readVarInt),
                    buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    buf.readByte(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                    buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                    buf.readByte(), buf.readByte(), buf.readVarInt(), buf.readVarInt(),
                    buf.readByte(), buf.readByte(), buf.readVarInt() - 100,
                    buf.readVarInt() - 1, buf.readVarInt(),
                    buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readFloat(), buf.readByte());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPowers.sync(this));
            context.get().setPacketHandled(true);
        }
    }

    /** How a player's powers look to everyone (red eyes, chakra cloak). */
    public record Visuals(int entityId, int sharinganStage, boolean byakugan, int cloakStage, int mangekyoLook) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeByte(sharinganStage);
            buf.writeBoolean(byakugan);
            buf.writeByte(cloakStage);
            buf.writeVarInt(mangekyoLook);
        }

        public static Visuals decode(FriendlyByteBuf buf) {
            return new Visuals(buf.readVarInt(), buf.readByte(), buf.readBoolean(), buf.readByte(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPowers.visuals(this));
            context.get().setPacketHandled(true);
        }
    }

    /** Sharingan precognition: {@code attackerId} will strike in about {@code ticks}. */
    public record Precognition(int attackerId, int ticks) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(attackerId);
            buf.writeVarInt(ticks);
        }

        public static Precognition decode(FriendlyByteBuf buf) {
            return new Precognition(buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPowers.precognition(this));
            context.get().setPacketHandled(true);
        }
    }
}

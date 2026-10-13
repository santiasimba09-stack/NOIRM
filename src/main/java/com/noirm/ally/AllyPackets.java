package com.noirm.ally;

import com.noirm.combat.CombatData;
import com.noirm.combat.CombatStyle;
import com.noirm.entity.SparringNinja;
import com.noirm.jutsu.JutsuData;
import com.noirm.jutsu.JutsuDefinition;
import com.noirm.network.NoirmNetwork;
import com.noirm.player.ShinobiCapability;
import com.noirm.training.TrainingSystem;
import com.noirm.training.TrainingSystem.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** What the ally screen shows (server → client) and what its buttons do (client → server). */
public final class AllyPackets {
    private AllyPackets() {}

    /** A jutsu you could teach: {@code blocker} is empty if you can, else why not (a translation key). */
    public record Teachable(ResourceLocation id, String rank, String blocker) {}

    public record Info(int entityId, float health, float maxHealth, float[] stats, float[] progress, int order,
                       String style, List<String> styles, String outfit, List<ResourceLocation> known, List<Teachable> teachable,
                       float respect, String trait, List<ResourceLocation> loadout, List<Integer> mastery) {

        public static Info of(ServerPlayer player, SparringNinja ally) {
            float[] stats = new float[Stat.values().length];
            float[] progress = new float[Stat.values().length];
            for (Stat stat : Stat.values()) {
                stats[stat.ordinal()] = ally.stat(stat);
                progress[stat.ordinal()] = Math.min(1f, ally.xp(stat) / TrainingSystem.xpToNext(ally.stat(stat)));
            }
            List<String> styles = new ArrayList<>();
            for (Map.Entry<ResourceLocation, CombatStyle> entry : CombatData.styles().entrySet()) {
                if (entry.getValue().playable() && entry.getValue().missingRequirement(ally.combatTaijutsu(), ally.byakuganActive()).isEmpty()) {
                    styles.add(entry.getKey().getPath());
                }
            }
            styles.sort(String::compareTo);
            List<Teachable> teachable = new ArrayList<>();
            ShinobiCapability.get(player).ifPresent(data -> {
                for (ResourceLocation id : data.jutsu().known()) {
                    if (ally.knownJutsu().contains(id)) {
                        continue;
                    }
                    JutsuDefinition def = JutsuData.get(id);
                    if (def == null) {
                        continue;
                    }
                    teachable.add(new Teachable(id, def.rank(), blocker(ally, def)));
                }
            });
            teachable.sort((a, b) -> a.id().toString().compareTo(b.id().toString()));
            List<ResourceLocation> known = List.copyOf(ally.knownJutsu());
            List<Integer> mastery = known.stream().map(ally::jutsuMastery).toList();
            return new Info(ally.getId(), ally.getHealth(), ally.getMaxHealth(), stats, progress, ally.order().ordinal(),
                    com.noirm.combat.CombatSystem.styleFor(ally).getPath(), styles, ally.outfit(), known, teachable,
                    ally.respect(), ally.trait().name(), List.copyOf(ally.loadout()), mastery);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeFloat(health);
            buf.writeFloat(maxHealth);
            for (int i = 0; i < stats.length; i++) {
                buf.writeFloat(stats[i]);
                buf.writeFloat(progress[i]);
            }
            buf.writeVarInt(order);
            buf.writeUtf(style, 64);
            buf.writeCollection(styles, (b, s) -> b.writeUtf(s, 64));
            buf.writeUtf(outfit, 64);
            buf.writeCollection(known, FriendlyByteBuf::writeResourceLocation);
            buf.writeCollection(teachable, (b, t) -> {
                b.writeResourceLocation(t.id());
                b.writeUtf(t.rank(), 4);
                b.writeUtf(t.blocker(), 128);
            });
            buf.writeFloat(respect);
            buf.writeUtf(trait, 16);
            buf.writeCollection(loadout, FriendlyByteBuf::writeResourceLocation);
            buf.writeCollection(mastery, FriendlyByteBuf::writeVarInt);
        }

        public static Info decode(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            float health = buf.readFloat();
            float max = buf.readFloat();
            int count = Stat.values().length;
            float[] stats = new float[count];
            float[] progress = new float[count];
            for (int i = 0; i < count; i++) {
                stats[i] = buf.readFloat();
                progress[i] = buf.readFloat();
            }
            int order = buf.readVarInt();
            String style = buf.readUtf(64);
            List<String> styles = buf.readList(b -> b.readUtf(64));
            String outfit = buf.readUtf(64);
            List<ResourceLocation> known = buf.readList(FriendlyByteBuf::readResourceLocation);
            List<Teachable> teachable = buf.readList(b -> new Teachable(b.readResourceLocation(), b.readUtf(4), b.readUtf(128)));
            float respect = buf.readFloat();
            String trait = buf.readUtf(16);
            List<ResourceLocation> loadout = buf.readList(FriendlyByteBuf::readResourceLocation);
            List<Integer> mastery = buf.readList(FriendlyByteBuf::readVarInt);
            return new Info(id, health, max, stats, progress, order, style, styles, outfit, known, teachable, respect, trait, loadout, mastery);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.noirm.client.AllyScreen.receive(this));
            context.get().setPacketHandled(true);
        }
    }

    /** Why {@code ally} can't learn {@code def} right now (a translation key), or "" if they can. */
    public static String blockerFor(SparringNinja ally, JutsuDefinition def) {
        return blocker(ally, def);
    }

    static String blocker(SparringNinja ally, JutsuDefinition def) {
        if (def.requires().isPresent()) {
            return "screen.noirm.ally.teach.eyes"; // dōjutsu and beast techniques can't be taught
        }
        if (def.nature().isPresent() && ally.jutsuAffinity(def.nature().get()) <= 0f) {
            return "screen.noirm.ally.teach.nature";
        }
        if (ally.combatReserves() < AllyTraining.reservesFor(def.rank())) {
            return "screen.noirm.ally.teach.reserves";
        }
        return "";
    }

    public enum Action { ORDER, STYLE, OUTFIT_NEXT, OUTFIT_PREV, TEACH, DISMISS, EQUIP }

    public record Command(int entityId, Action action, String arg) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeEnum(action);
            buf.writeUtf(arg, 128);
        }

        public static Command decode(FriendlyByteBuf buf) {
            return new Command(buf.readVarInt(), buf.readEnum(Action.class), buf.readUtf(128));
        }

        /** Runs on the server thread (registered with consumerMainThread). */
        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player == null || !(player.level().getEntity(entityId) instanceof SparringNinja ally)
                    || !player.getUUID().equals(ally.leaderId()) || player.distanceTo(ally) > 10) {
                return;
            }
            apply(player, ally, action, arg);
            if (ally.isAlive() && ally.leaderId() != null) {
                NoirmNetwork.sendToPlayer(player, Info.of(player, ally));
            }
        }
    }

    /** Carries out a button press (public for tests). */
    public static void apply(ServerPlayer player, SparringNinja ally, Action action, String arg) {
        switch (action) {
            case ORDER -> {
                if (!AllyMind.obeys(ally)) {
                    player.displayClientMessage(Component.translatable("message.noirm.ally.refuses", ally.getDisplayName()), true);
                    return;
                }
                try {
                    ally.setOrder(Order.valueOf(arg));
                } catch (IllegalArgumentException ignored) {
                    // unknown order: nothing
                }
            }
            case EQUIP -> {
                ResourceLocation id = ResourceLocation.tryParse(arg);
                if (id != null) {
                    ally.toggleLoadout(id);
                }
            }
            case STYLE -> {
                ResourceLocation id = ResourceLocation.tryParse(com.noirm.Noirm.MOD_ID + ":" + arg);
                CombatStyle style = id == null ? null : CombatData.style(id);
                if (style != null && style.playable() && style.missingRequirement(ally.combatTaijutsu(), ally.byakuganActive()).isEmpty()) {
                    ally.setCombatStyle(id);
                }
            }
            case OUTFIT_NEXT, OUTFIT_PREV -> {
                // Step through the endless looks of their own clan (same seed, same look).
                int step = action == Action.OUTFIT_NEXT ? 1 : -1;
                ally.changeOutfit(com.noirm.entity.NinjaLook.cycle(ally.outfit(), step));
            }
            case TEACH -> {
                ResourceLocation id = ResourceLocation.tryParse(arg);
                JutsuDefinition def = id == null ? null : JutsuData.get(id);
                boolean knows = ShinobiCapability.get(player).map(d -> id != null && d.jutsu().knows(id)).orElse(false);
                if (def != null && knows && blocker(ally, def).isEmpty()) {
                    ally.teach(id);
                    AllyMind.respect(ally, 5f, "message.noirm.ally.grateful");
                    player.sendSystemMessage(Component.translatable("message.noirm.ally.taught", ally.getDisplayName(),
                            Component.translatable(com.noirm.dojutsu.Sharingan.jutsuKey(id))).withStyle(ChatFormatting.AQUA));
                }
            }
            case DISMISS -> {
                ShinobiCapability.get(player).ifPresent(d -> d.crew().remove(ally.getUUID()));
                ally.leaveCrew();
                player.closeContainer();
                player.sendSystemMessage(Component.translatable("message.noirm.ally.dismissed", ally.getDisplayName()).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}

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

    /** How a nature looks from the ally screen's Natures tab (one per {@link com.noirm.clan.Nature}). */
    public static final int NATURE_OWNED = 0, NATURE_LEARNING = 1, NATURE_TEACHABLE = 2, NATURE_NEEDS_PARTS = 3,
            NATURE_YOU_LACK = 4, NATURE_BUSY = 5, NATURE_QUEUED = 6;
    /** Lesson pace flags (bits of {@link Lesson#flags}). */
    public static final int PACE_STUDYING = 1, PACE_NEAR = 2, PACE_FIGHTING = 4, PACE_MASTERED = 8, PACE_TEACHER_HAS = 16, PACE_TOGETHER = 32;

    /**
     * The extra half of the screen's data (round 6): clan, chakra, the unarmed style and the
     * weapon's moveset, tactics, natures and the nature lesson.
     */
    public record Extra(String clan, float chakra, float maxChakra, String weaponStyle, int range, int jutsuUse,
                        byte[] natureStatus, float[] natureAffinity, Lesson lesson, int occupation, int behaviour,
                        byte[] queue, int pendingJutsu, float natureEnergy, int carried) {
        static Extra of(ServerPlayer player, SparringNinja ally) {
            com.noirm.clan.Nature[] all = com.noirm.clan.Nature.values();
            byte[] status = new byte[all.length];
            float[] affinity = new float[all.length];
            com.noirm.clan.Nature current = ally.allyState().lesson();
            AllyState state = ally.allyState();
            for (com.noirm.clan.Nature nature : all) {
                affinity[nature.ordinal()] = ally.jutsuAffinity(nature);
                String why = current == null ? NatureTeaching.blocker(player, ally, nature) : NatureTeaching.queueBlocker(player, ally, nature);
                status[nature.ordinal()] = (byte) (nature == current ? NATURE_LEARNING : state.queue().contains(nature) ? NATURE_QUEUED : switch (why) {
                    case "" -> NATURE_TEACHABLE;
                    case "screen.noirm.ally.nature.why.owned" -> NATURE_OWNED;
                    case "screen.noirm.ally.nature.why.parts" -> NATURE_NEEDS_PARTS;
                    case "screen.noirm.ally.nature.why.busy" -> NATURE_BUSY;
                    default -> NATURE_YOU_LACK;
                });
            }
            NatureTeaching.Pace pace = NatureTeaching.pace(ally, player);
            int flags = (pace.studying() ? PACE_STUDYING : 0) | (pace.near() ? PACE_NEAR : 0) | (pace.fighting() ? PACE_FIGHTING : 0)
                    | (pace.mastered() ? PACE_MASTERED : 0) | (pace.teacherHas() ? PACE_TEACHER_HAS : 0) | (pace.together() ? PACE_TOGETHER : 0);
            Lesson lesson = current == null ? Lesson.NONE
                    : new Lesson(current.ordinal(), ally.allyState().lessonSeconds(), NatureTeaching.secondsNeeded(current), pace.rate(), flags);
            String clan = player.getServer() == null ? "" : com.noirm.clan.WorldClans.get(player.getServer()).find(ally.clanId())
                    .map(com.noirm.clan.Clan::name).orElse("");
            ResourceLocation weapon = com.noirm.weapon.WeaponMoves.styleFor(ally.getMainHandItem());
            byte[] queue = new byte[state.queue().size()];
            for (int i = 0; i < queue.length; i++) {
                queue[i] = (byte) state.queue().get(i).ordinal();
            }
            int carried = state.carried().stream().mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
            return new Extra(clan, ally.chakra(), ally.maxChakra(), weapon == null ? "" : weapon.getPath(),
                    state.range().ordinal(), state.jutsuUse().ordinal(), status, affinity, lesson, state.occupation().ordinal(),
                    state.behaviour().ordinal(), queue, state.pendingJutsu().size(), state.natureEnergy(), carried);
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(clan, 64);
            buf.writeFloat(chakra);
            buf.writeFloat(maxChakra);
            buf.writeUtf(weaponStyle, 64);
            buf.writeVarInt(range);
            buf.writeVarInt(jutsuUse);
            buf.writeByteArray(natureStatus);
            buf.writeVarInt(natureAffinity.length);
            for (float f : natureAffinity) {
                buf.writeFloat(f);
            }
            buf.writeVarInt(lesson.nature() + 1);
            buf.writeFloat(lesson.seconds());
            buf.writeFloat(lesson.needed());
            buf.writeFloat(lesson.rate());
            buf.writeVarInt(lesson.flags());
            buf.writeVarInt(occupation);
            buf.writeVarInt(behaviour);
            buf.writeByteArray(queue);
            buf.writeVarInt(pendingJutsu);
            buf.writeFloat(natureEnergy);
            buf.writeVarInt(carried);
        }

        static Extra decode(FriendlyByteBuf buf) {
            String clan = buf.readUtf(64);
            float chakra = buf.readFloat();
            float max = buf.readFloat();
            String weapon = buf.readUtf(64);
            int range = buf.readVarInt();
            int use = buf.readVarInt();
            byte[] status = buf.readByteArray(64);
            float[] affinity = new float[Math.min(64, buf.readVarInt())];
            for (int i = 0; i < affinity.length; i++) {
                affinity[i] = buf.readFloat();
            }
            Lesson lesson = new Lesson(buf.readVarInt() - 1, buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
            return new Extra(clan, chakra, max, weapon, range, use, status, affinity, lesson, buf.readVarInt(), buf.readVarInt(),
                    buf.readByteArray(64), buf.readVarInt(), buf.readFloat(), buf.readVarInt());
        }
    }

    /** The nature lesson: which nature (-1 = none), seconds done and needed, the current pace and why ({@code PACE_*} bits). */
    public record Lesson(int nature, float seconds, float needed, float rate, int flags) {
        public static final Lesson NONE = new Lesson(-1, 0f, 1f, 0f, 0);

        public boolean has(int flag) {
            return (flags & flag) != 0;
        }
    }

    public record Info(int entityId, float health, float maxHealth, float[] stats, float[] progress, int order,
                       String style, List<String> styles, String outfit, List<ResourceLocation> known, List<Teachable> teachable,
                       float respect, String trait, List<ResourceLocation> loadout, List<Integer> mastery, Extra extra) {

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
                    teachable.add(new Teachable(id, def.rank(), teachBlocker(player, ally, id, def)));
                }
            });
            // Ones they can learn right now first, then the rest (each alphabetically).
            teachable.sort(java.util.Comparator.comparing((Teachable t) -> !t.blocker().isEmpty()).thenComparing(t -> t.id().toString()));
            List<ResourceLocation> known = List.copyOf(ally.knownJutsu());
            List<Integer> mastery = known.stream().map(ally::jutsuMastery).toList();
            ResourceLocation own = ally.combatStyle() == null ? com.noirm.combat.CombatData.DEFAULT_STYLE : ally.combatStyle();
            return new Info(ally.getId(), ally.getHealth(), ally.getMaxHealth(), stats, progress, ally.order().ordinal(),
                    own.getPath(), styles, ally.outfit(), known, teachable,
                    ally.respect(), ally.trait().name(), List.copyOf(ally.loadout()), mastery, Extra.of(player, ally));
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
            extra.encode(buf);
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
            return new Info(id, health, max, stats, progress, order, style, styles, outfit, known, teachable, respect, trait, loadout,
                    mastery, Extra.decode(buf));
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

    /** A jutsu they can learn once you've taught them its nature (the button teaches both). */
    public static final String VIA_NATURE = "screen.noirm.ally.teach.via_nature";
    public static final String PENDING = "screen.noirm.ally.teach.pending";

    /** What the Teach button does for this jutsu: "" teaches it now, {@link #VIA_NATURE} queues its nature first, else why not. */
    static String teachBlocker(ServerPlayer player, SparringNinja ally, ResourceLocation id, JutsuDefinition def) {
        if (ally.allyState().pendingJutsu().contains(id)) {
            return PENDING;
        }
        String why = blocker(ally, def);
        if (why.equals("screen.noirm.ally.teach.nature") && def.nature().isPresent()) {
            com.noirm.clan.Nature nature = def.nature().get();
            AllyState state = ally.allyState();
            boolean coming = state.lesson() == nature || state.queue().contains(nature);
            String natureWhy = state.lesson() == null ? NatureTeaching.blocker(player, ally, nature) : NatureTeaching.queueBlocker(player, ally, nature);
            if (coming || natureWhy.isEmpty()) {
                return VIA_NATURE;
            }
        }
        return why;
    }

    /**
     * Teaches {@code id} if they can learn it now; if they lack its nature but you can teach it,
     * starts (or queues) that nature's lesson and keeps the jutsu for when it's done. Returns true
     * if something happened.
     */
    public static boolean teachOrQueue(ServerPlayer player, SparringNinja ally, ResourceLocation id) {
        JutsuDefinition def = JutsuData.get(id);
        boolean knows = ShinobiCapability.get(player).map(d -> d.jutsu().knows(id)).orElse(false);
        if (def == null || !knows || ally.knownJutsu().contains(id)) {
            return false;
        }
        String why = teachBlocker(player, ally, id, def);
        if (why.isEmpty()) {
            ally.teach(id);
            AllyMind.respect(ally, 5f, "message.noirm.ally.grateful");
            player.sendSystemMessage(Component.translatable("message.noirm.ally.taught", ally.getDisplayName(),
                    Component.translatable(com.noirm.dojutsu.Sharingan.jutsuKey(id))).withStyle(ChatFormatting.AQUA));
            return true;
        }
        if (why.equals(VIA_NATURE)) {
            com.noirm.clan.Nature nature = def.nature().orElseThrow();
            AllyState state = ally.allyState();
            if (state.lesson() != nature && !state.queue().contains(nature)) {
                NatureTeaching.start(player, ally, nature);
            }
            if (!state.pendingJutsu().contains(id)) {
                state.pendingJutsu().add(id);
            }
            player.displayClientMessage(Component.translatable("message.noirm.ally.teach.after_nature", ally.getDisplayName(),
                    Component.translatable(com.noirm.dojutsu.Sharingan.jutsuKey(id)), Component.translatable(nature.translationKey())), true);
            return true;
        }
        return false;
    }

    /** Teaches everything you can teach them now (and queues natures for the rest that need one). Returns how many. */
    public static int teachAll(ServerPlayer player, SparringNinja ally) {
        int count = 0;
        List<ResourceLocation> yours = ShinobiCapability.get(player).map(d -> List.copyOf(d.jutsu().known())).orElse(List.of());
        for (ResourceLocation id : yours.stream().sorted().toList()) {
            if (!ally.knownJutsu().contains(id) && teachOrQueue(player, ally, id)) {
                count++;
            }
        }
        return count;
    }

    static String blocker(SparringNinja ally, JutsuDefinition def) {
        if (com.noirm.master.MasterData.taughtByMaster(def)) {
            return "screen.noirm.ally.teach.master"; // the Gates and their techniques: only a master teaches those
        }
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

    public enum Action { ORDER, STYLE, OUTFIT_NEXT, OUTFIT_PREV, TEACH, DISMISS, EQUIP, NATURE_TEACH, NATURE_STOP, RANGE, JUTSU_USE,
        OCCUPATION, BEHAVIOUR, TEACH_ALL, NATURE_DEQUEUE, COME, FALL_BACK }

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
                    CrewBrain.giveOrder(ally, Order.valueOf(arg), player);
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
                if (id != null) {
                    teachOrQueue(player, ally, id);
                }
            }
            case TEACH_ALL -> {
                int count = teachAll(player, ally);
                player.displayClientMessage(Component.translatable("message.noirm.ally.teach.all", ally.getDisplayName(), count), true);
            }
            case NATURE_DEQUEUE -> {
                com.noirm.clan.Nature nature = AllyState.nature(arg);
                if (nature != null) {
                    NatureTeaching.dequeue(ally, nature);
                }
            }
            case OCCUPATION -> {
                for (Occupation job : Occupation.values()) {
                    if (job.name().equals(arg)) {
                        ally.allyState().setOccupation(job);
                    }
                }
            }
            case BEHAVIOUR -> {
                for (Behaviour behaviour : Behaviour.values()) {
                    if (behaviour.name().equals(arg)) {
                        ally.allyState().setBehaviour(behaviour);
                    }
                }
            }
            case COME -> CrewBrain.come(ally, player);
            case FALL_BACK -> CrewBrain.fallBack(ally, player);
            case NATURE_TEACH -> {
                com.noirm.clan.Nature nature = AllyState.nature(arg);
                if (nature != null) {
                    NatureTeaching.start(player, ally, nature);
                }
            }
            case NATURE_STOP -> NatureTeaching.stop(ally);
            case RANGE -> {
                for (Tactics.Range range : Tactics.Range.values()) {
                    if (range.name().equals(arg)) {
                        ally.allyState().setRange(range);
                    }
                }
            }
            case JUTSU_USE -> {
                for (Tactics.JutsuUse use : Tactics.JutsuUse.values()) {
                    if (use.name().equals(arg)) {
                        ally.allyState().setJutsuUse(use);
                    }
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

package com.noirm.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.noirm.Noirm;
import com.noirm.awakening.AwakeningSystem;
import com.noirm.biju.BijuData;
import com.noirm.biju.BijuSystem;
import com.noirm.clan.Clan;
import com.noirm.clan.ClanSystem;
import com.noirm.clan.Nature;
import com.noirm.clan.WorldClans;
import com.noirm.dojutsu.EyeData;
import com.noirm.dojutsu.Sharingan;
import com.noirm.jutsu.JutsuBook;
import com.noirm.jutsu.JutsuData;
import com.noirm.player.PowerVisuals;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Locale;

/**
 * {@code /noirm} commands for powers, to test everything without waiting for the right clan:
 * <ul>
 *   <li>{@code /noirm clan} — your clan · {@code clan list} — clans known in this world</li>
 *   <li>{@code /noirm clan set <id>} · {@code clan reroll} · {@code clan new} (cheats)</li>
 *   <li>{@code /noirm nature add <nature>} — learn a nature as if by training (cheats)</li>
 *   <li>{@code /noirm jutsu list} · {@code jutsu learn <id>|all} · {@code jutsu equip <slot> <id>} (cheats)</li>
 *   <li>{@code /noirm sharingan <0-3>} · {@code resolve <0-100>} · {@code awaken} (cheats)</li>
 *   <li>{@code /noirm biju seal <tails>} · {@code biju release} (cheats)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class PowersCommand {
    private static final int CHEATS = 2;

    private static final SuggestionProvider<CommandSourceStack> CLANS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(WorldClans.get(ctx.getSource().getServer()).allClans().stream().map(Clan::id), builder);
    private static final SuggestionProvider<CommandSourceStack> JUTSU = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(JutsuData.all().keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> NATURES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Nature.values()).map(Nature::getSerializedName), builder);

    private PowersCommand() {}

    /** {@code /noirm beast}: your beast, your bond, the tails it lets you draw, what it wants. */
    private static int beastStatus(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var data = com.noirm.player.ShinobiCapability.get(player).orElse(null);
        if (data == null || !data.biju().isJinchuriki()) {
            source.sendFailure(Component.translatable("message.noirm.biju.none"));
            return 0;
        }
        var biju = data.biju();
        source.sendSuccess(() -> Component.translatable("commands.noirm.beast.status", com.noirm.biju.BijuSystem.beastName(biju.tails()),
                biju.bond(), biju.unlockedTails(), biju.tails(), com.noirm.biju.BeastBond.safeTails(biju.bond())), false);
        var request = biju.request();
        if (request != null) {
            long left = Math.max(0, request.deadline() - player.level().getGameTime()) / 20;
            source.sendSuccess(() -> Component.translatable("commands.noirm.beast.request",
                    Component.translatable("commands.noirm.beast.kind." + request.kind().name().toLowerCase(java.util.Locale.ROOT)),
                    request.progress(), request.goal(), left), false);
        }
        return 1;
    }

    private static int setBeast(CommandSourceStack source, int bond, int tails) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        com.noirm.player.ShinobiCapability.get(player).ifPresent(data -> {
            if (bond != Integer.MIN_VALUE) {
                data.biju().setBond(bond);
            }
            if (tails > 0) {
                data.biju().setUnlockedTails(tails);
            }
            data.markPowersDirty();
        });
        return beastStatus(source);
    }

    /** {@code /noirm ninja <clan> [tails] [strength]}: a ninja of exactly that clan, in front of you. */
    private static int spawnNinja(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String clanId, int tails, int strength)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        var clan = WorldClans.get(source.getServer()).find(clanId).orElse(null);
        if (clan == null) {
            source.sendFailure(Component.translatable("commands.noirm.clan.unknown", clanId));
            return 0;
        }
        net.minecraft.server.level.ServerLevel level = source.getLevel();
        var ninja = com.noirm.registry.NoirmEntities.SPARRING_NINJA.get().create(level);
        if (ninja == null) {
            return 0;
        }
        net.minecraft.world.phys.Vec3 at = source.getPosition();
        if (source.getEntity() != null) {
            net.minecraft.world.phys.Vec3 look = source.getEntity().getViewVector(1f);
            at = at.add(look.x * 3, 0, look.z * 3);
        }
        ninja.moveTo(at.x, at.y, at.z, source.getRotation().y + 180f, 0f);
        ninja.finalizeSpawn(level, level.getCurrentDifficultyAt(ninja.blockPosition()), net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
        ninja.joinClan(clan);
        ninja.sealBeast(tails); // 0 = no beast: exactly what you asked for
        if (strength > 0) {
            ninja.setCombatStats(strength, strength);
        }
        level.addFreshEntity(ninja);
        source.sendSuccess(() -> Component.translatable("commands.noirm.ninja.spawned", clan.name()), true);
        return 1;
    }

    /** {@code /noirm village <size> [look]}: builds a whole village right here, on the spot (no site checks). */
    private static int buildVillage(CommandSourceStack source, String size, String look) {
        com.noirm.world.village.VillageKind kind;
        com.noirm.world.village.VillagePalette palette;
        try {
            kind = com.noirm.world.village.VillageKind.valueOf(size.toUpperCase(Locale.ROOT));
            palette = look.isEmpty()
                    ? com.noirm.world.village.VillagePalette.forBiome(source.getLevel().getBiome(net.minecraft.core.BlockPos.containing(source.getPosition())))
                    : com.noirm.world.village.VillagePalette.valueOf(look.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable("commands.noirm.village.unknown"));
            return 0;
        }
        // The village floor is the ground under you (even if you're flying).
        net.minecraft.core.BlockPos centre = source.getLevel().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.core.BlockPos.containing(source.getPosition()));
        long seed = source.getLevel().getRandom().nextLong();
        String sizeName = kind.getSerializedName();
        String lookName = palette.name().toLowerCase(Locale.ROOT);
        // Built a few chunks per tick (a hidden village is ~1,000 chunks), so tell the player when it's done.
        var job = com.noirm.world.village.VillageBuilder.buildNow(source.getLevel(), centre, kind, palette, seed,
                done -> source.sendSuccess(() -> Component.translatable("commands.noirm.village.built", sizeName, lookName), true));
        source.sendSuccess(() -> Component.translatableWithFallback("commands.noirm.village.building",
                "Building a %s (%s look) here: %s chunks, a few every tick...", sizeName, lookName, job.chunks()), true);
        return 1;
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm")
                .then(Commands.literal("village").requires(s -> s.hasPermission(CHEATS))
                        .then(Commands.argument("size", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[] {"capital", "town", "outpost"}, builder))
                                .executes(ctx -> buildVillage(ctx.getSource(), StringArgumentType.getString(ctx, "size"), ""))
                                .then(Commands.argument("look", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[] {"leaf", "sand", "snow"}, builder))
                                        .executes(ctx -> buildVillage(ctx.getSource(), StringArgumentType.getString(ctx, "size"),
                                                StringArgumentType.getString(ctx, "look"))))))
                .then(Commands.literal("ninja").requires(s -> s.hasPermission(CHEATS))
                        .then(Commands.argument("clan", StringArgumentType.word()).suggests(CLANS)
                                .executes(ctx -> spawnNinja(ctx, StringArgumentType.getString(ctx, "clan"), 0, 0))
                                .then(Commands.argument("tails", IntegerArgumentType.integer(0, BijuData.MAX_TAILS))
                                        .executes(ctx -> spawnNinja(ctx, StringArgumentType.getString(ctx, "clan"), IntegerArgumentType.getInteger(ctx, "tails"), 0))
                                        .then(Commands.argument("strength", IntegerArgumentType.integer(1, 10000))
                                                .executes(ctx -> spawnNinja(ctx, StringArgumentType.getString(ctx, "clan"),
                                                        IntegerArgumentType.getInteger(ctx, "tails"), IntegerArgumentType.getInteger(ctx, "strength")))))))
                .then(Commands.literal("clan")
                        .executes(ctx -> showClan(ctx.getSource()))
                        .then(Commands.literal("list").executes(ctx -> listClans(ctx.getSource())))
                        .then(Commands.literal("set").requires(s -> s.hasPermission(CHEATS))
                                .then(Commands.argument("clan", StringArgumentType.word()).suggests(CLANS)
                                        .executes(ctx -> setClan(ctx, StringArgumentType.getString(ctx, "clan")))))
                        .then(Commands.literal("reroll").requires(s -> s.hasPermission(CHEATS))
                                .executes(ctx -> assign(ctx.getSource(), WorldClans.get(ctx.getSource().getServer())
                                        .rollForNewcomer(ctx.getSource().getPlayerOrException().getRandom()))))
                        .then(Commands.literal("new").requires(s -> s.hasPermission(CHEATS))
                                .executes(ctx -> assign(ctx.getSource(), WorldClans.get(ctx.getSource().getServer())
                                        .generate(ctx.getSource().getPlayerOrException().getRandom())))))
                .then(Commands.literal("nature").requires(s -> s.hasPermission(CHEATS))
                        .then(Commands.literal("add").then(Commands.argument("nature", StringArgumentType.word()).suggests(NATURES)
                                .executes(ctx -> addNature(ctx, StringArgumentType.getString(ctx, "nature"))))))
                .then(Commands.literal("jutsu")
                        .then(Commands.literal("list").executes(ctx -> listJutsu(ctx.getSource())))
                        .then(Commands.literal("learn").requires(s -> s.hasPermission(CHEATS))
                                .then(Commands.literal("all").executes(ctx -> learnAll(ctx.getSource())))
                                .then(Commands.argument("jutsu", ResourceLocationArgument.id()).suggests(JUTSU)
                                        .executes(ctx -> learn(ctx.getSource(), ResourceLocationArgument.getId(ctx, "jutsu")))))
                        .then(Commands.literal("mastery").requires(s -> s.hasPermission(CHEATS))
                                .then(Commands.argument("jutsu", ResourceLocationArgument.id()).suggests(JUTSU)
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0, JutsuBook.MAX_MASTERY))
                                                .executes(ctx -> setMastery(ctx.getSource(), ResourceLocationArgument.getId(ctx, "jutsu"),
                                                        IntegerArgumentType.getInteger(ctx, "level"))))))
                        .then(Commands.literal("equip").requires(s -> s.hasPermission(CHEATS))
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, JutsuBook.SLOTS))
                                        .then(Commands.argument("jutsu", ResourceLocationArgument.id()).suggests(JUTSU)
                                                .executes(ctx -> equip(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot") - 1,
                                                        ResourceLocationArgument.getId(ctx, "jutsu")))))))
                .then(Commands.literal("sharingan").requires(s -> s.hasPermission(CHEATS))
                        .then(Commands.argument("tomoe", IntegerArgumentType.integer(0, EyeData.MAX_STAGE))
                                .executes(ctx -> setSharingan(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "tomoe")))))
                .then(Commands.literal("byakugan").requires(s -> s.hasPermission(CHEATS))
                        .executes(ctx -> setByakugan(ctx.getSource(), true))
                        .then(Commands.literal("remove").executes(ctx -> setByakugan(ctx.getSource(), false))))
                .then(Commands.literal("resolve").requires(s -> s.hasPermission(CHEATS))
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0, 100))
                                .executes(ctx -> setResolve(ctx.getSource(), FloatArgumentType.getFloat(ctx, "value")))))
                .then(Commands.literal("awaken").requires(s -> s.hasPermission(CHEATS))
                        .executes(ctx -> awaken(ctx.getSource())))
                .then(Commands.literal("beast").executes(ctx -> beastStatus(ctx.getSource()))
                        .then(Commands.literal("bond").requires(s -> s.hasPermission(CHEATS))
                                .then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
                                        .executes(ctx -> setBeast(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "value"), -1))))
                        .then(Commands.literal("tails").requires(s -> s.hasPermission(CHEATS))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, BijuData.MAX_TAILS))
                                        .executes(ctx -> setBeast(ctx.getSource(), Integer.MIN_VALUE, IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("biju").requires(s -> s.hasPermission(CHEATS))
                        .then(Commands.literal("seal").then(Commands.argument("tails", IntegerArgumentType.integer(1, BijuData.MAX_TAILS))
                                .executes(ctx -> seal(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "tails")))))
                        .then(Commands.literal("release").executes(ctx -> seal(ctx.getSource(), 0)))));
    }

    // ------------------------------------------------------------ clans

    private static int showClan(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = data(source);
        source.sendSuccess(() -> ClanSystem.describe(data.identity()), false);
        return 1;
    }

    private static int listClans(CommandSourceStack source) {
        MutableComponent text = Component.translatable("command.noirm.clan.list");
        for (Clan clan : WorldClans.get(source.getServer()).allClans()) {
            text.append("\n - ").append(Component.literal(clan.name()).withStyle(clan.canon() ? ChatFormatting.RED : ChatFormatting.GOLD))
                    .append(Component.literal(" (" + clan.id() + ")").withStyle(ChatFormatting.GRAY));
        }
        source.sendSuccess(() -> text, false);
        return 1;
    }

    private static int setClan(CommandContext<CommandSourceStack> ctx, String id) throws CommandSyntaxException {
        Clan clan = WorldClans.get(ctx.getSource().getServer()).find(id).orElse(null);
        if (clan == null) {
            ctx.getSource().sendFailure(Component.translatable("command.noirm.clan.unknown", id));
            return 0;
        }
        return assign(ctx.getSource(), clan);
    }

    private static int assign(CommandSourceStack source, Clan clan) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ShinobiData data = data(source);
        ClanSystem.assign(player, data, clan);
        PowerVisuals.broadcast(player);
        source.sendSuccess(() -> ClanSystem.describe(data.identity()), false);
        return 1;
    }

    private static int addNature(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        Nature nature = Arrays.stream(Nature.values()).filter(n -> n.getSerializedName().equals(name.toLowerCase(Locale.ROOT))).findFirst().orElse(null);
        if (nature == null) {
            ctx.getSource().sendFailure(Component.translatable("command.noirm.nature.unknown", name));
            return 0;
        }
        ShinobiData data = data(ctx.getSource());
        com.noirm.clan.NatureSystem.grant(data, nature, Clan.TRAINED_AFFINITY);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.noirm.clan.natures", ClanSystem.natures(data.identity())), false);
        return 1;
    }

    // ------------------------------------------------------------ jutsu

    private static int listJutsu(CommandSourceStack source) throws CommandSyntaxException {
        JutsuBook book = data(source).jutsu();
        MutableComponent text = Component.translatable("command.noirm.jutsu.list");
        for (ResourceLocation id : JutsuData.all().keySet().stream().sorted().toList()) {
            boolean known = book.knows(id);
            text.append("\n ").append(Component.literal(known ? "✔ " : "· ").withStyle(known ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(Sharingan.jutsuKey(id)))
                    .append(Component.literal(" " + id + (known ? " ★" + book.masteryLevel(id) : "")).withStyle(ChatFormatting.GRAY));
        }
        source.sendSuccess(() -> text, false);
        return 1;
    }

    private static int learn(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        if (JutsuData.get(id) == null) {
            source.sendFailure(Component.translatable("command.noirm.jutsu.unknown", id.toString()));
            return 0;
        }
        ShinobiData data = data(source);
        data.jutsu().learn(id, false);
        data.jutsu().equipIfRoom(id);
        data.markPowersDirty();
        source.sendSuccess(() -> Component.translatable("command.noirm.jutsu.learned", Component.translatable(Sharingan.jutsuKey(id))), false);
        return 1;
    }

    private static int learnAll(CommandSourceStack source) throws CommandSyntaxException {
        ShinobiData data = data(source);
        JutsuData.all().keySet().forEach(id -> data.jutsu().learn(id, false));
        data.markPowersDirty();
        source.sendSuccess(() -> Component.translatable("command.noirm.jutsu.learned_all", JutsuData.all().size()), false);
        return 1;
    }

    private static int equip(CommandSourceStack source, int slot, ResourceLocation id) throws CommandSyntaxException {
        ShinobiData data = data(source);
        if (!data.jutsu().equip(slot, id)) {
            source.sendFailure(Component.translatable("command.noirm.jutsu.not_known", id.toString()));
            return 0;
        }
        data.markPowersDirty();
        source.sendSuccess(() -> Component.translatable("command.noirm.jutsu.equipped", Component.translatable(Sharingan.jutsuKey(id)), slot + 1), false);
        return 1;
    }

    /** Sets a known jutsu's mastery (learning it first if needed), and applies what that unlocks. */
    private static int setMastery(CommandSourceStack source, ResourceLocation id, int level) throws CommandSyntaxException {
        if (JutsuData.get(id) == null) {
            source.sendFailure(Component.translatable("command.noirm.jutsu.unknown", id.toString()));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ShinobiData data = data(source);
        data.jutsu().learn(id, false);
        int xp = level <= 0 ? 0 : JutsuBook.xpForLevel(level);
        data.jutsu().addXp(id, xp - data.jutsu().xp(id));
        com.noirm.clan.NatureSystem.checkMasteryUnlocks(player, data, id);
        data.markPowersDirty();
        source.sendSuccess(() -> Component.translatable("command.noirm.jutsu.mastery", Component.translatable(Sharingan.jutsuKey(id)), level), false);
        return 1;
    }

    // ------------------------------------------------------------ eyes, awakening, biju

    private static int setSharingan(CommandSourceStack source, int tomoe) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ShinobiData data = data(source);
        data.eyes().setSharinganStage(0);
        data.eyes().setSharinganXp(0);
        for (int i = 0; i < tomoe; i++) {
            Sharingan.advance(player, data, "message.noirm.sharingan.tomoe_growth");
        }
        data.markPowersDirty();
        PowerVisuals.broadcast(player);
        return 1;
    }

    /** Gives (or takes away) the Byakugan and its two jutsu, whatever your clan. */
    private static int setByakugan(CommandSourceStack source, boolean give) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ShinobiData data = data(source);
        data.eyes().setByakugan(give);
        if (give) {
            for (net.minecraft.resources.ResourceLocation id : com.noirm.dojutsu.Byakugan.JUTSU) {
                if (JutsuData.get(id) != null && data.jutsu().learn(id, false)) {
                    data.jutsu().equipIfRoom(id);
                }
            }
        }
        data.markPowersDirty();
        PowerVisuals.broadcast(player);
        source.sendSuccess(() -> Component.translatable(give ? "command.noirm.byakugan.given" : "command.noirm.byakugan.removed"), false);
        return 1;
    }

    private static int setResolve(CommandSourceStack source, float value) throws CommandSyntaxException {
        ShinobiData data = data(source);
        data.awakening().setResolve(value);
        data.awakening().setLastAwakening(-1);
        data.markPowersDirty();
        source.sendSuccess(() -> Component.translatable("command.noirm.resolve", Math.round(value)), false);
        return 1;
    }

    private static int awaken(CommandSourceStack source) throws CommandSyntaxException {
        AwakeningSystem.awaken(source.getPlayerOrException(), data(source));
        return 1;
    }

    private static int seal(CommandSourceStack source, int tails) throws CommandSyntaxException {
        BijuSystem.seal(source.getPlayerOrException(), data(source), tails);
        source.sendSuccess(() -> tails == 0 ? Component.translatable("command.noirm.biju.released")
                : Component.translatable("command.noirm.biju.sealed", tails), false);
        return 1;
    }

    private static ShinobiData data(CommandSourceStack source) throws CommandSyntaxException {
        return ShinobiCapability.get(source.getPlayerOrException()).orElseThrow(() -> new IllegalStateException("No shinobi data"));
    }
}

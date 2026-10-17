package com.noirm.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.noirm.Noirm;
import com.noirm.ally.Crew;
import com.noirm.biju.BijuData;
import com.noirm.clan.Clan;
import com.noirm.clan.WorldClans;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.TailedBeast;
import com.noirm.entity.TrainingDummy;
import com.noirm.master.MasterData;
import com.noirm.master.MasterSystem;
import com.noirm.registry.NoirmEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /noirm spawn …}: everything the mod adds, three blocks in front of you (cheats).
 * <ul>
 *   <li>{@code ninja <clan> [tails] [strength]} — a ninja of exactly that clan</li>
 *   <li>{@code rogue [strength]} — a ninja of a random clan · {@code ally <clan>} — one who joins your crew</li>
 *   <li>{@code dummy} · {@code beast <tails>} · {@code master <id>}</li>
 * </ul>
 * {@code /noirm beast spawn <tails>} is the same as {@code spawn beast}.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class SpawnCommand {
    static final SuggestionProvider<CommandSourceStack> CLANS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(WorldClans.get(ctx.getSource().getServer()).allClans().stream().map(Clan::id), builder);
    static final SuggestionProvider<CommandSourceStack> MASTERS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Cmd.shortIds(MasterData.all().keySet()), builder);

    private SpawnCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var tails = IntegerArgumentType.integer(1, BijuData.MAX_TAILS);
        event.getDispatcher().register(Commands.literal("noirm")
                .then(Commands.literal("spawn").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.literal("ninja").then(Commands.argument("clan", StringArgumentType.word()).suggests(CLANS)
                                .executes(ctx -> ninja(ctx.getSource(), StringArgumentType.getString(ctx, "clan"), 0, 0, false))
                                .then(Commands.argument("tails", IntegerArgumentType.integer(0, BijuData.MAX_TAILS))
                                        .executes(ctx -> ninja(ctx.getSource(), StringArgumentType.getString(ctx, "clan"),
                                                IntegerArgumentType.getInteger(ctx, "tails"), 0, false))
                                        .then(Commands.argument("strength", IntegerArgumentType.integer(1, 10000))
                                                .executes(ctx -> ninja(ctx.getSource(), StringArgumentType.getString(ctx, "clan"),
                                                        IntegerArgumentType.getInteger(ctx, "tails"), IntegerArgumentType.getInteger(ctx, "strength"), false))))))
                        .then(Commands.literal("rogue")
                                .executes(ctx -> ninja(ctx.getSource(), "", 0, 0, false))
                                .then(Commands.argument("strength", IntegerArgumentType.integer(1, 10000))
                                        .executes(ctx -> ninja(ctx.getSource(), "", 0, IntegerArgumentType.getInteger(ctx, "strength"), false))))
                        .then(Commands.literal("ally").then(Commands.argument("clan", StringArgumentType.word()).suggests(CLANS)
                                .executes(ctx -> ninja(ctx.getSource(), StringArgumentType.getString(ctx, "clan"), 0, 0, true))))
                        .then(Commands.literal("dummy").executes(ctx -> dummy(ctx.getSource())))
                        .then(Commands.literal("beast").then(Commands.argument("tails", tails)
                                .executes(ctx -> beast(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "tails")))))
                        .then(Commands.literal("master").then(Commands.argument("master", StringArgumentType.greedyString()).suggests(MASTERS)
                                .executes(ctx -> master(ctx.getSource(), StringArgumentType.getString(ctx, "master"))))))
                .then(Commands.literal("beast").then(Commands.literal("spawn").requires(s -> s.hasPermission(Cmd.CHEATS))
                        .then(Commands.argument("tails", tails)
                                .executes(ctx -> beast(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "tails")))))));
    }

    /** Places {@code mob} in front of the command's user, facing them, and adds it to the world. */
    private static <T extends Mob> T place(CommandSourceStack source, T mob, double distance) {
        ServerLevel level = source.getLevel();
        Vec3 at = Cmd.inFront(source, distance);
        mob.moveTo(at.x, at.y, at.z, source.getRotation().y + 180f, 0f);
        mob.setYHeadRot(mob.getYRot());
        mob.setYBodyRot(mob.getYRot());
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.COMMAND, null, null);
        return mob;
    }

    /** A ninja of {@code clanId} (or a random clan if empty); with {@code ally}, it joins your crew. */
    static int ninja(CommandSourceStack source, String clanId, int tails, int strength, boolean ally) throws CommandSyntaxException {
        WorldClans clans = WorldClans.get(source.getServer());
        ServerLevel level = source.getLevel();
        Clan clan = clanId.isEmpty() ? clans.rollForNewcomer(level.getRandom()) : clans.find(clanId).orElse(null);
        if (clan == null) {
            return Cmd.fail(source, "commands.noirm.clan.unknown", clanId);
        }
        SparringNinja ninja = NoirmEntities.SPARRING_NINJA.get().create(level);
        if (ninja == null) {
            return 0;
        }
        place(source, ninja, 3);
        ninja.joinClan(clan);
        ninja.sealBeast(tails);
        if (strength > 0) {
            ninja.setCombatStats(strength, strength);
        }
        level.addFreshEntity(ninja);
        if (ally) {
            return Crew.recruit(Cmd.player(source), ninja) ? 1 : 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.noirm.ninja.spawned", clan.name()), true);
        return 1;
    }

    static int dummy(CommandSourceStack source) {
        TrainingDummy dummy = NoirmEntities.TRAINING_DUMMY.get().create(source.getLevel());
        if (dummy == null) {
            return 0;
        }
        source.getLevel().addFreshEntity(place(source, dummy, 2.5));
        return Cmd.ok(source, "command.noirm.spawn.spawned", dummy.getDisplayName());
    }

    static int beast(CommandSourceStack source, int tails) {
        TailedBeast beast = NoirmEntities.TAILED_BEAST.get().create(source.getLevel());
        if (beast == null) {
            return 0;
        }
        place(source, beast, 6 + tails);
        beast.setTails(tails);
        source.getLevel().addFreshEntity(beast);
        return Cmd.ok(source, "command.noirm.spawn.spawned", beast.getDisplayName());
    }

    static int master(CommandSourceStack source, String name) {
        ResourceLocation id = Cmd.id(name);
        if (id == null || MasterData.get(id) == null) {
            return Cmd.fail(source, "commands.noirm.master.unknown", name);
        }
        var master = MasterSystem.spawn(source.getLevel(), id, Cmd.inFront(source, 3), source.getRotation().y + 180f);
        return master == null ? 0 : Cmd.ok(source, "command.noirm.spawn.spawned", master.getDisplayName());
    }
}

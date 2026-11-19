package com.noirm.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.Noirm;
import com.noirm.clan.WorldClans;
import com.noirm.entity.SparringNinja;
import com.noirm.genjutsu.GenjutsuMinds;
import com.noirm.genjutsu.GenjutsuScene;
import com.noirm.genjutsu.GenjutsuScenes;
import com.noirm.hiraishin.HiraishinItems;
import com.noirm.hiraishin.HiraishinLearning;
import com.noirm.hiraishin.HiraishinMark;
import com.noirm.hiraishin.HiraishinMarking;
import com.noirm.hiraishin.HiraishinMarks;
import com.noirm.registry.NoirmEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.List;

/**
 * Space-time and genjutsu commands ({@code /noirm help spacetime}):
 * <ul>
 *   <li>{@code /noirm hiraishin}: where you are on the road to the Flying Raijin;
 *       {@code marks}: your marks; {@code clear}: wipe them;</li>
 *   <li>{@code /noirm hiraishin give} (cheats): learn the formula and the Flying Raijin, and 16 marked kunai;
 *       {@code legend} (cheats): a legend who fights with it, in front of you;</li>
 *   <li>{@code /noirm genjutsu list}; {@code /noirm genjutsu test <scene> [targets]} (cheats): put yourself
 *       (or creatures) in a genjutsu scene, cast by whoever stands nearest.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class SpaceTimeCommand {
    private SpaceTimeCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm")
                .then(Commands.literal("hiraishin")
                        .executes(ctx -> info(ctx.getSource()))
                        .then(Commands.literal("marks").executes(ctx -> marks(ctx.getSource())))
                        .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())))
                        .then(Commands.literal("give").requires(s -> s.hasPermission(2)).executes(ctx -> give(ctx.getSource())))
                        .then(Commands.literal("legend").requires(s -> s.hasPermission(2)).executes(ctx -> legend(ctx.getSource()))))
                .then(Commands.literal("genjutsu")
                        .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("test").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("scene", ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(GenjutsuScenes.all().keySet(), b))
                                        .executes(ctx -> test(ctx.getSource(), ResourceLocationArgument.getId(ctx, "scene"), null))
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .executes(ctx -> test(ctx.getSource(), ResourceLocationArgument.getId(ctx, "scene"),
                                                        EntityArgument.getEntities(ctx, "targets"))))))));
    }

    private static int info(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MutableComponent text = Component.translatable("command.noirm.hiraishin.title").withStyle(ChatFormatting.GOLD);
        if (HiraishinLearning.knowsRaijin(player)) {
            text.append("\n ").append(Component.translatable("command.noirm.hiraishin.knows_raijin",
                    HiraishinLearning.stars(player, HiraishinLearning.RAIJIN)).withStyle(ChatFormatting.YELLOW));
        } else if (HiraishinLearning.knowsFormula(player)) {
            text.append("\n ").append(HiraishinLearning.raijinBlocker(player).copy().withStyle(ChatFormatting.GRAY));
        } else {
            Component blocker = HiraishinLearning.formulaBlocker(player);
            text.append("\n ").append((blocker != null ? blocker : Component.translatable("command.noirm.hiraishin.find_scroll")).copy()
                    .withStyle(ChatFormatting.GRAY));
        }
        int count = HiraishinMarks.get(source.getServer()).marks(player.getUUID()).size();
        text.append("\n ").append(Component.translatable("command.noirm.hiraishin.mark_count", count, HiraishinMarking.cap(player)));
        source.sendSuccess(() -> text, false);
        return 1;
    }

    private static int marks(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<HiraishinMark> list = HiraishinMarks.get(source.getServer()).marks(player.getUUID());
        long now = source.getLevel().getGameTime();
        MutableComponent text = Component.translatable("command.noirm.hiraishin.marks_title", list.size(), HiraishinMarking.cap(player))
                .withStyle(ChatFormatting.GOLD);
        for (HiraishinMark mark : list) {
            Vec3 at = HiraishinMarking.resolve(source.getServer(), player.getUUID(), mark);
            String left = mark.expires() < 0 ? "∞" : Math.max(0, (mark.expires() - now) / 20) + "s";
            text.append("\n ").append(Component.literal("#" + mark.id() + " ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("hud.noirm.hiraishin.kind." + mark.kind().name().toLowerCase(java.util.Locale.ROOT)))
                    .append(Component.literal(String.format("  %.0f %.0f %.0f  (%.0fm, %s)", at.x, at.y, at.z,
                            at.distanceTo(player.position()), left)).withStyle(ChatFormatting.GRAY));
        }
        source.sendSuccess(() -> text, false);
        return list.size();
    }

    private static int clear(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int count = HiraishinMarks.get(source.getServer()).clear(player.getUUID());
        HiraishinMarking.sync(player);
        source.sendSuccess(() -> Component.translatable("command.noirm.hiraishin.cleared", count), false);
        return count;
    }

    private static int give(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HiraishinLearning.teachFormula(player);
        HiraishinLearning.tryTeachRaijin(player, true);
        ItemStack kunai = new ItemStack(HiraishinItems.KUNAI.get(), 16);
        if (!player.getInventory().add(kunai)) {
            player.drop(kunai, false);
        }
        HiraishinMarking.sync(player);
        source.sendSuccess(() -> Component.translatable("command.noirm.hiraishin.given"), false);
        return 1;
    }

    private static int legend(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SparringNinja ninja = NoirmEntities.SPARRING_NINJA.get().create(source.getLevel());
        var clan = WorldClans.get(source.getServer()).find("uzumaki").orElse(null);
        if (ninja == null) {
            return 0;
        }
        Vec3 at = player.position().add(player.getLookAngle().multiply(6, 0, 6));
        ninja.moveTo(at.x, player.getY(), at.z, player.getYRot() + 180, 0);
        if (clan != null) {
            ninja.joinClan(clan);
        }
        ninja.setCombatStats(45, 45);
        HiraishinLearning.teachNpc(ninja, 5);
        ninja.setHealth(ninja.getMaxHealth());
        source.getLevel().addFreshEntity(ninja);
        source.sendSuccess(() -> Component.translatable("command.noirm.hiraishin.legend"), false);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        MutableComponent text = Component.translatable("command.noirm.genjutsu.list_title").withStyle(ChatFormatting.DARK_PURPLE);
        GenjutsuScenes.all().forEach((id, scene) -> text.append("\n ").append(Component.literal(id.toString()).withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal("  " + scene.jutsu() + "  " + scene.duration() + "t").withStyle(ChatFormatting.GRAY)));
        source.sendSuccess(() -> text, false);
        return GenjutsuScenes.all().size();
    }

    private static int test(CommandSourceStack source, ResourceLocation id, Collection<? extends Entity> targets) throws CommandSyntaxException {
        GenjutsuScene scene = GenjutsuScenes.get(id);
        if (scene == null) {
            return Cmd.fail(source, "command.noirm.genjutsu.unknown", id.toString());
        }
        Collection<? extends Entity> victims = targets != null ? targets : List.of(source.getPlayerOrException());
        int caught = 0;
        for (Entity entity : victims) {
            if (!(entity instanceof LivingEntity victim)) {
                continue;
            }
            // The caster: whoever else stands nearest (so the copies and phantoms have a face), or the one who typed it.
            LivingEntity caster = source.getLevel().getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(16),
                            e -> e != victim && e.isAlive() && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand))
                    .stream().min(java.util.Comparator.<LivingEntity>comparingInt(e -> e instanceof SparringNinja || e instanceof net.minecraft.world.entity.player.Player ? 0 : 1)
                            .thenComparingDouble(e -> e.distanceToSqr(victim)))
                    .orElse(source.getEntity() instanceof LivingEntity self && self != victim ? self : victim);
            Vec3 decoy = caster.position().add(caster.position().subtract(victim.position()).normalize().cross(new Vec3(0, 1, 0)).scale(3));
            if (GenjutsuMinds.enterScene(caster, victim, id, scene.duration(), decoy)) {
                caught++;
            }
        }
        int count = caught;
        source.sendSuccess(() -> Component.translatable("command.noirm.genjutsu.tested", id.toString(), count), false);
        return caught;
    }
}

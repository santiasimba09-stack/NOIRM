package com.noirm.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.noirm.Noirm;
import com.noirm.entity.ShadowClone;
import com.noirm.entity.SparringNinja;
import com.noirm.entity.TailedBeast;
import com.noirm.entity.TrainingDummy;
import com.noirm.jutsu.JutsuProjectile;
import com.noirm.world.Destruction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * {@code /noirm world …}: the mod's world settings and a quick tidy-up (cheats).
 * <ul>
 *   <li>{@code destruction on|off} — whether jutsu break terrain (the {@code noirmTerrainDamage} rule)</li>
 *   <li>{@code gore on|off} — bruises, dirt, soot and cuts on fighters, and severed limbs (the {@code noirmGore} rule)</li>
 *   <li>{@code regen <seconds>} — how long broken terrain takes to grow back (0 = never; {@code noirmTerrainRegen})</li>
 *   <li>{@code cleanup [radius]} — removes ninjas (not your crew, masters or village people), beasts,
 *       clones, dummies and stray jutsu around you</li>
 * </ul>
 * Building a village on the spot is {@code /noirm village <size> [look]}.
 */
@Mod.EventBusSubscriber(modid = Noirm.MOD_ID)
public final class WorldCommand {
    static final int DEFAULT_CLEANUP_RADIUS = 48;

    private WorldCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("noirm").then(Commands.literal("world").requires(s -> s.hasPermission(Cmd.CHEATS))
                .then(Commands.literal("destruction")
                        .then(Commands.literal("on").executes(ctx -> destruction(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> destruction(ctx.getSource(), false))))
                .then(Commands.literal("gore")
                        .then(Commands.literal("on").executes(ctx -> gore(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> gore(ctx.getSource(), false))))
                .then(Commands.literal("regen").then(Commands.argument("seconds", IntegerArgumentType.integer(0, 86400))
                        .executes(ctx -> regen(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
                .then(Commands.literal("cleanup")
                        .executes(ctx -> cleanup(ctx.getSource(), DEFAULT_CLEANUP_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
                                .executes(ctx -> cleanup(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))));
    }

    static int destruction(CommandSourceStack source, boolean on) {
        source.getLevel().getGameRules().getRule(Destruction.TERRAIN_DAMAGE).set(on, source.getServer());
        return Cmd.ok(source, on ? "command.noirm.world.destruction_on" : "command.noirm.world.destruction_off");
    }

    static int gore(CommandSourceStack source, boolean on) {
        source.getLevel().getGameRules().getRule(com.noirm.gore.Gore.GORE).set(on, source.getServer());
        return Cmd.ok(source, on ? "command.noirm.world.gore_on" : "command.noirm.world.gore_off");
    }

    static int regen(CommandSourceStack source, int seconds) {
        source.getLevel().getGameRules().getRule(Destruction.TERRAIN_REGEN).set(seconds, source.getServer());
        return Cmd.ok(source, "command.noirm.world.regen", seconds);
    }

    static int cleanup(CommandSourceStack source, int radius) {
        AABB area = new AABB(source.getPosition(), source.getPosition()).inflate(radius);
        List<Entity> doomed = source.getLevel().getEntitiesOfClass(Entity.class, area, WorldCommand::tidyUp);
        doomed.forEach(Entity::discard);
        return Cmd.ok(source, "command.noirm.world.cleaned", doomed.size(), radius);
    }

    /** What a cleanup removes: fighters and leftovers, never crew mates, masters or villagers. */
    static boolean tidyUp(Entity entity) {
        if (entity instanceof com.noirm.entity.MasterNinja) {
            return false;
        }
        if (entity instanceof SparringNinja ninja) {
            return !ninja.inCrew() && !ninja.isResident();
        }
        return entity instanceof TailedBeast || entity instanceof ShadowClone || entity instanceof TrainingDummy
                || entity instanceof JutsuProjectile || entity instanceof com.noirm.gore.SeveredLimb;
    }
}

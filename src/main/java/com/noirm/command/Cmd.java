package com.noirm.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noirm.player.ShinobiCapability;
import com.noirm.player.ShinobiData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Small helpers shared by the {@code /noirm} commands. */
final class Cmd {
    /** Permission level 2 = "cheats enabled" in single-player, operator on servers. */
    static final int CHEATS = 2;

    private Cmd() {}

    static ServerPlayer player(CommandSourceStack source) throws CommandSyntaxException {
        return source.getPlayerOrException();
    }

    static ShinobiData data(CommandSourceStack source) throws CommandSyntaxException {
        return ShinobiCapability.get(player(source)).orElseThrow(() -> new IllegalStateException("No shinobi data"));
    }

    static int ok(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> Component.translatable(key, args), false);
        return 1;
    }

    static int fail(CommandSourceStack source, String key, Object... args) {
        source.sendFailure(Component.translatable(key, args));
        return 0;
    }

    /** "tools" or "noirm:tools" → noirm:tools; null if it isn't a valid id. */
    static net.minecraft.resources.ResourceLocation id(String text) {
        String trimmed = text.trim().toLowerCase(java.util.Locale.ROOT);
        return net.minecraft.resources.ResourceLocation.tryParse(trimmed.contains(":") ? trimmed : com.noirm.Noirm.MOD_ID + ":" + trimmed);
    }

    /** Suggests ids, the mod's own without their {@code noirm:} prefix. */
    static java.util.stream.Stream<String> shortIds(java.util.Collection<net.minecraft.resources.ResourceLocation> ids) {
        return ids.stream().sorted().map(id -> id.getNamespace().equals(com.noirm.Noirm.MOD_ID) ? id.getPath() : id.toString());
    }

    /** Where to put something spawned by a command: three blocks in front of whoever ran it. */
    static Vec3 inFront(CommandSourceStack source, double distance) {
        Vec3 at = source.getPosition();
        Entity entity = source.getEntity();
        if (entity != null) {
            Vec3 look = entity.getViewVector(1f);
            Vec3 flat = new Vec3(look.x, 0, look.z);
            if (flat.lengthSqr() > 1.0E-4) {
                at = at.add(flat.normalize().scale(distance));
            }
        }
        return at;
    }

    /** The living entity of {@code type} closest to where {@code viewer} is looking, within {@code range} blocks. */
    static <T extends LivingEntity> T lookedAt(Entity viewer, Class<T> type, double range) {
        Vec3 eye = viewer.getEyePosition();
        Vec3 look = viewer.getViewVector(1f);
        T best = null;
        double bestScore = Double.MAX_VALUE;
        for (T candidate : viewer.level().getEntitiesOfClass(type, new AABB(eye, eye).inflate(range), e -> e != viewer && e.isAlive())) {
            Vec3 to = candidate.getBoundingBox().getCenter().subtract(eye);
            double along = to.dot(look);
            if (along <= 0 || along > range) {
                continue;
            }
            double off = to.subtract(look.scale(along)).length();
            if (off > 1.5 + candidate.getBbWidth()) {
                continue;
            }
            double score = off * 4 + along;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }
}

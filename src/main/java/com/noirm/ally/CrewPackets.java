package com.noirm.ally;

import com.noirm.entity.SparringNinja;
import com.noirm.player.ShinobiCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Orders for the whole crew at once, from the crew order screen (the K key). */
public final class CrewPackets {
    private CrewPackets() {}

    public enum Action { ORDER, COME, FALL_BACK, ATTACK, BEHAVIOUR }

    public record Orders(Action action, String arg) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeEnum(action);
            buf.writeUtf(arg, 32);
        }

        public static Orders decode(FriendlyByteBuf buf) {
            return new Orders(buf.readEnum(Action.class), buf.readUtf(32));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().setPacketHandled(true);
            if (player != null) {
                apply(player, action, arg);
            }
        }
    }

    /** Carries out a crew-wide order; returns how many crew mates took it (public for tests). */
    public static int apply(ServerPlayer player, Action action, String arg) {
        List<SparringNinja> crew = loaded(player);
        if (crew.isEmpty()) {
            player.displayClientMessage(Component.translatable("command.noirm.crew.empty").withStyle(ChatFormatting.GRAY), true);
            return 0;
        }
        LivingEntity target = action == Action.ATTACK ? lookTarget(player) : null;
        if (action == Action.ATTACK && target == null) {
            player.displayClientMessage(Component.translatable("command.noirm.crew.no_enemy").withStyle(ChatFormatting.GRAY), true);
            return 0;
        }
        int count = 0;
        for (SparringNinja ninja : crew) {
            if ((action == Action.ORDER || action == Action.ATTACK) && !AllyMind.obeys(ninja)) {
                player.displayClientMessage(Component.translatable("message.noirm.ally.refuses", ninja.getDisplayName()), true);
                continue;
            }
            switch (action) {
                case ORDER -> {
                    try {
                        CrewBrain.giveOrder(ninja, Order.valueOf(arg), player);
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                }
                case COME -> CrewBrain.come(ninja, player);
                case FALL_BACK -> CrewBrain.fallBack(ninja, player);
                case ATTACK -> CrewBrain.attack(ninja, target);
                case BEHAVIOUR -> {
                    try {
                        ninja.allyState().setBehaviour(Behaviour.valueOf(arg));
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                }
            }
            count++;
        }
        player.displayClientMessage(Component.translatable("message.noirm.crew.order." + action.name().toLowerCase(java.util.Locale.ROOT), count)
                .withStyle(ChatFormatting.AQUA), true);
        return count;
    }

    /** The crew mates loaded anywhere on the server. */
    public static List<SparringNinja> loaded(ServerPlayer player) {
        List<SparringNinja> found = new ArrayList<>();
        var data = ShinobiCapability.get(player).orElse(null);
        if (data == null) {
            return found;
        }
        for (UUID id : data.crew()) {
            for (ServerLevel level : player.server.getAllLevels()) {
                Entity entity = level.getEntity(id);
                if (entity instanceof SparringNinja ninja && ninja.isAlive()) {
                    found.add(ninja);
                    break;
                }
            }
        }
        return found;
    }

    /** The enemy under your crosshair (up to 40 blocks), not one of your own. */
    static LivingEntity lookTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(40),
                e -> e != player && e.isAlive() && !Crew.sameSide(player, e))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double along = to.dot(look);
            if (along <= 0 || along > 40) {
                continue;
            }
            double off = to.subtract(look.scale(along)).length();
            if (off > 1.5 + e.getBbWidth()) {
                continue;
            }
            double score = off * 4 + along;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }
}
